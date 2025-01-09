package io.github.srdjanv.autobotserver.ipc.messages;

public interface MessageClosable extends AutoCloseable {
    @Override void close();
}
