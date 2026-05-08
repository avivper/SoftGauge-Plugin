package org.softgauges_behaviors.logging;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;

/**
 * Dedicated chat-only logger.
 *
 * <p>Writes a single line per chat message to
 * {@code plugins/SoftGaugesBehaviors/chat.log}. Unlike {@code BehaviorLogger},
 * this file contains <strong>only</strong> player chat messages — no behavior
 * categories, severity, metadata, or location data. The output is intended
 * for downstream tools that need a clean conversation transcript (language
 * analytics, exports, manual review).</p>
 *
 * <h3>Format</h3>
 * <pre>
 * [2026-05-08T14:23:45.123Z] Alice: hello world
 * [2026-05-08T14:24:01.456Z] Bob: hi alice
 * </pre>
 *
 * <p>Newlines and carriage returns inside messages are flattened to single
 * spaces so the one-line-per-message contract holds even if a player sends
 * a multi-line paste.</p>
 *
 * <p>Thread-safety: {@link #log} is synchronized — safe to call from the
 * async chat thread.</p>
 */
public class ChatLogger {

    private final JavaPlugin  plugin;
    private final PrintWriter fileWriter;

    public ChatLogger(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();

        PrintWriter writer = null;
        try {
            File logFile = new File(plugin.getDataFolder(), "chat.log");
            writer = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)));
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to open chat.log: " + e.getMessage());
        }
        this.fileWriter = writer;
    }

    /**
     * Append one chat message to chat.log.
     *
     * @param playerName the sender's in-game name
     * @param timestamp  epoch milliseconds when the message was received
     * @param message    plain-text message contents
     */
    public synchronized void log(String playerName, long timestamp, String message) {
        if (fileWriter == null) return;

        String safe = message == null ? "" : message.replace('\n', ' ').replace('\r', ' ');
        fileWriter.println("[" + Instant.ofEpochMilli(timestamp) + "] "
                + playerName + ": " + safe);
        fileWriter.flush();
    }

    /** Flush and close the file writer. Call once from {@code onDisable}. */
    public synchronized void close() {
        if (fileWriter != null) {
            fileWriter.flush();
            fileWriter.close();
        }
    }
}
