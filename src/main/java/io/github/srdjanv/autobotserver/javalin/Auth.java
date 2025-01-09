package io.github.srdjanv.autobotserver.javalin;

import io.github.srdjanv.autobotserver.Config;
import io.javalin.http.Context;
import io.javalin.http.Header;
import io.javalin.http.UnauthorizedResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class Auth {
    private final Config config;

    public Auth(Config config) {
        this.config = config;
    }

    public void handleAccess(Context ctx) {
        if (!config.useAuth()) {
            return;
        }
        var authToken = ctx.header(Header.AUTHORIZATION);
        if (StringUtils.equals(authToken, config.authToken())) {
            return;
        }
        throw new UnauthorizedResponse();
    }
}
