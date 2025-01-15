package io.github.srdjanv.autobotserver.ipc.messages;

@FunctionalInterface
public interface MessageCloseable extends AutoCloseable {
    @Override void close();
}
