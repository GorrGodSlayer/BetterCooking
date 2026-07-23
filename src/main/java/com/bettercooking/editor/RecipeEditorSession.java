package com.bettercooking.editor;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Holds one admin's in-progress recipe while they build it via /cooking add or /cooking change. */
public class RecipeEditorSession {

    public enum ChatTarget { NONE, LORE, NAME }

    /** Per-quality output: the item (carrying name/lore in its own meta) plus an optional eat effect. */
    public static class QualityDraft {
        public ItemStack item;
        public String effectType; // null = no effect
        public int effectAmplifier = 0;
        public int effectDuration = 200; // ticks (10s)
    }

    private final String id;
    private final String displayName;
    private final Map<String, QualityDraft> qualities = new LinkedHashMap<>();
    private String currentQuality = "perfect";
    private ChatTarget chatTarget = ChatTarget.NONE;
    private Inventory inventory;
    private long needleSpeed = 4;
    private int goodZone = 2;
    private List<ItemStack> seedIngredients;

    public RecipeEditorSession(String id) {
        this.id = id;
        this.displayName = capitalize(id);
        qualities.put("perfect", new QualityDraft());
        qualities.put("good", new QualityDraft());
        qualities.put("failed", new QualityDraft());
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        String spaced = s.replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }

    public QualityDraft getQuality(String quality) { return qualities.get(quality); }
    public QualityDraft getCurrentDraft() { return qualities.get(currentQuality); }
    public String getCurrentQuality() { return currentQuality; }

    public String nextQuality() {
        currentQuality = switch (currentQuality) {
            case "perfect" -> "good";
            case "good" -> "failed";
            default -> "perfect";
        };
        return currentQuality;
    }

    public ChatTarget getChatTarget() { return chatTarget; }
    public void setChatTarget(ChatTarget target) { this.chatTarget = target; }

    public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    public long getNeedleSpeed() { return needleSpeed; }
    public void setNeedleSpeed(long needleSpeed) { this.needleSpeed = needleSpeed; }

    public int getGoodZone() { return goodZone; }
    public void setGoodZone(int goodZone) { this.goodZone = goodZone; }

    /** Ingredient items to pre-fill when the editor first builds its inventory (used by /cooking change). */
    public List<ItemStack> getSeedIngredients() { return seedIngredients; }
    public void setSeedIngredients(List<ItemStack> seedIngredients) { this.seedIngredients = seedIngredients; }
}
