package com.bettercooking;

import com.bettercooking.listeners.BlockListener;
import com.bettercooking.listeners.InventoryListener;
import com.bettercooking.managers.RecipeManager;
import com.bettercooking.managers.StationManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class BetterCooking extends JavaPlugin {

    private RecipeManager recipeManager;
    private StationManager stationManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        recipeManager = new RecipeManager(this);
        recipeManager.loadRecipes();

        stationManager = new StationManager(this);
        stationManager.load();

        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);

        getLogger().info("BetterCooking enabled.");
    }

    @Override
    public void onDisable() {
        if (stationManager != null) stationManager.save();
        getLogger().info("BetterCooking disabled.");
    }

    public RecipeManager getRecipeManager() { return recipeManager; }
    public StationManager getStationManager() { return stationManager; }
}
