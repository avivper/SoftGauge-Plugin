# SoftGauge

SoftGauge is a comprehensive player behavior analysis tool for Minecraft servers. Built for modern Minecraft (1.21.1) using the Paper API, it detects, categorizes, and logs a wide range of player actions. Its primary goal is to provide server administrators and developers with actionable insights into the social dynamics and "soft skills" of their player base.

## Features

- **Modular Behavior Detection**: Easily track positive (altruism, support, community-building), negative (aggression, griefing, harassment), and neutral behaviors.
- **Team Roles**: Players claim a role (Farmer, Librarian, Armorer) by shift + right-clicking its workstation block. Each role grants a unique, mechanically-meaningful gameplay buff and ships with an in-chat progression checklist.
- **Chat Transcript Capture**: Every message a player sends during their session is preserved verbatim (epoch-ms → message), exposed via the session API, dispatched as a `SESSION_CHAT_SUMMARY` record on disconnect, and streamed live to a dedicated `chat.log` file containing only the raw transcript.
- **Structured Data Logging**: Behaviors are consistently logged into a dedicated `plugins/SoftGaugesBehaviors/behaviors.log` file in a structured format suitable for parsing and analysis.
- **Pure Chat Log**: A separate `plugins/SoftGaugesBehaviors/chat.log` file holds only `[timestamp] Player: message` lines — no behavior metadata, no severity, no location — for tooling that needs a clean conversation transcript.
- **Smart Crafting (Educational)**: A typo-tolerant "type-the-item-name" crafting service. Players write the item they want (e.g. *"Dimond swrd"*) into a chat prompt or via `/craft`; SoftGauge resolves it to the closest valid `Material` using native Levenshtein distance, gently corrects the spelling in chat, and logs every correction so educators can review language mistakes later.
- **Daily Login Streaks (Duolingo-style)**: Every player accrues a consecutive-days login counter. The streak increments on the next calendar day, resets to 1 if a day is skipped, and is shown in chat the moment they join the server. Persists across server restarts via `streaks.yml`. Includes `/streak` (own status) and `/streak top` (server-wide leaderboard).
- **AI English Feedback (Gemini-powered)**: At server shutdown SoftGauge sweeps the chat transcripts of every player who spoke this session, sends each one to Google's Gemini API, and writes a friendly, concrete English-coaching note to `feedback/<player>.txt`. The feature is opt-in: it stays dormant until an API key is set in `config.yml`.
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

## Daily Login Streaks (Duolingo-style)

A consecutive-day login counter that keeps players coming back. The streak system activates as soon as the plugin enables, listens for every player join, and surfaces the result in-game on a 1-second delay so the banner doesn't compete with vanilla join chatter.

### Rules

| Scenario | Effect |
|---|---|
| First-ever login | `current = 1`, `longest = 1`, banner: **"🎉 Welcome to SoftGauge! Day 1 of your streak starts today!"** |
| Same calendar day re-join | No state change. Banner: **"🔥 Day N streak — keep it up!"** |
| Exactly the next day | `current += 1`, `longest = max(longest, current)`. Banner: **"🔥 Day N! You're on fire!"** |
| 2+ days gap (broken streak) | `current = 1` (today restarts), `longest` preserved. Banner: **"💔 Streak reset — last login was X days ago. Day 1 starts now!"** |

The "today" boundary uses the **server's** default time zone (`Clock.systemDefaultZone()`) so all players share a consistent calendar regardless of where they connect from.

### Components (`org.softgauge_streak`)

| Class | Responsibility |
|---|---|
| `StreakRecord`         | Immutable record (`UUID`, `playerName`, `lastLogin`, `currentStreak`, `longestStreak`). |
| `StreakRepository`     | Loads `streaks.yml` into a `ConcurrentHashMap` on enable; persists on every change and on disable. |
| `StreakService`        | The only class that interprets the calendar. Returns a typed `LoginOutcome` (`FIRST_LOGIN` / `SAME_DAY` / `CONTINUED` / `RESET`). Injectable `Clock` for unit testing. |
| `StreakLoginListener`  | Hooks `PlayerJoinEvent`, calls `service.recordLogin(...)`, schedules the styled Adventure-component banner 20 ticks later. |
| `StreakCommand`        | Read-only `/streak` and `/streak top` (server leaderboard, sorted by current streak, ties broken by longest). |

### Persistence

Streak data is stored in `plugins/SoftGaugesBehaviors/streaks.yml`:

