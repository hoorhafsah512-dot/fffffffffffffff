package com.nationssmp.managers;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import com.nationssmp.data.NationAnimal;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawns and manages 50 nation animals per player.
 * Replaces the old ZombieBot army — pure vanilla entities, no Citizens dependency.
 *
 * On first login completion → 50 animals spawn.
 * On quit / death             → army despawns.
 * On rejoin                   → army respawns.
 * Oathbreaker punishment      → half the army is killed.
 */
public class AnimalArmyManager {

    private static final String META_ARMY_OWNER = "army_owner";
    private static final String META_IS_ARMY    = "is_nations_army";
    public  static final int    ARMY_SIZE       = 50;

    private final NationsSMP    plugin;
    private final NationManager nationManager;

    /** player uuid → live entity UUID list */
    private final Map<String, List<UUID>> playerArmies = new ConcurrentHashMap<>();
    private BukkitTask aiTask;

    public AnimalArmyManager(NationsSMP plugin, NationManager nationManager) {
        this.plugin        = plugin;
        this.nationManager = nationManager;
        startAILoop();
    }

    // ── Spawn / Despawn ───────────────────────────────────────────────────────

    public void spawnArmyForPlayer(Player player, Nation nation) {
        despawnArmyForPlayer(player.getUniqueId());
        if (nation.getAnimalKey() == null) return;

        NationAnimal animal = NationAnimal.byKey(nation.getAnimalKey());
        EntityType   type   = getSafeArmyType(animal);
        List<UUID>   ids    = new ArrayList<>();
        Location     base   = player.getLocation();

        for (int i = 0; i < ARMY_SIZE; i++) {
            Location loc = base.clone().add(
                (Math.random() - 0.5) * 8,
                0,
                (Math.random() - 0.5) * 8);
            try {
                LivingEntity entity = (LivingEntity) player.getWorld().spawnEntity(loc, type);
                configureEntity(entity, player, nation, animal);
                ids.add(entity.getUniqueId());
            } catch (Exception e) {
                plugin.getLogger().warning("Could not spawn army entity for "
                    + player.getName() + ": " + e.getMessage());
            }
        }

        playerArmies.put(player.getUniqueId().toString(), ids);
        player.sendMessage(ChatColor.GREEN + animal.getEmoji() + " " + ids.size()
            + " " + animal.getDisplayName() + "s have rallied to your banner!");
    }

    private void configureEntity(LivingEntity entity, Player player,
                                  Nation nation, NationAnimal animal) {
        entity.setCustomName(ChatColor.YELLOW + animal.getEmoji()
            + " " + nation.getNationName());
        entity.setCustomNameVisible(true);
        entity.setRemoveWhenFarAway(false);
        entity.setMetadata(META_IS_ARMY,    new FixedMetadataValue(plugin, true));
        entity.setMetadata(META_ARMY_OWNER, new FixedMetadataValue(plugin,
            player.getUniqueId().toString()));

        // Tame tameable entities so they don't attack their own master
        if (entity instanceof Wolf wolf) {
            wolf.setTamed(true);
            wolf.setOwner(player);
        } else if (entity instanceof Cat cat) {
            cat.setTamed(true);
            cat.setOwner(player);
        } else if (entity instanceof Horse horse) {
            horse.setTamed(true);
        }

        // Give each animal a solid HP pool
        double hp = plugin.getConfig().getDouble("army-animal-health", 40.0);
        try {
            var attr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (attr != null) {
                attr.setBaseValue(hp);
                entity.setHealth(hp);
            }
        } catch (Exception ignored) {}
    }

    public void despawnArmyForPlayer(UUID playerUUID) {
        List<UUID> ids = playerArmies.remove(playerUUID.toString());
        if (ids == null) return;
        for (UUID id : ids) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
    }

    /** Kill half the army — oathbreaker punishment. Returns count killed. */
    public int killHalfArmy(Player player) {
        List<UUID> ids = playerArmies.getOrDefault(
            player.getUniqueId().toString(), new ArrayList<>());
        int half   = Math.max(1, ids.size() / 2);
        int killed = 0;
        Iterator<UUID> it = ids.iterator();
        while (it.hasNext() && killed < half) {
            Entity e = Bukkit.getEntity(it.next());
            if (e != null) e.remove();
            it.remove();
            killed++;
        }
        return killed;
    }

    public void respawnArmy(Player player) {
        Nation n = nationManager.getNation(player.getUniqueId());
        if (n == null || !n.isSetupComplete()) return;
        Bukkit.getScheduler().runTaskLater(plugin,
            () -> spawnArmyForPlayer(player, n), 40L);
    }

    public int getArmySize(Player player) {
        return playerArmies
            .getOrDefault(player.getUniqueId().toString(), Collections.emptyList())
            .size();
    }

    public boolean isArmyAnimal(Entity entity) {
        return entity.hasMetadata(META_IS_ARMY);
    }

    // ── AI Loop ───────────────────────────────────────────────────────────────

    private void startAILoop() {
        int ticks = plugin.getConfig().getInt("army-ai-tick", 20);

        aiTask = new BukkitRunnable() {
            @Override
            public void run() {
                double followDist   = plugin.getConfig().getDouble("army-follow-distance", 60);
                double combatRange  = plugin.getConfig().getDouble("army-combat-range",    8);
                double damage       = plugin.getConfig().getDouble("army-damage",           5.0);

                for (Map.Entry<String, List<UUID>> entry : playerArmies.entrySet()) {
                    Player master;
                    try {
                        master = Bukkit.getPlayer(UUID.fromString(entry.getKey()));
                    } catch (Exception e) { continue; }
                    if (master == null || !master.isOnline()) continue;

                    Iterator<UUID> it = entry.getValue().iterator();
                    while (it.hasNext()) {
                        Entity entity = Bukkit.getEntity(it.next());
                        if (entity == null || !entity.isValid() || entity.isDead()) {
                            it.remove();
                            continue;
                        }
                        if (!(entity instanceof LivingEntity animal)) continue;

                        // Follow master
                        double dist = animal.getLocation().distance(master.getLocation());
                        if (dist > followDist) {
                            animal.teleport(master.getLocation().clone().add(
                                (Math.random() - 0.5) * 4, 0, (Math.random() - 0.5) * 4));
                        } else if (dist > 4 && animal instanceof Mob mob) {
                            mob.getPathfinder().moveTo(master);
                        }

                        // Attack nearby monsters
                        for (Entity nearby : animal.getNearbyEntities(
                                combatRange, combatRange, combatRange)) {
                            if (nearby instanceof Monster || nearby instanceof Slime) {
                                if (animal instanceof Mob mob)
                                    mob.getPathfinder().moveTo((LivingEntity) nearby);
                                ((LivingEntity) nearby).damage(damage, animal);
                                break;
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, ticks);
    }

    public void shutdown() {
        if (aiTask != null) aiTask.cancel();
    }

    // ── Safe Entity Mapping ───────────────────────────────────────────────────

    /**
     * Maps each NationAnimal to a safe overworld-spawnable entity.
     * Water mobs fall back to land alternatives.
     */
    private EntityType getSafeArmyType(NationAnimal animal) {
        return switch (animal) {
            case DOLPHIN  -> EntityType.FOX;    // needs water
            case TURTLE   -> EntityType.RABBIT; // slow + needs beach
            case AXOLOTL  -> EntityType.CAT;    // needs water
            case BAT      -> EntityType.CAT;    // tiny + passive
            default       -> animal.getEntityType();
        };
    }
}
