package io.github.srdjanv.autobotserver.ipc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.srdjanv.autobotserver.Config;
import io.github.srdjanv.autobotserver.ipc.messages.IpcMessage;
import io.github.srdjanv.autobotserver.ipc.messages.Message;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
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
        log.info("AutobotServer starting...");
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
        Path socketFile = Path.of(FileUtils.getTempDirectoryPath()).resolve("app." + config.socketId());

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

    private void registerBotHandler(BotInfo info, IpcBotHandler handler) {
        log.info("Registered bot: {}", info);
        long botId = Long.parseUnsignedLong(info.id());
        IpcBotHandler put = idBotHandlerMap.put(botId, handler);
        if (put != null) {
            try {
                put.close();
            } catch (Exception e) {
                log.error("Error closing old ipc bot handler", e);
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

    public List<Bot> getAllBots() {
        return idBotHandlerMap.entrySet().stream().map(entry -> {
            return new Bot(entry.getKey(), entry.getValue());
        }).toList();
    }

    @Override
    public void close() throws Exception {
        List<IpcBotHandler> list = new ArrayList<>(idBotHandlerMap.values());
        idBotHandlerMap.clear();
        for (IpcBotHandler value : list) {
            value.close();
        }
    }

    public record Bot(long botId, IpcBotHandler handler) {
    }
}
