package com.nationssmp.listeners;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.metadata.MetadataValue;

import java.util.UUID;

/**
 * Prevents army animals and companion animals from attacking each other when
 * their owners are in an oath (allied).
 *
 * Rule: if entity A attacks entity B, and both have an owner (via metadata
 * army_owner or companion_owner), and those owners are oath allies → cancel.
 *
 * Exception: if the two human players have ACTIVELY attacked each other
 * recently (tracked by OathManager.checkForBetrayal), the pacifism breaks.
 * This lets oath wars still use animals as weapons once betrayal triggers.
 */
public class OathAnimalPacifismListener implements Listener {

    private static final String META_ARMY      = "army_owner";
    private static final String META_COMPANION = "companion_owner";

    private final NationsSMP plugin;

    public OathAnimalPacifismListener(NationsSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity attacker = getRootAttacker(event.getDamager());
        Entity target   = event.getEntity();

        if (attacker == null || target == null) return;
        if (!(attacker instanceof LivingEntity) || !(target instanceof LivingEntity)) return;

        String attackerOwner = getOwnerUUID(attacker);
        String targetOwner   = getOwnerUUID(target);

        if (attackerOwner == null || targetOwner == null) return;
        if (attackerOwner.equals(targetOwner)) return; // same owner — allow (e.g. wolf turned hostile)

        // Look up nation relationship
        Nation attackerNation = plugin.getNationManager().getNationByUUID(attackerOwner);
        Nation targetNation   = plugin.getNationManager().getNationByUUID(targetOwner);
        if (attackerNation == null || targetNation == null) return;

        // Check mutual oath
        boolean isAlly = attackerNation.getAllyNationName() != null
            && attackerNation.getAllyNationName()
                   .equalsIgnoreCase(targetNation.getNationName())
            && targetNation.getAllyNationName() != null
            && targetNation.getAllyNationName()
                   .equalsIgnoreCase(attackerNation.getNationName());

        if (!isAlly) return;

        // Check if these two owners have attacked each other (betrayal triggered)
        // If so, let the animals fight
        if (plugin.getOathManager().hasBetrayed(attackerOwner)) return;

        // Cancel the damage — allies' animals stand down
        event.setCancelled(true);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** For projectiles, return the shooter entity. Otherwise return the entity itself. */
    private Entity getRootAttacker(Entity e) {
        if (e instanceof Projectile proj && proj.getShooter() instanceof Entity shooter)
            return shooter;
        return e;
    }

    /** Returns the owner UUID string from metadata, or null if the entity has none. */
    private String getOwnerUUID(Entity entity) {
        // Army animal
        if (entity.hasMetadata(META_ARMY)) {
            for (MetadataValue v : entity.getMetadata(META_ARMY))
                if (v.value() instanceof String s) return s;
        }
        // Companion
        if (entity.hasMetadata(META_COMPANION)) {
            for (MetadataValue v : entity.getMetadata(META_COMPANION))
                if (v.value() instanceof String s) return s;
        }
        // Vanilla tamed animal (Wolf, Cat, Horse, Parrot)
        if (entity instanceof Tameable t && t.isTamed() && t.getOwner() instanceof Player p)
            return p.getUniqueId().toString();
        return null;
    }
}
