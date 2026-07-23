package com.bettercooking.listeners;

import com.bettercooking.BetterCooking;
import com.bettercooking.cooking.RecipeEffect;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

/** Grants the potion effect (if any) baked into a cooked item's PersistentDataContainer when eaten. */
public class FoodEffectListener implements Listener {

    private final BetterCooking plugin;

    public FoodEffectListener(BetterCooking plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String typeName = pdc.get(new NamespacedKey(plugin, RecipeEffect.KEY_TYPE), PersistentDataType.STRING);
        if (typeName == null) return;

        PotionEffectType effectType = Registry.EFFECT.get(NamespacedKey.minecraft(typeName.toLowerCase(Locale.ROOT)));
        if (effectType == null) {
            plugin.getLogger().warning("Unknown potion effect type on eaten item: " + typeName);
            return;
        }

        int amplifier = pdc.getOrDefault(new NamespacedKey(plugin, RecipeEffect.KEY_AMPLIFIER), PersistentDataType.INTEGER, 0);
        int duration = pdc.getOrDefault(new NamespacedKey(plugin, RecipeEffect.KEY_DURATION), PersistentDataType.INTEGER, 200);

        Player player = event.getPlayer();
        player.addPotionEffect(new PotionEffect(effectType, duration, amplifier));
    }
}
