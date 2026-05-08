package org.softgauge_streak;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent store of {@link StreakRecord}s, backed by
 * {@code plugins/SoftGaugesBehaviors/streaks.yml}.
 *
 * <p>Strategy:
 * <ul>
 *   <li>Load the entire file into an in-memory {@link ConcurrentHashMap} on
 *       construction. Streak data is small (a few dozen bytes per player) so
 *       full hydration is cheap and avoids per-lookup disk I/O.</li>
 *   <li>{@link #put(StreakRecord)} mutates the cache only. Persistence is the
 *       caller's responsibility via {@link #persist()} so we don't write the
 *       whole YAML on every minor change (hot path for dozens of joins).</li>
 *   <li>{@link #persist()} is synchronised — safe to call from a shutdown hook
 *       or from a debounced async task.</li>
 * </ul></p>
 *
 * <p>YAML schema:</p>
 * <pre>
 * streaks:
 *   "uuid-1234":
 *     name: "Alice"
 *     last_login: "2026-05-08"
 *     current: 5
 *     longest: 12
 * </pre>
 */
public final class StreakRepository {

    private static final String ROOT_KEY = "streaks";

    private final JavaPlugin                 plugin;
    private final File                       file;
    private final Map<UUID, StreakRecord>    cache = new ConcurrentHashMap<>();

    public StreakRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "streaks.yml");
        load();
    }

    // ── Read API ─────────────────────────────────────────────────────────────

    public Optional<StreakRecord> find(UUID id) {
        return Optional.ofNullable(cache.get(id));
    }

    /** Read-only snapshot of every record (used by leaderboards). */
    public Map<UUID, StreakRecord> all() {
        return Collections.unmodifiableMap(cache);
    }

    // ── Write API ────────────────────────────────────────────────────────────

    public void put(StreakRecord record) {
        cache.put(record.playerId(), record);
    }

    /**
     * Serialise the entire cache to {@code streaks.yml}.
     * Synchronised — concurrent calls will queue rather than corrupt the file.
     */
    public synchronized void persist() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (StreakRecord r : cache.values()) {
            String key = ROOT_KEY + "." + r.playerId();
            yaml.set(key + ".name",       r.playerName());
            yaml.set(key + ".last_login", r.lastLogin().toString());
            yaml.set(key + ".current",    r.currentStreak());
            yaml.set(key + ".longest",    r.longestStreak());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to write streaks.yml: " + e.getMessage());
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    /** Hydrate the cache from disk (called once during construction). */
    private synchronized void load() {
        if (!file.exists()) {
            plugin.getLogger().info("streaks.yml not found — starting with an empty streak store.");
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection(ROOT_KEY);
        if (section == null) return;

        int loaded = 0;
        int skipped = 0;
        for (String key : section.getKeys(false)) {
            try {
                UUID      id      = UUID.fromString(key);
                String    name    = section.getString(key + ".name", "Unknown");
                LocalDate date    = LocalDate.parse(section.getString(key + ".last_login"));
                int       current = section.getInt(key + ".current");
                int       longest = section.getInt(key + ".longest");
                cache.put(id, new StreakRecord(id, name, date, current, longest));
                loaded++;
            } catch (IllegalArgumentException | DateTimeParseException | NullPointerException e) {
                plugin.getLogger().warning("Skipping malformed streak entry '" + key
                        + "': " + e.getMessage());
                skipped++;
            }
        }
        plugin.getLogger().info("Loaded " + loaded + " streak record(s) from streaks.yml"
                + (skipped > 0 ? " (" + skipped + " skipped)" : ""));
    }
}
