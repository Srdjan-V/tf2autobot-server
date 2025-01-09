package io.github.srdjanv.autobotserver.javalin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.github.benmanes.caffeine.cache.*;
import io.github.srdjanv.autobotserver.ipc.AutobotIpcServer;
import io.github.srdjanv.autobotserver.ipc.IpcBotHandler;
import io.github.srdjanv.autobotserver.ipc.messages.*;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import static org.eclipse.jetty.http.MimeTypes.Type.APPLICATION_JSON;

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
            String sku = ctx.queryParam("sku");
            if (sku == null) {
                ctx.status(404);
                return;
            }
            CompletableFuture<JsonNode> response = handler.awaitResponse(IpcMessage.Item_Remove, sku);
            handleResponse(ctx, response);
        });
    }

    public void updateItem(Context ctx) {
        getBotHandler(ctx, handler -> {
            String sku = ctx.queryParam("sku");
            if (sku == null) {
                ctx.status(404);
                return;
            }
            CompletableFuture<JsonNode> response = handler.awaitResponse(IpcMessage.Item_Update, extractBotListing(ctx));
            handleResponse(ctx, response);
        });
    }

    public void addItem(Context ctx) {
        getBotHandler(ctx, handler -> {
            String sku = ctx.queryParam("sku");
            if (sku == null) {
                ctx.status(404);
                return;
            }
            CompletableFuture<JsonNode> response = handler.awaitResponse(IpcMessage.Item_Add, extractBotListing(ctx));
            handleResponse(ctx, response);
        });
    }

    private void getBotId(Context ctx, LongConsumer onValid) {
        String id = ctx.queryParam("bot_id");
        if (id == null) {
            ctx.status(400);
            ctx.result("bot_id is required");
            return;
        }
        try {
            long parsed = Long.parseUnsignedLong(id);
            onValid.accept(parsed);
        } catch (NumberFormatException e) {
            ctx.status(400);
            ctx.result("bot_id is not a number");
        }
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

    private void handleResponse(Context ctx, CompletableFuture<JsonNode> response) {
        response
                .thenAccept(node -> {
                    ctx.json(node.toString());
                })
                .exceptionally(throwable -> {
                    ctx.status(500);
                    ctx.result(ExceptionUtils.getMessage(throwable));
                    return null;
                }).join();
    }

    //todo fix
    private BotListing extractBotListing(Context ctx) {
        Map<String, String> ret = new HashMap<>();
        Map<String, List<String>> listMap = ctx.queryParamMap();
        listMap.forEach((k, v) -> {
            if (v == null || v.isEmpty()) {
                return;
            }
            ret.put(k, v.getFirst());
        });
        ret.remove("bot_id");
        ObjectNode listingNode = mapper.valueToTree(ret);
        listingNode.putIfAbsent("enabled", BooleanNode.getTrue());
        listingNode.putIfAbsent("min", IntNode.valueOf(0));
        listingNode.putIfAbsent("max", IntNode.valueOf(1));

        JsonNode intent = listingNode.get("intent");
        if (intent == null) {
            listingNode.set("intent", IntNode.valueOf(2));
        } else if (intent.isTextual()) {
            BotListing.Intent value = BotListing.Intent.valueOf(intent.asText());
            if (value != null) {
                listingNode.putIfAbsent("intent", IntNode.valueOf(value.intent()));
            }
        }

        JsonNode buy = listingNode.get("buy");
        if (buy == null) {
            buy = listingNode.putObject("buy");
        }
        JsonNode sell = listingNode.get("sell");
        if (sell == null) {
            sell = listingNode.putObject("sell");
        }

        if (buy.isObject()) {
            ((ObjectNode) buy).putIfAbsent("keys", IntNode.valueOf(0));
            ((ObjectNode) buy).putIfAbsent("metal", IntNode.valueOf(0));

            listingNode.putIfAbsent("autoprice", BooleanNode.getFalse());
        } else if (!buy.isObject() && sell.isObject()) {
            buy = listingNode.putObject("buy");

            ((ObjectNode) buy).putIfAbsent("keys", IntNode.valueOf(0));
            ((ObjectNode) buy).putIfAbsent("metal", IntNode.valueOf(0));
        }

        if (sell.isObject()) {
            ((ObjectNode) sell).putIfAbsent("keys", IntNode.valueOf(0));
            ((ObjectNode) sell).putIfAbsent("metal", IntNode.valueOf(0));

            listingNode.putIfAbsent("autoprice", BooleanNode.getFalse());
        } else if (!sell.isObject() && buy.isObject()) {
            sell = listingNode.putObject("buy");

            ((ObjectNode) sell).putIfAbsent("keys", IntNode.valueOf(0));
            ((ObjectNode) sell).putIfAbsent("metal", IntNode.valueOf(0));
        }

        JsonNode note = listingNode.get("note");
        if (note == null) {
            // If note parameter is not defined, set both note.buy and note.sell to null.
            note = listingNode.putObject("note");
        }
        if (note.isObject()) {
            ((ObjectNode) note).putIfAbsent("buy", NullNode.getInstance());
            ((ObjectNode) note).putIfAbsent("sell", NullNode.getInstance());
        }

        JsonNode group = listingNode.get("group");
        if (group == null) {
            listingNode.putIfAbsent("group", TextNode.valueOf("all"));
        }

        listingNode.putIfAbsent("autoprice", BooleanNode.getTrue());
        listingNode.putIfAbsent("isPartialPriced", BooleanNode.getFalse());
        try {
            return mapper.treeToValue(listingNode, BotListing.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
