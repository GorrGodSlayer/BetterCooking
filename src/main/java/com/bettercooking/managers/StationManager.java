package com.bettercooking.managers;

import com.bettercooking.BetterCooking;
import com.bettercooking.cooking.CookingMinigame;
import com.bettercooking.ui.CookingUI;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StationManager {

    private final BetterCooking plugin;
    /** Serialized location key → stored ingredient stacks */
    private final Map<String, ItemStack[]> stationItems = new HashMap<>();
    /** Player UUID → running minigame */
    private final Map<UUID, CookingMinigame> activeMinigames = new HashMap<>();
    private File dataFile;

    public StationManager(BetterCooking plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Persistence

    public void load() {
        dataFile = new File(plugin.getDataFolder(), "stations.yml");
        if (!dataFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : config.getKeys(false)) {
            ItemStack[] items = new ItemStack[CookingUI.INGREDIENT_SLOTS.length];
            for (int i = 0; i < items.length; i++) {
                items[i] = config.getItemStack(key + ".slot_" + i);
            }
            stationItems.put(key, items);
        }
    }

    public void save() {
        if (dataFile == null) return;
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, ItemStack[]> entry : stationItems.entrySet()) {
            ItemStack[] items = entry.getValue();
            for (int i = 0; i < items.length; i++) {
                if (items[i] != null) {
                    config.set(entry.getKey() + ".slot_" + i, items[i]);
                }
            }
        }
        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save station data: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Station inventory

    public void openStation(Player player, Location location) {
        String key = locationKey(location);
        ItemStack[] stored = stationItems.getOrDefault(key, new ItemStack[CookingUI.INGREDIENT_SLOTS.length]);
        CookingUI.open(player, location, stored, plugin);
    }

    public void saveIngredients(Location location, ItemStack[] items) {
        stationItems.put(locationKey(location), items.clone());
    }

    // -------------------------------------------------------------------------
    // Minigame tracking

    public void setActiveMinigame(UUID playerId, CookingMinigame minigame) {
        activeMinigames.put(playerId, minigame);
    }

    public CookingMinigame getActiveMinigame(UUID playerId) {
        return activeMinigames.get(playerId);
    }

    public boolean hasActiveMinigame(UUID playerId) {
        return activeMinigames.containsKey(playerId);
    }

    public void removeMinigame(UUID playerId) {
        activeMinigames.remove(playerId);
    }

    // -------------------------------------------------------------------------

    private String locationKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
}
