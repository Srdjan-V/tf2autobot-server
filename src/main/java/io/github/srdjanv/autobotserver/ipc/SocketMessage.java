package io.github.srdjanv.autobotserver.ipc;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jetbrains.annotations.Nullable;

public abstract class SocketMessage {
    @Nullable
    protected BotInfo botInfo;
    @Nullable
    protected String botId;

    void initialize(BotInfo botInfo) {
        this.botInfo = botInfo;
        this.botId = botInfo.id();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("botInfo", botInfo)
                .toString();
    }
}
