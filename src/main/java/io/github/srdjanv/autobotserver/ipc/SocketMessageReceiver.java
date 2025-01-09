package io.github.srdjanv.autobotserver.ipc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.srdjanv.autobotserver.Config;
import io.github.srdjanv.autobotserver.ipc.messages.IpcMessage;
import io.github.srdjanv.autobotserver.ipc.messages.MessageListener;
import lombok.extern.slf4j.Slf4j;
import org.newsclub.net.unix.AFUNIXSocket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class SocketMessageReceiver implements AutoCloseable {
    private final Config config;
    private final ObjectMapper mapper;
    public final BufferedReader in;
    public final Map<IpcMessage, Collection<MessageListener>> handlers;
    private final IpcBotHandler ipcBotHandler;

    public SocketMessageReceiver(Config config, ObjectMapper mapper, AFUNIXSocket socket, Map<IpcMessage, Collection<MessageListener>> handlers, IpcBotHandler ipcBotHandler) throws IOException {
        this.config = config;
        this.mapper = mapper;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.handlers = handlers;
        this.ipcBotHandler = ipcBotHandler;
    }

    public void readMessage() throws IOException {
        char delimiter = config.messageDelimiter();
        List<Character> responses = new ArrayList<>();
        int steamChar;
        while ((steamChar = in.read()) != delimiter) {
            if (steamChar == -1) {
                log.warn("Unexpected end of stream");
                return;
            }
            responses.add((char) steamChar);
        }
        String string = responses.stream().map(String::valueOf).collect(Collectors.joining());
        ObjectNode objectNode = (ObjectNode) mapper.readTree(string);
        Optional<IpcMessage> optionalResponseType = IpcMessage.fromReceive(objectNode.get("type").asText());
        if (optionalResponseType.isEmpty()) {
            log.warn("Received a message from client with unknown response type: {}", string);
            return;
        }
        IpcMessage responseType = optionalResponseType.get();
        Collection<MessageListener> messageListeners = handlers.get(responseType);
        if (messageListeners == null) {
            log.warn("No listeners registered for response: {}", responseType);
            return;
        }
        for (MessageListener listener : messageListeners) {
            if (listener.listener() != null) {
                JsonNode data = objectNode.get("data");
                if (data == null) {
                    log.warn("Received a message from client without data: {}", string);
                    continue;
                }
                switch (responseType) {
                    case Trades, Inventory, Pricelist -> {
                        log.info("Received message from {}", responseType);
                    }
                    default -> {
                        log.info("Received message from {}, data: {}", responseType, data);
                    }
                }
                listener.listener().onMessage(data, ipcBotHandler);
            }
        }
    }

    @Override
    public void close() throws Exception {
        in.close();
    }
}
