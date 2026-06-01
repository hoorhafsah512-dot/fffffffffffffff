package com.nationssmp.data;

import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

import java.util.*;

/**
 * Stores all data for a single player's nation.
 * Serialised to YAML by DataManager.
 */
public class Nation {

    // ── Identity ─────────────────────────────────────────────────────────────
    private final String playerUUID;
    private String playerName;
    private String nationName;
    private String title;          // e.g. "The Unconquered"
    private String motto;          // GOT-style words
    private String animalKey;      // key from NationAnimal enum
    private String buildingBlock;  // Material name of chosen block

    // ── Status flags ─────────────────────────────────────────────────────────
    private boolean oathbreaker;
    private boolean martyr;
    private boolean setupComplete;

    // ── Land ─────────────────────────────────────────────────────────────────
    private final Set<String> claimedChunks; // "world:cx:cz"

    // ── Alliance ─────────────────────────────────────────────────────────────
    private String allyNationName; // null if no ally

    // ── Castle / spawn ────────────────────────────────────────────────────────
    private String spawnWorldName;
    private double spawnX, spawnY = 64, spawnZ;
    private float  spawnYaw = 180f;

    // ── Banner ────────────────────────────────────────────────────────────────
    private String       bannerBase     = "WHITE";       // DyeColor name
    private List<String> bannerPatterns = new ArrayList<>(); // "TYPE_ID:DYE_COLOR"

    // ── Companion NPC ─────────────────────────────────────────────────────────
    // ── Oath ──────────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────────────────
    public Nation(String playerUUID, String playerName) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.claimedChunks = new HashSet<>();
        this.oathbreaker = false;
        this.martyr = false;
        this.setupComplete = false;
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public String getPlayerUUID()    { return playerUUID; }
    public String getPlayerName()    { return playerName; }
    public String getNationName()    { return nationName; }
    public String getTitle()         { return title; }
    public String getMotto()         { return motto; }
    public String getAnimalKey()     { return animalKey; }
    public String getBuildingBlock() { return buildingBlock; }
    public boolean isOathbreaker()   { return oathbreaker; }
    public boolean isMartyr()        { return martyr; }
    public boolean isSetupComplete() { return setupComplete; }
    public Set<String> getClaimedChunks()     { return claimedChunks; }
    public String getAllyNationName() { return allyNationName; }

    // ── Setters ───────────────────────────────────────────────────────────────
    public void setPlayerName(String n)    { this.playerName = n; }
    public void setNationName(String n)    { this.nationName = n; }
    public void setTitle(String t)         { this.title = t; }
    public void setMotto(String m)         { this.motto = m; }
    public void setAnimalKey(String a)     { this.animalKey = a; }
    public void setBuildingBlock(String b) { this.buildingBlock = b; }
    public void setOathbreaker(boolean v)  { this.oathbreaker = v; }
    public void setMartyr(boolean v)       { this.martyr = v; }
    public void setSetupComplete(boolean v){ this.setupComplete = v; }
    public void setAllyNationName(String a){ this.allyNationName = a; }

    // ── Spawn / castle ────────────────────────────────────────────────────────
    public boolean hasSpawnLocation() { return spawnWorldName != null; }

    public Location getSpawnLocation() {
        if (spawnWorldName == null) return null;
        World w = Bukkit.getWorld(spawnWorldName);
        if (w == null) return null;
        return new Location(w, spawnX, spawnY, spawnZ, spawnYaw, 0f);
    }

