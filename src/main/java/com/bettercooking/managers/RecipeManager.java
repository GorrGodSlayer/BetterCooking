package com.bettercooking.managers;

import com.bettercooking.BetterCooking;
import com.bettercooking.cooking.CookingRecipe;
import com.bettercooking.cooking.CookingRecipe.RecipeIngredient;
import com.bettercooking.cooking.CookingRecipe.RecipeResult;
import com.nexomc.nexo.api.NexoItems;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

public class RecipeManager {

    private final BetterCooking plugin;
    private final Map<String, CookingRecipe> recipes = new LinkedHashMap<>();

    public RecipeManager(BetterCooking plugin) {
        this.plugin = plugin;
    }

    public void loadRecipes() {
        recipes.clear();

        File file = new File(plugin.getDataFolder(), "recipes.yml");
        if (!file.exists()) {
            plugin.saveResource("recipes.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        for (String key : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) continue;

            String displayName = section.getString("display-name", key);
            long needleSpeed = section.getLong("needle-speed", 4);
            int perfectZone = section.getInt("perfect-zone", 1);
            int goodZone = section.getInt("good-zone", 2);

            List<RecipeIngredient> ingredients = new ArrayList<>();
            for (String raw : section.getStringList("ingredients")) {
                RecipeIngredient ing = parseIngredient(raw);
                if (ing != null) ingredients.add(ing);
            }

            ConfigurationSection resultsSection = section.getConfigurationSection("results");
            Map<String, RecipeResult> results = new HashMap<>();
            if (resultsSection != null) {
                for (String quality : resultsSection.getKeys(false)) {
                    RecipeResult result = parseResult(resultsSection.getString(quality));
                    if (result != null) results.put(quality, result);
                }
            }

            if (ingredients.isEmpty()) {
                plugin.getLogger().warning("Recipe '" + key + "' has no valid ingredients, skipping.");
                continue;
            }

            recipes.put(key, new CookingRecipe(key, displayName, ingredients, results, needleSpeed, perfectZone, goodZone));
        }

        plugin.getLogger().info("Loaded " + recipes.size() + " cooking recipe(s).");
    }

    /** Returns the first recipe whose ingredients match the provided slots, or null. */
    public CookingRecipe matchRecipe(ItemStack[] ingredientSlots) {
        List<ItemStack> provided = new ArrayList<>();
        for (ItemStack item : ingredientSlots) {
            if (item != null && item.getType() != Material.AIR) provided.add(item);
        }

        for (CookingRecipe recipe : recipes.values()) {
            if (ingredientsMatch(provided, recipe)) return recipe;
        }
        return null;
    }

    // -------------------------------------------------------------------------

    private boolean ingredientsMatch(List<ItemStack> provided, CookingRecipe recipe) {
        List<RecipeIngredient> required = recipe.getIngredients();
        if (provided.size() != required.size()) return false;

        boolean[] used = new boolean[provided.size()];
        for (RecipeIngredient req : required) {
            boolean found = false;
            for (int i = 0; i < provided.size(); i++) {
                if (used[i]) continue;
                if (itemMatchesIngredient(provided.get(i), req)) {
                    used[i] = true;
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private boolean itemMatchesIngredient(ItemStack item, RecipeIngredient req) {
        if (item.getAmount() < req.amount()) return false;
        if (req.type().equals("nexo")) {
            String nexoId = NexoItems.idFromItem(item);
            return req.id().equals(nexoId);
        } else {
            try {
                return item.getType() == Material.valueOf(req.id());
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }

    /**
     * Parses ingredient strings:
     *   "nexo:<id>:<amount>"   → Nexo item
     *   "<MATERIAL>:<amount>"  → Vanilla item
     */
    private RecipeIngredient parseIngredient(String raw) {
        String[] parts = raw.split(":");
        try {
            if (parts[0].equalsIgnoreCase("nexo") && parts.length == 3) {
                return new RecipeIngredient("nexo", parts[1], Integer.parseInt(parts[2]));
            } else if (parts.length == 2) {
                return new RecipeIngredient("vanilla", parts[0].toUpperCase(), Integer.parseInt(parts[1]));
            }
        } catch (NumberFormatException e) {
            // fall through
        }
        plugin.getLogger().warning("Invalid ingredient format (skipping): " + raw);
        return null;
    }

    /**
     * Parses result strings:
     *   "nexo:<id>:<amount>"   → Nexo item
     *   "<MATERIAL>:<amount>"  → Vanilla item
     */
    private RecipeResult parseResult(String raw) {
        if (raw == null) return null;
        String[] parts = raw.split(":");
        try {
            if (parts[0].equalsIgnoreCase("nexo") && parts.length == 3) {
                return new RecipeResult("nexo", parts[1], Integer.parseInt(parts[2]));
            } else if (parts.length == 2) {
                return new RecipeResult("vanilla", parts[0].toUpperCase(), Integer.parseInt(parts[1]));
            }
        } catch (NumberFormatException e) {
            // fall through
        }
        plugin.getLogger().warning("Invalid result format (skipping): " + raw);
        return null;
    }

    public Collection<CookingRecipe> getRecipes() {
        return Collections.unmodifiableCollection(recipes.values());
    }
}
