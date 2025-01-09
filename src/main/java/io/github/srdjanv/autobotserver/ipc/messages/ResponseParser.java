package io.github.srdjanv.autobotserver.ipc.messages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@FunctionalInterface
public interface ResponseParser<T> {
    T parse(ObjectMapper objectMapper, JsonNode node) throws JsonProcessingException;
}
