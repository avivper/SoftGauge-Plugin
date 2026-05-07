package org.softgauge_roles;

import org.bukkit.plugin.java.JavaPlugin;
import org.softgauge_roles.role.ArmorerRole;
import org.softgauge_roles.role.FarmerRole;
import org.softgauge_roles.role.LibrarianRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Central registry that wires every {@link AbstractRolePlugin} into the Bukkit
 * event bus.
 *
 * Mirrors the design of {@code DetectorRegistry} in the behaviors subsystem:
 * adding a new role is a single line in {@link #registerAll()}.
 *
 * Lifecycle: instantiate once during {@code SoftGauge.onEnable()} and call
 * {@link #registerAll()} immediately.
 */
public class RoleRegistry {

    private final JavaPlugin                plugin;
    private final RoleManager               roleManager;
    private final List<AbstractRolePlugin>  registered = new ArrayList<>();

    public RoleRegistry(JavaPlugin plugin, RoleManager roleManager) {
        this.plugin      = plugin;
        this.roleManager = roleManager;
    }

    /** Instantiate all role plugins + the claim listener and register them. */
    public void registerAll() {
        // Claim mechanism (shift + right-click workstation)
        plugin.getServer().getPluginManager()
                .registerEvents(new RoleClaimListener(roleManager), plugin);

        // Concrete role plugins — one per PlayerRole constant
        register(new FarmerRole(roleManager));
        register(new LibrarianRole(roleManager));
        register(new ArmorerRole(roleManager));

        plugin.getLogger().info(
                "RoleRegistry: " + registered.size() + " role plugins active "
                        + "(claim by shift + right-clicking the workstation block).");
    }

    /** Read-only snapshot of registered role plugins (useful for diagnostics). */
    public List<AbstractRolePlugin> getRegistered() {
        return Collections.unmodifiableList(registered);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void register(AbstractRolePlugin rolePlugin) {
        plugin.getServer().getPluginManager().registerEvents(rolePlugin, plugin);
        registered.add(rolePlugin);
        plugin.getLogger().fine("Registered role plugin: "
                + rolePlugin.getClass().getSimpleName()
                + " [" + rolePlugin.getRole() + "]");
    }
}
