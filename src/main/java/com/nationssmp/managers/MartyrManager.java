package com.nationssmp.managers;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import com.nationssmp.data.NationAnimal;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.ArmorStand;
import org.bukkit.DyeColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds a high-quality Winterfell-style grave at the martyr's death location.
 *
 *  Structure (7 blocks tall, 3 wide):
 *
 *    Y+6  [Nation Banner on wall]
 *    Y+5  [ArmorStand — full armour, sword in hand, name floating above]
 *    Y+4  [ArmorStand head level]
 *    Y+3  [Chiselled Stone Bricks — base plinth top]
 *    Y+2  [Chiselled Stone Bricks — base plinth mid]
 *    Y+1  [Soul Lantern + Sign (north) + Sign (south)]
 *    Y+0  [Polished Blackstone — grave pad 3×3]
 *
 *  • ArmorStand wears the dead player's saved armour (decorative, NOT lootable)
 *  • Sword item in hand — lootable ONLY by the owner (if respawned) or oath ally
 *  • Signs show: name | title | nation | reason of death | motto
 *  • Nation banner placed on the wall behind the stand
 *  • Grave location broadcast in chat with coords
 *  • All blocks indestructible
 */
public class MartyrManager {

    private final NationsSMP  plugin;
    private final NationManager nationManager;

    private final Set<String>  graveBlocks   = ConcurrentHashMap.newKeySet();
    /** armorStandEntityUUID → ownerUUID (for sword-loot restriction) */
    private final Map<String, String> graveStandOwner = new ConcurrentHashMap<>();
    /** armorStandEntityUUID → allyNationName (for sword-loot permission) */
    private final Map<String, String> graveStandAlly  = new ConcurrentHashMap<>();

    public MartyrManager(NationsSMP plugin, NationManager nationManager) {
        this.plugin        = plugin;
        this.nationManager = nationManager;
        load();
    }

    // ── Build grave ───────────────────────────────────────────────────────────

