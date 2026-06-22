package de.skystudios.playertime.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Liest die MariaDB-Verbindungsdaten aus der config.yml und baut daraus die JDBC-URL.
 */
public final class DatabaseConfig {

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final int poolSize;
    private final boolean useSsl;

    public DatabaseConfig(String host, int port, String database,
                          String username, String password, int poolSize, boolean useSsl) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.poolSize = poolSize;
        this.useSsl = useSsl;
    }

    public static DatabaseConfig fromConfig(FileConfiguration config) {
        return new DatabaseConfig(
                config.getString("database.host", "localhost"),
                config.getInt("database.port", 3306),
                config.getString("database.name", "minecraft"),
                config.getString("database.user", "root"),
                config.getString("database.password", ""),
                config.getInt("database.pool-size", 10),
                config.getBoolean("database.use-ssl", false)
        );
    }

    public String jdbcUrl() {
        return "jdbc:mariadb://" + host + ":" + port + "/" + database
                + "?useSSL=" + useSsl + "&autoReconnect=true";
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public int poolSize() {
        return poolSize;
    }
}
