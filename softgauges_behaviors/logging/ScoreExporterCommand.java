package org.softgauges_behaviors.logging;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.softgauge.SoftGauge;

/**
 * Command to parse behaviors.log and export Positive/Negative scores to a JSON file.
 * Usage: /exportscores
 */
public class ScoreExporterCommand implements CommandExecutor {

    private final SoftGauge plugin;

    public ScoreExporterCommand(SoftGauge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("softgauge.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Starting score extraction from logs in the background...");

        // Run file I/O asynchronously so we don't lag the server thread when called by command
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            ScoreExporter exporter = new ScoreExporter(plugin);
            boolean success = exporter.runExport();
            if (success) {
                sender.sendMessage(ChatColor.GREEN + "Successfully exported scores to player_scores.json");
            } else {
                sender.sendMessage(ChatColor.RED + "Failed to export scores. Check server console for errors.");
            }
        });

        return true;
    }
}