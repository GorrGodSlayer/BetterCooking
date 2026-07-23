package com.bettercooking.ui;

import com.bettercooking.BetterCooking;
import com.bettercooking.cooking.CookingRecipe;
import com.bettercooking.managers.StationManager;
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

    // Slot layout (double chest, 54 slots, rows numbered 1-6):
    //  Row 2, middle 5 (slots 11-15): ingredients
    //  Row 3, middle (slot 22): recipe display
    //  Row 4 (slots 27-35): timing bar
    //  Row 6: [F][fuel][gauge][F][cook][out][out][F][F]  (slots 45-53)

    public static final int[] INGREDIENT_SLOTS = {11, 12, 13, 14, 15};
    public static final int RECIPE_DISPLAY_SLOT = 22;
    public static final int BAR_START = 27;
    public static final int BAR_SIZE = 9;
    public static final int FUEL_SLOT = 46;
    public static final int FUEL_GAUGE_SLOT = 47;
    public static final int COOK_BUTTON_SLOT = 49;
    public static final int[] OUTPUT_SLOTS = {50, 51};

    private static final int INVENTORY_SIZE = 54;

    public static void open(Player player, Location location, StationManager.StationData data, BetterCooking plugin) {
        StationHolder holder = new StationHolder(location);
        Inventory inv = Bukkit.createInventory(holder, INVENTORY_SIZE,
                Component.text("Cooking Station", NamedTextColor.DARK_GRAY));
        holder.setInventory(inv);

        // Filler for all slots
        ItemStack filler = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            inv.setItem(i, filler);
        }

        setIdleBar(inv);

        // Restore stored ingredients
        for (int i = 0; i < INGREDIENT_SLOTS.length; i++) {
            ItemStack item = (data.ingredients != null && i < data.ingredients.length) ? data.ingredients[i] : null;
            inv.setItem(INGREDIENT_SLOTS[i], item);
        }

        inv.setItem(FUEL_SLOT, data.fuel);
        inv.setItem(FUEL_GAUGE_SLOT, buildFuelGauge(data.burns));
        // Restore uncollected output (null clears the filler)
        for (int i = 0; i < OUTPUT_SLOTS.length; i++) {
            ItemStack item = (data.output != null && i < data.output.length) ? data.output[i] : null;
            inv.setItem(OUTPUT_SLOTS[i], item);
        }
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
            inv.setItem(BAR_START + i, createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
    }

    /**
     * Redraws the timing bar with zone colours and the needle at needlePos.
     * Zone layout (center = index 4):
     *   distance == 0                       → LIME   (perfect — dead center only)
     *   distance <= goodZone                → YELLOW (good)
     *   otherwise                           → RED    (miss)
     */
    public static void updateBar(Inventory inv, int needlePos, CookingRecipe recipe) {
        int center = BAR_SIZE / 2;
        for (int i = 0; i < BAR_SIZE; i++) {
            int distance = Math.abs(i - center);
            ItemStack pane;
            if (i == needlePos) {
                pane = createGuiItem(Material.WHITE_STAINED_GLASS_PANE, "▼ Click Cook!");
            } else if (distance == 0) {
                pane = createGuiItem(Material.LIME_STAINED_GLASS_PANE, "Perfect!");
            } else if (distance <= recipe.getGoodZone()) {
                pane = createGuiItem(Material.YELLOW_STAINED_GLASS_PANE, "Good");
            } else {
                pane = createGuiItem(Material.RED_STAINED_GLASS_PANE, "Miss");
            }
            inv.setItem(BAR_START + i, pane);
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

    public static boolean isOutputSlot(int slot) {
        for (int s : OUTPUT_SLOTS) if (s == slot) return true;
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

    /** Fuel gauge: stored fuel in furnace units — 1 unit cooks 1 ingredient item. */
    public static ItemStack buildFuelGauge(int burns) {
        Material mat = burns > 0 ? Material.BLAZE_POWDER : Material.GRAY_DYE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Fuel", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Fuel for " + burns + " item" + (burns == 1 ? "" : "s"), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Click with coal to refuel", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
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
