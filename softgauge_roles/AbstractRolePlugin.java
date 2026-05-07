package org.softgauge_roles;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/**
 * Base class for every concrete role implementation.
 *
 * Mirrors the {@code AbstractBehaviorDetector} pattern used in the behavior
 * subsystem: each subclass is a Bukkit {@link Listener} that handles its own
 * {@code @EventHandler} methods and uses the shared {@link RoleManager} to
 * decide whether a given player currently holds this role.
 *
 * Subclasses must:
 *   1. Return their {@link PlayerRole} from {@link #getRole()}.
 *   2. Annotate event handlers with {@code @EventHandler}.
 *   3. Gate every handler body on {@link #hasThisRole(Player)} so only players
 *      who hold this role receive the role-specific buff.
 */
public abstract class AbstractRolePlugin implements Listener {

    protected final RoleManager roleManager;

    protected AbstractRolePlugin(RoleManager roleManager) {
        this.roleManager = roleManager;
    }

    /** Which role this plugin implements. */
    public abstract PlayerRole getRole();

    /** True when the supplied player currently holds this role. */
    protected final boolean hasThisRole(Player player) {
        return roleManager.hasRole(player, getRole());
    }
}
