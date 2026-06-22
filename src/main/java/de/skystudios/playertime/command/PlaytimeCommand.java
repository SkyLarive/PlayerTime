package de.skystudios.playertime.command;

import de.skystudios.playertime.PlayerTimePlugin;
import de.skystudios.playertime.tracking.PlaytimeTracker;
import de.skystudios.playertime.util.TimeFormatter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class PlaytimeCommand implements TabExecutor {

    private final PlayerTimePlugin plugin;
    private final PlaytimeTracker tracker;

    public PlaytimeCommand(PlayerTimePlugin plugin, PlaytimeTracker tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Ohne Argument: eigene Spielzeit
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Bitte gib einen Spieler an: /playtime <Spieler>");
                return true;
            }
            long seconds = tracker.getCurrentSeconds(player.getUniqueId());
            player.sendMessage(ChatColor.GRAY + "Deine Spielzeit: "
                    + ChatColor.AQUA + TimeFormatter.format(seconds));
            return true;
        }

        // Mit Argument: Spielzeit eines anderen Spielers (braucht Berechtigung)
        if (!sender.hasPermission("playertime.others")) {
            sender.sendMessage(ChatColor.RED + "Dazu hast du keine Berechtigung.");
            return true;
        }

        String targetName = args[0];
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            long seconds = tracker.getCurrentSeconds(online.getUniqueId());
            sender.sendMessage(ChatColor.GRAY + online.getName() + " hat eine Spielzeit von "
                    + ChatColor.AQUA + TimeFormatter.format(seconds));
            return true;
        }

        // Offline: asynchron aus der DB lesen und danach auf dem Main-Thread antworten
        sender.sendMessage(ChatColor.GRAY + "Suche Spielzeit von " + targetName + "...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                var result = plugin.getStorage().loadPlaytimeByName(targetName);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (result.isEmpty()) {
                        sender.sendMessage(ChatColor.RED + "Für " + targetName + " ist keine Spielzeit gespeichert.");
                    } else {
                        sender.sendMessage(ChatColor.GRAY + targetName + " hat eine Spielzeit von "
                                + ChatColor.AQUA + TimeFormatter.format(result.get()));
                    }
                });
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Spielzeit-Abfrage fehlgeschlagen.", exception);
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ChatColor.RED + "Bei der Abfrage ist ein Fehler aufgetreten."));
            }
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && sender.hasPermission("playertime.others")) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
