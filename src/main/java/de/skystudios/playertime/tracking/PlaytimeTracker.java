package de.skystudios.playertime.tracking;

import de.skystudios.playertime.storage.PlaytimeStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Hält die laufende Spielzeit pro Online-Spieler im Speicher und schreibt sie
 * regelmäßig sowie beim Quit/Stoppen in die Datenbank.
 *
 * Idee dahinter:
 *  - bankedSeconds = der zuletzt bekannte Gesamtwert (aus DB geladen, bei jedem Flush erhöht)
 *  - segmentStart  = Beginn des aktuellen, noch nicht gespeicherten Zeitabschnitts
 *  - aktuelle Spielzeit = bankedSeconds + (jetzt - segmentStart)
 *
 * Die Map-Mutationen laufen auf dem Main-Thread (Events, geplanter Task), nur die reine
 * DB-Ein-/Ausgabe wird async ausgeführt. So bleibt das Ganze konsistent und blockiert nie den Server.
 */
public final class PlaytimeTracker {

    private final Plugin plugin;
    private final PlaytimeStorage storage;
    private final long saveIntervalSeconds;

    private final Map<UUID, Long> bankedSeconds = new ConcurrentHashMap<>();
    private final Map<UUID, Long> segmentStart = new ConcurrentHashMap<>();

    private BukkitTask saveTask;

    public PlaytimeTracker(Plugin plugin, PlaytimeStorage storage, long saveIntervalSeconds) {
        this.plugin = plugin;
        this.storage = storage;
        this.saveIntervalSeconds = saveIntervalSeconds;
    }

    public void start() {
        long ticks = saveIntervalSeconds * 20L;
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushAll, ticks, ticks);
    }

    public void handleJoin(Player player) {
        UUID uuid = player.getUniqueId();
        segmentStart.put(uuid, System.currentTimeMillis());

        // Bisherige Spielzeit asynchron nachladen, damit der Join-Event nicht auf die DB wartet
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long stored = storage.loadPlaytime(uuid);
                // Nur übernehmen wenn der Spieler noch online ist (schützt vor Race bei schnellem Rejoin)
                if (player.isOnline()) {
                    bankedSeconds.putIfAbsent(uuid, stored);
                }
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING,
                        "Konnte Spielzeit von " + player.getName() + " nicht laden.", exception);
            }
        });
    }

    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        flush(uuid, player.getName(), true);
        segmentStart.remove(uuid);
        bankedSeconds.remove(uuid);
    }

    /** Gesamte Spielzeit in Sekunden: gespeichert plus laufender Abschnitt. */
    public long getCurrentSeconds(UUID uuid) {
        long banked = bankedSeconds.getOrDefault(uuid, 0L);
        Long start = segmentStart.get(uuid);
        long current = start != null ? (System.currentTimeMillis() - start) / 1000L : 0L;
        return banked + current;
    }

    /** Ob die DB-Daten des Spielers schon im Speicher liegen. */
    public boolean isLoaded(UUID uuid) {
        return bankedSeconds.containsKey(uuid);
    }

    private void flushAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            flush(player.getUniqueId(), player.getName(), false);
        }
    }

    /**
     * Rechnet den laufenden Abschnitt auf die Bank und schreibt asynchron in die DB.
     * Läuft immer auf dem Main-Thread, damit sich die Map-Zugriffe nicht in die Quere kommen.
     *
     * @param removing true beim Quit/Stoppen, dann wird der segmentStart nicht neu gesetzt
     */
    private void flush(UUID uuid, String username, boolean removing) {
        // Noch nicht geladen? Dann nichts tun, sonst würden wir echte Daten mit 0 überschreiben.
        if (!bankedSeconds.containsKey(uuid)) {
            return;
        }
        Long start = segmentStart.get(uuid);
        if (start == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long deltaSeconds = (now - start) / 1000L;
        long total = bankedSeconds.getOrDefault(uuid, 0L) + deltaSeconds;

        bankedSeconds.put(uuid, total);
        if (!removing) {
            segmentStart.put(uuid, now);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                storage.savePlaytime(uuid, username, total);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING,
                        "Konnte Spielzeit von " + username + " nicht speichern.", exception);
            }
        });
    }

    /** Beim Plugin-Disable: Task stoppen und alle Online-Spieler synchron sichern. */
    public void shutdown() {
        if (saveTask != null) {
            saveTask.cancel();
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!bankedSeconds.containsKey(uuid)) {
                continue;
            }
            Long start = segmentStart.get(uuid);
            if (start == null) {
                continue;
            }
            long deltaSeconds = (System.currentTimeMillis() - start) / 1000L;
            long total = bankedSeconds.getOrDefault(uuid, 0L) + deltaSeconds;
            try {
                // Synchron, weil der Server gerade herunterfährt und async-Tasks nicht mehr laufen
                storage.savePlaytime(uuid, player.getName(), total);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING,
                        "Konnte Spielzeit beim Stoppen nicht speichern: " + player.getName(), exception);
            }
        }

        bankedSeconds.clear();
        segmentStart.clear();
    }
}
