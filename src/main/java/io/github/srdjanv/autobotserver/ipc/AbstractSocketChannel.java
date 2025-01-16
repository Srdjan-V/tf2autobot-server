package io.github.srdjanv.autobotserver.ipc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.srdjanv.autobotserver.Config;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractSocketChannel implements AutoCloseable {
    private boolean closed;

    @Nullable
    protected BotInfo botInfo;
    @Nullable
    protected String botId;

    protected final IpcBotHandler ipcBotHandler;
    protected final Config config;
    protected final ObjectMapper mapper;

    protected AbstractSocketChannel(IpcBotHandler ipcBotHandler, Config config, ObjectMapper mapper) {
        this.ipcBotHandler = ipcBotHandler;
        this.config = config;
        this.mapper = mapper;
    }

    void initialize(BotInfo botInfo) {
        this.botInfo = botInfo;
        this.botId = botInfo.id();
    }

    protected boolean isSocketActive() {
        return !Thread.currentThread().isInterrupted() || !closed;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("botInfo", botInfo)
                .toString();
    }

    @Override
    public void close() {
        this.closed = true;
    }
}
