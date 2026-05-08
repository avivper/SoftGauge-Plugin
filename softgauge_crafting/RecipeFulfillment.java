package org.softgauge_crafting;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Encapsulates the "do they have the materials?" half of smart crafting.
 *
 * <p>Knows how to:
 * <ul>
 *   <li>Aggregate a Bukkit {@link Recipe}'s required ingredients as a
 *       {@code Material → quantity} map.</li>
 *   <li>Verify that a player's {@link Inventory} contains all required
 *       ingredients ({@link #canFulfill}).</li>
 *   <li>Produce a human-readable description of what's missing
 *       ({@link #describeMissing}) — used to give the player precise feedback.</li>
 *   <li>Atomically deduct the required ingredients from an inventory
 *       ({@link #deductIngredients}).</li>
 * </ul></p>
 *
 * <p>Currently supports {@link ShapedRecipe} and {@link ShapelessRecipe}
 * (the two crafting-table variants). Furnace, smithing, and merchant recipes
 * are intentionally out of scope — they are surfaced via different mechanics
 * in vanilla Minecraft and don't fit a "type the item name" UX.</p>
 */
public final class RecipeFulfillment {

    /**
     * Build a flat {@code Material → quantity} map of every ingredient a
     * recipe consumes. For shaped recipes this collapses the 2-D grid into
     * the underlying counts; for shapeless recipes it's a direct sum.
     *
     * <p>Returns an <strong>empty</strong> map for unsupported recipe types
     * (e.g. {@code FurnaceRecipe}). Callers should treat empty as "cannot
     * auto-craft this" rather than "no ingredients required".</p>
     */
    public Map<Material, Integer> requiredIngredients(Recipe recipe) {
        Map<Material, Integer> required = new HashMap<>();

        if (recipe instanceof ShapedRecipe shaped) {
            // ingredientMap: Character → ItemStack (one entry per grid letter)
            for (ItemStack ingredient : shaped.getIngredientMap().values()) {
                if (ingredient == null) continue;
                if (ingredient.getType().isAir()) continue;
                required.merge(ingredient.getType(), ingredient.getAmount(), Integer::sum);
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            for (ItemStack ingredient : shapeless.getIngredientList()) {
                if (ingredient == null) continue;
                if (ingredient.getType().isAir()) continue;
                required.merge(ingredient.getType(), ingredient.getAmount(), Integer::sum);
            }
        }
        // Any other Recipe subtype → empty map → caller treats as "unsupported".

        return Collections.unmodifiableMap(required);
    }

    /**
     * Does the inventory contain everything this recipe needs?
     *
     * @return {@code true} only if every required Material is present in
     *         sufficient quantity. Returns {@code false} (not throws) for
     *         unsupported recipe types so the caller can simply skip them.
     */
    public boolean canFulfill(Inventory inventory, Recipe recipe) {
        Map<Material, Integer> required = requiredIngredients(recipe);
        if (required.isEmpty()) return false; // unsupported recipe type

        for (Map.Entry<Material, Integer> entry : required.entrySet()) {
            if (countMaterial(inventory, entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Describe (for player feedback) which items the inventory is short of.
     * Returns a comma-separated list like {@code "2x stick, 1x diamond"}.
     * Returns {@code "(unknown)"} for unsupported recipe types.
     */
    public String describeMissing(Inventory inventory, Recipe recipe) {
        Map<Material, Integer> required = requiredIngredients(recipe);
        if (required.isEmpty()) return "(unknown)";

        List<String> missing = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : required.entrySet()) {
            int have = countMaterial(inventory, entry.getKey());
            int need = entry.getValue();
            if (have < need) {
                int short_by = need - have;
                missing.add(short_by + "x " + prettyName(entry.getKey()));
            }
        }
        return missing.isEmpty() ? "(none — recipe is fulfillable)" : String.join(", ", missing);
    }

    /**
     * Remove every required ingredient from the inventory.
     *
     * <p><b>Pre-condition:</b> {@link #canFulfill} returned {@code true} for the
     * same {@code (inventory, recipe)} pair. Calling this without that check
     * will partially mutate the inventory if quantities are insufficient.</p>
     *
     * <p>Must be called on the Bukkit main thread — inventory mutations are
     * not thread-safe.</p>
     */
    public void deductIngredients(Inventory inventory, Recipe recipe) {
        Map<Material, Integer> required = requiredIngredients(recipe);
        for (Map.Entry<Material, Integer> entry : required.entrySet()) {
            removeMaterial(inventory, entry.getKey(), entry.getValue());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Inventory primitives
    // ─────────────────────────────────────────────────────────────────────────

    /** Total number of {@code material} units across the inventory's storage slots. */
    private int countMaterial(Inventory inventory, Material material) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /**
     * Remove up to {@code amount} of {@code material} from the inventory.
     * Walks slots in order, peeling the requested quantity off each stack
     * until either the count is satisfied or the inventory runs out.
     *
     * <p>If the inventory has fewer than {@code amount} items the difference
     * is silently ignored — callers must validate with {@link #canFulfill}
     * first.</p>
     */
    private void removeMaterial(Inventory inventory, Material material, int amount) {
        ItemStack[] contents = inventory.getStorageContents();
        int remaining = amount;

        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType() != material) continue;

            int take = Math.min(stack.getAmount(), remaining);
            stack.setAmount(stack.getAmount() - take);
            remaining -= take;

            if (stack.getAmount() <= 0) {
                contents[slot] = null;
            }
        }
        inventory.setStorageContents(contents);
    }

    /** Convert {@code DIAMOND_SWORD} → {@code "diamond sword"} for chat output. */
    private static String prettyName(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
