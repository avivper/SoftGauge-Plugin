# SoftGauge

SoftGauge is a comprehensive player behavior analysis tool for Minecraft servers. Built for modern Minecraft (1.21.1) using the Paper API, it detects, categorizes, and logs a wide range of player actions. Its primary goal is to provide server administrators and developers with actionable insights into the social dynamics and "soft skills" of their player base.

## Features

- **Modular Behavior Detection**: Easily track positive (altruism, support, community-building), negative (aggression, griefing, harassment), and neutral behaviors.
- **Structured Data Logging**: Behaviors are consistently logged into a dedicated `plugins/SoftGaugesBehaviors/behaviors.log` file in a structured format suitable for parsing and analysis.
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
- **`SoftGauge.java`**: The main plugin class. It manages the plugin lifecycle, basic session tracking, and the central dispatching of behavior records to listeners.
- **`DetectorRegistry.java`**: A central registry that instantiates and registers every behavior detector.
- **`BehaviorLogger.java`**: Handles thread-safe logging of `BehaviorRecord` objects to the console and to the log file.
- **`PlacementTracker.java`**: A utility designed to track blocks placed by players, which is essential for certain griefing detectors (e.g., distinguishing between destroying natural terrain versus player-built structures).

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