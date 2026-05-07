package org.softgauge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class HelloCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Create a colorful "Hello World" message
        Component message = Component.text("Hello World!", NamedTextColor.GREEN);

        // Send to the player or console
        sender.sendMessage(message);

        return true;
    }
}
