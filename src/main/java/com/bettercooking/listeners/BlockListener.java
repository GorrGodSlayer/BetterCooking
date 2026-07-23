package com.bettercooking.listeners;

import com.bettercooking.BetterCooking;
import com.nexomc.nexo.api.NexoBlocks;
import com.nexomc.nexo.mechanics.custom_block.CustomBlockMechanic;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class BlockListener implements Listener {

    private final BetterCooking plugin;

    public BlockListener(BetterCooking plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || !isStation(block)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        // Don't reopen if the player already has a minigame running
        if (plugin.getStationManager().hasActiveMinigame(player.getUniqueId())) return;

        if (!plugin.getStationManager().openStation(player, block.getLocation())) {
            player.sendMessage(Component.text(
                    colorize(plugin.getConfig().getString("messages.station-busy",
                            "&cSomeone else is using this station.")),
                    NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isStation(block)) return;

        // Someone has the GUI open — breaking now would dupe or void its contents
        if (plugin.getStationManager().isStationOpen(block.getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text(
                    colorize(plugin.getConfig().getString("messages.station-busy",
                            "&cSomeone else is using this station.")),
                    NamedTextColor.RED));
            return;
        }

        plugin.getStationManager().dropStationContents(block.getLocation());
    }

    /**
     * A configured station block only acts as a cooking station while a pressure plate (any
     * kind) sits on top of it — otherwise it's left alone to behave as its vanilla block
     * (e.g. a plain Smoker still smelts normally when nothing is placed on top of it).
     */
    private boolean isStation(Block block) {
        if (!hasPressurePlateOnTop(block)) return false;

        String stationId = plugin.getConfig().getString("cooking-station.block-id", "SMOKER");
        if (stationId.startsWith("nexo:")) {
            if (!plugin.isNexoEnabled()) return false;
            CustomBlockMechanic mechanic = NexoBlocks.customBlockMechanic(block.getLocation());
            return mechanic != null && stationId.substring("nexo:".length()).equals(mechanic.getItemID());
        }
        return block.getType() == Material.matchMaterial(stationId);
    }

    private boolean hasPressurePlateOnTop(Block block) {
        return block.getRelative(BlockFace.UP).getType().name().endsWith("_PRESSURE_PLATE");
    }

    private String colorize(String s) {
        return s.replace("&", "§");
    }
}
