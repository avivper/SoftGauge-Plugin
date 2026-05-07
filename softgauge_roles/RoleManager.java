package org.softgauge_roles;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory authoritative store of which player holds which {@link PlayerRole}.
 * Thread-safe: backed by a {@link ConcurrentHashMap}.
 * Persistence is intentionally out of scope here — assignments live for the server
 * session.  A future iteration can plug a database-backed implementation in by
 * either subclassing this manager or replacing it via dependency injection in
 * {@link org.softgauge.SoftGauge}.
 */
public class RoleManager {

    private final Map<UUID, PlayerRole> assignments = new ConcurrentHashMap<>();

    /**
     * Assign a role to a player.  Idempotent — re-assigning the same role is a no-op.
     *
     * @return {@code true} if the assignment was newly applied,
     *         {@code false} if the player already held that role.
     */
    public boolean assign(UUID playerId, PlayerRole role) {
        PlayerRole previous = assignments.put(playerId, role);
        return previous != role;
    }

    /** Remove the player's role assignment (e.g. on quit, command, or rebalance). */
    public void unassign(UUID playerId) {
        assignments.remove(playerId);
    }

    public Optional<PlayerRole> getRole(UUID playerId) {
        return Optional.ofNullable(assignments.get(playerId));
    }

    public Optional<PlayerRole> getRole(Player player) {
        return getRole(player.getUniqueId());
    }

    /** Convenience: true when the player holds the supplied role. */
    public boolean hasRole(Player player, PlayerRole role) {
        return assignments.get(player.getUniqueId()) == role;
    }

    /** Read-only view for diagnostics. */
    public Map<UUID, PlayerRole> getAssignments() {
        return Collections.unmodifiableMap(assignments);
    }
}
