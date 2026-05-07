package org.softgauge_roles;

import org.bukkit.Material;

import java.util.List;

/**
 * Every selectable team role.
 *
 * Each constant is self-describing: it carries its display name, the workstation
 * block that doubles as the in-world claim marker, and an ordered TODO list
 * (the player's progression goals while playing this role).
 *
 * Adding a new role:
 *   1. Add a constant here with its workstation Material and TODO list.
 *   2. Create a concrete plugin class extending {@link AbstractRolePlugin}.
 *   3. Register it in {@link RoleRegistry#registerAll()}.
 */
public enum PlayerRole {

    FARMER(
            "Farmer",
            Material.COMPOSTER,
            List.of(
                    "Plant 10 wheat seeds in a tilled farm",
                    "Harvest 20 fully-grown crops",
                    "Share food with at least one teammate",
                    "Build or extend a communal farm with another player"
            )
    ),

    LIBRARIAN(
            "Librarian",
            Material.LECTERN,
            List.of(
                    "Mine 5 lapis lazuli ore for the team's enchantments",
                    "Place a Lectern inside a residential area",
                    "Enchant an item for a teammate using your lectern",
                    "Build a bookshelf wall (4+ bookshelves) near a community area"
            )
    ),

    ARMORER(
            "Armorer",
            Material.BLAST_FURNACE,
            List.of(
                    "Smelt iron in your blast furnace",
                    "Craft a full set of iron (or better) armor",
                    "Take a hit while protecting a teammate",
                    "Equip and gift a teammate one piece of armor"
            )
    );

    // ── Fields ────────────────────────────────────────────────────────────────

    private final String       displayName;
    private final Material     workstationBlock;
    private final List<String> progressionGoals;

    PlayerRole(String displayName, Material workstationBlock, List<String> progressionGoals) {
        this.displayName      = displayName;
        this.workstationBlock = workstationBlock;
        this.progressionGoals = List.copyOf(progressionGoals);
    }

    public String       getDisplayName()      { return displayName; }
    public Material     getWorkstationBlock() { return workstationBlock; }
    public List<String> getProgressionGoals() { return progressionGoals; }

    /**
     * Reverse lookup: which role (if any) is claimed via the given workstation block?
     * Returns {@code null} when the material is not a recognised workstation.
     */
    public static PlayerRole fromWorkstation(Material material) {
        for (PlayerRole role : values()) {
            if (role.workstationBlock == material) return role;
        }
        return null;
    }
}
