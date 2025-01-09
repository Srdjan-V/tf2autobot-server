package io.github.srdjanv.autobotserver.javalin;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.srdjanv.autobotserver.ipc.AutobotIpcServer;
import io.github.srdjanv.autobotserver.ipc.BotInfo;
import io.github.srdjanv.autobotserver.ipc.IpcBotHandler;
import io.github.srdjanv.autobotserver.ipc.messages.IpcMessage;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
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
    private final AsyncLoadingCache<Long, JsonNode> priceListCache;
    private final AsyncLoadingCache<Long, JsonNode> tradeListCache;
    private final AsyncLoadingCache<Long, JsonNode> inventoryCache;

    public BotController(AutobotIpcServer server) {
        this.server = server;
        int timeout = server.getConfig().responseCacheTimeout();
        Duration duration = Duration.ofSeconds(timeout);
        priceListCache = Caffeine.newBuilder()
                .expireAfterWrite(duration)
                .buildAsync((key, executor) -> {
                    IpcBotHandler botHandler = server.getBotHandler(key);
                    if (botHandler == null) {
                        return CompletableFuture.failedFuture(new Exception("Bot handler not found"));
                    }
                    return botHandler.awaitResponse(IpcMessage.Pricelist);
                });
        tradeListCache = Caffeine.newBuilder()
                .expireAfterWrite(duration)
                .buildAsync((key, executor) -> {
                    IpcBotHandler botHandler = server.getBotHandler(key);
                    if (botHandler == null) {
                        return CompletableFuture.failedFuture(new Exception("Bot handler not found"));
                    }
                    return botHandler.awaitResponse(IpcMessage.Trades);
                });

        inventoryCache = Caffeine.newBuilder()
                .expireAfterWrite(duration)
                .buildAsync((key, executor) -> {
                    IpcBotHandler botHandler = server.getBotHandler(key);
                    if (botHandler == null) {
                        return CompletableFuture.failedFuture(new Exception("Bot handler not found"));
                    }
                    return botHandler.awaitResponse(IpcMessage.Inventory);
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
        List<BotInfo> botInfos = new ArrayList<>();
        for (IpcBotHandler botHandler : server.getAllBots().values()) {
            BotInfo botInfo = botHandler.botInfo();
            if (botInfo != null) {
                botInfos.add(botInfo);
            }
        }
        ctx.json(botInfos);
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
        IpcBotHandler botHandler = server.getBotHandler(name);
        if (botHandler == null) {
            ctx.status(404);
            ctx.result("Unable to find bot");
            return;
        }

        BotInfo info = botHandler.botInfo();
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
            IpcBotHandler botHandler = server.getBotHandler(botId);
            if (botHandler == null) {
                ctx.status(404);
                ctx.result("Unable to find bot");
                return;
            }
            onValid.accept(botHandler);
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
        response
                .thenAccept(node -> {
                    if (node.isTextual()) {
                        String botResponse = node.asText();
                        if (StringUtils.isBlank(botResponse)) {
                            //empty response, request error probably
                            ctx.status(400);
                            ctx.result("Bot returned empty response, probably invalid request");
                            return;
                        }
                        if (StringUtils.contains(botResponse, "Error")) {
                            ctx.status(400);
                            ctx.result(botResponse);
                            return;
                        }
                    }
                    ctx.json(node.toString());
                })
                .exceptionally(throwable -> {
                    ctx.status(500);
                    ctx.result(ExceptionUtils.getRootCauseMessage(throwable));
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
