package io.github.srdjanv.autobotserver.ipc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import io.github.srdjanv.autobotserver.Config;
import io.github.srdjanv.autobotserver.ipc.messages.IpcMessage;
import lombok.Getter;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jetbrains.annotations.Nullable;
import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

@Slf4j
public class AutobotIpcServer implements AutoCloseable {
    @Getter
    private final Config config;
    private final ObjectMapper mapper;
    private final Map<Long, IpcBotHandler> idBotHandlerMap = new ConcurrentHashMap<>();
    private final List<BiConsumer<Long, IpcBotHandler>> ipcRegisterCallbacks = new ArrayList<>();
    private final ScheduledExecutorService socketScheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("Socket listener").factory());

    public AutobotIpcServer(Config config) {
        this.config = config;
        this.mapper = new ObjectMapper();
    }

    public void start() {
        log.info("AutobotIpcServer starting...");
        final int seconds = config.ipcSocketAutoRestart();
        socketScheduler.scheduleAtFixedRate(() -> {
            try {
                listenerForClients();
            } catch (Throwable e) {
                log.error("AutobotServer socket listener error, retrying in {} seconds", seconds, e);
            }
        }, 2, seconds, TimeUnit.SECONDS);
    }

    private void listenerForClients() throws IOException {
        Path socketFile = Path.of(config.socketPath());

        try (AFUNIXServerSocket server = AFUNIXServerSocket.newInstance()) {
            server.setReuseAddress(false);

            server.bind(AFUNIXSocketAddress.of(socketFile));
            log.info("server: {}", server);

            while (!Thread.interrupted() && !server.isClosed()) {
                log.info("Waiting for connection...");

                AFUNIXSocket sock = server.accept();
                log.info("Client connected: {}", sock);
                CompletableFuture.runAsync(() -> {
                    try {
                        IpcBotHandler ipcBotHandler = new IpcBotHandler(config, mapper, sock);
                        BotInfo botInfo = ipcBotHandler.awaitParsedResponse(IpcMessage.Info, (objectMapper, node) -> {
                            JsonNode success = Objects.requireNonNullElse(node.get("success"), BooleanNode.getFalse());
                            if (!success.isBoolean() || !success.asBoolean()) {
                                log.error("Not successful {} for {}", node, ipcBotHandler);
                                return null;
                            }
                            JsonNode data = Objects.requireNonNullElse(node.get("data"), BooleanNode.getFalse());
                            if (!data.isObject()) {
                                log.error("Invalid data {} for {}", node, ipcBotHandler);
                                return null;
                            }
                            return objectMapper.treeToValue(data, BotInfo.class);
                        }).join();
                        registerBotHandler(botInfo, ipcBotHandler);
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                        throw new RuntimeException(e);
                    }
                });
            }
        } finally {
            log.info("Server terminated");
        }
    }

    @Synchronized
    private void registerBotHandler(@Nullable BotInfo info, IpcBotHandler handler) {
        if (info == null) {
            log.error("BotInfo resolve ws not successful");
            closeHandler(handler);
            return;
        }
        log.info("Registered bot: {}", info);
        handler.initialize(info);
        long botId = Long.parseUnsignedLong(info.id());
        IpcBotHandler put = idBotHandlerMap.put(botId, handler);
        closeHandler(put);
        ipcRegisterCallbacks.forEach(cb -> cb.accept(botId, handler));
    }

    private void closeHandler(IpcBotHandler handler) {
        if (handler == null) {
            return;
        }
        try {
            handler.close();
        } catch (Exception e) {
            log.error("Error closing {}", handler, e);
        }
    }

    public void registerCallback(BiConsumer<Long, IpcBotHandler> cb) {
        ipcRegisterCallbacks.add(cb);
    }

    public Optional<IpcBotHandler> getBotHandler(long id) {
        IpcBotHandler handler = idBotHandlerMap.get(id);
        if (handler == null) {
            return Optional.empty();
        }
        if (handler.isOpen()) {
            return Optional.of(handler);
        }
        closeHandler(handler);
        return Optional.empty();
    }

    public Optional<IpcBotHandler> getBotHandler(String name) {
        return idBotHandlerMap.values()
                .stream()
                .map(handler -> {
                    BotInfo info = handler.botInfo();
                    if (info == null) {
                        return Optional.<IpcBotHandler>empty();
                    }
                    if (!StringUtils.equals(info.name(), name)) {
                        return Optional.<IpcBotHandler>empty();
                    }
                    long id = Long.parseUnsignedLong(info.id());
                    //used to ensure the handler is open
                    return this.getBotHandler(id);
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    public Map<Long, IpcBotHandler> getAllBots() {
        return Collections.unmodifiableMap(idBotHandlerMap);
    }

    @Override
    public void close() {
        List<IpcBotHandler> list = new ArrayList<>(idBotHandlerMap.values());
        idBotHandlerMap.clear();
        for (IpcBotHandler value : list) {
            closeHandler(value);
        }
        try {
            socketScheduler.shutdown();
            socketScheduler.awaitTermination(20, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("bots", idBotHandlerMap)
                .toString();
    }
}
