package io.github.srdjanv.autobotserver.ipc.messages;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.srdjanv.autobotserver.ipc.IpcBotHandler;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface OnMessage {
    void onMessage(@NotNull JsonNode node, IpcBotHandler ipcBotHandler);
}
