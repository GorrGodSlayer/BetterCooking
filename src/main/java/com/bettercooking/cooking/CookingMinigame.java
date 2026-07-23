package com.bettercooking.cooking;

import com.bettercooking.BetterCooking;
import com.bettercooking.cooking.CookingRecipe.RecipeResult;
import com.bettercooking.ui.CookingUI;
import com.nexomc.nexo.api.NexoItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class CookingMinigame extends BukkitRunnable {

    private final BetterCooking plugin;
    private final Player player;
    private final Inventory inv;
    private final CookingRecipe recipe;
    private final Location stationLocation;
    private final int batches;
    private final int burnsCost;

    private int needlePos = 0;
    private int direction = 1;
    private boolean finished = false;

    public CookingMinigame(BetterCooking plugin, Player player, Inventory inv,
                            CookingRecipe recipe, Location stationLocation,
                            int batches, int burnsCost) {
        this.plugin = plugin;
        this.player = player;
        this.inv = inv;
        this.recipe = recipe;
        this.stationLocation = stationLocation;
        this.batches = batches;
        this.burnsCost = burnsCost;
    }

    @Override
    public void run() {
        // Cancel if player closed the inventory
        if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inv) {
            finish(false);
            return;
        }

        // Advance needle (bounces between 0 and BAR_SIZE-1)
        needlePos += direction;
        if (needlePos >= CookingUI.BAR_SIZE - 1) direction = -1;
        if (needlePos <= 0) direction = 1;

        CookingUI.updateBar(inv, needlePos, recipe);
    }

    /** Cancels without delivering (e.g. GUI closed) and refunds the reserved fuel. */
    public void abort() {
        if (!finished) finish(false);
    }

    /** Called by InventoryListener when the player clicks the Cook button during the minigame. */
    public void registerClick() {
        if (finished) return;
        String quality = determineQuality(needlePos);
        finish(true);
        deliverOutput(quality);
    }

    // -------------------------------------------------------------------------

    private String determineQuality(int pos) {
        int center = CookingUI.BAR_SIZE / 2;
        int distance = Math.abs(pos - center);
        if (distance == 0) return "perfect";
        if (distance <= recipe.getGoodZone()) return "good";
        return "failed";
    }

    private void finish(boolean deliver) {
        finished = true;
        cancel();
        plugin.getStationManager().removeMinigame(player.getUniqueId());
        CookingUI.setIdleBar(inv);
        if (!deliver) {
            // Cook was aborted (e.g. inventory closed) — refund the fuel it reserved
            plugin.getStationManager().getStation(stationLocation).burns += burnsCost;
        }
    }

    private void deliverOutput(String quality) {
        RecipeResult result = recipe.getResults().getOrDefault(quality, recipe.getResults().get("failed"));

        consumeIngredients();

        // Refresh recipe display — leftovers may still form another batch
        CookingRecipe remaining = plugin.getRecipeManager().matchRecipe(CookingUI.getIngredients(inv));
        if (remaining != null) {
            CookingUI.updateRecipeDisplay(inv, remaining);
            inv.setItem(CookingUI.COOK_BUTTON_SLOT, CookingUI.buildCookButton(true));
        } else {
            inv.setItem(CookingUI.COOK_BUTTON_SLOT, CookingUI.buildCookButton(false));
            inv.setItem(CookingUI.RECIPE_DISPLAY_SLOT, CookingUI.buildNoRecipeItem());
        }

        // Build and place output items (whole batch)
        if (result != null) {
            ItemStack output = buildItem(result);
            if (output != null) {
                placeOutput(output, result.amount() * batches);
            }
        }

        // Feedback
        switch (quality) {
            case "perfect" -> {
                player.sendMessage(Component.text(
                        colorize(plugin.getConfig().getString("messages.perfect", "&aPerfect cook!"))));
                playSound("sounds.perfect", Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
            }
            case "good" -> {
                player.sendMessage(Component.text(
                        colorize(plugin.getConfig().getString("messages.good", "&eGood cook!"))));
                playSound("sounds.good", Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
            }
            default -> {
                player.sendMessage(Component.text(
                        colorize(plugin.getConfig().getString("messages.failed", "&cBurnt!"))));
                playSound("sounds.failed", Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1f);
            }
        }
    }

    /**
     * Consumes one batch worth of each ingredient per cooked batch, leaving any
     * remainder in the slots (e.g. 5 beef, fuel for 3 → 2 beef stay).
     */
    private void consumeIngredients() {
        boolean[] used = new boolean[CookingUI.INGREDIENT_SLOTS.length];
        for (CookingRecipe.RecipeIngredient req : recipe.getIngredients()) {
            for (int i = 0; i < CookingUI.INGREDIENT_SLOTS.length; i++) {
                if (used[i]) continue;
                int slot = CookingUI.INGREDIENT_SLOTS[i];
                ItemStack item = inv.getItem(slot);
                if (item == null || item.getType() == Material.AIR) continue;
                if (!plugin.getRecipeManager().itemMatchesIngredient(item, req)) continue;
                used[i] = true;
                int take = req.amount() * batches;
                if (item.getAmount() > take) {
                    item.setAmount(item.getAmount() - take);
                    inv.setItem(slot, item);
                } else {
                    inv.setItem(slot, null);
                }
                break;
            }
        }
        plugin.getStationManager().getStation(stationLocation).ingredients = CookingUI.getIngredients(inv);
    }

    /** Places the batch output across the output slots, dropping whatever doesn't fit. */
    private void placeOutput(ItemStack template, int total) {
        int max = template.getMaxStackSize();
        for (int slot : CookingUI.OUTPUT_SLOTS) {
            if (total <= 0) break;
            ItemStack existing = inv.getItem(slot);
            if (existing == null || existing.getType() == Material.AIR) {
                int put = Math.min(max, total);
                ItemStack out = template.clone();
                out.setAmount(put);
                inv.setItem(slot, out);
                total -= put;
            } else if (existing.isSimilar(template)) {
                int add = Math.min(max - existing.getAmount(), total);
                if (add > 0) {
                    existing.setAmount(existing.getAmount() + add);
                    inv.setItem(slot, existing);
                    total -= add;
                }
            }
        }
        while (total > 0) {
            int put = Math.min(max, total);
            ItemStack out = template.clone();
            out.setAmount(put);
            player.getWorld().dropItemNaturally(player.getLocation(), out);
            total -= put;
        }
    }

    /** Builds a single result item; batch amounts are applied by placeOutput. */
    private ItemStack buildItem(RecipeResult result) {
        ItemStack item;
        if (result.type().equals("nexo")) {
            if (!plugin.isNexoEnabled()) {
                plugin.getLogger().warning("Recipe result '" + result.id() + "' needs Nexo, but Nexo isn't installed.");
                return null;
            }
            var nexoItem = NexoItems.itemFromId(result.id());
            if (nexoItem == null) {
                plugin.getLogger().warning("Unknown Nexo result item: " + result.id());
                return null;
            }
            item = nexoItem.build();
        } else {
            try {
                item = new ItemStack(Material.valueOf(result.id()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown material in result: " + result.id());
                return null;
            }
        }

        ItemMeta meta = item.getItemMeta();
        boolean metaChanged = false;

        if (result.displayName() != null) {
            meta.displayName(LegacyComponentSerializer.legacySection()
                    .deserialize(colorize(result.displayName()))
                    .decoration(TextDecoration.ITALIC, false));
            metaChanged = true;
        }

        if (result.lore() != null && !result.lore().isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : result.lore()) {
                lore.add(LegacyComponentSerializer.legacySection()
                        .deserialize(colorize(line))
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            metaChanged = true;
        }

        if (result.effectType() != null) {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, RecipeEffect.KEY_TYPE), PersistentDataType.STRING, result.effectType());
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, RecipeEffect.KEY_AMPLIFIER), PersistentDataType.INTEGER, result.effectAmplifier());
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, RecipeEffect.KEY_DURATION), PersistentDataType.INTEGER, result.effectDuration());
            metaChanged = true;
        }

        if (metaChanged) item.setItemMeta(meta);
        return item;
    }

    private void playSound(String configKey, Sound fallback, float volume, float pitch) {
        String soundName = plugin.getConfig().getString(configKey);
        Sound sound = fallback;
        if (soundName != null) {
            try {
                sound = Sound.valueOf(soundName);
            } catch (IllegalArgumentException ignored) {}
        }
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    /** Strips legacy colour codes for Adventure component display. */
    private String colorize(String s) {
        return s.replace("&", "§");
    }
}
