package io.github.srdjanv.autobotserver.ipc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.srdjanv.autobotserver.Config;
import io.github.srdjanv.autobotserver.ipc.messages.IpcMessage;
import io.github.srdjanv.autobotserver.ipc.messages.MessageListener;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jetbrains.annotations.Nullable;
import org.newsclub.net.unix.AFUNIXSocket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class SocketMessageReceiver extends SocketMessage {
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
        while ((steamChar = in.read()) != delimiter && !Thread.currentThread().isInterrupted()) {
            if (steamChar == -1) {
                log.warn("BotId: {}, Unexpected end of stream", botId);
                return;
            }
            responses.add((char) steamChar);
        }
        String string = responses.stream().map(String::valueOf).collect(Collectors.joining());
        ObjectNode objectNode = (ObjectNode) mapper.readTree(string);
        Optional<IpcMessage> optionalResponseType = IpcMessage.fromReceive(objectNode.get("type").asText());
        if (optionalResponseType.isEmpty()) {
            log.warn("BotId: {}, Received a message from client with unknown response type: {}", botId, string);
            return;
        }
        IpcMessage responseType = optionalResponseType.get();
        Collection<MessageListener> messageListeners = handlers.get(responseType);
        if (messageListeners == null) {
            log.warn("BotId: {}, No listeners registered for response: {}", botId, responseType);
            return;
        }
        JsonNode data = objectNode.get("data");
        if (data == null) {
            log.warn("BotId: {}, Received a message from client without data: {}", botId, string);
            return;
        }
        switch (responseType) {
            case Trades, Inventory, Pricelist -> {
                log.info("BotId: {}, Received message from {}", botId, responseType);
            }
            default -> {
                log.info("BotId: {}, Received message from {}, data: {}", botId, responseType, data);
            }
        }
        for (MessageListener listener : messageListeners) {
            if (listener.listener() != null) {
                listener.listener().onMessage(data, ipcBotHandler);
            }
        }
    }

}
