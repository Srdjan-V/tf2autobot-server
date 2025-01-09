package io.github.srdjanv.autobotserver;

import io.github.srdjanv.autobotserver.ipc.AutobotIpcServer;
import io.github.srdjanv.autobotserver.javalin.JavalinApp;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class Main {
	public static void main(String[] args) throws Exception {
		Config config = new Config();
		AutobotIpcServer autobotIpcServer = new AutobotIpcServer(config);
		JavalinApp javalinApp = new JavalinApp(autobotIpcServer, config);
		autobotIpcServer.start();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			for (AutoCloseable closeable : List.of(javalinApp, autobotIpcServer, config)) {
                try {
					log.info("Closing {}", closeable);
                    closeable.close();
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
		}));
	}
}