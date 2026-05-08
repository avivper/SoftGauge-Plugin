package org.softgauges_behaviors.tracking;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.softgauge.SoftGauge;
import org.softgauges_behaviors.logging.ChatLogger;

/**
 * Records the full chat transcript of every active session.
 *
 * <p>Mirrors the {@link PlacementTracker} pattern: lightweight Listener whose
 * sole responsibility is to populate per-player session state. It does NOT
 * emit {@code BehaviorRecord}s — analysis of message content is handled by
 * {@code ChatBehaviorDetector}. This tracker keeps the raw transcript so
 * downstream consumers (e.g. JSON exporters, language-skill analytics) can
 * read every line a player wrote, in order, with exact timestamps.</p>
 *
 * <p>Storage: each {@link org.softgauge_player.Player} session owns a
 * {@code Map<Long, String>} (epoch-ms → message). The tracker looks up the
 * active session and appends, and additionally delegates to {@link ChatLogger}
 * which writes the same line to a dedicated chat-only log file
 * ({@code plugins/SoftGaugesBehaviors/chat.log}) for downstream tools that
 * want a clean transcript with no behavior metadata.</p>
 *
 * <p>Thread-safety: {@code AsyncChatEvent} fires on a background thread.
 * Both {@link org.softgauge_player.Player#recordChatMessage(long, String)}
 * and {@link ChatLogger#log(String, long, String)} are synchronized.</p>
 */
public class ChatHistoryTracker implements Listener {

    private final SoftGauge  plugin;
    private final ChatLogger chatLogger;

    public ChatHistoryTracker(SoftGauge plugin, ChatLogger chatLogger) {
        this.plugin     = plugin;
        this.chatLogger = chatLogger;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        long   now    = System.currentTimeMillis();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        // 1) Append to the per-session in-memory transcript (if the session exists)
        org.softgauge_player.Player session =
                plugin.getActiveSessions().get(sender.getUniqueId());
        if (session != null) {
            session.recordChatMessage(now, message);
        }

        // 2) Append to the dedicated chat.log file — runs even if the session
        //    map briefly lacks the player (defensive: chat.log is the source of
        //    truth for raw transcripts and must never lose a message).
        chatLogger.log(sender.getName(), now, message);
    }
}
