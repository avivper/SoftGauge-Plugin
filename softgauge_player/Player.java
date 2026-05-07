package org.softgauge_player;

import org.softgauges_behaviors.model.BehaviorRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
    private final Map<String, Integer> gatheredResources = new HashMap<>();
    private final Map<String, Integer> discardedResources = new HashMap<>();

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

    public List<BehaviorRecord> getBehaviorHistory() {
        return Collections.unmodifiableList(behaviorHistory);
    }
    
    public Map<String, Integer> getGatheredResources() {
        return Collections.unmodifiableMap(gatheredResources);
    }
    
    public Map<String, Integer> getDiscardedResources() {
        return Collections.unmodifiableMap(discardedResources);
    }

    public UUID   getPlayerId()         { return playerId; }
    public String getPlayerName()       { return playerName; }
    public long   getSessionStartTime() { return sessionStartTime; }
}