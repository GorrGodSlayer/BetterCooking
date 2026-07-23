package com.bettercooking.editor;

import com.bettercooking.ui.CookingUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RecipeEditorUI {

    // Slot layout (3-row chest, 27 slots):
    //  [F][F][ing][ing][ing][ing][ing][F][F]              <- 5 ingredient slots
    //  [quality][speed][F][output][F][effect][name][lore][save]
    //  [F]x3[cancel][F]x5

    public static final int[] INGREDIENT_SLOTS = {2, 3, 4, 5, 6};
    public static final int QUALITY_SLOT = 9;
    public static final int NEEDLE_SPEED_SLOT = 10;
    public static final int OUTPUT_SLOT = 12;
    public static final int EFFECT_SLOT = 14;
    public static final int NAME_SLOT = 15;
    public static final int LORE_SLOT = 16;
    public static final int SAVE_SLOT = 17;
    public static final int CANCEL_SLOT = 22;

    private static final int INVENTORY_SIZE = 27;

    /** null = no effect; cycled in order by clicking the effect button. */
    public static final String[] EFFECT_TYPES = {
            null, "SPEED", "STRENGTH", "REGENERATION", "HASTE", "JUMP_BOOST",
            "FIRE_RESISTANCE", "NIGHT_VISION", "SATURATION", "RESISTANCE", "ABSORPTION"
    };
    public static final int[] DURATION_STEPS = {100, 200, 400, 600, 1200};
    /** Ticks between each needle step, cycled in order by clicking the needle-speed button. */
    public static final long[] NEEDLE_SPEEDS = {2, 3, 4, 5, 6, 8};

    public static void open(Player player, RecipeEditorSession session) {
        Inventory inv = session.getInventory();
        if (inv == null) {
            RecipeEditorHolder holder = new RecipeEditorHolder(session);
            inv = Bukkit.createInventory(holder, INVENTORY_SIZE,
                    Component.text("Recipe Editor: " + session.getId(), NamedTextColor.DARK_GRAY));
            holder.setInventory(inv);
            session.setInventory(inv);

            ItemStack filler = CookingUI.createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
            for (int i = 0; i < INVENTORY_SIZE; i++) {
                inv.setItem(i, filler);
            }

            List<ItemStack> seedIngredients = session.getSeedIngredients();
            for (int i = 0; i < INGREDIENT_SLOTS.length; i++) {
                ItemStack seed = (seedIngredients != null && i < seedIngredients.size()) ? seedIngredients.get(i) : null;
                inv.setItem(INGREDIENT_SLOTS[i], seed);
            }

            RecipeEditorSession.QualityDraft draft = session.getCurrentDraft();
            inv.setItem(OUTPUT_SLOT, draft.item != null ? draft.item.clone() : null);

            refreshButtons(inv, session);
        }

        player.openInventory(inv);
    }

    public static void refreshButtons(Inventory inv, RecipeEditorSession session) {
        inv.setItem(QUALITY_SLOT, buildQualityButton(session.getCurrentQuality()));
        inv.setItem(NEEDLE_SPEED_SLOT, buildNeedleSpeedButton(session.getNeedleSpeed()));
        inv.setItem(EFFECT_SLOT, buildEffectButton(session.getCurrentDraft()));
        inv.setItem(NAME_SLOT, CookingUI.createGuiItem(Material.NAME_TAG, "Set Name (type in chat)", NamedTextColor.AQUA));
        inv.setItem(LORE_SLOT, CookingUI.createGuiItem(Material.FEATHER, "Set Lore (type in chat)", NamedTextColor.AQUA));
        inv.setItem(SAVE_SLOT, CookingUI.createGuiItem(Material.EMERALD, "Save Recipe", NamedTextColor.GREEN));
        inv.setItem(CANCEL_SLOT, CookingUI.createGuiItem(Material.BARRIER, "Cancel", NamedTextColor.RED));
    }

    public static ItemStack buildNeedleSpeedButton(long speed) {
        String difficulty = speed <= 2 ? "Hard" : speed <= 5 ? "Medium" : "Easy";
        ItemStack item = CookingUI.createGuiItem(Material.CLOCK,
                "Needle Speed: " + speed + " (" + difficulty + ")", NamedTextColor.GOLD);
        ItemMeta meta = item.getItemMeta();
        meta.lore(List.of(
                Component.text("Lower = harder", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Click to cycle", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack buildQualityButton(String quality) {
        Material mat = switch (quality) {
            case "perfect" -> Material.LIME_DYE;
            case "good" -> Material.YELLOW_DYE;
            default -> Material.RED_DYE;
        };
        return CookingUI.createGuiItem(mat,
                "Editing: " + quality.toUpperCase() + " output (click to switch)", NamedTextColor.WHITE);
    }

    public static ItemStack buildEffectButton(RecipeEditorSession.QualityDraft draft) {
        if (draft.effectType == null) {
            ItemStack item = CookingUI.createGuiItem(Material.GLASS_BOTTLE, "No Effect (click to add)", NamedTextColor.GRAY);
            ItemMeta meta = item.getItemMeta();
            meta.lore(List.of(
                    Component.text("Left-click: choose an effect", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            item.setItemMeta(meta);
            return item;
        }

        ItemStack item = CookingUI.createGuiItem(Material.POTION, "Effect: " + draft.effectType, NamedTextColor.LIGHT_PURPLE);
        ItemMeta meta = item.getItemMeta();
        meta.lore(List.of(
                Component.text("Amplifier: " + (draft.effectAmplifier + 1), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Duration: " + (draft.effectDuration / 20) + "s", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Left: change effect", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Shift-click: change level", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click: change duration", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isIngredientSlot(int slot) {
        for (int s : INGREDIENT_SLOTS) if (s == slot) return true;
        return false;
    }

    public static class RecipeEditorHolder implements InventoryHolder {
        private final RecipeEditorSession session;
        private Inventory inventory;

        public RecipeEditorHolder(RecipeEditorSession session) {
            this.session = session;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public RecipeEditorSession getSession() {
            return session;
        }
    }
}
