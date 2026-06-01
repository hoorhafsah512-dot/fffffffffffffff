package com.nationssmp.listeners;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import com.nationssmp.data.NationAnimal;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Fires a title message + sound whenever a player crosses into a different
 * nation's claimed chunk.
 *
 *  ■ Entering OWN land   → aqua title  + pleasant chime
 *  ■ Entering ALLY land  → green title + celebratory sound
 *  ■ Entering ENEMY land → red title   + ominous wither sound
 *  ■ Entering unclaimed  → subtle grey action-bar (no sound)
 *
 *  Only fires on chunk boundary changes — not every block move.
 */
public class LandBorderListener implements Listener {

    private final NationsSMP plugin;
    // Track last chunk key per player to avoid spam
    private final Map<UUID, String> lastChunk = new ConcurrentHashMap<>();

    public LandBorderListener(NationsSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only care about chunk changes
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) return;

        Player player = event.getPlayer();
        UUID   uid    = player.getUniqueId();
        Chunk  chunk  = event.getTo().getChunk();
        String key    = chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();

        // Avoid duplicate fire on the same chunk
        if (key.equals(lastChunk.get(uid))) return;
        lastChunk.put(uid, key);

        String ownerUUID = plugin.getLandManager().getOwnerUUID(chunk);

        // Unclaimed
        if (ownerUUID == null) {
            player.sendActionBar(ChatColor.GRAY + "~ Unclaimed territory ~");
            return;
        }

        // Own land
        if (ownerUUID.equals(uid.toString())) {
            Nation own = plugin.getNationManager().getNation(uid);
            if (own == null) return;
            String label = NationAnimal.byKey(own.getAnimalKey()).getEmoji()
                + " " + own.getNationName();
            player.sendTitle(
                ChatColor.AQUA + "" + ChatColor.BOLD + label,
                ChatColor.GRAY + "Your territory",
                5, 40, 15);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME,
                0.6f, 1.2f);
            return;
        }

        // Find owner nation
        Nation ownerNation = plugin.getNationManager().getNationByUUID(ownerUUID);
        if (ownerNation == null) return;
        String label = NationAnimal.byKey(ownerNation.getAnimalKey()).getEmoji()
            + " " + ownerNation.getNationName();
        String subtitle = ownerNation.getTitle() + " of " + ownerNation.getNationName();

        // Check relationship
        Nation selfNation = plugin.getNationManager().getNation(uid);
        boolean isAlly = selfNation != null
            && selfNation.getAllyNationName() != null
            && selfNation.getAllyNationName().equalsIgnoreCase(ownerNation.getNationName());

        if (isAlly) {
            player.sendTitle(
                ChatColor.GREEN + "" + ChatColor.BOLD + label,
                ChatColor.GREEN + "⚑ Allied territory — " + subtitle,
                5, 60, 20);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE,
                0.7f, 1.0f);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_FLUTE,
                0.5f, 1.5f);
        } else {
            // Enemy
            player.sendTitle(
                ChatColor.RED + "" + ChatColor.BOLD + "⚠ " + label,
                ChatColor.RED + "ENEMY territory — " + subtitle,
                5, 80, 20);
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT,
                0.4f, 0.7f);
            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRIGGER,
                0.3f, 0.5f);
            // Extra warning in chat for enemies
            player.sendMessage(ChatColor.DARK_RED + "⚠ You have entered "
                + ChatColor.RED + ownerNation.getNationName()
                + ChatColor.DARK_RED + " territory. You are not welcome here.");
        }
    }
}
