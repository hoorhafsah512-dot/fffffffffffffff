package com.nationssmp.listeners;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fires a siege alarm whenever an enemy player enters a nation's castle bounding box.
 *
 *  • Owner receives a red title + multiple sounds ("YOUR CAPITAL IS UNDER SIEGE")
 *  • Attacker receives a bold warning that they have entered enemy fortifications
 *  • Per-attacker cooldown of 60s prevents spam
 *  • Only triggers for players with a castle (castle origin known)
 *  • War-declared enemies get a louder alarm than regular enemies
 */
public class SiegeListener implements Listener {

    private static final int CASTLE_RADIUS = 40;       // match CastleManager.HALF
    private static final long COOLDOWN_MS  = 60_000L;  // 60 seconds

    private final NationsSMP plugin;
    /** "ownerUUID:attackerUUID" → last alert timestamp */
    private final Map<String, Long> lastAlert = new ConcurrentHashMap<>();

    public SiegeListener(NationsSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only care about block changes (not head rotation)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
         && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player attacker = event.getPlayer();
        if (!isNationPlayer(attacker)) return;

        // Check every online player's castle
        for (Player owner : Bukkit.getOnlinePlayers()) {
            if (owner.equals(attacker)) continue;
            Nation ownerNation = plugin.getNationManager().getNation(owner.getUniqueId());
            if (ownerNation == null || !ownerNation.isSetupComplete()) continue;
            if (!ownerNation.hasSpawnLocation()) continue; // no castle yet

            // Determine if attacker is an enemy (not allied)
            Nation attackerNation = plugin.getNationManager().getNation(attacker.getUniqueId());
            if (attackerNation == null) continue;
            boolean allied = attackerNation.getAllyNationName() != null
                && attackerNation.getAllyNationName().equalsIgnoreCase(ownerNation.getNationName());
            if (allied) continue; // allies don't trigger siege

            // Check bounding box
            Location spawnLoc = ownerNation.getSpawnLocation();
            if (spawnLoc == null) continue;
            if (!spawnLoc.getWorld().equals(event.getTo().getWorld())) continue;
            int dx = Math.abs(event.getTo().getBlockX() - spawnLoc.getBlockX());
            int dz = Math.abs(event.getTo().getBlockZ() - spawnLoc.getBlockZ());
            if (dx > CASTLE_RADIUS || dz > CASTLE_RADIUS) continue;

            // Cooldown check
            String coolKey = owner.getUniqueId() + ":" + attacker.getUniqueId();
            long now = System.currentTimeMillis();
            if (now - lastAlert.getOrDefault(coolKey, 0L) < COOLDOWN_MS) continue;
            lastAlert.put(coolKey, now);

            boolean atWar = plugin.getWarManager().atWar(
                owner.getUniqueId(), attacker.getUniqueId());

            // ── Alert the OWNER ───────────────────────────────────────────────
            String ownerTitle   = ChatColor.DARK_RED + "🔥 CAPITAL UNDER SIEGE!";
            String ownerSubtitle = ChatColor.RED + attackerNation.getNationName()
                + " forces have entered your walls!";
            owner.sendTitle(ownerTitle, ownerSubtitle, 5, 100, 20);
            owner.sendMessage(ChatColor.DARK_RED + "⚔ " + ChatColor.RED
                + attacker.getName() + " of " + attackerNation.getNationName()
                + ChatColor.DARK_RED + " is inside your capital!");
            owner.playSound(owner.getLocation(), Sound.BLOCK_BELL_USE, 1.5f, 0.5f);
            owner.playSound(owner.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.3f, 1.0f);
            if (atWar) {
                // Extra urgency if at war
                owner.playSound(owner.getLocation(), Sound.ENTITY_WITHER_HURT, 0.5f, 0.7f);
                owner.sendMessage(ChatColor.DARK_RED + "⚔ You are AT WAR with "
                    + attackerNation.getNationName() + "!");
            }

            // ── Warn the ATTACKER ─────────────────────────────────────────────
            attacker.sendTitle(
                ChatColor.DARK_RED + "⚔ ENEMY FORTIFICATION",
                ChatColor.RED + "You have breached " + ownerNation.getNationName() + "'s capital!",
                5, 60, 15);
            attacker.playSound(attacker.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.4f, 0.6f);

            // ── Log to history ────────────────────────────────────────────────
            plugin.getNationHistoryManager().log(ownerNation,
                "🔥 Capital sieged by " + attacker.getName()
                    + " of " + attackerNation.getNationName());
            plugin.getNationHistoryManager().log(attackerNation,
                "⚔ Breached capital of " + ownerNation.getNationName());
        }
    }

    private boolean isNationPlayer(Player p) {
        Nation n = plugin.getNationManager().getNation(p.getUniqueId());
        return n != null && n.isSetupComplete();
    }
}
