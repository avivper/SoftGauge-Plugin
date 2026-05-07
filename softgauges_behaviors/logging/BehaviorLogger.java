package org.softgauges_behaviors.logging;

import org.bukkit.plugin.java.JavaPlugin;
import org.softgauges_behaviors.model.BehaviorRecord;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Thread-safe logger for {@link BehaviorRecord} objects.
 *
 * Writes every record to:
 *  1. The server console via {@code plugin.getLogger().info()}
 *  2. {@code plugins/SoftGaugesBehaviors/behaviors.log} — one line per record
 *
 * The log line format is defined by {@link BehaviorRecord#toLogLine()}.
 * Downstream consumers that need structured data should implement a
 * {@link java.util.function.Consumer Consumer&lt;BehaviorRecord&gt;} and
 * register it via {@code SoftGauge.addBehaviorConsumer()}.
 */
public class BehaviorLogger {

    private final JavaPlugin  plugin;
    private final PrintWriter fileWriter;

    public BehaviorLogger(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();

        PrintWriter writer = null;
        try {
            File logFile = new File(plugin.getDataFolder(), "behaviors.log");
            writer = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)));
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to open behaviors.log: " + e.getMessage());
        }
        this.fileWriter = writer;
    }

    /** Thread-safe — safe to call from async event threads. */
    public synchronized void log(BehaviorRecord record) {
        String line = record.toLogLine();
        plugin.getLogger().info(line);
        if (fileWriter != null) {
            fileWriter.println(line);
            fileWriter.flush();
        }
    }

    public synchronized void close() {
        if (fileWriter != null) {
            fileWriter.flush();
            fileWriter.close();
        }
    }
}
