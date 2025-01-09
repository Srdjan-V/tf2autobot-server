package io.github.srdjanv.autobotserver;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.json.JsonFormat;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.util.Objects;

@Slf4j
@Getter
@Accessors(fluent = true)
public class Config implements AutoCloseable {
    private final Path path;
    private final FileConfig fileConfig;

    public Config() {
        this(Path.of(System.getProperty("user.dir")));
    }

    public Config(Path path) {
        this.path = path;
        Path configPath = path.resolve("server_config.json");
        log.info("Loading config from {}", configPath);

        fileConfig = FileConfig.builder(configPath, JsonFormat.fancyInstance())
                .sync()
                .autoreload()
                .build();
        fileConfig.load();
    }

    public char messageDelimiter() {
        return fileConfig.getOrElse("message_delimiter", '\f');
    }

    public int ipcMessageTimeout() {
        return fileConfig.getOrElse("ipc_message_timeout", 120);
    }


    public String socketId() {
        return fileConfig.getOrElse("socket_id", "autobot_gui");
    }

    public int serverPort() {
        return fileConfig.getOrElse("server_port", 443);
    }

    public int responseCacheTimeout() {
        return fileConfig.getOrElse("response_cache_timeout", 10);
    }

    public String authToken() {
        String authToken = fileConfig.get("auth_token");
        if (StringUtils.isBlank(authToken)) {
            throw new IllegalArgumentException("Auth token is empty");
        }
        return Objects.requireNonNull(authToken, "No auth token provided");
    }

    public String sslPassword() {
        String ssl_password = fileConfig.get("ssl_password");
        return Objects.requireNonNull(ssl_password, "No ssl password provided");
    }

    public Path certificate() {
        return path.resolve("cert.pem");
    }

    public Path privateKey() {
        return path.resolve("key.pem");
    }

    @Override public void close()  {
        fileConfig.save();
    }
}
