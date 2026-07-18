package com.bettercooking.listeners;

import com.bettercooking.BetterCooking;
import com.nexomc.nexo.api.NexoBlocks;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
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
        if (block == null) return;

        String stationId = plugin.getConfig().getString("cooking-station.nexo-block-id", "cooking_station");

        // Check if the block at this location is the configured Nexo custom block
        // Note: verify NexoBlocks API matches your installed Nexo version
        String blockId = NexoBlocks.getId(block.getLocation());
        if (!stationId.equals(blockId)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        // Don't reopen if the player already has a minigame running
        if (plugin.getStationManager().hasActiveMinigame(player.getUniqueId())) return;

        plugin.getStationManager().openStation(player, block.getLocation());
    }
}
