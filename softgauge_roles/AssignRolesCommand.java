package org.softgauge_roles;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.softgauge.SoftGauge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class AssignRolesCommand implements CommandExecutor {

    private final SoftGauge plugin;
    private final Random random = new Random();

    public AssignRolesCommand(SoftGauge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("softgauge.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        List<Player> onlinePlayers = new ArrayList<>(plugin.getServer().getOnlinePlayers());

        if (onlinePlayers.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No players online to assign roles to.");
            return true;
        }

        RoleManager roleManager = plugin.getRoleManager();

        if (onlinePlayers.size() == 1) {
            // Solo player: assign any random role
            Player solo = onlinePlayers.get(0);
            PlayerRole[] allRoles = PlayerRole.values();
            PlayerRole assignedRole = allRoles[random.nextInt(allRoles.length)];
            roleManager.assign(solo.getUniqueId(), assignedRole);
            solo.sendMessage(ChatColor.GREEN + "You are playing solo. You have been assigned the role: " +
                    ChatColor.GOLD + assignedRole.getDisplayName());
            sender.sendMessage(ChatColor.GREEN + "Assigned " + solo.getName() + " to " +
                    assignedRole.getDisplayName() + ".");
            return true;
        }

        // Squad play (>1 player)
        Collections.shuffle(onlinePlayers, random);

        // Assign exactly one Librarian
        Player librarianPlayer = onlinePlayers.remove(0);
        roleManager.assign(librarianPlayer.getUniqueId(), PlayerRole.LIBRARIAN);
        librarianPlayer.sendMessage(ChatColor.GREEN + "Your team has been formed! You are the " +
                ChatColor.GOLD + "Librarian" + ChatColor.GREEN + ".");

        // Assign the rest to either Armorer or Farmer
        PlayerRole[] remainingRoles = {PlayerRole.ARMORER, PlayerRole.FARMER};
        for (Player p : onlinePlayers) {
            PlayerRole role = remainingRoles[random.nextInt(remainingRoles.length)];
            roleManager.assign(p.getUniqueId(), role);
            p.sendMessage(ChatColor.GREEN + "Your team has been formed! You are an " +
                    ChatColor.GOLD + role.getDisplayName() + ChatColor.GREEN + ".");
        }

        sender.sendMessage(ChatColor.GREEN + "Roles have been successfully distributed to the squad.");
        return true;
    }
}