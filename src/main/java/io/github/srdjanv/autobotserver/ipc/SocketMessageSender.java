package io.github.srdjanv.autobotserver.ipc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.srdjanv.autobotserver.Config;
import io.github.srdjanv.autobotserver.ipc.messages.Message;
import lombok.extern.slf4j.Slf4j;
import org.newsclub.net.unix.AFUNIXSocket;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Deque;

@Slf4j
public class SocketMessageSender implements AutoCloseable {
    private final Config config;
    private final ObjectMapper mapper;
    public final PrintWriter out;
    public final Deque<Message> messages;

    public SocketMessageSender(Config config, ObjectMapper mapper, AFUNIXSocket socket, Deque<Message> messages) throws IOException {
        this.config = config;
        this.mapper = mapper;
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.messages = messages;
    }

    public void sendMessage() throws IOException {
        char delimiter = config.messageDelimiter();
        Message poll = messages.poll();
        if (poll == null) {
            return;
        }
        String message = mapper.writeValueAsString(poll);
        message = message + delimiter;
        log.info("Sending message: {}", message);
        out.write(message);
        out.flush();
    }

    @Override public void close() {
        out.close();
    }
}
