package org.softgauges_behaviors.logging;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Handles the logic of parsing the behaviors.log file and exporting scores to JSON.
 * This is separated to be reusable by commands and shutdown hooks.
 */
public class ScoreExporter {

    private final JavaPlugin plugin;

    public ScoreExporter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Synchronously reads the log file, aggregates scores, and writes to player_scores.json.
     * This method performs file I/O and should be called asynchronously during normal server operation,
     * but can be called synchronously during onDisable.
     *
     * @return true if successful, false if an error occurred.
     */
    public boolean runExport() {
        File logFile = new File(plugin.getDataFolder(), "behaviors.log");
        if (!logFile.exists()) {
            plugin.getLogger().warning("No behaviors.log found. Nothing to export.");
            return false;
        }

        Map<String, PlayerScores> scoresMap = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Log format: [TIMESTAMP] [SEVERITY] [CATEGORY/TYPE] PlayerName ACTION_NAME: ...
                String[] parts = line.split(" ", 5);
                if (parts.length < 4) continue;

                String severityToken = parts[1].replaceAll("[\\[\\]]", "");
                String playerName = parts[3];

                if (severityToken.equals("POSITIVE") || severityToken.equals("NEGATIVE")) {
                    scoresMap.putIfAbsent(playerName, new PlayerScores());
                    PlayerScores ps = scoresMap.get(playerName);

                    if (severityToken.equals("POSITIVE")) {
                        ps.positive++;
                    } else {
                        ps.negative++;
                    }
                }
            }

            // Construct and write JSON
            JsonArray rootArray = buildJson(scoresMap);
            File outFile = new File(plugin.getDataFolder(), "player_scores.json");
            try (FileWriter writer = new FileWriter(outFile)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(rootArray, writer);
            }

            plugin.getLogger().info("Successfully exported scores to " + outFile.getName());
            return true;

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error parsing or writing score export file", e);
            return false;
        }
    }

    private JsonArray buildJson(Map<String, PlayerScores> scoresMap) {
        JsonArray rootArray = new JsonArray();
        for (Map.Entry<String, PlayerScores> entry : scoresMap.entrySet()) {
            JsonObject playerObj = new JsonObject();
            playerObj.addProperty("player", entry.getKey());

            JsonArray scoresArray = new JsonArray();
            JsonObject posObj = new JsonObject();
            posObj.addProperty("category", "Positive");
            posObj.addProperty("score", entry.getValue().positive);
            scoresArray.add(posObj);

            JsonObject negObj = new JsonObject();
            negObj.addProperty("category", "Negative");
            negObj.addProperty("score", entry.getValue().negative);
            scoresArray.add(negObj);

            playerObj.add("scores", scoresArray);
            rootArray.add(playerObj);
        }
        return rootArray;
    }

    private static class PlayerScores {
        int positive = 0;
        int negative = 0;
    }
}