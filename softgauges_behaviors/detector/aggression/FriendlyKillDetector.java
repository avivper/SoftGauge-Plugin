package org.softgauges_behaviors.detector.aggression;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scoreboard.Team;
import org.softgauge.SoftGauge;
import org.softgauges_behaviors.api.AbstractBehaviorDetector;
import org.softgauges_behaviors.model.GameAction;

/**
 * PLAYER_KILL_FRIENDLY
 *
 * Fires whenever a player kills another player.
 * The {@code same_team} metadata flag is {@code true} when both players are
 * on the same Bukkit scoreboard team (clearly friendly-fire).
 * Even without a team, the kill is logged so the data consumer can apply
 * its own context rules.
 */
public class FriendlyKillDetector extends AbstractBehaviorDetector {

    public FriendlyKillDetector(SoftGauge plugin) {
        super(plugin);
    }

    @Override
    public GameAction getAction() {
        return GameAction.PLAYER_KILL_FRIENDLY;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof Player killer)) return;

        Player victim  = event.getEntity();
        boolean sameTeam = areSameTeam(killer, victim);

        String worldName = victim.getWorld().getName();
        String deathMsg  = event.getDeathMessage() != null
                ? event.getDeathMessage() : "no message";

        emit(record(GameAction.PLAYER_KILL_FRIENDLY, killer)
                .at(victim.getLocation())
                .description(killer.getName() + " killed " + victim.getName()
                        + (sameTeam ? " (SAME TEAM)" : "") + " — " + deathMsg)
                .meta("victim",    victim.getName())
                .meta("same_team", sameTeam)
                .meta("world",     worldName)
                .build());
    }

    private boolean areSameTeam(Player a, Player b) {
        Team teamA = plugin.getServer().getScoreboardManager()
                .getMainScoreboard().getEntryTeam(a.getName());
        Team teamB = plugin.getServer().getScoreboardManager()
                .getMainScoreboard().getEntryTeam(b.getName());
        return teamA != null && teamA.equals(teamB);
    }
}
