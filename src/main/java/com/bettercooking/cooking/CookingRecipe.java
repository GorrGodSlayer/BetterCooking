package com.bettercooking.cooking;

import java.util.List;
import java.util.Map;

public class CookingRecipe {

    private final String id;
    private final String displayName;
    private final List<RecipeIngredient> ingredients;
    private final Map<String, RecipeResult> results;
    private final long needleSpeed;
    private final int perfectZone;
    private final int goodZone;

    public CookingRecipe(String id, String displayName, List<RecipeIngredient> ingredients,
                          Map<String, RecipeResult> results, long needleSpeed,
                          int perfectZone, int goodZone) {
        this.id = id;
        this.displayName = displayName;
        this.ingredients = ingredients;
        this.results = results;
        this.needleSpeed = needleSpeed;
        this.perfectZone = perfectZone;
        this.goodZone = goodZone;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public List<RecipeIngredient> getIngredients() { return ingredients; }
    public Map<String, RecipeResult> getResults() { return results; }
    public long getNeedleSpeed() { return needleSpeed; }
    public int getPerfectZone() { return perfectZone; }
    public int getGoodZone() { return goodZone; }

    /**
     * @param type   "nexo" or "vanilla"
     * @param id     Nexo item ID or vanilla Material name
     * @param amount required stack size
     */
    public record RecipeIngredient(String type, String id, int amount) {}

    /**
     * @param type          "nexo" or "vanilla"
     * @param id            Nexo item ID or vanilla Material name
     * @param amount        output stack size
     * @param displayName   custom display name (colour codes with &), or null to keep the item's own name
     * @param lore          extra lore lines (colour codes with &), or empty for none
     * @param effectType    potion effect type name (e.g. "SPEED") granted on eating, or null for none
     * @param effectAmplifier effect amplifier (0 = level I)
     * @param effectDuration effect duration in ticks
     */
    public record RecipeResult(String type, String id, int amount, String displayName, List<String> lore,
                                String effectType, int effectAmplifier, int effectDuration) {}
}
