# SoftGauge

SoftGauge is a comprehensive player behavior analysis tool for Minecraft servers. Built for modern Minecraft (1.21.1) using the Paper API, it detects, categorizes, and logs a wide range of player actions. Its primary goal is to provide server administrators and developers with actionable insights into the social dynamics and "soft skills" of their player base.

## Features

- **Modular Behavior Detection**: Easily track positive (altruism, support, community-building), negative (aggression, griefing, harassment), and neutral behaviors.
- **Team Roles**: Players claim a role (Farmer, Librarian, Armorer) by shift + right-clicking its workstation block. Each role grants a unique, mechanically-meaningful gameplay buff and ships with an in-chat progression checklist.
- **Chat Transcript Capture**: Every message a player sends during their session is preserved verbatim (epoch-ms → message), exposed via the session API, dispatched as a `SESSION_CHAT_SUMMARY` record on disconnect, and streamed live to a dedicated `chat.log` file containing only the raw transcript.
- **Structured Data Logging**: Behaviors are consistently logged into a dedicated `plugins/SoftGaugesBehaviors/behaviors.log` file in a structured format suitable for parsing and analysis.
- **Pure Chat Log**: A separate `plugins/SoftGaugesBehaviors/chat.log` file holds only `[timestamp] Player: message` lines — no behavior metadata, no severity, no location — for tooling that needs a clean conversation transcript.
- **Developer API**: In-memory access to behavior records via a simple consumer callback, allowing other plugins or data pipelines to react to player behaviors in real-time.
- **Session Tracking**: Maintains internal states of player sessions to provide context to interactions.

## Installation

1. Clone the repository.
2. Build the project using Maven:
   ```bash
   mvn clean package
   ```
3. Copy the compiled `.jar` file from the `target/` directory to your server's `plugins/` directory.
4. Restart your server.

## Code Architecture

The plugin is designed to be highly modular and extensible. Here's a breakdown of the core components:

### Core Components
- **`SoftGauge.java`**: The main plugin class. It manages the plugin lifecycle, basic session tracking, and the central dispatching of behavior records to listeners. It also bootstraps the team-role subsystem.
- **`DetectorRegistry.java`**: A central registry that instantiates and registers every behavior detector.
- **`BehaviorLogger.java`**: Handles thread-safe logging of `BehaviorRecord` objects to the console and to the log file.
- **`ChatLogger.java`**: Dedicated writer for `chat.log`. Receives every chat message from `ChatHistoryTracker` and persists it as a single `[timestamp] Player: message` line — no other data is mixed into this file.
- **`PlacementTracker.java`**: A utility designed to track blocks placed by players, which is essential for certain griefing detectors (e.g., distinguishing between destroying natural terrain versus player-built structures).
- **`ChatHistoryTracker.java`**: Listener that captures every chat message a player sends and appends it to their `Player` session as `Map<Long, String>` (epoch-ms → plain-text message). Lightweight — does not analyse content (that is the role of `ChatBehaviorDetector`).
- **`RoleManager.java`**: Thread-safe in-memory store mapping each player to their claimed `PlayerRole`. Authoritative source consulted by every role plugin before applying its buff.
- **`RoleRegistry.java`**: Mirror of `DetectorRegistry` for the role subsystem — instantiates every `AbstractRolePlugin` and the shared `RoleClaimListener`, and wires them into the Bukkit event bus.

### Detectors
Detectors are the heart of SoftGauge. They handle the business logic of analyzing server events. They are categorized into sub-packages under `org.softgauges_behaviors.detector`:
- `activity`: Basic movement and session behaviors.
- `aggression`: Unprovoked attacks, harassment.
- `altruism`: Giving items, trading fairly.
- `communication`: Chat patterns.
- `construction`: Building structures, communal farming.
- `griefing`: Destroying property, theft, fire/lava abuse.
- `support`: Healing allies, feeding pets.

### Adding a New Detector (Modification Guide)
To add a new behavior to track, follow these steps:
1. **Create the Class**: Create a new class extending `AbstractBehaviorDetector` (or implementing `BehaviorDetector`) in the appropriate sub-package under `org.softgauges_behaviors.detector`.
2. **Implement Logic**: Use standard Bukkit `@EventHandler` annotations to listen for relevant events. When the behavior criteria are met, use the `emit(...)` method to dispatch a `BehaviorRecord`.
3. **Register the Detector**: Open `DetectorRegistry.java` and add a new `register(new YourNewDetector(plugin));` call inside the `registerAll()` method. **No other files need to be modified.**

## Chat Transcript API

Every line a player types in chat is preserved per-session, in order, with the exact wall-clock timestamp it was sent.

### Live access while the player is online

```java
SoftGauge softGauge = (SoftGauge) Bukkit.getPluginManager().getPlugin("SoftGaugesBehaviors");
org.softgauge_player.Player session = softGauge.getActiveSessions().get(player.getUniqueId());
if (session != null) {
    Map<Long, String> transcript = session.getChatHistory();
    // Key   = epoch-ms timestamp (insertion order = chronological order)
    // Value = plain-text message contents
    transcript.forEach((ts, msg) -> System.out.println(ts + " → " + msg));
}
```

The map returned by `getChatHistory()` is a defensive snapshot — safe to iterate without holding any locks.

### Pure chat log file

In addition to the in-memory transcript, every chat message is streamed in real time to a dedicated file:

