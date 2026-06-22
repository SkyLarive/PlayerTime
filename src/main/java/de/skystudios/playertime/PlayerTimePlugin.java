package de.skystudios.playertime;

import de.skystudios.playertime.command.PlaytimeCommand;
import de.skystudios.playertime.config.DatabaseConfig;
import de.skystudios.playertime.listener.ConnectionListener;
import de.skystudios.playertime.storage.PlaytimeStorage;
import de.skystudios.playertime.tracking.PlaytimeTracker;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class PlayerTimePlugin extends JavaPlugin {

    private PlaytimeStorage storage;
    private PlaytimeTracker tracker;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            DatabaseConfig databaseConfig = DatabaseConfig.fromConfig(getConfig());
            storage = new PlaytimeStorage(databaseConfig);
            storage.initialize();
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE,
                    "Konnte keine Verbindung zur MariaDB herstellen. Plugin wird deaktiviert.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        long saveIntervalSeconds = Math.max(30, getConfig().getLong("save-interval-seconds", 300));
        tracker = new PlaytimeTracker(this, storage, saveIntervalSeconds);
        tracker.start();

        // Falls beim /reload schon Spieler online sind, fangen wir die direkt mit ein
        getServer().getOnlinePlayers().forEach(tracker::handleJoin);

        getServer().getPluginManager().registerEvents(new ConnectionListener(tracker), this);

        PluginCommand command = getCommand("playtime");
        if (command != null) {
            PlaytimeCommand executor = new PlaytimeCommand(this, tracker);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("PlayerTime ist aktiv.");
    }

    @Override
    public void onDisable() {
        if (tracker != null) {
            tracker.shutdown();
        }
        if (storage != null) {
            storage.close();
        }
        getLogger().info("PlayerTime wurde deaktiviert.");
    }

    public PlaytimeStorage getStorage() {
        return storage;
    }
}
