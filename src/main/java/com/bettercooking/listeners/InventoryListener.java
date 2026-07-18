package com.bettercooking.listeners;

import com.bettercooking.BetterCooking;
import com.bettercooking.cooking.CookingMinigame;
import com.bettercooking.cooking.CookingRecipe;
import com.bettercooking.ui.CookingUI;
import com.bettercooking.ui.CookingUI.StationHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class InventoryListener implements Listener {

    private final BetterCooking plugin;

    public InventoryListener(BetterCooking plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Click handling

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof StationHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inv = event.getInventory();
        int rawSlot = event.getRawSlot();
        boolean isTopInventory = rawSlot >= 0 && rawSlot < inv.getSize();

        // --- Shift-click from player inventory → route to ingredient slot ---
        if (!isTopInventory && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true);
            if (!plugin.getStationManager().hasActiveMinigame(player.getUniqueId())) {
                routeToIngredientSlot(event.getCurrentItem(), inv, player);
            }
            return;
        }

        if (!isTopInventory) return; // allow normal player-inventory interactions

        // --- Cook button ---
        if (rawSlot == CookingUI.COOK_BUTTON_SLOT) {
            event.setCancelled(true);
            handleCookButton(player, inv, holder);
            return;
        }

        // --- Output slot: allow taking, block placing ---
        if (rawSlot == CookingUI.OUTPUT_SLOT) {
            InventoryAction action = event.getAction();
            if (action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_ONE
                    || action == InventoryAction.PLACE_SOME || action == InventoryAction.SWAP_WITH_CURSOR) {
                event.setCancelled(true);
            }
            return;
        }

        // --- Ingredient slots ---
        if (CookingUI.isIngredientSlot(rawSlot)) {
            // Lock ingredient changes while minigame is running
            if (plugin.getStationManager().hasActiveMinigame(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            // Refresh recipe display after the click resolves
            scheduleRecipeRefresh(player, inv);
            return;
        }

        // --- All other top-inventory slots (bar, filler) → block ---
        event.setCancelled(true);
    }

    // -------------------------------------------------------------------------
    // Drag handling

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof StationHolder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inv = event.getInventory();

        for (int slot : event.getRawSlots()) {
            if (slot >= inv.getSize()) continue; // player inventory — fine
            if (!CookingUI.isIngredientSlot(slot)) {
                event.setCancelled(true);
                return;
            }
        }

        // If drag touched ingredient slots and no minigame is active, refresh recipe
        if (!plugin.getStationManager().hasActiveMinigame(player.getUniqueId())) {
            scheduleRecipeRefresh(player, inv);
        }
    }

    // -------------------------------------------------------------------------
    // Close handling

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof StationHolder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        // Cancel any running minigame
        CookingMinigame minigame = plugin.getStationManager().getActiveMinigame(player.getUniqueId());
        if (minigame != null) {
            minigame.cancel();
            plugin.getStationManager().removeMinigame(player.getUniqueId());
        }

        // Persist ingredient state
        ItemStack[] ingredients = CookingUI.getIngredients(event.getInventory());
        plugin.getStationManager().saveIngredients(holder.getLocation(), ingredients);
    }

    // -------------------------------------------------------------------------
    // Helpers

    private void handleCookButton(Player player, Inventory inv, StationHolder holder) {
        // If minigame is running → register the timing click
        if (plugin.getStationManager().hasActiveMinigame(player.getUniqueId())) {
            plugin.getStationManager().getActiveMinigame(player.getUniqueId()).registerClick();
            return;
        }

        // Otherwise start the minigame if a recipe is matched
        CookingRecipe recipe = plugin.getRecipeManager().matchRecipe(CookingUI.getIngredients(inv));
        if (recipe == null) {
            player.sendMessage(Component.text(
                    colorize(plugin.getConfig().getString("messages.no-recipe", "&cNo matching recipe.")),
                    NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text(
                colorize(plugin.getConfig().getString("messages.minigame-start",
                        "&7Click Cook at the right moment!")),
                NamedTextColor.GRAY));

        CookingMinigame minigame = new CookingMinigame(plugin, player, inv, recipe, holder.getLocation());
        plugin.getStationManager().setActiveMinigame(player.getUniqueId(), minigame);
        minigame.runTaskTimer(plugin, 0L, recipe.getNeedleSpeed());
    }

    private void routeToIngredientSlot(ItemStack item, Inventory inv, Player player) {
        if (item == null || item.getType() == Material.AIR) return;

        for (int slot : CookingUI.INGREDIENT_SLOTS) {
            ItemStack existing = inv.getItem(slot);
            if (existing == null || existing.getType() == Material.AIR) {
                inv.setItem(slot, item.clone());
                player.getInventory().removeItem(item);
                scheduleRecipeRefresh(player, inv);
                return;
            }
        }
        player.sendMessage(Component.text("No empty ingredient slots!", NamedTextColor.RED));
    }

    private void scheduleRecipeRefresh(Player player, Inventory inv) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof StationHolder)) return;
            if (plugin.getStationManager().hasActiveMinigame(player.getUniqueId())) return;

            CookingRecipe matched = plugin.getRecipeManager().matchRecipe(CookingUI.getIngredients(inv));
            if (matched != null) {
                CookingUI.updateRecipeDisplay(inv, matched);
                inv.setItem(CookingUI.COOK_BUTTON_SLOT, CookingUI.buildCookButton(true));
            } else {
                inv.setItem(CookingUI.RECIPE_DISPLAY_SLOT, CookingUI.buildNoRecipeItem());
                inv.setItem(CookingUI.COOK_BUTTON_SLOT, CookingUI.buildCookButton(false));
            }
        }, 1L);
    }

    private String colorize(String s) {
        return s.replace("&", "§");
    }
}
