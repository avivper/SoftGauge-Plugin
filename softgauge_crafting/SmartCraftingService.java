package org.softgauge_crafting;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Educational, fault-tolerant "type-the-item-name" crafting service.
 *
 * <p>Designed to be invoked from any input source — chat prompt, custom Anvil
 * GUI listener, or command — by passing the player and their raw input to
 * {@link #processCraftingAttempt(Player, String)}.</p>
 *
 * <p>The service composes two collaborators:
 * <ul>
 *   <li>{@link MaterialFuzzyMatcher} — resolves the input to a Material,
 *       tolerating typos via Levenshtein distance.</li>
 *   <li>{@link RecipeFulfillment}    — checks the inventory against a recipe
 *       and atomically deducts ingredients on a successful craft.</li>
 * </ul></p>
 *
 * <p><b>Threading:</b> must be called on the Bukkit main thread. Inventory
 * mutation and recipe lookup are not thread-safe. If your input source is
 * async (e.g. {@code AsyncChatEvent}), schedule the call back to the main
 * thread first:</p>
 *
 * <pre>{@code
 * Bukkit.getScheduler().runTask(plugin,
 *     () -> craftingService.processCraftingAttempt(player, message));
 * }</pre>
 */
public final class SmartCraftingService {

    private final JavaPlugin           plugin;
    private final MaterialFuzzyMatcher fuzzyMatcher;
    private final RecipeFulfillment    recipeFulfillment;

    public SmartCraftingService(JavaPlugin plugin) {
        this.plugin            = plugin;
        this.fuzzyMatcher      = new MaterialFuzzyMatcher();
        this.recipeFulfillment = new RecipeFulfillment();
    }

    /** Constructor used by tests / advanced callers wanting custom collaborators. */
    public SmartCraftingService(JavaPlugin plugin,
                                MaterialFuzzyMatcher fuzzyMatcher,
                                RecipeFulfillment recipeFulfillment) {
        this.plugin            = plugin;
        this.fuzzyMatcher      = fuzzyMatcher;
        this.recipeFulfillment = recipeFulfillment;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Public entry point
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Drive the full Parse → Spell-check → Resource-check → Craft pipeline.
     *
     * <p>All player-visible feedback is sent through {@link Player#sendMessage}.
     * All educator-visible feedback is sent through {@link JavaPlugin#getLogger}
     * — typos are logged at {@code WARNING} so they're easy to filter from
     * the server log.</p>
     *
     * @param player      the crafter
     * @param inputString the raw text the player typed (any case, may contain
     *                    spaces/hyphens, may be misspelled)
     */
    public void processCraftingAttempt(Player player, String inputString) {
        // ── Phase 1: parse + spell-check ────────────────────────────────────
        Optional<MaterialFuzzyMatcher.MatchResult> match = fuzzyMatcher.findBestMatch(inputString);

        if (match.isEmpty()) {
            // Either blank or too far from any real Material — refuse politely.
            player.sendMessage(ChatColor.RED + "I don't recognise '"
                    + inputString + "' as a Minecraft item. Check your spelling and try again.");
            return;
        }

        Material target = match.get().material();
        boolean  wasCorrected = match.get().wasCorrected();

        if (wasCorrected) {
            // Educational nudge — show the player the canonical name.
            player.sendMessage(ChatColor.YELLOW + "Did you mean '"
                    + prettyName(target) + "'? I fixed that for you!");

            // Educator log — single line, easily grep-able.
            plugin.getLogger().warning(String.format(Locale.ROOT,
                    "[lingocraft] LANGUAGE_MISTAKE player=%s input=\"%s\" corrected=\"%s\" distance=%d",
                    player.getName(),
                    inputString,
                    target.name(),
                    match.get().editDistance()));
        }

        // ── Phase 2: resource check ─────────────────────────────────────────
        List<Recipe> recipes = Bukkit.getServer().getRecipesFor(new ItemStack(target));

        if (recipes.isEmpty()) {
            // Material exists but isn't craftable (e.g. raw ores, mob drops, bedrock).
            player.sendMessage(ChatColor.RED + "Sorry, '"
                    + prettyName(target) + "' isn't something you can craft.");
            return;
        }

        // Find the FIRST recipe whose ingredients we already have. We deliberately
        // scan all recipes (Minecraft has multiple recipes for many items, e.g.
        // sticks from any planks, ladders from any wood) so the player can use
        // whatever resources they happen to be carrying.
        Optional<Recipe> craftable = recipes.stream()
                .filter(recipe -> recipeFulfillment.canFulfill(player.getInventory(), recipe))
                .findFirst();

        if (craftable.isEmpty()) {
            // None of the recipes match what's in the inventory.
            // Use the first recipe's missing-list as a representative example.
            String missing = recipeFulfillment.describeMissing(
                    player.getInventory(), recipes.get(0));
            player.sendMessage(ChatColor.RED + "You're missing items to craft '"
                    + prettyName(target) + "'. You still need: " + missing);
            return;
        }

        // ── Phase 3: execution ──────────────────────────────────────────────
        Recipe recipe = craftable.get();
        recipeFulfillment.deductIngredients(player.getInventory(), recipe);

        ItemStack result = recipe.getResult().clone();

        // Try to give the result; drop on the ground if the inventory is full
        // so the player never silently loses their craft.
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(result);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        player.sendMessage(ChatColor.GREEN + "Successfully crafted "
                + result.getAmount() + "x " + prettyName(target) + "!");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** {@code DIAMOND_SWORD → "Diamond Sword"} for chat output. */
    private static String prettyName(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder(material.name().length());
        for (int i = 0; i < words.length; i++) {
            if (i > 0) out.append(' ');
            String w = words[i];
            if (w.isEmpty()) continue;
            out.append(Character.toUpperCase(w.charAt(0))).append(w, 1, w.length());
        }
        return out.toString();
    }
}