    public void buildMartyrGrave(Player dead, Player killer, Nation deadNation) {
        World    world   = dead.getWorld();
        Location deathLoc = dead.getLocation();
        Location base    = findGround(deathLoc);

        // ── 1. 3×3 blackstone pad ────────────────────────────────────────────
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                placeGrave(world, base.getBlockX() + dx, base.getBlockY(), base.getBlockZ() + dz,
                    Material.POLISHED_BLACKSTONE);

        // ── 2. Two-block chiselled stone plinth at centre ────────────────────
        placeGrave(world, base.getBlockX(), base.getBlockY() + 1, base.getBlockZ(),
            Material.CHISELED_STONE_BRICKS);
        placeGrave(world, base.getBlockX(), base.getBlockY() + 2, base.getBlockZ(),
            Material.CHISELED_STONE_BRICKS);

        // ── 3. Soul lantern on top of plinth ────────────────────────────────
        placeGrave(world, base.getBlockX(), base.getBlockY() + 3, base.getBlockZ(),
            Material.SOUL_LANTERN);

        // ── 4. Decorative corner candles ────────────────────────────────────
        for (int[] off : new int[][]{{-1,0,-1},{1,0,-1},{-1,0,1},{1,0,1}})
            placeGrave(world, base.getBlockX()+off[0], base.getBlockY()+1, base.getBlockZ()+off[2],
                Material.CANDLE);

        // ── 5. Nation banner behind the stand (on stone brick wall) ─────────
        placeGrave(world, base.getBlockX(), base.getBlockY() + 1, base.getBlockZ() - 1,
            Material.STONE_BRICKS);
        placeGrave(world, base.getBlockX(), base.getBlockY() + 2, base.getBlockZ() - 1,
            Material.STONE_BRICKS);
        Block bannerBlock = world.getBlockAt(base.getBlockX(), base.getBlockY() + 3, base.getBlockZ() - 1);
        placeBannerOnWall(bannerBlock, deadNation);
        graveBlocks.add(blockKey(bannerBlock));

        // ── 6. Signs (front + back of plinth) ───────────────────────────────
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            placeSign(world, base.getBlockX(), base.getBlockY() + 3, base.getBlockZ() + 1,
                dead, killer, deadNation, true);   // south-facing (front)
            placeSign(world, base.getBlockX(), base.getBlockY() + 3, base.getBlockZ() - 2,
                dead, killer, deadNation, false);  // north-facing (back)
        }, 2L);

        // ── 7. ArmorStand — full armour + sword ─────────────────────────────
        ItemStack[] savedArmour = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            ItemStack piece = dead.getInventory().getArmorContents()[i];
            savedArmour[i] = (piece != null && piece.getType() != Material.AIR)
                ? piece.clone() : defaultArmour(i);
        }
        // Grave sword — iron sword with lore
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        ItemMeta sm = sword.getItemMeta();
        if (sm != null) {
            sm.setDisplayName(ChatColor.DARK_GRAY + dead.getName() + "'s Blade");
            sm.setLore(List.of(
                ChatColor.GRAY + "Resting with " + dead.getName(),
                ChatColor.DARK_GRAY + "Only their ally or themselves may claim this."));
            sword.setItemMeta(sm);
        }

        Location standLoc = base.clone().add(0.5, 1.0, 0.5);
        final String[] standUUID = new String[1];
        ItemStack[] finalArmour = savedArmour;

        world.spawn(standLoc, ArmorStand.class, stand -> {
            stand.setCustomName(ChatColor.GOLD + "✦ " + dead.getName()
                + " ✦  " + ChatColor.GRAY + deadNation.getTitle());
            stand.setCustomNameVisible(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setVisible(true);
            stand.setArms(true);
            // Equipment
            var eq = stand.getEquipment();
            eq.setBoots(finalArmour[0]);
            eq.setLeggings(finalArmour[1]);
            eq.setChestplate(finalArmour[2]);
            eq.setHelmet(finalArmour[3]);
            eq.setItemInMainHand(sword);
            // Equipment protected via PlayerInteractAtEntityEvent in BlockListener
            // Sword slot left unlocked — restricted in InventoryClickListener by owner/ally check
            standUUID[0] = stand.getUniqueId().toString();
        });

        // Register stand for sword-loot restriction AFTER spawn (lambda delay)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (standUUID[0] != null) {
                graveStandOwner.put(standUUID[0], dead.getUniqueId().toString());
                String allyName = deadNation.getAllyNationName();
                if (allyName != null) graveStandAlly.put(standUUID[0], allyName.toLowerCase());
            }
        }, 3L);

        save();

        // ── 8. Broadcast with coordinates ───────────────────────────────────
        NationAnimal animal = NationAnimal.byKey(deadNation.getAnimalKey());
        String reason = getDeathReason(dead, killer);
        int bx = base.getBlockX(), by = base.getBlockY(), bz = base.getBlockZ();

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "✦══════════════════════════✦");
        Bukkit.broadcastMessage("  " + animal.getEmoji() + " " + ChatColor.WHITE
            + dead.getName() + " " + ChatColor.YELLOW + deadNation.getTitle());
        Bukkit.broadcastMessage("  " + ChatColor.GRAY + "of " + ChatColor.WHITE
            + deadNation.getNationName() + ChatColor.GRAY + " — " + ChatColor.RED + "MARTYR");
        Bukkit.broadcastMessage("  " + ChatColor.DARK_GRAY + "\"" + deadNation.getMotto() + "\"");
        Bukkit.broadcastMessage("  " + ChatColor.GRAY + "Cause: " + ChatColor.RED + reason);
        Bukkit.broadcastMessage("  " + ChatColor.DARK_GRAY + "Grave: "
            + ChatColor.WHITE + bx + ", " + by + ", " + bz
            + ChatColor.DARK_GRAY + " (" + dead.getWorld().getName() + ")");
        Bukkit.broadcastMessage(ChatColor.GOLD + "✦══════════════════════════✦");
        Bukkit.broadcastMessage("");
    }

    // ── Sword loot check ─────────────────────────────────────────────────────

    /**
     * Called from InventoryClickListener when a player tries to take from an ArmorStand.
     * Returns true (and cancels) if the player is NOT allowed to take the sword.
     */
    public boolean isSwordLootRestricted(String standUUID, String looterUUID) {
        String ownerUUID = graveStandOwner.get(standUUID);
        if (ownerUUID == null) return false; // not a grave stand
        if (ownerUUID.equals(looterUUID)) return false; // owner can take it
        // Check oath ally
        String allyName = graveStandAlly.get(standUUID);
        if (allyName != null) {
            Nation looterNation = nationManager.getNation(UUID.fromString(looterUUID));
            if (looterNation != null
                    && allyName.equalsIgnoreCase(looterNation.getNationName()))
                return false; // oath ally can take it
        }
        return true; // everyone else is blocked
    }

    public boolean isGraveStand(String entityUUID) {
        return graveStandOwner.containsKey(entityUUID);
    }

    // ── Protection ────────────────────────────────────────────────────────────

    public boolean isGraveBlock(Block block) {
        return graveBlocks.contains(blockKey(block));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private void placeBannerOnWall(Block block, Nation nation) {
        block.setType(Material.WHITE_WALL_BANNER, false);
        BlockState state = block.getState();
        if (state instanceof Banner banner) {
            ItemStack bannerItem = nation.buildBannerItem();
            if (bannerItem.hasItemMeta() && bannerItem.getItemMeta() instanceof BannerMeta meta) {
                banner.setPatterns(meta.getPatterns());
                DyeColor base = dyeColorFromBannerMaterial(bannerItem.getType());
                banner.setBaseColor(base);
            }
            banner.update(true, false);
        }
    }

    private void placeSign(World world, int x, int y, int z,
                            Player dead, Player killer, Nation deadNation, boolean front) {
        Block signBlock = world.getBlockAt(x, y, z);
        signBlock.setType(Material.OAK_WALL_SIGN, false);
        graveBlocks.add(blockKey(signBlock));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!(signBlock.getState() instanceof Sign sign)) return;
            var side = sign.getSide(front ? Side.FRONT : Side.BACK);
            if (front) {
                side.setLine(0, ChatColor.DARK_RED + "" + ChatColor.BOLD + "✦ MARTYR ✦");
                side.setLine(1, ChatColor.WHITE + dead.getName());
                side.setLine(2, ChatColor.YELLOW + deadNation.getTitle());
                side.setLine(3, ChatColor.GRAY + deadNation.getNationName());
            } else {
                String reason = getDeathReason(dead, killer);
                side.setLine(0, ChatColor.DARK_GRAY + "Fell to:");
                side.setLine(1, ChatColor.RED + reason);
                side.setLine(2, ChatColor.DARK_GRAY + "\"" + truncate(deadNation.getMotto(), 14) + "\"");
                side.setLine(3, ChatColor.GRAY + deadNation.getAnimalKey() != null
                    ? NationAnimal.byKey(deadNation.getAnimalKey()).getEmoji() : "");
            }
            sign.update(true);
        }, 3L);
    }

    private void placeGrave(World world, int x, int y, int z, Material mat) {
        Block b = world.getBlockAt(x, y, z);
        b.setType(mat, false);
        graveBlocks.add(blockKey(b));
    }

    private ItemStack defaultArmour(int slot) {
        return switch (slot) {
            case 0  -> new ItemStack(Material.IRON_BOOTS);
            case 1  -> new ItemStack(Material.IRON_LEGGINGS);
            case 2  -> new ItemStack(Material.IRON_CHESTPLATE);
            default -> new ItemStack(Material.IRON_HELMET);
        };
    }

    private String getDeathReason(Player dead, Player killer) {
        if (killer != null) {
            Nation kn = nationManager.getNation(killer.getUniqueId());
            String kNation = kn != null ? kn.getNationName() : "";
            return killer.getName() + " of " + kNation;
        }
        var dmg = dead.getLastDamageCause();
        if (dmg != null) {
            return switch (dmg.getCause()) {
                case FALL           -> "Fell to their death";
                case FIRE, FIRE_TICK-> "Consumed by fire";
                case DROWNING       -> "Drowned";
                case STARVATION     -> "Starved";
                case POISON         -> "Poisoned";
                case WITHER         -> "Withered away";
                case ENTITY_EXPLOSION      -> "Blown up";
                case LIGHTNING      -> "Struck by lightning";
                case VOID           -> "Lost to the void";
                default             -> "Unknown cause";
            };
        }
        return "Unknown cause";
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private Location findGround(Location loc) {
        World world = loc.getWorld();
        if (world == null) return loc;
        for (int y = loc.getBlockY(); y > 0; y--) {
            Block b = world.getBlockAt(loc.getBlockX(), y, loc.getBlockZ());
            if (b.getType().isSolid() && b.getType() != Material.BEDROCK) {
                return new Location(world, loc.getX(), y + 1, loc.getZ(),
                    loc.getYaw(), loc.getPitch());
            }
        }
        return loc;
    }

    private String blockKey(Block b) {
        return b.getWorld().getName()+":"+b.getX()+":"+b.getY()+":"+b.getZ();
    }

    /** Extracts DyeColor from a banner Material (WHITE_BANNER -> WHITE, etc.) */
    private static DyeColor dyeColorFromBannerMaterial(Material mat) {
        String name = mat.name().replace("_WALL_BANNER","").replace("_BANNER","");
        try { return DyeColor.valueOf(name); } catch (Exception e) { return DyeColor.WHITE; }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void load() {
        var cfg = plugin.getDataManager().getLegendaryConfig();
        graveBlocks.addAll(cfg.getStringList("graves.blocks"));
        var so = cfg.getConfigurationSection("graves.standOwners");
        if (so != null) for (String k : so.getKeys(false)) {
            graveStandOwner.put(k, so.getString(k));
        }
        var sa = cfg.getConfigurationSection("graves.standAllies");
        if (sa != null) for (String k : sa.getKeys(false)) {
            graveStandAlly.put(k, sa.getString(k));
        }
    }

    private void save() {
        var cfg = plugin.getDataManager().getLegendaryConfig();
        cfg.set("graves.blocks", new ArrayList<>(graveBlocks));
        graveStandOwner.forEach((k,v) -> cfg.set("graves.standOwners."+k, v));
        graveStandAlly .forEach((k,v) -> cfg.set("graves.standAllies."+k, v));
        plugin.getDataManager().saveLegendary();
    }
}
