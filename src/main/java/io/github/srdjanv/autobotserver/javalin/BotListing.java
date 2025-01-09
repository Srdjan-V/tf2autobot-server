package io.github.srdjanv.autobotserver.javalin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import io.soabase.recordbuilder.core.RecordBuilder;
import org.apache.commons.lang3.StringUtils;

@RecordBuilder
public record BotListing(
        String sku,
        boolean enabled,
        boolean autoprice,
        int min,
        int max,
        int intent,
        Currency buy,
        Currency sell,
        Note note,
        int promoted,
        String group
) {

    public record NodeHandle(ObjectNode node) {
        JsonNode getOr(String propName, JsonNode defaultValue) {
            JsonNode jsonNode = node.get(propName);
            if (jsonNode == null) {
                return defaultValue;
            }
            return jsonNode;
        }
    }

    public static BotListing transform(ObjectNode node) throws IllegalArgumentException {
        BotListingBuilder builder = BotListingBuilder.builder();
        NodeHandle nodeHandle = new NodeHandle(node);

        JsonNode sku = nodeHandle.getOr("sku", TextNode.valueOf(""));
        if (sku.isTextual() && StringUtils.isNotBlank(sku.asText())) {
            builder.sku(sku.asText());
        } else {
            throw new IllegalArgumentException("sku");
        }

        JsonNode enabled = nodeHandle.getOr("enabled", BooleanNode.getTrue());
        if (enabled.isBoolean()) {
            builder.enabled(enabled.asBoolean());
        } else if (enabled.isTextual()) {
            switch (enabled.asText()) {
                case "true" -> builder.enabled(true);
                case "false" -> builder.enabled(false);
                default -> throw new IllegalArgumentException("enabled");
            }
        } else {
            throw new IllegalArgumentException("enabled");
        }

        JsonNode min = nodeHandle.getOr("min", IntNode.valueOf(0));
        if (min.isInt()) {
            builder.min(min.asInt());
        } else if (min.isTextual()) {
            builder.min(Integer.parseInt(min.asText()));
        } else {
            throw new IllegalArgumentException("min");
        }

        JsonNode max = nodeHandle.getOr("max", IntNode.valueOf(1));
        if (max.isInt()) {
            builder.max(max.asInt());
        } else if (max.isTextual()) {
            builder.max(Integer.parseInt(max.asText()));
        } else {
            throw new IllegalArgumentException("max");
        }

        JsonNode intent = nodeHandle.getOr("intent", IntNode.valueOf(2));
        if (intent.isInt()) {
            if (Intent.from(intent.asInt()) == null) {
                throw new IllegalArgumentException("intent");
            }
            builder.intent(intent.asInt());
        } else if (intent.isTextual()) {
            //throw if cant find
            Intent intentEnum = Intent.valueOf(intent.asText());
            builder.intent(intentEnum.intent());
        } else {
            throw new IllegalArgumentException("intent");
        }

        JsonNode autoprice = nodeHandle.getOr("autoprice", BooleanNode.getTrue());
        if (autoprice.isBoolean()) {
            builder.autoprice(autoprice.asBoolean());
        } else if (autoprice.isTextual()) {
            switch (autoprice.asText()) {
                case "true" -> builder.autoprice(true);
                case "false" -> builder.autoprice(false);
                default -> throw new IllegalArgumentException("autoprice");
            }
        } else {
            throw new IllegalArgumentException("autoprice");
        }

        Currency buy = Currency.parseNode(nodeHandle, "buy");
        builder.buy(buy);

        Currency sell = Currency.parseNode(nodeHandle, "sell");
        builder.sell(sell);

        if (buy.isEmpty() && sell.isEmpty()) {
            if (!builder.autoprice()) {
                throw new IllegalArgumentException("autoprice");
            }
        } else {
            builder.autoprice(false);
        }

        builder.note(Note.parseNode(nodeHandle));
        return builder.build();
    }

    record Currency(int keys, double metal) {
        public static Currency parseNode(NodeHandle node, String key) {
            final int keys;
            final double metal;

            JsonNode keysNode = node.getOr(key + ".keys", IntNode.valueOf(0));
            if (keysNode.isInt()) {
                keys = keysNode.asInt();
            } else if (keysNode.isTextual()) {
                keys = Integer.parseInt(keysNode.asText());
            } else {
                throw new IllegalArgumentException("keys");
            }

            JsonNode metalNode = node.getOr(key + ".metal", DoubleNode.valueOf(0));
            if (metalNode.isDouble()) {
                metal = metalNode.asDouble();
            } else if (metalNode.isTextual()) {
                metal = Integer.parseInt(metalNode.asText());
            } else {
                throw new IllegalArgumentException("metal");
            }
            return new Currency(keys, metal);
        }

        @JsonIgnore
        public boolean isEmpty() {
            return keys == 0 && metal == 0;
        }
    }

    record Note(String buy, String sell) {
        public static Note parseNode(NodeHandle node) {
            final String buy, sell;

            JsonNode buyNode = node.getOr("note.buy", NullNode.getInstance());
            if (buyNode.isTextual()) {
                buy = buyNode.asText();
            } else if (buyNode.isNull()) {
                buy = null;
            } else {
                throw new IllegalArgumentException("note.buy is not valid");
            }

            JsonNode sellNode = node.getOr("note.buy", NullNode.getInstance());
            if (sellNode.isTextual()) {
                sell = sellNode.asText();
            } else if (sellNode.isNull()) {
                sell = null;
            } else {
                throw new IllegalArgumentException("note.buy is not valid");
            }
            return new Note(buy, sell);
        }
    }

    public enum Intent {
        Buy(0),
        Sell(1),
        Bank(2);
        private final int intent;

        Intent(int intent) {
            this.intent = intent;
        }

        public int intent() {
            return intent;
        }

        public static Intent from(int code) {
            for (Intent value : values()) {
                if (value.intent() == code) {
                    return value;
                }
            }
            return null;
        }
    }
}
