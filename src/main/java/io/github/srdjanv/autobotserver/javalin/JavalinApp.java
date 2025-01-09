package io.github.srdjanv.autobotserver.javalin;

import io.github.srdjanv.autobotserver.ipc.AutobotIpcServer;
import io.github.srdjanv.autobotserver.Config;
import io.javalin.Javalin;
import io.javalin.apibuilder.EndpointGroup;
import io.javalin.community.ssl.SslPlugin;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import static io.javalin.apibuilder.ApiBuilder.*;

import java.nio.file.Path;

@Slf4j
@Getter
@Accessors(fluent = true)
public class JavalinApp implements AutoCloseable {
    private final AutobotIpcServer autobotIpcServer;
    private final Config config;
    private final Auth auth;
    private final BotController botController;
    private final Javalin javalin;

    public JavalinApp(AutobotIpcServer autobotIpcServer, Config config) {
        this.autobotIpcServer = autobotIpcServer;
        this.botController = new BotController(autobotIpcServer);
        this.config = config;
        this.auth = new Auth(config);
        SslPlugin sslPlugin = new SslPlugin(conf -> {
            Path cert = config.certificate().toAbsolutePath();
            Path path = config.privateKey().toAbsolutePath();
            String password = config.sslPassword();
            conf.pemFromPath(cert.toString(), path.toString(), password);

            conf.sniHostCheck = false;
        });

        javalin = Javalin.create(javalinConfig -> {
            javalinConfig.registerPlugin(sslPlugin);
            javalinConfig.router.mount(router -> {
                router.beforeMatched(auth::handleAccess);
            }).apiBuilder(() -> {
                path("v1", () -> {
                    path("price_list", () -> {
                        get(botController::getPriceList);
                    });
                    path("trades", () -> {
                        get(botController::getTrades);
                    });
                    path("remove_item", () -> {
                        delete(botController::removeItem);
                    });
                    path("update_item", () -> {
                        post(botController::updateItem);
                    });
                    path("add_item", () -> {
                        post(botController::addItem);
                    });
                    path("inventory", () -> {
                        get(botController::getInventory);
                    });
                });
            });
        }).start(config.serverPort());
        log.info("JavalinApp started on port {}", config.serverPort());
    }

    @Override public void close() {
        javalin.jettyServer().stop();
    }
}
