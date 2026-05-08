package org.softgauge_roles;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.softgauge.SoftGauge;

import java.util.Optional;

/**
 * CommandExecutor for the /missions command.
 * Displays the progression missions for the player's currently assigned role.
 */
public class MissionsCommand implements CommandExecutor {

    private final SoftGauge plugin;

    public MissionsCommand(SoftGauge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Optional<PlayerRole> optionalRole = plugin.getRoleManager().getRole(player);

        if (optionalRole.isEmpty()) {
            player.sendMessage(ChatColor.RED + "You have not chosen a path yet!");
            return true;
        }

        PlayerRole role = optionalRole.get();
        
        player.sendMessage(ChatColor.GOLD + "=================================");
        player.sendMessage(ChatColor.YELLOW + ChatColor.BOLD.toString() + " Progression Missions: " + role.getDisplayName());
        player.sendMessage(ChatColor.GOLD + "=================================");

        switch (role) {
            case FARMER -> {
                player.sendMessage(ChatColor.GREEN + "• Apprentice: " + ChatColor.WHITE + "\"Breaking Ground\" – Harvest 500 fully grown crops.");
                player.sendMessage(ChatColor.GREEN + "• Journeyman: " + ChatColor.WHITE + "\"The Automation Age\" – Build an automated farm.");
                player.sendMessage(ChatColor.GREEN + "• Expert: " + ChatColor.WHITE + "\"Animal Whisperer\" – Successfully breed 100 passive mobs.");
                player.sendMessage(ChatColor.GREEN + "• Master: " + ChatColor.WHITE + "\"The Golden Standard\" – Craft 128 Golden Carrots.");
            }
            case ARMORER -> {
                player.sendMessage(ChatColor.GRAY + "• Apprentice: " + ChatColor.WHITE + "\"Stoking the Flames\" – Smelt 1,000 raw ores.");
                player.sendMessage(ChatColor.GRAY + "• Journeyman: " + ChatColor.WHITE + "\"Suiting Up\" – Craft a complete set of Diamond Armor and Sword.");
                player.sendMessage(ChatColor.GRAY + "• Expert: " + ChatColor.WHITE + "\"The Fixer\" – Repair 50 pieces of gear.");
                player.sendMessage(ChatColor.GRAY + "• Master: " + ChatColor.WHITE + "\"Forged in Hell\" – Upgrade a complete set of diamond gear to Netherite.");
            }
            case LIBRARIAN -> {
                player.sendMessage(ChatColor.AQUA + "• Apprentice: " + ChatColor.WHITE + "\"Knowledge Gatherer\" – Mine 300 Lapis Lazuli and craft 100 Books.");
                player.sendMessage(ChatColor.AQUA + "• Journeyman: " + ChatColor.WHITE + "\"Table of Power\" – Perform 20 Level-30 enchantments.");
                player.sendMessage(ChatColor.AQUA + "• Expert: " + ChatColor.WHITE + "\"The XP Architect\" – Collect 5,000 Experience Orbs.");
                player.sendMessage(ChatColor.AQUA + "• Master: " + ChatColor.WHITE + "\"The Mending Miracle\" – Cure a Zombie Villager or apply Mending 10 times.");
            }
            default -> player.sendMessage(ChatColor.RED + "Missions for your role are not defined yet.");
        }
        
        player.sendMessage(ChatColor.GOLD + "=================================");

        return true;
    }
}