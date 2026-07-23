package com.bettercooking.editor;

import com.bettercooking.BetterCooking;
import com.bettercooking.cooking.CookingRecipe;
import com.bettercooking.editor.RecipeEditorUI.RecipeEditorHolder;
import com.bettercooking.managers.RecipeEditorManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Handles clicks/drags in the recipe editor GUI. Deliberately has no dependency on chat events —
 * see RecipeEditorChatListener — so a runtime issue resolving that event class on some server
 * builds can't take down Save/Cancel/etc. registration along with it (Bukkit resolves every
 * @EventHandler method's parameter type via reflection for the whole listener at once).
 */

public class RecipeEditorListener implements Listener {

    private final BetterCooking plugin;
    private final RecipeEditorManager manager;

    public RecipeEditorListener(BetterCooking plugin, RecipeEditorManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // -------------------------------------------------------------------------
    // Click handling

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RecipeEditorHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inv = event.getInventory();
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= inv.getSize()) return; // player inventory — allow normal behaviour

        RecipeEditorSession session = holder.getSession();

        if (rawSlot == RecipeEditorUI.QUALITY_SLOT) {
            event.setCancelled(true);
            switchQuality(inv, session);
            return;
        }
        if (rawSlot == RecipeEditorUI.NEEDLE_SPEED_SLOT) {
            event.setCancelled(true);
            cycleNeedleSpeed(inv, session);
            return;
        }
        if (rawSlot == RecipeEditorUI.EFFECT_SLOT) {
            event.setCancelled(true);
            cycleEffect(inv, session, event.getClick());
            return;
        }
        if (rawSlot == RecipeEditorUI.NAME_SLOT) {
            event.setCancelled(true);
            startNameInput(player, session);
            return;
        }
        if (rawSlot == RecipeEditorUI.LORE_SLOT) {
            event.setCancelled(true);
            startLoreInput(player, session);
            return;
        }
        if (rawSlot == RecipeEditorUI.SAVE_SLOT) {
            event.setCancelled(true);
            saveRecipe(player, inv, session);
            return;
        }
        if (rawSlot == RecipeEditorUI.CANCEL_SLOT) {
            event.setCancelled(true);
            manager.cancelEditor(player);
            return;
        }
        if (RecipeEditorUI.isIngredientSlot(rawSlot) || rawSlot == RecipeEditorUI.OUTPUT_SLOT) {
            return; // free placement/removal
        }

