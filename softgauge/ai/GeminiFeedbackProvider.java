package org.softgauge.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.softgauge.SoftGauge;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.logging.Level;

/**
 * Connects to the Google Gemini API to get feedback on a player's English proficiency.
 */
public class GeminiFeedbackProvider {

    private static final String API_URL_FORMAT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=%s";
    private final SoftGauge plugin;
    private final String apiKey;
    private final HttpClient httpClient;
    private final Gson gson;

    public GeminiFeedbackProvider(SoftGauge plugin, String apiKey) {
        this.plugin = plugin;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.gson = new Gson();
    }

    /**
     * Sends a player's chat history to the Gemini API and returns the generated feedback.
     *
     * @param playerName The name of the player being evaluated.
     * @param chatHistory A list of the player's chat messages.
     * @return The AI-generated feedback as a string, or null if an error occurs.
     */
    public String getFeedback(String playerName, List<String> chatHistory) {
        if (apiKey == null || apiKey.isBlank()) {
            plugin.getLogger().severe("Gemini API key is not set. Cannot get feedback.");
            return null;
        }
        if (chatHistory.isEmpty()) {
            return "No chat history found for this player. Cannot provide feedback.";
        }

        String prompt = buildPrompt(playerName, chatHistory);
        String requestBody = buildRequestBody(prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(API_URL_FORMAT, apiKey)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return parseResponse(response.body());
            } else {
                plugin.getLogger().severe("Gemini API request failed with status code " + response.statusCode() + ": " + response.body());
                return "Error: Could not get feedback from the AI. Status code: " + response.statusCode();
            }
        } catch (IOException | InterruptedException e) {
            plugin.getLogger().log(Level.SEVERE, "Error sending request to Gemini API", e);
            return "Error: An exception occurred while contacting the AI service.";
        }
    }

    private String buildPrompt(String playerName, List<String> chatHistory) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("You are an English language coach providing feedback to a Minecraft player. ");
        promptBuilder.append("Analyze the following chat messages from the player named '").append(playerName).append("'. ");
        promptBuilder.append("Provide a brief, constructive assessment of their English level. ");
        promptBuilder.append("Focus on grammar, vocabulary, and clarity. Offer one or two simple tips for improvement. ");
        promptBuilder.append("Keep the tone friendly and encouraging. The feedback should be no more than 3-4 sentences.\n\n");
        promptBuilder.append("Here are the chat messages:\n");
        for (String message : chatHistory) {
            promptBuilder.append("- \"").append(message).append("\"\n");
        }
        return promptBuilder.toString();
    }

    private String buildRequestBody(String prompt) {
        JsonObject content = new JsonObject();
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);
        JsonArray parts = new JsonArray();
        parts.add(part);
        content.add("parts", parts);

        JsonObject root = new JsonObject();
        JsonArray contents = new JsonArray();
        contents.add(content);
        root.add("contents", contents);

        return gson.toJson(root);
    }

    private String parseResponse(String responseBody) {
        JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
        try {
            return responseJson.getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error parsing Gemini API response", e);
            return "Error: Could not parse the feedback from the AI response.";
        }
    }
}