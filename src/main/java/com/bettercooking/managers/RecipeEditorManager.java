package com.bettercooking.managers;

import com.bettercooking.BetterCooking;
import com.bettercooking.cooking.CookingRecipe;
import com.bettercooking.editor.RecipeEditorSession;
import com.bettercooking.editor.RecipeEditorUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RecipeEditorManager {

    private final BetterCooking plugin;
    private final Map<UUID, RecipeEditorSession> sessions = new HashMap<>();

    public RecipeEditorManager(BetterCooking plugin) {
        this.plugin = plugin;
    }

    /** Opens a blank editor for a new recipe id, resuming an in-progress edit of the same id if there is one. */
    public void openEditor(Player player, String id) {
        RecipeEditorSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.getId().equals(id)) {
            session = new RecipeEditorSession(id);
            sessions.put(player.getUniqueId(), session);
        }
        RecipeEditorUI.open(player, session);
    }

    /** Opens the editor pre-loaded with an existing recipe's data. Returns false if no such recipe exists. */
    public boolean openEditorForExisting(Player player, String id) {
        CookingRecipe existing = plugin.getRecipeManager().getRecipe(id);
        if (existing == null) return false;

        RecipeEditorSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.getId().equals(id)) {
            session = new RecipeEditorSession(id);
            session.setNeedleSpeed(existing.getNeedleSpeed());
            session.setGoodZone(existing.getGoodZone());

            List<ItemStack> seedIngredients = new ArrayList<>();
            for (CookingRecipe.RecipeIngredient ing : existing.getIngredients()) {
                ItemStack item = plugin.getRecipeManager().ingredientToItem(ing);
                if (item != null) seedIngredients.add(item);
            }
            session.setSeedIngredients(seedIngredients);

            for (Map.Entry<String, CookingRecipe.RecipeResult> entry : existing.getResults().entrySet()) {
                RecipeEditorSession.QualityDraft draft = session.getQuality(entry.getKey());
                if (draft == null) continue; // unknown quality key, ignore
                CookingRecipe.RecipeResult result = entry.getValue();
                draft.item = plugin.getRecipeManager().resultToItem(result);
                draft.effectType = result.effectType();
                draft.effectAmplifier = result.effectAmplifier();
                draft.effectDuration = result.effectDuration() > 0 ? result.effectDuration() : 200;
            }

            sessions.put(player.getUniqueId(), session);
        }
        RecipeEditorUI.open(player, session);
        return true;
    }

    public void cancelEditor(Player player) {
        boolean had = sessions.remove(player.getUniqueId()) != null;
        player.closeInventory();
        player.sendMessage(Component.text(
                had ? "Recipe editing cancelled." : "You don't have a recipe being edited.",
                NamedTextColor.YELLOW));
    }

    public RecipeEditorSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    public void removeSession(UUID playerId) {
        sessions.remove(playerId);
    }
}
