package io.github.srdjanv.autobotserver.ipc.messages;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.Objects;

public record Message(
        String type,
        Object data
) {
    public Message(MessageSendType type) {
        this(type, null);
    }

    public Message(MessageSendType type, Object data) {
        this(type.type(), data);
    }

    public Message(String type, Object data) {
        this.type = Objects.requireNonNull(type);
        this.data = data == null ? JsonNodeFactory.instance.objectNode() : data;
    }

}
