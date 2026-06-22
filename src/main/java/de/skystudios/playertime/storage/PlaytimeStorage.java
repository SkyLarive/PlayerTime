package de.skystudios.playertime.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.skystudios.playertime.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Kümmert sich um alles rund um MariaDB: Connection-Pool, Tabelle, Lesen und Schreiben.
 * Alle Methoden hier sind blockierend gedacht und sollten vom Aufrufer async ausgeführt werden.
 */
public final class PlaytimeStorage {

    private final DatabaseConfig config;
    private HikariDataSource dataSource;

    public PlaytimeStorage(DatabaseConfig config) {
        this.config = config;
    }

    public void initialize() throws SQLException {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("PlayerTime");
        hikari.setJdbcUrl(config.jdbcUrl());
        hikari.setUsername(config.username());
        hikari.setPassword(config.password());
        hikari.setMaximumPoolSize(config.poolSize());
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(10_000);
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(hikari);
        createTable();
    }

    private void createTable() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS playtime (
                    uuid CHAR(36) NOT NULL PRIMARY KEY,
                    username VARCHAR(16) NOT NULL,
                    playtime_seconds BIGINT NOT NULL DEFAULT 0,
                    last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }

    /** Bisher gespeicherte Spielzeit in Sekunden, 0 wenn der Spieler noch nicht in der DB steht. */
    public long loadPlaytime(UUID uuid) throws SQLException {
        String sql = "SELECT playtime_seconds FROM playtime WHERE uuid = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong("playtime_seconds") : 0L;
            }
        }
    }

    /** Spielzeit anlegen oder aktualisieren (Upsert). */
    public void savePlaytime(UUID uuid, String username, long seconds) throws SQLException {
        String sql = """
                INSERT INTO playtime (uuid, username, playtime_seconds)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    username = VALUES(username),
                    playtime_seconds = VALUES(playtime_seconds)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, username);
            statement.setLong(3, seconds);
            statement.executeUpdate();
        }
    }

    /** Spielzeit anhand des zuletzt bekannten Namens (für Offline-Abfragen). */
    public Optional<Long> loadPlaytimeByName(String username) throws SQLException {
        String sql = "SELECT playtime_seconds FROM playtime WHERE username = ? ORDER BY last_seen DESC LIMIT 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(result.getLong("playtime_seconds")) : Optional.empty();
            }
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
