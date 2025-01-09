package io.github.srdjanv.autobotserver.ipc.messages;

import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)

public enum MessageResponseType {
    Info("info"),
    Pricelist("pricelist"),
    Trades("polldata"),
    Item_Removed("itemRemoved"),
    Item_Updated("itemUpdated"),
    Item_Added("itemAdded"),
    Inventory("inventory");

    private final String type;
    MessageResponseType(String type) {
        this.type = type;
    }

    public static MessageResponseType fromString(String type) {
        MessageResponseType result = null;
        for (MessageResponseType responseType : MessageResponseType.values()) {
            if (responseType.type.equals(type)) {
                result = responseType;
                break;
            }
        }
        return result;
    }
}