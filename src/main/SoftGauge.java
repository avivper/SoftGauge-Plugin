package org.softgauge;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class SoftGauge extends JavaPlugin {
    @Override
    public void onEnable() {
        // Register the command
        if (getCommand("hello") != null) {
            Objects.requireNonNull(getCommand("hello")).setExecutor(new HelloCommand());
        }

        getLogger().info("Hello World Plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Plugin disabled!");
    }
}