```
plugins/SoftGaugesBehaviors/chat.log
```

This file contains **only** chat — no behavior categories, no severity flags, no metadata, no location data. Each line follows the format:

```
[2026-05-08T14:23:45.123Z] Alice: hello world
[2026-05-08T14:24:01.456Z] Bob: hi alice
```

Multi-line / pasted messages are flattened to a single space so the one-line-per-message contract holds for line-oriented parsers (`tail -f`, `grep`, `awk`, etc.). The file is opened in append mode, flushed after every write, and closed cleanly on plugin shutdown.

This is the recommended source for downstream language-analytics tools that just want raw conversation text. For richer context (chat-derived behavior detections such as `CHAT_GAVE_INSTRUCTION`, `CHAT_ENCOURAGEMENT`, etc.) consume `behaviors.log` or the in-process consumer API instead.

### End-of-session dispatch

When a player disconnects, SoftGauge dispatches a `SESSION_CHAT_SUMMARY` `BehaviorRecord` carrying the full transcript in metadata. Any registered behavior consumer receives it automatically:

```java
softGauge.addBehaviorConsumer(record -> {
    if (record.getGameAction() == GameAction.SESSION_CHAT_SUMMARY) {
        Map<Long, String> chat = record.getMeta("chat_history");
        int count   = record.getMeta("message_count");
        long firstAt = record.getMeta("first_message_at");
        long lastAt  = record.getMeta("last_message_at");
        // forward to your data pipeline / language analytics / dashboard
    }
});
```

The same record is also written verbatim to `behaviors.log` in the standard structured format.

### Storage Model

| Owner | Field | Type | Lifecycle |
|---|---|---|---|
| `org.softgauge_player.Player` | `chatHistory` | `LinkedHashMap<Long, String>` (synchronised) | Lives for the session; cleared automatically when SoftGauge removes the session entry on quit. |
| `ChatLogger` | `plugins/SoftGaugesBehaviors/chat.log` | append-only text file | Persists across server restarts. Opened on `onEnable`, closed on `onDisable`. |

`recordChatMessage(timestamp, message)` is idempotent on collisions: if two messages share an epoch-ms (effectively impossible from a single human typist), the later one is shifted forward 1 ms so no message is overwritten.

## Team Roles

SoftGauge ships a parallel "team role" subsystem (`org.softgauge_roles`) that gives players differentiated, cooperation-oriented gameplay buffs. The architecture intentionally mirrors the behavior-detector pipeline so the two systems read the same way.

### Available Roles

| Role | Workstation | Buff | In-game Goals |
|---|---|---|---|
| **Farmer** | Composter | Fully-grown crop drops are doubled (Wheat / Carrots / Potatoes). Vanilla Fortune is preserved before the multiplier is applied. | Plant 10 wheat seeds · Harvest 20 fully-grown crops · Share food with a teammate · Build a communal farm. |
| **Librarian** | Lectern | Mining Lapis Lazuli ore (overworld + deepslate variants) drops 2× experience. Silk Touch is respected. | Mine 5 lapis ores · Place a Lectern in a residential area · Enchant for a teammate · Build a bookshelf wall. |
| **Armorer** | Blast Furnace | Equipped armor takes 50 % less durability damage. Tools are unaffected. | Smelt iron · Craft a full iron+ armor set · Take a hit defending a teammate · Gift a teammate one armor piece. |

### Claiming a Role

Players claim a role by **shift + right-clicking its workstation block** in-world:

- If they have no role, the role is assigned and the goal list is sent in chat.
- If they hold this role already, the goal list is re-displayed.
- If they hold a different role, the claim is refused with a clear chat message.

A plain right-click on the workstation passes through unchanged so vanilla composting / lectern / smelting still works.

### Adding a New Role (Modification Guide)
1. **Add an enum constant** to `PlayerRole` with its display name, workstation `Material`, and a `List<String>` of progression goals.
2. **Create the role class** in `org.softgauge_roles.role`, extending `AbstractRolePlugin`. Add `@EventHandler` methods that gate every action on `hasThisRole(player)`.
3. **Register it** with one line in `RoleRegistry.registerAll()`. **No other files need to be modified.**

### Role Code Layout

```
org.softgauge_roles
├── PlayerRole.java          (enum: name + workstation + goal list)
├── RoleManager.java         (thread-safe UUID → role store)
├── AbstractRolePlugin.java  (base class for every concrete role)
├── RoleClaimListener.java   (shift + right-click → claim)
├── RoleRegistry.java        (registers every role with Bukkit)
└── role/
    ├── FarmerRole.java
    ├── LibrarianRole.java
    └── ArmorerRole.java
```

## Developer API

You can hook into SoftGauge's real-time data stream from another plugin.

```java
SoftGauge softGauge = (SoftGauge) Bukkit.getPluginManager().getPlugin("lingocraft"); // Note: Internal plugin name based on pom.xml
if (softGauge != null) {
    softGauge.addBehaviorConsumer(record -> {
        // Send record to a database, a Discord webhook, or an external analytics pipeline
        System.out.println("Intercepted behavior from " + record.getActorId() + ": " + record.getAction());
    });
}
```

## Contributing & Maintenance
When making changes to the codebase, **please ensure this README is updated** to reflect any new architecture, core changes, new configuration options, or API modifications.