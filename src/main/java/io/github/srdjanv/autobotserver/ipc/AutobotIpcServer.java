package io.github.srdjanv.autobotserver.ipc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.srdjanv.autobotserver.Config;
import io.github.srdjanv.autobotserver.ipc.messages.IpcMessage;
import lombok.Getter;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

@Slf4j
public class AutobotIpcServer implements AutoCloseable {
    @Getter
    private final Config config;
    private final ObjectMapper mapper;
    private final Map<Long, IpcBotHandler> idBotHandlerMap = new ConcurrentHashMap<>();
    private final List<BiConsumer<Long, IpcBotHandler>> registerCallbacks = new ArrayList<>();

    public AutobotIpcServer(Config config) {
        this.config = config;
        this.mapper = new ObjectMapper();
    }

    public void start() {
        log.info("AutobotIpcServer starting...");
        CompletableFuture.runAsync(() -> {
            try {
                doStart();
            } catch (IOException e) {
                log.error("AutobotServer start failed", e);
                throw new UncheckedIOException(e);
            }
        });
    }

    private void doStart() throws IOException {
        Path socketFile = Path.of(config.socketPath());

        try (AFUNIXServerSocket server = AFUNIXServerSocket.newInstance()) {
            server.setReuseAddress(false);

            server.bind(AFUNIXSocketAddress.of(socketFile));
            log.info("server: {}", server);

            while (!Thread.interrupted() && !server.isClosed()) {
                log.info("Waiting for connection...");

                AFUNIXSocket sock = server.accept();
                CompletableFuture.runAsync(() -> {
                    try {
                        IpcBotHandler ipcBotHandler = new IpcBotHandler(config, mapper, sock);
                        BotInfo botInfo = ipcBotHandler.awaitParsedResponse(
                                IpcMessage.Info,
                                (objectMapper, node) -> objectMapper.treeToValue(node, BotInfo.class)
                        ).join();
                        ipcBotHandler.initialize(botInfo);
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
    private void registerBotHandler(BotInfo info, IpcBotHandler handler) {
        log.info("Registered bot: {}", info);
        long botId = Long.parseUnsignedLong(info.id());
        IpcBotHandler put = idBotHandlerMap.put(botId, handler);
        if (put != null) {
            try {
                put.close();
            } catch (Exception e) {
                log.error("Error closing old {}", put, e);
            }
        }
        registerCallbacks.forEach(cb -> cb.accept(botId, handler));
    }

    public void registerCallback(BiConsumer<Long, IpcBotHandler> cb) {
        registerCallbacks.add(cb);
    }

    public IpcBotHandler getBotHandler(long id) {
        return idBotHandlerMap.get(id);
    }

    public IpcBotHandler getBotHandler(String name) {
        return idBotHandlerMap.values()
                .stream()
                .filter(ipcBotHandler -> {
                    BotInfo info = ipcBotHandler.botInfo();
                    if (info == null) {
                        return false;
                    }
                    return StringUtils.equals(info.name(), name);
                })
                .findFirst()
                .orElse(null);
    }

    public Map<Long, IpcBotHandler> getAllBots() {
        return Collections.unmodifiableMap(idBotHandlerMap);
    }

    @Override
    public void close() throws Exception {
        List<IpcBotHandler> list = new ArrayList<>(idBotHandlerMap.values());
        idBotHandlerMap.clear();
        for (IpcBotHandler value : list) {
            value.close();
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("bots", idBotHandlerMap)
                .toString();
    }
}
