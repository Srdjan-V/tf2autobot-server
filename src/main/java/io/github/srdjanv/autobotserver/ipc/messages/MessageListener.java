package io.github.srdjanv.autobotserver.ipc.messages;

import java.util.Objects;

public record MessageListener(
        IpcMessage type,
        OnMessage listener
) {
    public MessageListener(IpcMessage type, OnMessage listener) {
        this.type = Objects.requireNonNull(type, "type");
        this.listener = Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(type.receive(), "Ipc message has no receive type");
    }
}
