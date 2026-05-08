package org.softgauge_crafting;

import org.bukkit.Material;

import java.util.Locale;
import java.util.Optional;

/**
 * Resolves a free-form player string to a Bukkit {@link Material}, tolerating
 * common misspellings via Levenshtein edit distance.
 *
 * <p>Pure-Java implementation — depends only on {@link Material} from the
 * Spigot/Paper API and the JDK. No external libraries required.</p>
 *
 * <p>Distance threshold: {@link #MAX_ACCEPTABLE_DISTANCE} ({@value}).
 * Inputs further than this from every Material are rejected (treated as
 * gibberish) so we don't auto-correct nonsense like "asdf" to a random item.</p>
 *
 * <p>Stateless and immutable — a single instance can be safely shared by
 * any number of services / threads.</p>
 */
public final class MaterialFuzzyMatcher {

    /** Maximum acceptable edit distance between input and a Material name. */
    public static final int MAX_ACCEPTABLE_DISTANCE = 3;

    /**
     * Result of a match attempt.
     *
     * @param material      the resolved Material
     * @param wasCorrected  {@code true} if the input was fuzzy-matched (typo),
     *                      {@code false} if it was an exact match
     * @param editDistance  Levenshtein distance between the (normalised) input
     *                      and the resolved Material name (0 for exact matches)
     */
    public record MatchResult(Material material, boolean wasCorrected, int editDistance) {}

    /**
     * Resolve an input string to a Material.
     *
     * @param input the raw string the player typed
     * @return  - the {@link MatchResult} on success (exact OR fuzzy)
     *          - {@link Optional#empty()} if the input is blank or every Material
     *            is more than {@link #MAX_ACCEPTABLE_DISTANCE} edits away
     */
    public Optional<MatchResult> findBestMatch(String input) {
        if (input == null || input.isBlank()) return Optional.empty();

        final String normalised = normalise(input);

        // ── Phase 1: exact match ─────────────────────────────────────────────
        // Walk the enum once; if we hit an exact name, return immediately with
        // wasCorrected = false — no need to run the expensive distance metric.
        for (Material material : Material.values()) {
            if (!isCandidate(material)) continue;
            if (material.name().toLowerCase(Locale.ROOT).equals(normalised)) {
                return Optional.of(new MatchResult(material, false, 0));
            }
        }

        // ── Phase 2: fuzzy match ─────────────────────────────────────────────
        // Track the closest candidate. We early-exit at distance 1 (one typo)
        // because that's effectively as close as you can be without a match.
        Material bestMaterial = null;
        int      bestDistance = Integer.MAX_VALUE;

        for (Material material : Material.values()) {
            if (!isCandidate(material)) continue;

            int distance = levenshteinDistance(
                    normalised,
                    material.name().toLowerCase(Locale.ROOT));

            if (distance < bestDistance) {
                bestDistance = distance;
                bestMaterial = material;
                if (distance == 1) break; // can't get any better without exact match
            }
        }

        if (bestMaterial == null || bestDistance > MAX_ACCEPTABLE_DISTANCE) {
            return Optional.empty();
        }
        return Optional.of(new MatchResult(bestMaterial, true, bestDistance));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Levenshtein distance
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Compute the Levenshtein edit distance between two strings — i.e. the
     * minimum number of single-character insertions, deletions, or substitutions
     * required to turn {@code a} into {@code b}.
     *
     * <p>Iterative two-row dynamic programming.
     * Time: O(n·m). Memory: O(min(n, m)).</p>
     *
     * <p>Made {@code public static} so tests / other plugin classes can reuse it.</p>
     */
    public static int levenshteinDistance(String a, String b) {
        // ── Trivial fast paths ──────────────────────────────────────────────
        if (a.equals(b))   return 0;
        if (a.isEmpty())   return b.length();
        if (b.isEmpty())   return a.length();

        // Ensure 'a' is the shorter — minimises memory for the working rows.
        if (a.length() > b.length()) {
            String tmp = a; a = b; b = tmp;
        }

        final int n = a.length();
        final int m = b.length();
        int[] previousRow = new int[n + 1];
        int[] currentRow  = new int[n + 1];

        // Distance from "" to a[0..i] is i (i deletions)
        for (int i = 0; i <= n; i++) previousRow[i] = i;

        for (int j = 1; j <= m; j++) {
            currentRow[0] = j; // distance from b[0..j] to "" is j (j insertions)

            for (int i = 1; i <= n; i++) {
                int substitutionCost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;

                currentRow[i] = Math.min(
                        Math.min(
                                currentRow[i - 1] + 1,                  // insertion
                                previousRow[i]    + 1),                  // deletion
                        previousRow[i - 1] + substitutionCost            // substitution
                );
            }

            // Swap rows for the next iteration (avoids re-allocation)
            int[] swap = previousRow;
            previousRow = currentRow;
            currentRow  = swap;
        }
        return previousRow[n];
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Normalise a player-supplied string for comparison against
     * {@link Material#name()}, which is always {@code UPPER_SNAKE_CASE}.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "Diamond Sword"}  → {@code "diamond_sword"}</li>
     *   <li>{@code "  red-stone  "}  → {@code "redstone"}  (after collapse)</li>
     *   <li>{@code "Dimond swrd"}    → {@code "dimond_swrd"}</li>
     * </ul>
     */
    private static String normalise(String input) {
        return input.trim()
                .toLowerCase(Locale.ROOT)
                .replace(' ',  '_')
                .replace('-',  '_');
    }

    /**
     * Should this Material be considered as a possible target?
     * Excludes legacy (pre-1.13) numeric-ID aliases and non-item materials
     * (like AIR, FIRE, MOVING_PISTON) which a player can't reasonably craft.
     */
    private static boolean isCandidate(Material material) {
        return !material.isLegacy() && material.isItem();
    }
}
