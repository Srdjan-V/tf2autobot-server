package io.github.srdjanv.autobotserver.ipc.messages;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
@Accessors(fluent = true)
public enum IpcMessage {
    Info("getInfo", "info"),
    KeyPrice("getKeyPrices", "keyPrices"),
    Pricelist("getPricelist", "pricelist"),
    Trades("getTrades", "polldata"),
    Item_Remove("removeItem", "itemRemoved"),
    Item_Update("updateItem", "itemUpdated"),
    Item_Add("addItem", "itemAdded"),
    Inventory("getInventory", "inventory"),
    UserInventory("getUserInventory", "userInventory"),
    Halt("haltBot", "haltStatus"),
    HaltStatus("getHaltStatus", "haltStatus");

    @NotNull
    private final String send;
    @Nullable
    private final String receive;

    IpcMessage(@NotNull String send, @Nullable String receive) {
        this.send = send;
        this.receive = receive;
    }

    public static Optional<IpcMessage> fromSend(String type) {
        IpcMessage result = null;
        for (IpcMessage ipcMessage : values()) {
            if (StringUtils.equals(ipcMessage.send, type)) {
                result = ipcMessage;
                break;
            }
        }
        return Optional.ofNullable(result);
    }

    public static List<IpcMessage> fromReceive(String type) {
        List<IpcMessage> result = new ArrayList<>();
        for (IpcMessage ipcMessage : values()) {
            if (StringUtils.equals(ipcMessage.receive, type)) {
                result.add(ipcMessage);
            }
        }
        return result;
    }
}