    public void setSpawnLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        spawnWorldName = loc.getWorld().getName();
        spawnX = loc.getX(); spawnY = loc.getY(); spawnZ = loc.getZ();
        spawnYaw = loc.getYaw();
    }

    public String getSpawnWorldName() { return spawnWorldName; }
    public double getSpawnX()         { return spawnX; }
    public double getSpawnY()         { return spawnY; }
    public double getSpawnZ()         { return spawnZ; }
    public float  getSpawnYaw()       { return spawnYaw; }

    public void setSpawnWorldName(String w) { this.spawnWorldName = w; }
    public void setSpawnX(double v)         { this.spawnX = v; }
    public void setSpawnY(double v)         { this.spawnY = v; }
    public void setSpawnZ(double v)         { this.spawnZ = v; }
    public void setSpawnYaw(float v)        { this.spawnYaw = v; }

    // ── Banner ────────────────────────────────────────────────────────────────
    public String       getBannerBase()     { return bannerBase; }
    public List<String> getBannerPatterns() { return bannerPatterns; }

    public void setBannerBase(String base)             { this.bannerBase = base; }
    public void setBannerPatterns(List<String> list)   { this.bannerPatterns = list; }

    /** Builds a physical Minecraft banner ItemStack from stored pattern data. */
    @SuppressWarnings("deprecation")
    public ItemStack buildBannerItem() {
        DyeColor base;
        try { base = DyeColor.valueOf(bannerBase); }
        catch (Exception e) { base = DyeColor.WHITE; }

        ItemStack banner = new ItemStack(com.nationssmp.managers.BannerManager.bannerMaterialOf(base));
        org.bukkit.inventory.meta.BannerMeta meta =
            (org.bukkit.inventory.meta.BannerMeta) banner.getItemMeta();
        if (meta == null) return banner;

        for (String encoded : bannerPatterns) {
            String[] parts = encoded.split(":", 2);
            if (parts.length != 2) continue;
            try {
                org.bukkit.block.banner.PatternType type;
                try {
                    type = org.bukkit.Registry.BANNER_PATTERN.get(
                        org.bukkit.NamespacedKey.minecraft(parts[0].toLowerCase()));
                } catch (Exception _ex) { type = null; }
                if (type == null) continue;
                DyeColor color = DyeColor.valueOf(parts[1]);
                meta.addPattern(new org.bukkit.block.banner.Pattern(color, type));
            } catch (Exception ignored) {}
        }
        meta.setDisplayName(ChatColor.GOLD + nationName + "'s Banner");
        banner.setItemMeta(meta);
        return banner;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Permanently mark this player as oathbreaker and lock the title. */
    public void applyOathbreakerPunishment() {
        this.oathbreaker = true;
        this.title = "Oathbreaker";
    }

    /** Permanently mark this player as Martyr and prepend it to their title. */
    public void applyMartyrTitle() {
        this.martyr = true;
        if (this.title != null && !this.title.startsWith("Martyr")) {
            this.title = "Martyr, " + this.title;
        }
    }

    /** Add a chunk to this nation's territory. */
    public void claimChunk(String worldName, int cx, int cz) {
        claimedChunks.add(worldName + ":" + cx + ":" + cz);
    }

    /** Remove a chunk from this nation's territory. */
    public void unclaimChunk(String worldName, int cx, int cz) {
        claimedChunks.remove(worldName + ":" + cx + ":" + cz);
    }

    /** Check if a chunk belongs to this nation. */
    public boolean ownsChunk(String worldName, int cx, int cz) {
        return claimedChunks.contains(worldName + ":" + cx + ":" + cz);
    }

    /** Full display tag shown in chat / above head. */
    public String getDisplayTag() {
        String animal = animalKey != null ? NationAnimal.byKey(animalKey).getEmoji() + " " : "";
        String nation = nationName != null ? "[" + nationName + "] " : "";
        String t = title != null ? title + " - " : "";
        return animal + nation + playerName + ", " + t.replace(" - ", "");
    }

    /** Short tag for chat prefix. */
    public String getChatPrefix() {
        String animal = animalKey != null ? NationAnimal.byKey(animalKey).getEmoji() : "";
        String nation = nationName != null ? nationName : "?";
        return animal + "[" + nation + "]";
    }

    public boolean hasAlly() {
        return allyNationName != null && !allyNationName.isEmpty();
    }

    /** Whether setup step is pending (so we should capture chat input). */
    private SetupStep pendingStep = SetupStep.NONE;

    public SetupStep getPendingStep() { return pendingStep; }
    public void setPendingStep(SetupStep s) { this.pendingStep = s; }

    public enum SetupStep {
        NONE,
        AWAITING_NATION_NAME,
        AWAITING_TITLE,
        AWAITING_MOTTO,
        AWAITING_ANIMAL_SELECTION,
        AWAITING_BLOCK_SELECTION,
        AWAITING_BANNER_DESIGN
    }
}
