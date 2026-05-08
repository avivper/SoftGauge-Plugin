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
 * CommandExecutor for the /respon command.
 * Displays the responsibilities of the player's currently assigned role.
 */
public class ResponCommand implements CommandExecutor {

    private final SoftGauge plugin;

    public ResponCommand(SoftGauge plugin) {
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
        player.sendMessage(ChatColor.YELLOW + ChatColor.BOLD.toString() + " Areas of Responsibility: " + role.getDisplayName());
        player.sendMessage(ChatColor.GOLD + "=================================");

        switch (role) {
            case FARMER -> {
                player.sendMessage(ChatColor.GREEN + "• The Food Supply: " + ChatColor.WHITE +
                        "Ensuring team chests are stocked with high-saturation foods.");
                player.sendMessage(ChatColor.GREEN + "• Agricultural Automation: " + ChatColor.WHITE +
                        "Designing automated crop farms.");
                player.sendMessage(ChatColor.GREEN + "• Livestock Management: " + ChatColor.WHITE +
                        "Breeding and managing animal pens.");
                player.sendMessage(ChatColor.GREEN + "• Base Economy: " + ChatColor.WHITE +
                        "Generating the team's first emeralds by mass-trading bulk crops.");
            }
            case ARMORER -> {
                player.sendMessage(ChatColor.GRAY + "• Gear Production: " + ChatColor.WHITE +
                        "Crafting and distributing armor, weapons, and tools.");
                player.sendMessage(ChatColor.GRAY + "• Smelting Logistics: " + ChatColor.WHITE +
                        "Managing blast furnaces, standard furnaces, and fuel economy.");
                player.sendMessage(ChatColor.GRAY + "• Durability Maintenance: " + ChatColor.WHITE +
                        "Repairing damaged gear before it breaks.");
                player.sendMessage(ChatColor.GRAY + "• Ore Processing: " + ChatColor.WHITE +
                        "Turning raw iron, gold, and ancient debris into ingots.");
            }
            case LIBRARIAN -> {
                player.sendMessage(ChatColor.AQUA + "• Enchantment Master: " + ChatColor.WHITE + "Providing top-tier enchantments.");
                player.sendMessage(ChatColor.AQUA + "• Experience Management: " + ChatColor.WHITE + "Maintaining mob grinders/XP farms.");
                player.sendMessage(ChatColor.AQUA + "• Magical Resources: " + ChatColor.WHITE + "Supplying Lapis Lazuli, Bookshelves, and Name Tags.");
                player.sendMessage(ChatColor.AQUA + "• Exploration Logistics: " + ChatColor.WHITE + "Crafting compasses, clocks, and maps.");
            }
            default -> player.sendMessage(ChatColor.RED + "Responsibilities for your role are not defined yet.");
        }
        
        player.sendMessage(ChatColor.GOLD + "=================================");

        return true;
    }
}