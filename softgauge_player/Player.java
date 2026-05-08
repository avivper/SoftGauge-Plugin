package org.softgauge_player;

import org.softgauges_behaviors.model.BehaviorRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-session player data. Accumulates BehaviorRecords for the active session. */
public class Player {

    private final UUID   playerId;
    private final String playerName;
    private final long   sessionStartTime;

    private final List<BehaviorRecord> behaviorHistory = new ArrayList<>();

    // Resource tracking for the session
    private final Map<String, Integer> gatheredResources  = new HashMap<>();
    private final Map<String, Integer> discardedResources = new HashMap<>();

    /**
     * Full chat history for the session, keyed by epoch-ms timestamp.
     * LinkedHashMap preserves insertion (chronological) order.
     * Access is gated by {@code synchronized} on this Player instance because
     * AsyncChatEvent fires on a non-main thread.
     */
    private final Map<Long, String> chatHistory = new LinkedHashMap<>();

    public Player(UUID playerId, String playerName) {
        this.playerId         = playerId;
        this.playerName       = playerName;
        this.sessionStartTime = System.currentTimeMillis();
    }

    public void recordBehavior(BehaviorRecord record) {
        behaviorHistory.add(record);
    }

    public void addGatheredResource(String material, int amount) {
        gatheredResources.put(material, gatheredResources.getOrDefault(material, 0) + amount);
    }

    public void addDiscardedResource(String material, int amount) {
        discardedResources.put(material, discardedResources.getOrDefault(material, 0) + amount);
    }

    /**
     * Append a chat message to this session's history.
     *
     * @param timestamp epoch milliseconds when the message was sent
     * @param message   the plain-text message contents
     *
     * Thread-safe: callable from the async chat thread.
     * On the very rare collision (two messages in the same millisecond),
     * the timestamp is bumped forward until unique so no message is lost.
     */
    public synchronized void recordChatMessage(long timestamp, String message) {
        long key = timestamp;
        while (chatHistory.containsKey(key)) key++;
        chatHistory.put(key, message);
    }

    public List<BehaviorRecord> getBehaviorHistory() {
        return Collections.unmodifiableList(behaviorHistory);
    }

    public Map<String, Integer> getGatheredResources() {
        return Collections.unmodifiableMap(gatheredResources);
    }

    public Map<String, Integer> getDiscardedResources() {
        return Collections.unmodifiableMap(discardedResources);
    }

    /**
     * Snapshot of all chat messages this player has sent during the session,
     * keyed by epoch-ms timestamp, ordered chronologically.
     *
     * Returns a defensive copy — safe to iterate without holding the lock.
     */
    public synchronized Map<Long, String> getChatHistory() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(chatHistory));
    }

    public UUID   getPlayerId()         { return playerId; }
    public String getPlayerName()       { return playerName; }
    public long   getSessionStartTime() { return sessionStartTime; }
}