package io.github.srdjanv.autobotserver;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.json.JsonFormat;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

@Slf4j
@Getter
@Accessors(fluent = true)
public class Config implements AutoCloseable {
    private final Path path;
    private final FileConfig fileConfig;

    public Config() {
        this(Path.of(System.getProperty("user.dir")).resolve("config"));
    }

    public Config(Path path) {
        this.path = path;
        Path configPath = path.resolve("server_config.json");
        log.info("Loading config from {}", configPath);

        fileConfig = FileConfig.builder(configPath, JsonFormat.fancyInstance())
                .sync()
                .autoreload()
                .onAutoReload(() -> {
                    log.info("Reloading config from {}", configPath);
                })
                .build();
        fileConfig.load();
    }

    public char messageDelimiter() {
        return fileConfig.getOrElse("message_delimiter", '\f');
    }

    public Duration ipcMessageTimeout() {
        int seconds = fileConfig.getOrElse("ipc_message_timeout", 120);
        return Duration.ofSeconds(seconds);
    }

    public Duration ipcMessagePollInterval() {
        int millis = fileConfig.getOrElse("ipc_message_poll_interval", 1000);
        return Duration.ofMillis(millis);
    }

    public Duration ipcSocketAutoRestart() {
        int seconds = fileConfig.getOrElse("ipc_socket_auto_restart", 15);
        return Duration.ofSeconds(seconds);
    }

    public String socketPath() {
        return fileConfig.getOrElse("socket_path", () -> {
            Path tmp = Path.of(FileUtils.getTempDirectoryPath());
            return tmp.resolve("app." + "autobot_gui").toAbsolutePath().toString();
        });
    }

    public int serverPort() {
        return fileConfig.getOrElse("server_port", 443);
    }

    public String serverHost() {
        return fileConfig.getOrElse("server_host", "localHost");
    }

    public boolean sniHostCheck() {
        return fileConfig.getOrElse("sni_host_check", true);
    }

    public Duration responseCacheTimeout() {
        int seconds = fileConfig.getOrElse("response_cache_timeout", 10);
        return Duration.ofSeconds(seconds);
    }

    public boolean useAuth() {
        return fileConfig.getOrElse("use_auth", true);
    }

    public String authToken() {
        String authToken = fileConfig.get("auth_token");
        if (StringUtils.isBlank(authToken)) {
            throw new IllegalArgumentException("Auth token is empty");
        }
        return Objects.requireNonNull(authToken, "No auth token provided");
    }

    public boolean useSsl() {
        return fileConfig.getOrElse("use_ssl", true);
    }

    public String sslPassword() {
        String ssl_password = fileConfig.get("ssl_password");
        return ssl_password;
    }

    public Path certificate() {
        return path.resolve("cert.pem");
    }

    public Path privateKey() {
        return path.resolve("key.pem");
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("path", path)
                .toString();
    }

    @Override
    public void close() {
        fileConfig.save();
    }
}
