package org.softgauge_player;

import org.softgauges_behaviors.model.BehaviorRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Per-session player data. Accumulates BehaviorRecords for the active session. */
public class Player {

    private final UUID   playerId;
    private final String playerName;
    private final long   sessionStartTime;

    private final List<BehaviorRecord> behaviorHistory = new ArrayList<>();

    public Player(UUID playerId, String playerName) {
        this.playerId         = playerId;
        this.playerName       = playerName;
        this.sessionStartTime = System.currentTimeMillis();
    }

    public void recordBehavior(BehaviorRecord record) {
        behaviorHistory.add(record);
    }

    public List<BehaviorRecord> getBehaviorHistory() {
        return Collections.unmodifiableList(behaviorHistory);
    }

    public UUID   getPlayerId()         { return playerId; }
    public String getPlayerName()       { return playerName; }
    public long   getSessionStartTime() { return sessionStartTime; }
}
