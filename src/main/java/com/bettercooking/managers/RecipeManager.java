package com.bettercooking.managers;

import com.bettercooking.BetterCooking;
import com.bettercooking.cooking.CookingRecipe;
import com.bettercooking.cooking.CookingRecipe.RecipeIngredient;
import com.bettercooking.cooking.CookingRecipe.RecipeResult;
import com.nexomc.nexo.api.NexoItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
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
                    RecipeResult result = resultsSection.isConfigurationSection(quality)
                            ? parseStructuredResult(resultsSection.getConfigurationSection(quality))
                            : parseLegacyResult(resultsSection.getString(quality));
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

    /** Returns the loaded recipe with this id, or null if none exists. */
    public CookingRecipe getRecipe(String id) {
        return recipes.get(id);
    }

    /** Returns the first recipe whose ingredients match the provided slots, or null. */
    public CookingRecipe matchRecipe(ItemStack[] ingredientSlots) {
        List<ItemStack> provided = collectProvided(ingredientSlots);
        for (CookingRecipe recipe : recipes.values()) {
            if (matchBatches(provided, recipe) > 0) return recipe;
        }
        return null;
    }

    /**
     * How many times the recipe fits in the provided slots (a full stack of beef
     * cooks as one batch of many steaks). 0 = no match.
     */
    public int maxBatches(CookingRecipe recipe, ItemStack[] ingredientSlots) {
        return matchBatches(collectProvided(ingredientSlots), recipe);
    }

    // -------------------------------------------------------------------------

    private List<ItemStack> collectProvided(ItemStack[] ingredientSlots) {
        List<ItemStack> provided = new ArrayList<>();
        for (ItemStack item : ingredientSlots) {
            if (item != null && item.getType() != Material.AIR) provided.add(item);
        }
        return provided;
    }

    private int matchBatches(List<ItemStack> provided, CookingRecipe recipe) {
        List<RecipeIngredient> required = recipe.getIngredients();
        if (provided.size() != required.size()) return 0;

        int batches = Integer.MAX_VALUE;
        boolean[] used = new boolean[provided.size()];
        for (RecipeIngredient req : required) {
            boolean found = false;
            for (int i = 0; i < provided.size(); i++) {
                if (used[i]) continue;
                if (itemMatchesIngredient(provided.get(i), req)) {
                    used[i] = true;
                    found = true;
                    batches = Math.min(batches, provided.get(i).getAmount() / req.amount());
                    break;
                }
            }
            if (!found) return 0;
        }
        return batches == Integer.MAX_VALUE ? 0 : batches;
    }

    public boolean itemMatchesIngredient(ItemStack item, RecipeIngredient req) {
        if (item.getAmount() < req.amount()) return false;
        if (req.type().equals("nexo")) {
            if (!plugin.isNexoEnabled()) return false;
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
                int amount = Integer.parseInt(parts[2]);
                if (amount > 0) return new RecipeIngredient("nexo", parts[1], amount);
            } else if (parts.length == 2) {
                int amount = Integer.parseInt(parts[1]);
                if (amount > 0) return new RecipeIngredient("vanilla", parts[0].toUpperCase(), amount);
            }
        } catch (NumberFormatException e) {
            // fall through
        }
        plugin.getLogger().warning("Invalid ingredient format (skipping): " + raw);
        return null;
    }

    /**
     * Parses result strings:
     *   "nexo:<id>:<amount>"              → Nexo item
     *   "<MATERIAL>:<amount>"             → Vanilla item
     *   "<MATERIAL>:<amount>:<Name>"      → Vanilla item with a custom display name (& colour codes)
     */
    private RecipeResult parseLegacyResult(String raw) {
        if (raw == null) return null;
        String[] parts = raw.split(":", 3);
        try {
            if (parts[0].equalsIgnoreCase("nexo") && parts.length == 3) {
                int amount = Integer.parseInt(parts[2]);
                if (amount > 0) return new RecipeResult("nexo", parts[1], amount, null,
                        Collections.emptyList(), null, 0, 0);
            } else if (parts.length >= 2) {
                int amount = Integer.parseInt(parts[1]);
                String displayName = parts.length == 3 ? parts[2] : null;
                if (amount > 0) return new RecipeResult("vanilla", parts[0].toUpperCase(), amount, displayName,
                        Collections.emptyList(), null, 0, 0);
            }
        } catch (NumberFormatException e) {
            // fall through
        }
        plugin.getLogger().warning("Invalid result format (skipping): " + raw);
        return null;
    }

    /**
     * Parses the structured result format written by the in-game recipe editor (/cooking add):
     *   item: "<MATERIAL>:<amount>" or "nexo:<id>:<amount>"
     *   name: optional custom display name (& colour codes)
     *   lore: optional list of lore lines (& colour codes)
     *   effect: optional { type, amplifier, duration } granted on eating
     */
    private RecipeResult parseStructuredResult(ConfigurationSection sec) {
        if (sec == null) return null;
        String itemStr = sec.getString("item");
        if (itemStr == null) {
            plugin.getLogger().warning("Result at '" + sec.getCurrentPath() + "' has no 'item' field, skipping.");
            return null;
        }

        String[] parts = itemStr.split(":");
        String type;
        String id;
        int amount;
        try {
            if (parts[0].equalsIgnoreCase("nexo") && parts.length == 3) {
                type = "nexo";
                id = parts[1];
                amount = Integer.parseInt(parts[2]);
            } else if (parts.length == 2) {
                type = "vanilla";
                id = parts[0].toUpperCase();
                amount = Integer.parseInt(parts[1]);
            } else {
                plugin.getLogger().warning("Invalid item format in result '" + itemStr + "', skipping.");
                return null;
            }
            if (amount <= 0) return null;
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid item format in result '" + itemStr + "', skipping.");
            return null;
        }

        String displayName = sec.getString("name");
        List<String> lore = sec.getStringList("lore");

        String effectType = null;
        int effectAmplifier = 0;
        int effectDuration = 0;
        ConfigurationSection effectSec = sec.getConfigurationSection("effect");
        if (effectSec != null) {
            effectType = effectSec.getString("type");
            effectAmplifier = effectSec.getInt("amplifier", 0);
            effectDuration = effectSec.getInt("duration", 200);
        }

        return new RecipeResult(type, id, amount, displayName, lore, effectType, effectAmplifier, effectDuration);
    }

    // -------------------------------------------------------------------------
    // In-game recipe editor support (/cooking add)

    /** Builds a RecipeIngredient from an ItemStack placed in the editor's ingredient slots. */
    public RecipeIngredient itemToIngredient(ItemStack item) {
        String nexoId = plugin.isNexoEnabled() ? NexoItems.idFromItem(item) : null;
        if (nexoId != null) return new RecipeIngredient("nexo", nexoId, item.getAmount());
        return new RecipeIngredient("vanilla", item.getType().name(), item.getAmount());
    }

    /** Builds a RecipeResult from an ItemStack placed in the editor's output slot, plus an optional eat effect. */
    public RecipeResult itemToResult(ItemStack item, String effectType, int effectAmplifier, int effectDuration) {
        String nexoId = plugin.isNexoEnabled() ? NexoItems.idFromItem(item) : null;
        String type = nexoId != null ? "nexo" : "vanilla";
        String id = nexoId != null ? nexoId : item.getType().name();

        ItemMeta meta = item.getItemMeta();
        String displayName = (meta != null && meta.hasDisplayName())
                ? LegacyComponentSerializer.legacyAmpersand().serialize(meta.displayName())
                : null;

        List<String> lore = new ArrayList<>();
        if (meta != null && meta.hasLore()) {
            for (Component line : meta.lore()) {
                lore.add(LegacyComponentSerializer.legacyAmpersand().serialize(line));
            }
        }

        return new RecipeResult(type, id, item.getAmount(), displayName, lore, effectType, effectAmplifier, effectDuration);
    }

    /** Builds a preview ItemStack for the editor from a saved ingredient (inverse of itemToIngredient). Null if it can't be built. */
    public ItemStack ingredientToItem(RecipeIngredient ing) {
        if (ing.type().equals("nexo")) {
            if (!plugin.isNexoEnabled()) return null;
            var nexoItem = NexoItems.itemFromId(ing.id());
            if (nexoItem == null) return null;
            ItemStack item = nexoItem.build();
            item.setAmount(ing.amount());
            return item;
        }
        try {
            return new ItemStack(Material.valueOf(ing.id()), ing.amount());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Builds a preview ItemStack for the editor from a saved result (inverse of itemToResult). Null if it can't be built. */
    public ItemStack resultToItem(RecipeResult result) {
        ItemStack item;
        if (result.type().equals("nexo")) {
            if (!plugin.isNexoEnabled()) return null;
            var nexoItem = NexoItems.itemFromId(result.id());
            if (nexoItem == null) return null;
            item = nexoItem.build();
        } else {
            try {
                item = new ItemStack(Material.valueOf(result.id()));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        item.setAmount(result.amount());

        ItemMeta meta = item.getItemMeta();
        boolean changed = false;
        if (result.displayName() != null) {
            meta.displayName(LegacyComponentSerializer.legacySection()
                    .deserialize(result.displayName().replace('&', '§'))
                    .decoration(TextDecoration.ITALIC, false));
            changed = true;
        }
        if (result.lore() != null && !result.lore().isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : result.lore()) {
                lore.add(LegacyComponentSerializer.legacySection()
                        .deserialize(line.replace('&', '§'))
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            changed = true;
        }
        if (changed) item.setItemMeta(meta);
        return item;
    }

    /** Writes (or overwrites) a recipe into recipes.yml and reloads all recipes. */
    public void saveRecipe(String id, String displayName, List<RecipeIngredient> ingredients,
                            Map<String, RecipeResult> results, long needleSpeed, int goodZone) {
        File file = new File(plugin.getDataFolder(), "recipes.yml");
        if (!file.exists()) {
            plugin.saveResource("recipes.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection section = config.createSection(id);
        section.set("display-name", displayName);

        List<String> ingredientStrings = new ArrayList<>();
        for (RecipeIngredient ing : ingredients) {
            ingredientStrings.add(ing.type().equals("nexo")
                    ? "nexo:" + ing.id() + ":" + ing.amount()
                    : ing.id() + ":" + ing.amount());
        }
        section.set("ingredients", ingredientStrings);

        ConfigurationSection resultsSection = section.createSection("results");
        for (Map.Entry<String, RecipeResult> entry : results.entrySet()) {
            RecipeResult r = entry.getValue();
            ConfigurationSection resultSec = resultsSection.createSection(entry.getKey());
            resultSec.set("item", r.type().equals("nexo")
                    ? "nexo:" + r.id() + ":" + r.amount()
                    : r.id() + ":" + r.amount());
            if (r.displayName() != null) resultSec.set("name", r.displayName());
            if (r.lore() != null && !r.lore().isEmpty()) resultSec.set("lore", r.lore());
            if (r.effectType() != null) {
                ConfigurationSection effectSec = resultSec.createSection("effect");
                effectSec.set("type", r.effectType());
                effectSec.set("amplifier", r.effectAmplifier());
                effectSec.set("duration", r.effectDuration());
            }
        }

        section.set("needle-speed", needleSpeed);
        section.set("good-zone", goodZone);

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save recipe '" + id + "': " + e.getMessage());
            return;
        }
        loadRecipes();
    }

}
