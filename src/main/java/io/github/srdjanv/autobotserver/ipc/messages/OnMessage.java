package io.github.srdjanv.autobotserver.ipc.messages;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.srdjanv.autobotserver.ipc.IpcBotHandler;

@FunctionalInterface
public interface OnMessage {
    void onMessage(JsonNode node, IpcBotHandler ipcBotHandler);
}
