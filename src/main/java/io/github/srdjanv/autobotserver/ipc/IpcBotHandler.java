package io.github.srdjanv.autobotserver.ipc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.srdjanv.autobotserver.Config;
import io.github.srdjanv.autobotserver.ipc.messages.MessageClosable;
import io.github.srdjanv.autobotserver.ipc.messages.*;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.newsclub.net.unix.AFUNIXSocket;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
public class IpcBotHandler implements AutoCloseable {
    private final ObjectMapper mapper;
    private final Config config;
    private final AFUNIXSocket socket;
    private boolean closed = false;
    private final Map<MessageResponseType, Collection<MessageListener>> reciverMap = new ConcurrentHashMap<>();
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

    public <T> CompletableFuture<T> awaitParsedResponse(Message message, MessageResponseType responseType, ResponseParser<T> parser) {
        return CompletableFuture.supplyAsync(() -> {
            JsonNode node = awaitResponse(message, responseType).join();
            try {
                return parser.parse(mapper, node);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<JsonNode> awaitResponse(Message message, MessageResponseType responseType) {
        return CompletableFuture.supplyAsync(() -> {
            JsonNode[] data = new JsonNode[1];
            send(message, responseType, (node, botHandler) -> {
                data[0] = node;
            }).join();
            Awaitility.await()
                    .failFast(() -> closed)
                    .atMost(Duration.ofSeconds(config.ipcMessageTimeout()))
                    .until(() -> data[0] != null);
            return data[0];
        });
    }

    public void send(Message message) {
        sendDeque.add(message);
    }

    public CompletableFuture<Void> send(Message message, MessageResponseType type, OnMessage onMessage) {
        return CompletableFuture.runAsync(() -> {
            boolean[] finished = new boolean[1];
            OnMessage wrapped = (node, botHandler) -> {
                try {
                    onMessage.onMessage(node, botHandler);
                } finally {
                    finished[0] = true;
                }
            };
            try (var ignore = registerListener(new MessageListener(type, wrapped))) {
                send(message);
                Awaitility.await()
                        .failFast(() -> closed)
                        .atMost(Duration.ofSeconds(config.ipcMessageTimeout()))
                        .until(() -> finished[0]);
            }
        });
    }

    public MessageClosable registerListener(MessageListener messageListener) {
        Collection<MessageListener> listeners = reciverMap.computeIfAbsent(messageListener.type(), t -> new ConcurrentLinkedDeque<>());
        listeners.add(messageListener);
        return () -> listeners.remove(messageListener);
    }

    @Override public void close() throws Exception {
        List<ScheduledExecutorService> services = List.of(receiverExecutor, senderExecutor);
        services.forEach(ExecutorService::shutdown);
        for (ScheduledExecutorService service : services) {
            service.awaitTermination(5, TimeUnit.SECONDS);
        }
        for (AutoCloseable closeable : List.of(receiver, sender)) {
            closeable.close();
        }
        socket.close();
        closed = true;
    }
}
