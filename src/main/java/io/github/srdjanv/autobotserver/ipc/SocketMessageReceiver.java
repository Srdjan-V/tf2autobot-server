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
public class SocketMessageReceiver extends AbstractSocketChannel {
    public final Map<IpcMessage, Collection<MessageListener>> handlers;
    public final BufferedReader in;

    public SocketMessageReceiver(IpcBotHandler ipcBotHandler, Config config, ObjectMapper mapper, AFUNIXSocket socket, Map<IpcMessage, Collection<MessageListener>> handlers) throws IOException {
        super(ipcBotHandler, config, mapper);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.handlers = handlers;
    }

    public void readMessage() throws IOException {
        final char delimiter = config.messageDelimiter();
        List<Character> response = new ArrayList<>();
        int streamChar;
        while ((streamChar = in.read()) != delimiter && isSocketActive()) {
            if (streamChar == -1) {
                if (!isSocketActive()) {
                    log.warn("BotId: {}, Unexpected end of stream", botId);
                }
                return;
            }
            response.add((char) streamChar);
        }
        if (!isSocketActive()) {
            if (!response.isEmpty()) {
                log.warn("BotId: {}, discarding response {}, closed stream", botId, response);
            }
            return;
        }
        String responseString = response.stream().map(String::valueOf).collect(Collectors.joining());
        JsonNode jsonNode = mapper.readTree(responseString);
        if (!jsonNode.isObject()) {
            log.error("BotId: {}, Unexpected json node", botId);
            return;
        }
        ObjectNode dataNode = (ObjectNode) jsonNode;
        List<IpcMessage> ipcMessageList = IpcMessage.fromReceive(dataNode.get("type").asText());
        if (ipcMessageList.isEmpty()) {
            log.warn("BotId: {}, Received a message from client with unknown response type: {}", botId, responseString);
            return;
        }
        for (IpcMessage responseType : ipcMessageList) {
            Collection<MessageListener> messageListeners = handlers.get(responseType);
            if (messageListeners == null) {
                log.info("BotId: {}, No listeners registered for response: {}", botId, responseType);
                continue;
            }
            JsonNode data = dataNode.get("data");
            if (data == null) {
                log.warn("BotId: {}, Received a message from client without data: {}", botId, responseString);
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