        event.setCancelled(true); // filler slots
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof RecipeEditorHolder)) return;
        Inventory inv = event.getInventory();

        for (int slot : event.getRawSlots()) {
            if (slot >= inv.getSize()) continue;
            if (!RecipeEditorUI.isIngredientSlot(slot) && slot != RecipeEditorUI.OUTPUT_SLOT) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.removeSession(event.getPlayer().getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Helpers

    private void switchQuality(Inventory inv, RecipeEditorSession session) {
        RecipeEditorSession.QualityDraft oldDraft = session.getCurrentDraft();
        ItemStack current = inv.getItem(RecipeEditorUI.OUTPUT_SLOT);
        oldDraft.item = (current != null && current.getType() != Material.AIR) ? current.clone() : null;

        session.nextQuality();
        RecipeEditorSession.QualityDraft newDraft = session.getCurrentDraft();
        inv.setItem(RecipeEditorUI.OUTPUT_SLOT, newDraft.item != null ? newDraft.item.clone() : null);

        RecipeEditorUI.refreshButtons(inv, session);
    }

    private void cycleNeedleSpeed(Inventory inv, RecipeEditorSession session) {
        long[] speeds = RecipeEditorUI.NEEDLE_SPEEDS;
        int idx = 0;
        for (int i = 0; i < speeds.length; i++) if (speeds[i] == session.getNeedleSpeed()) { idx = i; break; }
        session.setNeedleSpeed(speeds[(idx + 1) % speeds.length]);
        inv.setItem(RecipeEditorUI.NEEDLE_SPEED_SLOT, RecipeEditorUI.buildNeedleSpeedButton(session.getNeedleSpeed()));
    }

    private void cycleEffect(Inventory inv, RecipeEditorSession session, ClickType click) {
        RecipeEditorSession.QualityDraft draft = session.getCurrentDraft();

        if (click.isShiftClick()) {
            draft.effectAmplifier = (draft.effectAmplifier + 1) % 3;
        } else if (click.isRightClick()) {
            int[] steps = RecipeEditorUI.DURATION_STEPS;
            int idx = 0;
            for (int i = 0; i < steps.length; i++) if (steps[i] == draft.effectDuration) { idx = i; break; }
            draft.effectDuration = steps[(idx + 1) % steps.length];
        } else {
            String[] types = RecipeEditorUI.EFFECT_TYPES;
            int idx = 0;
            for (int i = 0; i < types.length; i++) if (Objects.equals(types[i], draft.effectType)) { idx = i; break; }
            draft.effectType = types[(idx + 1) % types.length];
        }

        inv.setItem(RecipeEditorUI.EFFECT_SLOT, RecipeEditorUI.buildEffectButton(draft));
    }

    private void startNameInput(Player player, RecipeEditorSession session) {
        ItemStack item = session.getInventory().getItem(RecipeEditorUI.OUTPUT_SLOT);
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(Component.text("Place an item in the output slot first.", NamedTextColor.RED));
            return;
        }
        session.setChatTarget(RecipeEditorSession.ChatTarget.NAME);
        player.closeInventory();
        player.sendMessage(Component.text("Type the item's name in chat.", NamedTextColor.AQUA));
    }

    private void startLoreInput(Player player, RecipeEditorSession session) {
        ItemStack item = session.getInventory().getItem(RecipeEditorUI.OUTPUT_SLOT);
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(Component.text("Place an item in the output slot first.", NamedTextColor.RED));
            return;
        }
        session.setChatTarget(RecipeEditorSession.ChatTarget.LORE);
        player.closeInventory();
        player.sendMessage(Component.text(
                "Type a lore line in chat. Type 'done' to finish, or 'clear' to remove all lore.", NamedTextColor.AQUA));
    }

    private void saveRecipe(Player player, Inventory inv, RecipeEditorSession session) {
        RecipeEditorSession.QualityDraft currentDraft = session.getCurrentDraft();
        ItemStack current = inv.getItem(RecipeEditorUI.OUTPUT_SLOT);
        currentDraft.item = (current != null && current.getType() != Material.AIR) ? current.clone() : null;

        List<CookingRecipe.RecipeIngredient> ingredients = new ArrayList<>();
        for (int slot : RecipeEditorUI.INGREDIENT_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                ingredients.add(plugin.getRecipeManager().itemToIngredient(item));
            }
        }
        if (ingredients.isEmpty()) {
            player.sendMessage(Component.text("Add at least one ingredient before saving.", NamedTextColor.RED));
            return;
        }

        Map<String, CookingRecipe.RecipeResult> results = new LinkedHashMap<>();
        for (String quality : new String[]{"perfect", "good", "failed"}) {
            RecipeEditorSession.QualityDraft draft = session.getQuality(quality);
            if (draft.item != null) {
                results.put(quality, plugin.getRecipeManager().itemToResult(
                        draft.item, draft.effectType, draft.effectAmplifier, draft.effectDuration));
            }
        }
        if (results.isEmpty()) {
            player.sendMessage(Component.text(
                    "Set at least one output (perfect/good/failed) before saving.", NamedTextColor.RED));
            return;
        }

        plugin.getRecipeManager().saveRecipe(session.getId(), session.getDisplayName(), ingredients, results,
                session.getNeedleSpeed(), session.getGoodZone());

        manager.removeSession(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(Component.text("Recipe '" + session.getId() + "' saved!", NamedTextColor.GREEN));
    }
}
