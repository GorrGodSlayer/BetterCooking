package com.bettercooking.ui;

import com.bettercooking.BetterCooking;
import com.bettercooking.cooking.CookingRecipe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CookingUI {

    // Slot layout (3-row chest, 27 slots):
    //  [0][1][2][3][4][5][6][7][8]  <- timing bar
    //  [9][10][11][12][F][15][F][F][F] <- ingredients + recipe display
    //  [F][F][F][F][22][F][24][F][F] <- cook button + output

    public static final int[] INGREDIENT_SLOTS = {9, 10, 11, 12};
    public static final int RECIPE_DISPLAY_SLOT = 15;
    public static final int COOK_BUTTON_SLOT = 22;
    public static final int OUTPUT_SLOT = 24;
    public static final int BAR_SIZE = 9;

    public static void open(Player player, Location location, ItemStack[] storedItems, BetterCooking plugin) {
        StationHolder holder = new StationHolder(location);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Cooking Station", NamedTextColor.DARK_GRAY));
        holder.setInventory(inv);

        // Filler for all slots
        ItemStack filler = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        setIdleBar(inv);

        // Restore stored ingredients
        for (int i = 0; i < INGREDIENT_SLOTS.length; i++) {
            ItemStack item = (storedItems != null && i < storedItems.length) ? storedItems[i] : null;
            inv.setItem(INGREDIENT_SLOTS[i], item);
        }

        inv.setItem(OUTPUT_SLOT, null); // clear output slot filler so items can be placed there
        inv.setItem(COOK_BUTTON_SLOT, buildCookButton(false));
        inv.setItem(RECIPE_DISPLAY_SLOT, buildNoRecipeItem());

        // Auto-detect recipe if ingredients were restored
        CookingRecipe matched = plugin.getRecipeManager().matchRecipe(getIngredients(inv));
        if (matched != null) {
            updateRecipeDisplay(inv, matched);
            inv.setItem(COOK_BUTTON_SLOT, buildCookButton(true));
        }

        player.openInventory(inv);
    }

    /** Sets the timing bar to the idle (gray) state. */
    public static void setIdleBar(Inventory inv) {
        for (int i = 0; i < BAR_SIZE; i++) {
            inv.setItem(i, createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
    }

    /**
     * Redraws the timing bar with zone colours and the needle at needlePos.
     * Zone layout (center = slot 4):
     *   |slot - center| <= perfectZone        → LIME  (perfect)
     *   |slot - center| <= perfectZone + goodZone → YELLOW (good)
     *   otherwise                              → RED   (miss)
     */
    public static void updateBar(Inventory inv, int needlePos, CookingRecipe recipe) {
        int center = BAR_SIZE / 2;
        for (int i = 0; i < BAR_SIZE; i++) {
            int distance = Math.abs(i - center);
            ItemStack pane;
            if (i == needlePos) {
                pane = createGuiItem(Material.WHITE_STAINED_GLASS_PANE, "▼ Click Cook!");
            } else if (distance <= recipe.getPerfectZone()) {
                pane = createGuiItem(Material.LIME_STAINED_GLASS_PANE, "Perfect!");
            } else if (distance <= recipe.getPerfectZone() + recipe.getGoodZone()) {
                pane = createGuiItem(Material.YELLOW_STAINED_GLASS_PANE, "Good");
            } else {
                pane = createGuiItem(Material.RED_STAINED_GLASS_PANE, "Miss");
            }
            inv.setItem(i, pane);
        }
    }

    /** Updates the recipe display slot with info about the matched recipe. */
    public static void updateRecipeDisplay(Inventory inv, CookingRecipe recipe) {
        ItemStack display = new ItemStack(Material.PAPER);
        ItemMeta meta = display.getItemMeta();
        meta.displayName(Component.text(recipe.getDisplayName(), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Difficulty: ", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(difficultyComponent(recipe.getNeedleSpeed()))
        ));
        display.setItemMeta(meta);
        inv.setItem(RECIPE_DISPLAY_SLOT, display);
    }

    /** Extracts current items from the ingredient slots. */
    public static ItemStack[] getIngredients(Inventory inv) {
        ItemStack[] items = new ItemStack[INGREDIENT_SLOTS.length];
        for (int i = 0; i < INGREDIENT_SLOTS.length; i++) {
            ItemStack item = inv.getItem(INGREDIENT_SLOTS[i]);
            items[i] = (item != null && item.getType() != Material.AIR) ? item : null;
        }
        return items;
    }

    public static boolean isIngredientSlot(int slot) {
        for (int s : INGREDIENT_SLOTS) if (s == slot) return true;
        return false;
    }

    public static ItemStack buildCookButton(boolean active) {
        Material mat = active ? Material.LIME_DYE : Material.GRAY_DYE;
        String label = active ? "Cook!" : "Place ingredients first";
        NamedTextColor color = active ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY;
        return createGuiItem(mat, label, color);
    }

    public static ItemStack buildNoRecipeItem() {
        return createGuiItem(Material.BARRIER, "No matching recipe", NamedTextColor.RED);
    }

    public static ItemStack createGuiItem(Material material, String name) {
        return createGuiItem(material, name, NamedTextColor.WHITE);
    }

    public static ItemStack createGuiItem(Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static Component difficultyComponent(long speed) {
        if (speed <= 2) return Component.text("Hard", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false);
        if (speed <= 5) return Component.text("Medium", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false);
        return Component.text("Easy", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false);
    }

    // -------------------------------------------------------------------------

    public static class StationHolder implements InventoryHolder {
        private final Location location;
        private Inventory inventory;

        public StationHolder(Location location) {
            this.location = location;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public Location getLocation() {
            return location;
        }
    }
}
