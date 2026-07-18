package com.bettercooking.cooking;

import com.bettercooking.BetterCooking;
import com.bettercooking.cooking.CookingRecipe.RecipeResult;
import com.bettercooking.ui.CookingUI;
import com.nexomc.nexo.api.NexoItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class CookingMinigame extends BukkitRunnable {

    private final BetterCooking plugin;
    private final Player player;
    private final Inventory inv;
    private final CookingRecipe recipe;
    private final Location stationLocation;

    private int needlePos = 0;
    private int direction = 1;
    private boolean finished = false;

    public CookingMinigame(BetterCooking plugin, Player player, Inventory inv,
                            CookingRecipe recipe, Location stationLocation) {
        this.plugin = plugin;
        this.player = player;
        this.inv = inv;
        this.recipe = recipe;
        this.stationLocation = stationLocation;
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
        if (distance <= recipe.getPerfectZone()) return "perfect";
        if (distance <= recipe.getPerfectZone() + recipe.getGoodZone()) return "good";
        return "failed";
    }

    private void finish(boolean deliver) {
        finished = true;
        cancel();
        plugin.getStationManager().removeMinigame(player.getUniqueId());
        CookingUI.setIdleBar(inv);
    }

    private void deliverOutput(String quality) {
        RecipeResult result = recipe.getResults().getOrDefault(quality, recipe.getResults().get("failed"));

        // Consume ingredients
        for (int slot : CookingUI.INGREDIENT_SLOTS) {
            inv.setItem(slot, null);
        }
        plugin.getStationManager().saveIngredients(stationLocation,
                new ItemStack[CookingUI.INGREDIENT_SLOTS.length]);

        // Reset UI to idle state
        inv.setItem(CookingUI.COOK_BUTTON_SLOT, CookingUI.buildCookButton(false));
        inv.setItem(CookingUI.RECIPE_DISPLAY_SLOT, CookingUI.buildNoRecipeItem());

        // Build and place output item
        if (result != null) {
            ItemStack output = buildItem(result);
            if (output != null) {
                ItemStack existing = inv.getItem(CookingUI.OUTPUT_SLOT);
                if (existing == null || existing.getType() == Material.AIR) {
                    inv.setItem(CookingUI.OUTPUT_SLOT, output);
                } else {
                    player.getWorld().dropItemNaturally(player.getLocation(), output);
                }
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

    private ItemStack buildItem(RecipeResult result) {
        if (result.type().equals("nexo")) {
            var nexoItem = NexoItems.itemFromId(result.id());
            if (nexoItem == null) {
                plugin.getLogger().warning("Unknown Nexo result item: " + result.id());
                return null;
            }
            ItemStack item = nexoItem.build();
            item.setAmount(result.amount());
            return item;
        } else {
            try {
                return new ItemStack(Material.valueOf(result.id()), result.amount());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown material in result: " + result.id());
                return null;
            }
        }
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
