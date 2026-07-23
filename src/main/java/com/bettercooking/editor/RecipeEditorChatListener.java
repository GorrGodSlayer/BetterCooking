package com.bettercooking.editor;

import com.bettercooking.BetterCooking;
import com.bettercooking.managers.RecipeEditorManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures chat input for the recipe editor's "Set Lore" flow.
 * Kept separate from RecipeEditorListener so registering this one listener doesn't
 * risk taking the click-handling listener down with it — see the note there.
 */
public class RecipeEditorChatListener implements Listener {

    private final BetterCooking plugin;
    private final RecipeEditorManager manager;

    public RecipeEditorChatListener(BetterCooking plugin, RecipeEditorManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        RecipeEditorSession session = manager.getSession(player.getUniqueId());
        if (session == null || session.getChatTarget() == RecipeEditorSession.ChatTarget.NONE) return;

        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        RecipeEditorSession.ChatTarget target = session.getChatTarget();

        // Inventory/item edits must happen on the main thread; chat events fire off it.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (target == RecipeEditorSession.ChatTarget.NAME) {
                handleNameInput(player, session, text);
            } else {
                handleLoreInput(player, session, text);
            }
        });
    }

    private void handleNameInput(Player player, RecipeEditorSession session, String text) {
        ItemStack item = session.getInventory().getItem(RecipeEditorUI.OUTPUT_SLOT);
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(Component.text(
                    "The output item is gone — place an item first, then set the name again.", NamedTextColor.RED));
            session.setChatTarget(RecipeEditorSession.ChatTarget.NONE);
            player.openInventory(session.getInventory());
            return;
        }

        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacySection()
                .deserialize(text.replace('&', '§'))
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        session.getInventory().setItem(RecipeEditorUI.OUTPUT_SLOT, item);

        session.setChatTarget(RecipeEditorSession.ChatTarget.NONE);
        player.openInventory(session.getInventory());
        player.sendMessage(Component.text("Name set to: " + text, NamedTextColor.GREEN));
    }

    private void handleLoreInput(Player player, RecipeEditorSession session, String text) {
        if (text.equalsIgnoreCase("done")) {
            session.setChatTarget(RecipeEditorSession.ChatTarget.NONE);
            player.openInventory(session.getInventory());
            player.sendMessage(Component.text("Lore updated.", NamedTextColor.GREEN));
            return;
        }

        ItemStack item = session.getInventory().getItem(RecipeEditorUI.OUTPUT_SLOT);
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(Component.text(
                    "The output item is gone — place an item first, then set lore again.", NamedTextColor.RED));
            session.setChatTarget(RecipeEditorSession.ChatTarget.NONE);
            player.openInventory(session.getInventory());
            return;
        }

        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();

        if (text.equalsIgnoreCase("clear")) {
            lore.clear();
            player.sendMessage(Component.text("Lore cleared. Type more lines, or 'done' to finish.", NamedTextColor.YELLOW));
        } else {
            lore.add(LegacyComponentSerializer.legacySection()
                    .deserialize(text.replace('&', '§'))
                    .decoration(TextDecoration.ITALIC, false));
            player.sendMessage(Component.text("Line added. Type more, or 'done' to finish.", NamedTextColor.GRAY));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        session.getInventory().setItem(RecipeEditorUI.OUTPUT_SLOT, item);
    }
}
