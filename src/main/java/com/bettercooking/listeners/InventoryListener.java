package com.bettercooking.listeners;

import com.bettercooking.BetterCooking;
import com.bettercooking.cooking.CookingMinigame;
import com.bettercooking.cooking.CookingRecipe;
import com.bettercooking.managers.StationManager;
import com.bettercooking.ui.CookingUI;
import com.bettercooking.ui.CookingUI.StationHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[debug] click by " + player.getName()
                    + " rawSlot=" + rawSlot + " action=" + event.getAction()
                    + " click=" + event.getClick()
                    + " cursor=" + event.getCursor() + " slotItem=" + event.getCurrentItem());
        }

        // --- Shift-click from player inventory → route to fuel or ingredient slot ---
        if (!isTopInventory && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true);
            if (!plugin.getStationManager().hasActiveMinigame(player.getUniqueId())) {
                routeItem(event.getCurrentItem(), inv, player);
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

        // --- Output slots: allow taking, block placing ---
        if (CookingUI.isOutputSlot(rawSlot)) {
            InventoryAction action = event.getAction();
            if (action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_ONE
                    || action == InventoryAction.PLACE_SOME || action == InventoryAction.SWAP_WITH_CURSOR) {
                event.setCancelled(true);
                return;
            }
            if (action == InventoryAction.HOTBAR_SWAP && event.getHotbarButton() >= 0) {
                ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
                if (hotbarItem != null && hotbarItem.getType() != Material.AIR) {
                    event.setCancelled(true);
                }
            }
            return;
        }

        // --- Fuel slot: only fuel items may be placed ---
        if (rawSlot == CookingUI.FUEL_SLOT) {
            if (plugin.getStationManager().hasActiveMinigame(player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(Component.text("Can't change fuel while cooking!", NamedTextColor.RED));
                return;
            }
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR
                    && !plugin.getStationManager().isFuel(cursor)) {
                event.setCancelled(true);
                sendNotFuel(player, cursor);
                return;
            }
            if (event.getAction() == InventoryAction.HOTBAR_SWAP && event.getHotbarButton() >= 0) {
                ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
                if (hotbarItem != null && hotbarItem.getType() != Material.AIR
                        && !plugin.getStationManager().isFuel(hotbarItem)) {
                    event.setCancelled(true);
                    sendNotFuel(player, hotbarItem);
                }
            }
            return;
        }

        // --- Fuel gauge: looks like the fuel slot, so clicking it with fuel on the
        // cursor deposits into the real fuel slot next to it ---
        if (rawSlot == CookingUI.FUEL_GAUGE_SLOT) {
            event.setCancelled(true);
            if (plugin.getStationManager().hasActiveMinigame(player.getUniqueId())) return;
            ItemStack cursor = event.getCursor();
            if (cursor == null || cursor.getType() == Material.AIR) return;
            if (!plugin.getStationManager().isFuel(cursor)) {
                sendNotFuel(player, cursor);
                return;
            }

            ItemStack existing = inv.getItem(CookingUI.FUEL_SLOT);
            if (existing == null || existing.getType() == Material.AIR) {
                inv.setItem(CookingUI.FUEL_SLOT, cursor.clone());
                player.setItemOnCursor(null);
            } else if (existing.isSimilar(cursor)) {
                int moved = Math.min(existing.getMaxStackSize() - existing.getAmount(), cursor.getAmount());
                if (moved > 0) {
                    existing.setAmount(existing.getAmount() + moved);
                    inv.setItem(CookingUI.FUEL_SLOT, existing);
                    ItemStack rest = cursor.clone();
                    rest.setAmount(cursor.getAmount() - moved);
                    player.setItemOnCursor(rest.getAmount() > 0 ? rest : null);
                }
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

    /** Runs after every other plugin — reveals externally-cancelled clicks in debug mode. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClickMonitor(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof StationHolder)) return;
        if (!plugin.getConfig().getBoolean("debug", false)) return;
        plugin.getLogger().info("[debug] click result rawSlot=" + event.getRawSlot()
                + " cancelled=" + event.isCancelled()
                + (event.isCancelled() ? " (if BetterCooking didn't cancel it, another plugin did)" : ""));
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
            if (slot == CookingUI.FUEL_SLOT && plugin.getStationManager().isFuel(event.getOldCursor())) continue;
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

        // Abort any running minigame (refunds the fuel it reserved)
        CookingMinigame minigame = plugin.getStationManager().getActiveMinigame(player.getUniqueId());
        if (minigame != null) {
            minigame.abort();
        }

        // Persist ingredient + fuel + output state (stored burns live in StationData already)
        StationManager.StationData data = plugin.getStationManager().getStation(holder.getLocation());
        data.ingredients = CookingUI.getIngredients(event.getInventory());
        ItemStack fuel = event.getInventory().getItem(CookingUI.FUEL_SLOT);
        data.fuel = (fuel != null && fuel.getType() != Material.AIR) ? fuel : null;
        for (int i = 0; i < CookingUI.OUTPUT_SLOTS.length; i++) {
            ItemStack output = event.getInventory().getItem(CookingUI.OUTPUT_SLOTS[i]);
            data.output[i] = (output != null && output.getType() != Material.AIR) ? output : null;
        }

        plugin.getStationManager().markClosed(holder.getLocation());
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
        ItemStack[] ingredients = CookingUI.getIngredients(inv);
        CookingRecipe recipe = plugin.getRecipeManager().matchRecipe(ingredients);
        if (recipe == null) {
            player.sendMessage(Component.text(
                    colorize(plugin.getConfig().getString("messages.no-recipe", "&cNo matching recipe.")),
                    NamedTextColor.RED));
            return;
        }

        // Batch cooking: the whole stack cooks at once. Fuel accounting matches a
        // vanilla furnace — every ingredient item consumed costs 1 unit, and a fuel
        // item is only taken from the slot when the stored units run out.
        int maxBatches = plugin.getRecipeManager().maxBatches(recipe, ingredients);
        int costPerBatch = 0;
        for (CookingRecipe.RecipeIngredient req : recipe.getIngredients()) {
            costPerBatch += req.amount();
        }

        StationManager.StationData data = plugin.getStationManager().getStation(holder.getLocation());
        while (data.burns < maxBatches * costPerBatch) {
            ItemStack fuel = inv.getItem(CookingUI.FUEL_SLOT);
            int perItem = (fuel != null) ? plugin.getStationManager().burnsPerItem(fuel.getType()) : 0;
            if (perItem <= 0) break;
            fuel.setAmount(fuel.getAmount() - 1);
            inv.setItem(CookingUI.FUEL_SLOT, fuel.getAmount() > 0 ? fuel : null);
            data.burns += perItem;
        }

        int batches = Math.min(maxBatches, data.burns / costPerBatch);
        if (batches <= 0) {
            player.sendMessage(Component.text(
                    colorize(plugin.getConfig().getString("messages.no-fuel",
                            "&cOut of fuel! Add coal to the fuel slot.")),
                    NamedTextColor.RED));
            return;
        }
        if (batches < maxBatches) {
            player.sendMessage(Component.text(
                    colorize(plugin.getConfig().getString("messages.low-fuel",
                            "&eLow fuel — only cooking part of the batch."))));
        }

        int burnsCost = batches * costPerBatch;
        data.burns -= burnsCost;
        inv.setItem(CookingUI.FUEL_GAUGE_SLOT, CookingUI.buildFuelGauge(data.burns));

        player.sendMessage(Component.text(
                colorize(plugin.getConfig().getString("messages.minigame-start",
                        "&7Click Cook at the right moment!")),
                NamedTextColor.GRAY));

        CookingMinigame minigame = new CookingMinigame(plugin, player, inv, recipe,
                holder.getLocation(), batches, burnsCost);
        plugin.getStationManager().setActiveMinigame(player.getUniqueId(), minigame);
        minigame.runTaskTimer(plugin, 0L, recipe.getNeedleSpeed());
    }

    private void routeItem(ItemStack item, Inventory inv, Player player) {
        if (item == null || item.getType() == Material.AIR) return;

        if (plugin.getStationManager().isFuel(item)) {
            routeToFuelSlot(item, inv, player);
            return;
        }

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

    private void routeToFuelSlot(ItemStack item, Inventory inv, Player player) {
        ItemStack existing = inv.getItem(CookingUI.FUEL_SLOT);
        if (existing == null || existing.getType() == Material.AIR) {
            inv.setItem(CookingUI.FUEL_SLOT, item.clone());
            player.getInventory().removeItem(item);
            return;
        }
        if (existing.isSimilar(item)) {
            int space = existing.getMaxStackSize() - existing.getAmount();
            if (space > 0) {
                int moved = Math.min(space, item.getAmount());
                existing.setAmount(existing.getAmount() + moved);
                inv.setItem(CookingUI.FUEL_SLOT, existing);
                ItemStack removed = item.clone();
                removed.setAmount(moved);
                player.getInventory().removeItem(removed);
                return;
            }
        }
        player.sendMessage(Component.text("The fuel slot is full!", NamedTextColor.RED));
    }

    private void sendNotFuel(Player player, ItemStack item) {
        StringBuilder accepted = new StringBuilder();
        org.bukkit.configuration.ConfigurationSection section =
                plugin.getConfig().getConfigurationSection("fuel.items");
        if (section != null && !section.getValues(false).isEmpty()) {
            accepted.append(String.join(", ", section.getKeys(false)));
        } else {
            accepted.append("COAL, CHARCOAL, COAL_BLOCK, BLAZE_ROD");
        }
        player.sendMessage(Component.text(
                item.getType().name() + " isn't a fuel here. Accepted: " + accepted,
                NamedTextColor.RED));
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
