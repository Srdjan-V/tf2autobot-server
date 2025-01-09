package io.github.srdjanv.autobotserver.ipc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.srdjanv.autobotserver.Config;
import io.github.srdjanv.autobotserver.ipc.messages.MessageClosable;
import io.github.srdjanv.autobotserver.ipc.messages.*;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.awaitility.Awaitility;
import org.jetbrains.annotations.Nullable;
import org.newsclub.net.unix.AFUNIXSocket;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
public class IpcBotHandler implements AutoCloseable {
    @Nullable
    @Getter
    @Accessors(fluent = true)
    //volatile is probably not needed
    private volatile BotInfo botInfo;

    private final ObjectMapper mapper;
    private final Config config;
    private final AFUNIXSocket socket;
    private boolean closed = false;
    private final Map<IpcMessage, Collection<MessageListener>> reciverMap = new ConcurrentHashMap<>();
    private final Deque<Message> sendDeque = new ConcurrentLinkedDeque<>();
    private final ScheduledExecutorService receiverExecutor;
    private final SocketMessageReceiver receiver;

    private final ScheduledExecutorService senderExecutor;
    private final SocketMessageSender sender;

    public IpcBotHandler(Config config, ObjectMapper objectMapper, AFUNIXSocket socket) throws IOException {
        this.socket = socket;
        this.config = config;
        mapper = objectMapper;
        if (socket.checkConnectionClosed()) {
            log.warn("Peer closed socket right after connecting");
            throw new IOException("Peer closed socket right after connecting");
        }

        receiverExecutor = Executors.newSingleThreadScheduledExecutor();
        receiver = new SocketMessageReceiver(config, objectMapper, socket, reciverMap, this);
        receiverExecutor.scheduleAtFixedRate(() -> {
            try {
                receiver.readMessage();
            } catch (Throwable e) {
                log.error("Error reading message", e);
                throw new RuntimeException(e);
            }
        }, 0, 1500, TimeUnit.MILLISECONDS);

        senderExecutor = Executors.newSingleThreadScheduledExecutor();
        sender = new SocketMessageSender(config, objectMapper, socket, sendDeque);
        senderExecutor.scheduleAtFixedRate(() -> {
            try {
                sender.sendMessage();
            } catch (Throwable e) {
                log.error("Error sending message", e);
                throw new RuntimeException(e);
            }
        }, 0, 1500, TimeUnit.MILLISECONDS);
    }

    public <T> CompletableFuture<T> awaitParsedResponse(IpcMessage message, ResponseParser<T> parser) {
        return awaitParsedResponse(new Message(message), parser);
    }

    public <T> CompletableFuture<T> awaitParsedResponse(IpcMessage message, Object data, ResponseParser<T> parser) {
        return awaitParsedResponse(new Message(message, data), parser);
    }

    public <T> CompletableFuture<T> awaitParsedResponse(Message message, ResponseParser<T> parser) {
        return CompletableFuture.supplyAsync(() -> {
            JsonNode node = awaitResponse(message).join();
            try {
                return parser.parse(mapper, node);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<JsonNode> awaitResponse(IpcMessage message) {
        return awaitResponse(new Message(message));
    }

    public CompletableFuture<JsonNode> awaitResponse(IpcMessage message, Object data) {
        return awaitResponse(new Message(message, data));
    }

    public CompletableFuture<JsonNode> awaitResponse(Message message) {
        return CompletableFuture.supplyAsync(() -> {
            JsonNode[] data = new JsonNode[1];
            send(message, (node, botHandler) -> {
                data[0] = node;
            }).join();
            Awaitility.await()
                    .failFast(() -> closed)
                    .atMost(Duration.ofSeconds(config.ipcMessageTimeout()))
                    .until(() -> data[0] != null);
            return data[0];
        });
    }

    public CompletableFuture<Void> send(Message message, OnMessage onMessage) {
        Optional<IpcMessage> ipcMessage = IpcMessage.fromSend(message.type());
        if (ipcMessage.isEmpty() || ipcMessage.get().receive() == null) {
            return CompletableFuture.failedFuture(new Exception("Invalid message type"));
        }
        return CompletableFuture.runAsync(() -> {
            boolean[] finished = new boolean[1];
            OnMessage wrapped = (node, botHandler) -> {
                try {
                    onMessage.onMessage(node, botHandler);
                } finally {
                    finished[0] = true;
                }
            };
            try (var ignore = registerListener(new MessageListener(ipcMessage.get(), wrapped))) {
                send(message);
                Awaitility.await()
                        .failFast(() -> closed)
                        .atMost(Duration.ofSeconds(config.ipcMessageTimeout()))
                        .until(() -> finished[0]);
            }
        });
    }

    public void send(Message message) {
        sendDeque.add(message);
    }

    public MessageClosable registerListener(IpcMessage type, OnMessage listener) {
        return registerListener(new MessageListener(type, listener));
    }

    public MessageClosable registerListener(MessageListener messageListener) {
        Collection<MessageListener> listeners = reciverMap.computeIfAbsent(messageListener.type(), t -> new ConcurrentLinkedDeque<>());
        listeners.add(messageListener);
        return () -> listeners.remove(messageListener);
    }

    public void initialize(BotInfo botInfo) {
        if (this.botInfo == null) {
            this.botInfo = botInfo;
            receiver.initialize(botInfo);
            sender.initialize(botInfo);
            return;
        }
        log.warn("Bot already initialized, botInfo: {}", this.botInfo);
    }

    @Override
    public void close() throws Exception {
        log.info("Closing {}", this);
        List<ScheduledExecutorService> services = List.of(receiverExecutor, senderExecutor);
        for (ScheduledExecutorService scheduledExecutorService : services) {
            log.info("Closing scheduled executor service: {}", scheduledExecutorService);
            scheduledExecutorService.shutdown();
        }
        for (ScheduledExecutorService service : services) {
            if (!service.awaitTermination(5, TimeUnit.SECONDS)) {
                log.info("Timed out executing service {}", service);
            }
        }
        log.info("Closing socket");
        try {
            socket.close();
        } catch (IOException e) {
            log.error("Error closing socket", e);
        }
        log.info("Finished closing {}", this);
        closed = true;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("botInfo", botInfo)
                .toString();
    }
}
