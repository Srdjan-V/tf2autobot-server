package io.github.srdjanv.autobotserver.ipc;

import java.util.List;

public record BotInfo(
        String name,
        String id,
        List<String> admins
) {
}
