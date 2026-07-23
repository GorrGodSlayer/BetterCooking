package com.bettercooking.commands;

import com.bettercooking.BetterCooking;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CookingCommand implements CommandExecutor {

    private final BetterCooking plugin;

    public CookingCommand(BetterCooking plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /cooking add <recipe-id>", NamedTextColor.YELLOW));
                    return true;
                }
                plugin.getRecipeEditorManager().openEditor(player, normalizeId(args));
            }
            case "change" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /cooking change <recipe-id>", NamedTextColor.YELLOW));
                    return true;
                }
                String id = normalizeId(args);
                if (!plugin.getRecipeEditorManager().openEditorForExisting(player, id)) {
                    player.sendMessage(Component.text("No recipe found with id '" + id + "'.", NamedTextColor.RED));
                }
            }
            case "cancel" -> plugin.getRecipeEditorManager().cancelEditor(player);
            default -> sendUsage(player);
        }
        return true;
    }

    /** Joins every word after the subcommand into one id, e.g. "/cooking add heart stew" -> "heart_stew". */
    private String normalizeId(String[] args) {
        StringBuilder id = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) id.append('_');
            id.append(args[i].toLowerCase());
        }
        return id.toString();
    }

    private void sendUsage(Player player) {
        player.sendMessage(Component.text(
                "Usage: /cooking add <recipe-id>  |  /cooking change <recipe-id>  |  /cooking cancel",
                NamedTextColor.YELLOW));
    }
}
