package io.github.srdjanv.autobotserver.ipc.messages;

public record MessageListener(
        MessageResponseType type,
        OnMessage listener
) {
}