```yaml
streaks:
  "uuid-1234":
    name: "Alice"
    last_login: "2026-05-08"
    current: 5
    longest: 12
```

Every `LoginOutcome` that changes state triggers an immediate `persist()` so a server crash mid-session never costs a player their day.

### Commands

| Command | Output |
|---|---|
| `/streak`     | `🔥 Current streak: 5 days  ✦  Longest ever: 12 days  ✦  Last login: 2026-05-08` |
| `/streak top` | Top 10 active streaks across the server, sorted by current streak then longest. |

### Wiring (already done in `SoftGauge.onEnable()`)

```java
StreakRepository streakRepo = new StreakRepository(this);
streakService = new StreakService(streakRepo);
getServer().getPluginManager().registerEvents(
        new StreakLoginListener(this, streakService), this);
getCommand("streak").setExecutor(new StreakCommand(streakService));
```

External plugins / commands can read streak state without touching internals:

```java
SoftGauge softGauge = (SoftGauge) Bukkit.getPluginManager().getPlugin("SoftGaugesBehaviors");
softGauge.getStreakService().getStreak(player.getUniqueId())
        .ifPresent(record -> /* … */);
```

## AI English Feedback (Gemini)

SoftGauge integrates with Google's Gemini API to give every player a short, friendly assessment of their in-game English: grammar, vocabulary, clarity, and one or two concrete tips. **Feedback is generated only at server shutdown** — there is no on-demand command. The reasoning: a clean batch at shutdown means one consistent run, no surprise per-player API spend during play, and the feedback files are ready to hand to educators after the lesson ends.

### Get an API key

API keys are issued by Google AI Studio.

1. Go to **https://aistudio.google.com/app/apikey** (sign in with a Google account).
2. Click **Create API key**, copy the value (it starts with `AIza…`).
3. Open `plugins/SoftGaugesBehaviors/config.yml` after the plugin's first run.
4. Paste the key into `gemini-api-key`:

```yaml
gemini-api-key: "AIza..."
```

5. Restart the server.

If `gemini-api-key` is left blank, AI feedback is silently skipped — the rest of the plugin keeps working. **Treat the key like a password**: don't commit `config.yml` to a public repo (add it to `.gitignore`).

### Components (`org.softgauge.ai`)

| Class | Responsibility |
|---|---|
| `GeminiFeedbackProvider` | HTTP client for `https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent`. Builds the coach prompt, sends the player transcript, and parses `candidates[0].content.parts[0].text` out of the response. Uses standard Java `HttpClient` — no extra dependencies beyond Gson (already on the classpath via Paper). |
| `AIFeedbackExporter`     | Invoked from `SoftGauge.onDisable`. Reads `behaviors.log`, groups chat entries by player, calls `GeminiFeedbackProvider` once per player, and writes the result to `feedback/<player>.txt`. The only entry point — there is no command, no chat trigger, no scheduler. |

### Output location

```
plugins/SoftGaugesBehaviors/feedback/
├── Alice.txt
├── Bob.txt
└── …
```

### Gemini API contract used

```http
POST https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=AIza…
content-type: application/json

{
  "contents": [
    {
      "parts": [
        { "text": "You are an English language coach …\n\nHere are the chat messages: …" }
      ]
    }
  ]
}
```

Non-2xx responses are logged at `SEVERE` with the raw body, and a clear `Error: …` string is returned to the caller so the per-player flow never crashes the shutdown sequence.

## Smart Crafting (Educational)

A typo-tolerant crafting service that turns natural-language item names into successful crafts. Designed for the language-learning context: every misspelling is silently corrected for the player AND logged for the teacher.

### Pipeline

```
input string ──▶ MaterialFuzzyMatcher ──▶ RecipeFulfillment ──▶ Player.inventory
                 (Levenshtein, threshold=3)  (canFulfill / deductIngredients)
                          │
                          └──▶ plugin.getLogger().warning(LANGUAGE_MISTAKE …)
```

### Components (`org.softgauge_crafting`)

