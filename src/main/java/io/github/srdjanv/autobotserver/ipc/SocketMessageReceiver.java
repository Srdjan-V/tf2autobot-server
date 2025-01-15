package io.github.srdjanv.autobotserver.ipc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.srdjanv.autobotserver.Config;
import io.github.srdjanv.autobotserver.ipc.messages.IpcMessage;
import io.github.srdjanv.autobotserver.ipc.messages.MessageListener;
import lombok.extern.slf4j.Slf4j;
import org.newsclub.net.unix.AFUNIXSocket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class SocketMessageReceiver extends SocketMessage {
    public final Map<IpcMessage, Collection<MessageListener>> handlers;
    public final BufferedReader in;

    public SocketMessageReceiver(IpcBotHandler ipcBotHandler, Config config, ObjectMapper mapper, AFUNIXSocket socket, Map<IpcMessage, Collection<MessageListener>> handlers) throws IOException {
        super(ipcBotHandler, config, mapper);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.handlers = handlers;
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
        List<IpcMessage> ipcMessageList = IpcMessage.fromReceive(objectNode.get("type").asText());
        if (ipcMessageList.isEmpty()) {
            log.warn("BotId: {}, Received a message from client with unknown response type: {}", botId, string);
            return;
        }
        for (IpcMessage responseType : ipcMessageList) {
            Collection<MessageListener> messageListeners = handlers.get(responseType);
            if (messageListeners == null) {
                log.info("BotId: {}, No listeners registered for response: {}", botId, responseType);
                continue;
            }
            JsonNode data = objectNode.get("data");
            if (data == null) {
                log.warn("BotId: {}, Received a message from client without data: {}", botId, string);
                data = NullNode.getInstance();
            }
            switch (responseType) {
                case Trades, Inventory, UserInventory, Pricelist -> {
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

}
