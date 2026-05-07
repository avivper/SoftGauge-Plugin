package org.softgauge;
import org.bukkit.plugin.java.JavaPlugin;

public class SoftGauge extends JavaPlugin {
    @Override
    public void onEnable() {
        if (getCommand("hello") != null) {
            getLogger().info("Hello World!");
        }
        getLogger().info("Plugin enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Plugin disabled!");
    }
}
