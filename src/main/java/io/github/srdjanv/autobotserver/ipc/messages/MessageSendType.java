package io.github.srdjanv.autobotserver.ipc.messages;

import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public enum MessageSendType {
    Get_Info("getInfo"),
    Get_Pricelist("getPricelist"),
    Get_Trades("getTrades"),
    Remove_Item("removeItem"),
    Update_Item("updateItem"),
    Add_Item("addItem"),
    Inventory("getInventory");

    private final String type;
    MessageSendType(String type) {
        this.type = type;
    }

    public static MessageSendType fromString(String type) {
        MessageSendType result = null;
        for (MessageSendType sendType : MessageSendType.values()) {
            if (sendType.type.equals(type)) {
                result = sendType;
                break;
            }
        }
        return result;
    }
}
