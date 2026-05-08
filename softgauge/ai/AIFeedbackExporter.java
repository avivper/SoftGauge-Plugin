package org.softgauge.ai;

import org.softgauge.SoftGauge;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles parsing chat history from behaviors.log and generating AI feedback
 * for all active players during server shutdown.
 */
public class AIFeedbackExporter {

    private final SoftGauge plugin;
    private final Pattern chatPattern = Pattern.compile("CHAT_\\w+: (\\w+) .* \"(.*)\"");

    public AIFeedbackExporter(SoftGauge plugin) {
        this.plugin = plugin;
    }

    public void runExport() {
        String apiKey = plugin.getConfig().getString("gemini-api-key", "");
        if (apiKey == null || apiKey.isBlank()) {
            plugin.getLogger().warning("Gemini API key is not set in config.yml. " +
                    "Skipping AI feedback generation. " +
                    "Get a key from Google AI Studio.");
            return;
        }

        plugin.getLogger().info("Generating AI English feedback for all players...");

        Map<String, List<String>> allPlayerChats = getAllChatHistories();

        if (allPlayerChats.isEmpty()) {
            plugin.getLogger().info("No chat history found. Skipping feedback generation.");
            return;
        }

        GeminiFeedbackProvider feedbackProvider = new GeminiFeedbackProvider(plugin, apiKey);
        
        File feedbackDir = new File(plugin.getDataFolder(), "feedback");
        if (!feedbackDir.exists()) {
            feedbackDir.mkdirs();
        }

        for (Map.Entry<String, List<String>> entry : allPlayerChats.entrySet()) {
            String playerName = entry.getKey();
            List<String> history = entry.getValue();
            
            plugin.getLogger().info("Generating feedback for " + playerName + "...");
            String feedback = feedbackProvider.getFeedback(playerName, history);

            if (feedback != null && !feedback.startsWith("Error:")) {
                File feedbackFile = new File(feedbackDir, playerName + ".txt");
                try (FileWriter writer = new FileWriter(feedbackFile)) {
                    writer.write(feedback);
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not save feedback file for " + playerName + ": " + e.getMessage());
                }
            } else {
                plugin.getLogger().warning("Failed to generate feedback for " + playerName + ": " + feedback);
            }
        }
        
        plugin.getLogger().info("AI feedback generation complete.");
    }

    private Map<String, List<String>> getAllChatHistories() {
        Map<String, List<String>> histories = new HashMap<>();
        File logFile = new File(plugin.getDataFolder(), "behaviors.log");
        if (!logFile.exists()) {
            return histories;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = chatPattern.matcher(line);
                if (matcher.find()) {
                    String playerName = matcher.group(1);
                    String message = matcher.group(2);
                    histories.computeIfAbsent(playerName, k -> new ArrayList<>()).add(message);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Could not read behaviors.log: " + e.getMessage());
        }
        return histories;
    }
}