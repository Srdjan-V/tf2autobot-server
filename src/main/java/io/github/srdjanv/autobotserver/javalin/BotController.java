package io.github.srdjanv.autobotserver.javalin;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.srdjanv.autobotserver.ipc.AutobotIpcServer;
import io.github.srdjanv.autobotserver.ipc.BotInfo;
import io.github.srdjanv.autobotserver.ipc.IpcBotHandler;
import io.github.srdjanv.autobotserver.ipc.messages.IpcMessage;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

@Slf4j
public class BotController {
    private final ObjectMapper mapper;
    private final AutobotIpcServer server;
    private final AsyncLoadingCache<Long, JsonNode> keyPricesCache;
    private final AsyncLoadingCache<Long, JsonNode> priceListCache;
    private final AsyncLoadingCache<Long, JsonNode> tradeListCache;
    private final AsyncLoadingCache<Long, JsonNode> inventoryCache;
    private final AsyncLoadingCache<UserInvReqKey, JsonNode> userInventoryCache;

    public BotController(AutobotIpcServer server) {
        this.server = server;
        int timeout = server.getConfig().responseCacheTimeout();
        Duration duration = Duration.ofSeconds(timeout);

        keyPricesCache = Caffeine.newBuilder()
                .expireAfterWrite(duration)
                .buildAsync((key, executor) -> {
                    Optional<IpcBotHandler> botHandler = server.getBotHandler(key);
                    if (botHandler.isEmpty()) {
                        return CompletableFuture.failedFuture(new Exception("Bot handler not found"));
                    }
                    return botHandler.get().awaitResponse(IpcMessage.KeyPrice);
                });
        priceListCache = Caffeine.newBuilder()
                .expireAfterWrite(duration)
                .buildAsync((key, executor) -> {
                    Optional<IpcBotHandler> botHandler = server.getBotHandler(key);
                    if (botHandler.isEmpty()) {
                        return CompletableFuture.failedFuture(new Exception("Bot handler not found"));
                    }
                    return botHandler.get().awaitResponse(IpcMessage.Pricelist);
                });
        tradeListCache = Caffeine.newBuilder()
                .expireAfterWrite(duration)
                .buildAsync((key, executor) -> {
                    Optional<IpcBotHandler> botHandler = server.getBotHandler(key);
                    if (botHandler.isEmpty()) {
                        return CompletableFuture.failedFuture(new Exception("Bot handler not found"));
                    }
                    return botHandler.get().awaitResponse(IpcMessage.Trades);
                });

        inventoryCache = Caffeine.newBuilder()
                .expireAfterWrite(duration)
                .buildAsync((key, executor) -> {
                    Optional<IpcBotHandler> botHandler = server.getBotHandler(key);
                    if (botHandler.isEmpty()) {
                        return CompletableFuture.failedFuture(new Exception("Bot handler not found"));
                    }
                    return botHandler.get().awaitResponse(IpcMessage.Inventory);
                });

        userInventoryCache = Caffeine.newBuilder()
                .expireAfterWrite(duration)
                .buildAsync((key, executor) -> {
                    Optional<IpcBotHandler> botHandler = server.getBotHandler(key.bot());
                    if (botHandler.isEmpty()) {
                        return CompletableFuture.failedFuture(new Exception("Bot handler not found"));
                    }
                    return botHandler.get().awaitResponse(IpcMessage.UserInventory, Long.toUnsignedString(key.user()));
                });

        server.registerCallback((botId, handler) -> {
            handler.registerListener(IpcMessage.Pricelist, (node, ipcBotHandler) -> {
                priceListCache.put(botId, CompletableFuture.completedFuture(node));
            });
            handler.registerListener(IpcMessage.Trades, (node, ipcBotHandler) -> {
                tradeListCache.put(botId, CompletableFuture.completedFuture(node));
            });
            handler.registerListener(IpcMessage.Inventory, (node, ipcBotHandler) -> {
                inventoryCache.put(botId, CompletableFuture.completedFuture(node));
            });
        });

        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public void getBots(Context ctx) {
        CompletableFuture<JsonNode> data = CompletableFuture.supplyAsync(() -> {
            List<BotInfo> botInfos = new ArrayList<>();
            for (IpcBotHandler botHandler : server.getAllBots().values()) {
                BotInfo botInfo = botHandler.botInfo();
                if (botInfo != null) {
                    botInfos.add(botInfo);
                }
            }
            ObjectNode response = mapper.createObjectNode();
            response.put("success", true);
            response.set("data", mapper.valueToTree(botInfos));
            return response;
        });
        handleResponse(ctx, data);
    }

    public void haltBot(Context ctx) {
        getBotHandler(ctx, handler -> {
            String halt = ctx.queryParam("halt");
            if (StringUtils.isBlank(halt)) {
                ctx.status(400);
                ctx.result("Invalid halt parameter");
                return;
            }
            Boolean boolHalt = BooleanUtils.toBooleanObject(halt);
            if (boolHalt == null) {
                ctx.status(400);
                ctx.result("Invalid halt parameter");
                return;
            }
            CompletableFuture<JsonNode> response = handler.awaitResponse(IpcMessage.Halt, boolHalt);
            handleResponse(ctx, response);
        });
    }

    public void haltStatus(Context ctx) {
        getBotHandler(ctx, handler -> {
            CompletableFuture<JsonNode> response = handler.awaitResponse(IpcMessage.HaltStatus);
            handleResponse(ctx, response);
        });
    }

    public void getKeyPrices(Context ctx) {
        getBotId(ctx, botId -> {
            CompletableFuture<JsonNode> future = keyPricesCache.get(botId);
            handleResponse(ctx, future);
        });
    }

    public void getPriceList(Context ctx) {
        getBotId(ctx, botId -> {
            CompletableFuture<JsonNode> future = priceListCache.get(botId);
            handleResponse(ctx, future);
        });
    }

    public void getTrades(Context ctx) {
        getBotId(ctx, botId -> {
            CompletableFuture<JsonNode> future = tradeListCache.get(botId);
            handleResponse(ctx, future);
        });
    }

    public void getInventory(Context ctx) {
        getBotId(ctx, botId -> {
            CompletableFuture<JsonNode> future = inventoryCache.get(botId);
            handleResponse(ctx, future);
        });
    }

    record UserInvReqKey(long bot, long user) {
    }

    public void getUserInventory(Context ctx) {
        getBotId(ctx, botId -> {
            String user = ctx.queryParam("user");
            if (user == null) {
                handleResponse(ctx, CompletableFuture.failedFuture(new IllegalArgumentException("User not specified")));
                return;
            }
            long userId;
            try {
                userId = Long.parseUnsignedLong(user);
            } catch (NumberFormatException ex) {
                handleResponse(ctx, CompletableFuture.failedFuture(ex));
                return;
            }
            if (botId == userId) {
                handleResponse(ctx, CompletableFuture.failedFuture(new IllegalArgumentException("User matches bot id")));
                return;
            }
            CompletableFuture<JsonNode> future = userInventoryCache.get(new UserInvReqKey(botId, userId));
            handleResponse(ctx, future);
        });
    }

    public void removeItem(Context ctx) {
        getBotHandler(ctx, handler -> {
            getSku(ctx, sku -> {
                CompletableFuture<JsonNode> response = handler.awaitResponse(IpcMessage.Item_Remove, sku);
                handleResponse(ctx, response);
            });
        });
    }

    public void updateItem(Context ctx) {
        getBotHandler(ctx, handler -> {
            getSku(ctx, sku -> {
                extractBotListing(ctx).ifPresent(botListing -> {
                    CompletableFuture<JsonNode> response = handler.awaitResponse(IpcMessage.Item_Update, botListing);
                    handleResponse(ctx, response);
                });
            });
        });
    }

    public void addItem(Context ctx) {
        getBotHandler(ctx, handler -> {
            getSku(ctx, sku -> {
                extractBotListing(ctx).ifPresent(botListing -> {
                    CompletableFuture<JsonNode> response = handler.awaitResponse(IpcMessage.Item_Add, botListing);
                    handleResponse(ctx, response);
                });
            });
        });
    }

    private void getBotId(Context ctx, LongConsumer onValid) {
        String name = ctx.queryParam("bot_name");
        String id = ctx.queryParam("bot_id");
        if (id == null && name == null) {
            ctx.status(400);
            ctx.result("bot_id or name is required");
            return;
        }
        if (id != null) {
            try {
                long parsed = Long.parseUnsignedLong(id);
                onValid.accept(parsed);
            } catch (NumberFormatException e) {
                ctx.status(400);
                ctx.result("bot_id is not a number");
            }
            return;
        }
        Optional<IpcBotHandler> botHandler = server.getBotHandler(name);
        if (botHandler.isEmpty()) {
            ctx.status(404);
            ctx.result("Unable to find bot");
            return;
        }

        BotInfo info = botHandler.get().botInfo();
        if (info == null) {
            ctx.status(404);
            ctx.result("Unable to find bot");
            return;
        }
        long parsed = Long.parseUnsignedLong(info.id());
        onValid.accept(parsed);
    }

    private void getBotHandler(Context ctx, Consumer<IpcBotHandler> onValid) {
        getBotId(ctx, botId -> {
            Optional<IpcBotHandler> botHandler = server.getBotHandler(botId);
            if (botHandler.isEmpty()) {
                ctx.status(404);
                ctx.result("Unable to find bot");
                return;
            }
            onValid.accept(botHandler.get());
        });
    }

    private void getSku(Context ctx, Consumer<String> onValid) {
        String sku = ctx.queryParam("sku");
        if (sku == null) {
            ctx.status(404);
            ctx.result("Missing sku");
            return;
        }
        onValid.accept(sku);
    }

    private void handleResponse(Context ctx, CompletableFuture<JsonNode> response) {
        response.thenAccept(node -> {
            JsonNode success = Objects.requireNonNullElse(node.get("success"), BooleanNode.getFalse());
            if (!success.isBoolean() || !success.asBoolean()) {
                ctx.status(400);
            }
            ctx.json(node.toString());
        }).exceptionally(throwable -> {
            ObjectNode errorResponse = mapper.createObjectNode();
            errorResponse.put("success", Boolean.FALSE);
            errorResponse.put("data", ExceptionUtils.getRootCauseMessage(throwable));

            ctx.status(500);
            ctx.json(errorResponse.toString());
            return null;
        }).join();
    }

    private Optional<BotListing> extractBotListing(Context ctx) {
        Map<String, String> ret = new HashMap<>();
        Map<String, List<String>> listMap = ctx.queryParamMap();
        listMap.forEach((k, v) -> {
            if (v == null || v.isEmpty()) {
                return;
            }
            ret.put(k, v.getFirst());
        });
        ObjectNode listingNode = mapper.valueToTree(ret);
        try {
            return Optional.ofNullable(BotListing.transform(listingNode));
        } catch (IllegalArgumentException e) {
            ctx.status(400);
            ctx.result(ExceptionUtils.getRootCauseMessage(e));
            return Optional.empty();
        }
    }
}
