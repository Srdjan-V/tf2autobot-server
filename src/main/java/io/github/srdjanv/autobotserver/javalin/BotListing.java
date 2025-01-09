package io.github.srdjanv.autobotserver.javalin;

import java.util.Date;

public record BotListing(
        String sku,
        boolean enabled,
        boolean autoprice,
        int min,
        int max,
        int intent,
        Currency buy,
        Currency sell,
        int promoted,
        String group,
        boolean isPartialPriced
) {
    record Currency(int keys, double metal) {}

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
