package org.softgauges_behaviors.detector.communication;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.softgauge.SoftGauge;
import org.softgauges_behaviors.api.AbstractBehaviorDetector;
import org.softgauges_behaviors.model.BehaviorRecord;
import org.softgauges_behaviors.model.GameAction;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Handles all chat-based GameActions in a single listener.
 *
 * Detection is ordered by specificity (most specific pattern checked first)
 * so a single message produces at most one leadership/communication log entry,
 * but emotional and question/encouragement detections are independent.
 *
 * Additionally schedules IGNORED_NEARBY_HELP checks: if a help request goes
 * unanswered by nearby players for 30 s, each silent neighbour is logged.
 *
 * Thread safety: this listener fires on the async chat thread.
 * All shared state uses ConcurrentHashMap; {@link #emit} delegates to
 * BehaviorLogger which is already synchronized.
 *
 * Primary action (returned by getAction()) is CHAT_ASKED_QUESTION —
 * this detector actually covers many actions; getAction() is informational only.
 */
public class ChatBehaviorDetector extends AbstractBehaviorDetector {

    // ── Leadership patterns (checked in priority order) ──────────────────────

    private static final Pattern DIVIDED_TASKS = Pattern.compile(
            "(?i)(you (handle|take care of|do|build|mine|gather|defend|attack)|"
                    + "@\\w+ (go|build|mine|do|handle)|"
                    + "\\w+[,:] (you|your job|your task)|"
                    + "i'?ll .+ (and|while) you)");

    private static final Pattern GROUP_TASK = Pattern.compile(
            "(?i)\\b(let'?s all|everyone (come|gather|meet|build|go|help)|who wants? to|"
                    + "anyone (want|up) (to|for)|let'?s .+ together|team up|join me|"
                    + "meet (me|us) at)\\b");

    private static final Pattern INSTRUCTION = Pattern.compile(
            "(?i)\\b(you should|you need to|you must|everyone go|all of you|"
                    + "go (to|mine|build|dig|fight|get|find)|"
                    + "come (here|to|with)|let'?s go|we (should|need to|must|have to))\\b");

    // ── Problem solving ───────────────────────────────────────────────────────

    private static final Pattern ASKED_HELP = Pattern.compile(
            "(?i)\\b(help|how do (i|you)|how to|can someone|anyone know|"
                    + "i'?m stuck|i need help|please help|what should i|"
                    + "where (is|do|can)|i don'?t know)\\b");

    // ── Communication ─────────────────────────────────────────────────────────

    private static final Pattern ENCOURAGEMENT = Pattern.compile(
            "(?i)\\b(good (job|work|one|going)|well done|nice (job|work|one)|"
                    + "keep (it up|going)|you('?ve)? got this|you can do it|"
                    + "proud of you|great (job|work)|well played|gj\\b|gg\\b|"
                    + "you'?re (doing great|awesome|amazing|killing it))\\b");

    private static final Pattern QUESTION = Pattern.compile(
            "(?i)(\\?\\s*$|^(what|where|when|how|why|who|which|"
                    + "can (you|someone|anyone)|does (anyone|someone)|"
                    + "is (there|it)|are (there|you)|do (you|we))\\b)");

    // "@PlayerName" or "PlayerName:" / "PlayerName,"  at message start
    private static final Pattern DIRECTED = Pattern.compile("@(\\w+)|^(\\w+)[,:]");

    // ── Emotional regulation ──────────────────────────────────────────────────

    private static final Pattern AGGRESSIVE = Pattern.compile(
            "(?i)\\b(hate (this|you|it)|stupid|idiot|you suck|"
                    + "this (sucks|is trash|is bad)|shut up|so bad|terrible|rage|"
                    + "i'?m (angry|mad|furious|done)|i quit|i'?m (out|leaving)|"
                    + "this (game|server) (sucks|is (terrible|awful|stupid)))\\b");

    private static final Pattern POSITIVE = Pattern.compile(
            "(?i)\\b(this is (fun|amazing|awesome|great|so good)|i love (this|it)|"
                    + "so (fun|cool|awesome|epic)|yay|woohoo|let'?s go+|"
                    + "best (game|server|day)|having (so much )?fun|happy)\\b");

    // ── IGNORED_NEARBY_HELP tracking ─────────────────────────────────────────

    /** actorUUID → last chat timestamp; updated atomically from async thread. */
    private final Map<UUID, Long> lastChatTime = new ConcurrentHashMap<>();

    private static final long IGNORED_HELP_WINDOW_MS = 30_000;
    private static final double IGNORED_HELP_RADIUS  = 50.0;

    public ChatBehaviorDetector(SoftGauge plugin) {
        super(plugin);
    }

    @Override
    public GameAction getAction() {
        // This detector is multi-action; returning a representative value.
        return GameAction.CHAT_ASKED_QUESTION;
    }

    // ── Main event handler ────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender  = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        lastChatTime.put(sender.getUniqueId(), System.currentTimeMillis());

        detectLeadership(sender, message);
        detectCommunication(sender, message);
        detectEmotional(sender, message);
        detectHelpAndScheduleIgnored(sender, message);
    }

    // ── Detection groups ──────────────────────────────────────────────────────

    /** Leadership: DIVIDED_TASKS → INITIATED_GROUP_TASK → GAVE_INSTRUCTION (priority order). */
    private void detectLeadership(Player sender, String message) {
        if (DIVIDED_TASKS.matcher(message).find()) {
            emitChat(sender, GameAction.CHAT_DIVIDED_TASKS,
                    sender.getName() + " divided tasks: \"" + message + "\"",
                    "message", message);

        } else if (GROUP_TASK.matcher(message).find()) {
            emitChat(sender, GameAction.CHAT_GROUP_TASK,
                    sender.getName() + " initiated a group task: \"" + message + "\"",
                    "message", message);

        } else if (INSTRUCTION.matcher(message).find()) {
            emitChat(sender, GameAction.CHAT_GAVE_INSTRUCTION,
                    sender.getName() + " gave an instruction: \"" + message + "\"",
                    "message", message);
        }
    }

    /** Communication: directed message, encouragement, questions (all independent). */
    private void detectCommunication(Player sender, String message) {
        java.util.regex.Matcher dm = DIRECTED.matcher(message);
        if (dm.find()) {
            String name = dm.group(1) != null ? dm.group(1) : dm.group(2);
            if (name != null && isOnlinePlayer(name)) {
                emitChat(sender, GameAction.CHAT_DIRECTED_MESSAGE,
                        sender.getName() + " sent directed message to " + name
                                + ": \"" + message + "\"",
                        "recipient", name, "message", message);
            }
        }

        if (ENCOURAGEMENT.matcher(message).find()) {
            emitChat(sender, GameAction.CHAT_ENCOURAGEMENT,
                    sender.getName() + " sent encouragement: \"" + message + "\"",
                    "message", message);
        }

        if (QUESTION.matcher(message).find()) {
            emitChat(sender, GameAction.CHAT_ASKED_QUESTION,
                    sender.getName() + " asked a question: \"" + message + "\"",
                    "message", message);
        }
    }

    /** Emotional: aggressive and positive are independent (both can fire). */
    private void detectEmotional(Player sender, String message) {
        if (AGGRESSIVE.matcher(message).find()) {
            emitChat(sender, GameAction.CHAT_AGGRESSIVE,
                    sender.getName() + " sent aggressive message: \"" + message + "\"",
                    "message", message);
        } else if (POSITIVE.matcher(message).find()) {
            emitChat(sender, GameAction.CHAT_POSITIVE,
                    sender.getName() + " expressed positively: \"" + message + "\"",
                    "message", message);
        }
    }

    /** Help request: log CHAT_ASKED_FOR_HELP and schedule IGNORED_NEARBY_HELP check. */
    private void detectHelpAndScheduleIgnored(Player sender, String message) {
        if (!ASKED_HELP.matcher(message).find()) return;

        emitChat(sender, GameAction.CHAT_ASKED_FOR_HELP,
                sender.getName() + " asked for help: \"" + message + "\"",
                "message", message);

        // Capture state for the lambda — avoid holding Player reference across ticks
        UUID   requesterId   = sender.getUniqueId();
        String requesterName = sender.getName();
        long   requestedAt   = System.currentTimeMillis();

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            org.bukkit.entity.Player requester = plugin.getServer().getPlayer(requesterId);
            if (requester == null) return;

            for (org.bukkit.entity.Player nearby : plugin.getServer().getOnlinePlayers()) {
                if (nearby.getUniqueId().equals(requesterId)) continue;
                if (!nearby.getWorld().equals(requester.getWorld())) continue;
                if (nearby.getLocation().distance(requester.getLocation()) > IGNORED_HELP_RADIUS) continue;

                Long theirLastChat = lastChatTime.get(nearby.getUniqueId());
                if (theirLastChat != null && theirLastChat > requestedAt) continue; // they responded

                emit(BehaviorRecord.detect(GameAction.IGNORED_NEARBY_HELP, nearby)
                        .at(nearby.getLocation())
                        .description(nearby.getName() + " ignored a help request from "
                                + requesterName)
                        .meta("requester", requesterName)
                        .build());
            }
        }, 20L * (IGNORED_HELP_WINDOW_MS / 1000));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Two-key meta shortcut for the common (key1, val1, key2, val2) pattern. */
    private void emitChat(Player sender, GameAction action, String description,
                          String k1, Object v1, String k2, Object v2) {
        emit(record(action, sender)
                .description(description)
                .meta(k1, v1).meta(k2, v2)
                .build());
    }

    private void emitChat(Player sender, GameAction action, String description,
                          String key, Object value) {
        emit(record(action, sender)
                .description(description)
                .meta(key, value)
                .build());
    }

    private boolean isOnlinePlayer(String name) {
        return plugin.getServer().getOnlinePlayers().stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(name));
    }
}