| Class | Responsibility |
|---|---|
| `SmartCraftingService`     | Public entry point. `processCraftingAttempt(Player, String)` drives the full Parse → Spell-check → Resource-check → Craft pipeline. |
| `MaterialFuzzyMatcher`     | Pure-Java Levenshtein distance + nearest-`Material` lookup. Stateless, thread-safe. Threshold (max edit distance) is `3`. |
| `RecipeFulfillment`        | Aggregates a `Recipe` into `Material → quantity`, checks `Inventory` against it, describes missing items, and atomically deducts on success. |
| `SmartCraftPromptManager`  | Per-player prompt session state (concurrent map of UUID → start-timestamp), 30 s timeout, idempotent `beginPrompt` / atomic `consumePromptIfActive` / explicit `cancelPrompt`. Listens for `PlayerQuitEvent` to clean up disconnected players. |
| `SmartCraftChatListener`   | `AsyncChatEvent` interceptor at `LOWEST` priority. If the sender has an active prompt the message is cancelled (so it doesn't broadcast publicly) and forwarded to `SmartCraftingService` on the main thread. Runs *before* `ChatBehaviorDetector` so a craft request never becomes a `CHAT_ASKED_QUESTION`. |
| `SmartCraftAccessListener` | `PlayerInteractEvent` listener: shift + right-click on a Crafting Table opens the prompt and suppresses the vanilla 3×3 GUI. Plain right-click is left untouched, so vanilla crafting still works. |
| `SmartCraftCommand`        | `/craft` opens the prompt; `/craft <item name>` is a one-shot direct attempt. |

### In-Game Interface

Players have **three** equally valid ways to trigger a smart-craft attempt — all routed through the same `SmartCraftingService.processCraftingAttempt(...)` so spell-check, recipe-check, deduction, and educator log behave identically regardless of entry point:

| Trigger | UX |
|---|---|
| **Sneak + right-click any Crafting Table** | Opens the chat prompt with a styled banner. The vanilla 3×3 GUI is suppressed for that interaction; plain right-clicks (no sneak) still open vanilla crafting unchanged. |
| **`/craft`** (no args)        | Opens the chat prompt — useful for keyboard players. |
| **`/craft <item name>`**      | One-shot direct attempt. Skips the prompt entirely; great when the player already knows what they want. Example: `/craft diamond sword`. |

While the prompt is open, the player sends their next chat message normally. `SmartCraftChatListener` intercepts that message, cancels the public broadcast so it doesn't appear to other players, and forwards it to the service. Typing **`cancel`** during the prompt backs out gracefully without crafting anything.

Prompt session rules:
- 30-second hard timeout (configurable via `SmartCraftPromptManager.PROMPT_TIMEOUT_MS`).
- Disconnecting cancels any active prompt automatically.
- A second sneak + right-click while already prompted just refreshes the timeout and re-displays the banner.

### Wiring (already done in `SoftGauge.onEnable()`)

```java
smartCraftingService    = new SmartCraftingService(this);
smartCraftPromptManager = new SmartCraftPromptManager();
getServer().getPluginManager().registerEvents(smartCraftPromptManager, this);
getServer().getPluginManager().registerEvents(
        new SmartCraftAccessListener(smartCraftPromptManager), this);
getServer().getPluginManager().registerEvents(
        new SmartCraftChatListener(this, smartCraftPromptManager, smartCraftingService), this);
getCommand("craft").setExecutor(
        new SmartCraftCommand(smartCraftPromptManager, smartCraftingService));
```

### Educator Log

Every spelling correction emits a single grep-friendly line at `WARNING` level:

```
[lingocraft] LANGUAGE_MISTAKE player=Alice input="Dimond swrd" corrected="DIAMOND_SWORD" distance=2
```

Filter the server log with `grep LANGUAGE_MISTAKE` to assemble a class-wide error report.

### Edge cases handled

| Input | Outcome |
|---|---|
| Exact match (`"Diamond Sword"`)  | No correction, no log entry. |
| Mild typo (`"Dimond swrd"`, distance ≤ 3) | Auto-corrected, player notified, mistake logged. |
| Gibberish (`"asdfgh"`, distance > 3) | Refused with red message — does not auto-craft a random item. |
| Item that has no recipe (`"Bedrock"`) | Refused with red message ("isn't something you can craft"). |
| Player missing items | Refused with red message listing exactly what's short, e.g. `"2x stick, 1x diamond"`. |
| Inventory full when adding result | Result drops at the player's feet — no silent loss. |
| Multiple recipes (e.g. sticks from any planks) | First recipe whose ingredients the player owns is used. |

### Threading

`processCraftingAttempt` mutates the player's inventory and must run on the Bukkit main thread. If your input source is async (e.g. `AsyncChatEvent`), schedule a task with `Bukkit.getScheduler().runTask(plugin, …)` first.

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