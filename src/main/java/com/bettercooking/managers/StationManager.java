package com.bettercooking.managers;

import com.bettercooking.BetterCooking;
import com.bettercooking.cooking.CookingMinigame;
import com.bettercooking.ui.CookingUI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class StationManager {

    /** Per-station state: ingredient slots, fuel slot, output slot, and stored burns. */
    public static class StationData {
        public ItemStack[] ingredients = new ItemStack[CookingUI.INGREDIENT_SLOTS.length];
        public ItemStack fuel;
        public ItemStack[] output = new ItemStack[CookingUI.OUTPUT_SLOTS.length];
        public int burns;
    }

    private final BetterCooking plugin;
    /** Serialized location key → station state */
    private final Map<String, StationData> stations = new HashMap<>();
    /** Player UUID → running minigame */
    private final Map<UUID, CookingMinigame> activeMinigames = new HashMap<>();
    /** Location keys of stations currently open in someone's GUI */
    private final Set<String> openStations = new HashSet<>();
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
            StationData data = new StationData();
            for (int i = 0; i < data.ingredients.length; i++) {
                data.ingredients[i] = config.getItemStack(key + ".slot_" + i);
            }
            data.fuel = config.getItemStack(key + ".fuel");
            for (int i = 0; i < data.output.length; i++) {
                data.output[i] = config.getItemStack(key + ".output_" + i);
            }
            data.burns = config.getInt(key + ".burns", 0);
            stations.put(key, data);
        }
    }

    public void save() {
        if (dataFile == null) return;
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, StationData> entry : stations.entrySet()) {
            StationData data = entry.getValue();
            for (int i = 0; i < data.ingredients.length; i++) {
                if (data.ingredients[i] != null) {
                    config.set(entry.getKey() + ".slot_" + i, data.ingredients[i]);
                }
            }
            if (data.fuel != null) config.set(entry.getKey() + ".fuel", data.fuel);
            for (int i = 0; i < data.output.length; i++) {
                if (data.output[i] != null) config.set(entry.getKey() + ".output_" + i, data.output[i]);
            }
            if (data.burns > 0) config.set(entry.getKey() + ".burns", data.burns);
        }
        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save station data: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Station inventory

    /**
     * Opens the station GUI unless another player already has it open (each viewer
     * would otherwise get an independent copy of the contents — a dupe).
     *
     * @return false if the station is in use by someone else
     */
    public boolean openStation(Player player, Location location) {
        if (!openStations.add(locationKey(location))) return false;
        CookingUI.open(player, location, getStation(location), plugin);
        return true;
    }

    public void markClosed(Location location) {
        openStations.remove(locationKey(location));
    }

    public boolean isStationOpen(Location location) {
        return openStations.contains(locationKey(location));
    }

    /** Drops all stored contents at the station's location and forgets it (block broken). */
    public void dropStationContents(Location location) {
        StationData data = stations.remove(locationKey(location));
        if (data == null || location.getWorld() == null) return;
        Location dropLoc = location.clone().add(0.5, 0.5, 0.5);
        for (ItemStack item : data.ingredients) {
            if (item != null) location.getWorld().dropItemNaturally(dropLoc, item);
        }
        if (data.fuel != null) location.getWorld().dropItemNaturally(dropLoc, data.fuel);
        for (ItemStack item : data.output) {
            if (item != null) location.getWorld().dropItemNaturally(dropLoc, item);
        }
    }

    public StationData getStation(Location location) {
        return stations.computeIfAbsent(locationKey(location), k -> new StationData());
    }

    // -------------------------------------------------------------------------
    // Fuel

    /** How many items one piece of this fuel can cook (0 = not a fuel). */
    public int burnsPerItem(Material material) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("fuel.items");
        if (section != null && !section.getValues(false).isEmpty()) {
            return section.getInt(material.name(), 0);
        }
        // Config missing or empty fuel list — fall back to furnace-parity defaults
        return switch (material) {
            case COAL, CHARCOAL -> 8;
            case COAL_BLOCK -> 80;
            case BLAZE_ROD -> 12;
            default -> 0;
        };
    }

    public boolean isFuel(ItemStack item) {
        return item != null && item.getType() != Material.AIR && burnsPerItem(item.getType()) > 0;
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
