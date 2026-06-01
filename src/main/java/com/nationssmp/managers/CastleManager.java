package com.nationssmp.managers;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import com.nationssmp.data.NationAnimal;
import org.bukkit.*;
import org.bukkit.DyeColor;
import org.bukkit.block.*;
import org.bukkit.block.banner.Pattern;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds a Winterfell-style castle for each nation when setup completes.
 *
 *  Structure (72×72 footprint, centered on player):
 *   • Outer curtain wall + crenellations
 *   • 4 corner towers (7×7, 14 high)
 *   • South gatehouse with iron portcullis
 *   • Great Hall  (north inner, oak-plank interior)
 *   • Throne Room (purpur floor, pillar throne, carpet path) — SPAWN POINT
 *   • Barracks     (east)
 *   • Stable       (west, with hay & fences)
 *   • Treasury     (NE, gold-block floor ring)
 *   • Trophy Hall  (NW)
 *   • Dragon Landing Courtyard (center, obsidian ring + magma X)
 *   • 4 inner banner towers (4×4, 20 high) — nation banners on top
 *
 *  Castle blocks are protected from enemy breaks as long as the chunk
 *  is still owned by the original nation.
 */
public class CastleManager {

    private static final int HALF = 36;       // castle extends ±36 from center
    private static final int BATCH = 500;     // blocks placed per tick

    private final NationsSMP plugin;

    // uuid → [worldName, cx, baseY, cz]  (for bounding-box protection check)
    private final Map<String, String[]> castleOrigins = new ConcurrentHashMap<>();
    // uuid → list of banner-tower top locations (to re-apply banners)
    private final Map<String, List<int[]>> towerTops = new ConcurrentHashMap<>();

    public CastleManager(NationsSMP plugin) {
        this.plugin = plugin;
        loadOrigins();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void buildCastleForNation(Player player, Nation nation) {
        World world = player.getWorld();
        int cx    = player.getLocation().getBlockX();
        int cz    = player.getLocation().getBlockZ();
        int baseY = world.getHighestBlockYAt(cx, cz);

        // Throne room spawn: inside north section, centred, elevated +3
        Location throne = new Location(world, cx + 0.5, baseY + 4, cz - 23.5, 180f, 0f);
        nation.setSpawnLocation(throne);

        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + ""
            + "🏰 " + nation.getNationName().toUpperCase()
            + "'s Winterfell Capital is rising!");
        player.sendMessage(ChatColor.GOLD + "⚔ Construction begins — stand back!");

        List<int[]> queue = generateBlocks(cx, baseY, cz);

        new BukkitRunnable() {
            private int idx = 0;

            @Override
            public void run() {
                int end = Math.min(idx + BATCH, queue.size());
                while (idx < end) {
                    int[] r = queue.get(idx++);
                    world.getBlockAt(r[0], r[1], r[2]).setType(Material.values()[r[3]], false);
                }
                if (idx >= queue.size()) {
                    // Store origin for protection
                    castleOrigins.put(nation.getPlayerUUID(),
                        new String[]{world.getName(),
                            String.valueOf(cx), String.valueOf(baseY), String.valueOf(cz)});
                    saveOrigins();

                    // Place banners on inner towers
                    placeBannersOnTowers(world, cx, baseY, cz, nation);

                    // Claim all chunks in footprint
                    claimCastleChunks(world, cx, cz, player, nation);

                    Bukkit.broadcastMessage(ChatColor.GOLD
                        + "🏰 " + nation.getNationName() + "'s capital stands!");
                    player.sendMessage(ChatColor.GREEN
                        + "✦ Welcome home, " + NationAnimal.byKey(nation.getAnimalKey()).getEmoji()
                        + " " + nation.getNationName() + "!");
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 5L, 1L);
    }

    /** Re-apply the nation's custom banner to all four inner towers. */
    public void refreshBanners(World world, Nation nation) {
        List<int[]> tops = towerTops.get(nation.getPlayerUUID());
        if (tops == null) return;
        ItemStack banner = nation.buildBannerItem();
        for (int[] t : tops) placeBannerAt(world.getBlockAt(t[0], t[1], t[2]), banner);
    }

    /** Returns true if this block is part of a castle that the given uuid owns,
     *  AND the chunk is still claimed by that uuid (so conquered castles can be raided). */
    public boolean isProtectedCastleBlock(Block block, String attackerUUID) {
        for (Map.Entry<String, String[]> e : castleOrigins.entrySet()) {
            String ownerUUID = e.getKey();
            if (ownerUUID.equals(attackerUUID)) continue; // own castle
            String[] o = e.getValue();
            if (!block.getWorld().getName().equals(o[0])) continue;
            int cx = Integer.parseInt(o[1]), by = Integer.parseInt(o[2]), cz = Integer.parseInt(o[3]);
            if (Math.abs(block.getX() - cx) <= HALF + 4
                    && block.getY() >= by
                    && block.getY() <= by + 25
                    && Math.abs(block.getZ() - cz) <= HALF + 4) {
                // Protected only while owner still holds the chunk
                try {
                    UUID uid = UUID.fromString(ownerUUID);
                    return plugin.getLandManager().isOwnedBy(block.getChunk(), uid);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return false;
    }

    // ── Block generation ──────────────────────────────────────────────────────

    private List<int[]> generateBlocks(int cx, int y, int cz) {
        List<int[]> q = new ArrayList<>(16000);

        // 1. Courtyard stone floor
        floor(q, cx - 33, y, cz - 33, cx + 33, cz + 33, Material.STONE_BRICKS);
        // Gravel path from gate to Great Hall
        for (int bz = cz + 32; bz >= cz - 12; bz--)
            for (int bx = cx - 2; bx <= cx + 2; bx++)
                add(q, bx, y, bz, Material.GRAVEL);

        // 2. Outer curtain walls (2 thick, 9 high)
        box(q, cx - 35, y + 1, cz - 35,  cx + 35, y + 9,  cz - 34, Material.STONE_BRICKS); // N
        box(q, cx - 35, y + 1, cz + 34,  cx + 35, y + 9,  cz + 35, Material.STONE_BRICKS); // S
        box(q, cx - 35, y + 1, cz - 35,  cx - 34, y + 9,  cz + 35, Material.STONE_BRICKS); // W
        box(q, cx + 34, y + 1, cz - 35,  cx + 35, y + 9,  cz + 35, Material.STONE_BRICKS); // E

        // Wall battlements
        for (int bx = cx - 35; bx <= cx + 35; bx += 3) {
            add(q, bx, y + 10, cz - 35, Material.STONE_BRICK_WALL);
            add(q, bx, y + 10, cz + 35, Material.STONE_BRICK_WALL);
        }
        for (int bz = cz - 35; bz <= cz + 35; bz += 3) {
            add(q, cx - 35, y + 10, bz, Material.STONE_BRICK_WALL);
            add(q, cx + 35, y + 10, bz, Material.STONE_BRICK_WALL);
        }

        // 3. Corner towers (7×7, 14 high + battlements)
        cornerTower(q, cx - 38, y, cz - 38); // NW
        cornerTower(q, cx + 32, y, cz - 38); // NE
        cornerTower(q, cx - 38, y, cz + 32); // SW
        cornerTower(q, cx + 32, y, cz + 32); // SE

        // 4. South Gatehouse
        gatehouse(q, cx, y, cz);

        // 5. Great Hall (north-centre, 37×21 exterior, oak interior)
        greatHall(q, cx, y, cz);

        // 6. Throne Room (inside Great Hall north end, purpur elevated)
        throneRoom(q, cx, y, cz);

        // 7. Barracks — east wing
        genericBuilding(q, cx + 12, y, cz - 10,  cx + 32, y + 8,  cz + 10,
                        Material.STONE_BRICKS, Material.DARK_OAK_PLANKS);
        // Barracks entrance south wall
        air(q, cx + 22, y + 1, cz + 10); air(q, cx + 22, y + 2, cz + 10);

        // 8. Stable — west wing
        stable(q, cx, y, cz);

        // 9. Treasury — NE inner
        treasury(q, cx, y, cz);

        // 10. Trophy Hall — NW inner
        genericBuilding(q, cx - 32, y, cz - 32,  cx - 12, y + 8,  cz - 14,
                        Material.STONE_BRICKS, Material.OAK_PLANKS);
        air(q, cx - 22, y + 1, cz - 14); air(q, cx - 22, y + 2, cz - 14);

        // 11. Dragon Landing Courtyard — centre
        dragonCourtyard(q, cx, y, cz);

        // 12. Inner banner towers (4×4, 20 high)
        bannerTower(q, cx - 24, y, cz - 24);
        bannerTower(q, cx + 21, y, cz - 24);
        bannerTower(q, cx - 24, y, cz + 21);
        bannerTower(q, cx + 21, y, cz + 21);

        return q;
    }

    // ── Individual structure builders ─────────────────────────────────────────

    private void cornerTower(List<int[]> q, int ox, int y, int oz) {
        box(q, ox, y, oz,  ox + 6, y + 14, oz + 6, Material.STONE_BRICKS);
        fill(q, ox + 1, y + 1, oz + 1,  ox + 5, y + 13, oz + 5, Material.AIR);
        // Battlement
        for (int i = 0; i <= 6; i += 2) {
            add(q, ox + i, y + 15, oz,      Material.STONE_BRICK_WALL);
            add(q, ox + i, y + 15, oz + 6,  Material.STONE_BRICK_WALL);
            add(q, ox,     y + 15, oz + i,  Material.STONE_BRICK_WALL);
            add(q, ox + 6, y + 15, oz + i,  Material.STONE_BRICK_WALL);
        }
        // Torch on top
        add(q, ox + 3, y + 15, oz + 3, Material.TORCH);
    }

    private void gatehouse(List<int[]> q, int cx, int y, int cz) {
        int oz = cz + 33;
        box(q, cx - 6, y + 1, oz,  cx + 6, y + 13, oz + 5, Material.STONE_BRICKS);
        // Gate tunnel (AIR)
        fill(q, cx - 3, y + 1, oz - 1,  cx + 3, y + 5, oz + 5, Material.AIR);
        // Iron portcullis
        for (int bx = cx - 3; bx <= cx + 3; bx++)
            for (int by = y + 1; by <= y + 5; by++)
                add(q, bx, by, oz + 1, Material.IRON_BARS);
        // Crenellations
        for (int bx = cx - 6; bx <= cx + 6; bx += 2)
            add(q, bx, y + 14, oz + 2, Material.STONE_BRICK_WALL);
        // Flanking torches
        add(q, cx - 4, y + 1, oz, Material.WALL_TORCH);
        add(q, cx + 4, y + 1, oz, Material.WALL_TORCH);
    }

    private void greatHall(List<int[]> q, int cx, int y, int cz) {
        int x1 = cx - 18, x2 = cx + 18, z1 = cz - 33, z2 = cz - 13;
        // Exterior walls 2 thick
        box(q, x1, y + 1, z1,  x2, y + 10, z1 + 1, Material.STONE_BRICKS);
        box(q, x1, y + 1, z2 - 1, x2, y + 10, z2, Material.STONE_BRICKS);
        box(q, x1, y + 1, z1,  x1 + 1, y + 10, z2, Material.STONE_BRICKS);
        box(q, x2 - 1, y + 1, z1, x2, y + 10, z2, Material.STONE_BRICKS);
        // Interior oak floor
        floor(q, x1 + 2, y, z1 + 2, x2 - 2, z2 - 2, Material.OAK_PLANKS);
        // Columns
        for (int bz = z1 + 4; bz < z2 - 2; bz += 5) {
            for (int by = y + 1; by <= y + 9; by++) {
                add(q, x1 + 3, by, bz, Material.STONE_BRICKS);
                add(q, x2 - 3, by, bz, Material.STONE_BRICKS);
            }
        }
        // Windows
        for (int bz = z1 + 5; bz < z2 - 2; bz += 5) {
            add(q, x1, y + 5, bz, Material.GLASS_PANE);
            add(q, x2, y + 5, bz, Material.GLASS_PANE);
        }
        // Slab roof
        floor(q, x1, y + 11, z1, x2, z2, Material.STONE_BRICK_SLAB);
        // Central fireplace
        add(q, cx,     y + 1, z2 - 3, Material.BRICKS);
        add(q, cx,     y + 2, z2 - 3, Material.BRICKS);
        add(q, cx,     y + 3, z2 - 3, Material.CAMPFIRE);
        // South entrance opening (faces courtyard)
        air(q, cx - 1, y + 1, z2); air(q, cx, y + 1, z2); air(q, cx + 1, y + 1, z2);
        air(q, cx - 1, y + 2, z2); air(q, cx, y + 2, z2); air(q, cx + 1, y + 2, z2);
        air(q, cx - 1, y + 3, z2); air(q, cx, y + 3, z2); air(q, cx + 1, y + 3, z2);
    }

    private void throneRoom(List<int[]> q, int cx, int y, int cz) {
        int x1 = cx - 7, x2 = cx + 7, z1 = cz - 32, z2 = cz - 22;
        // Elevated purpur floor (+2)
        floor(q, x1, y + 2, z1, x2, z2, Material.PURPUR_BLOCK);
        // Steps up from Great Hall floor
        for (int bx = x1; bx <= x2; bx++) {
            add(q, bx, y + 1, z2 + 1, Material.STONE_SLAB);
            add(q, bx, y + 2, z2,     Material.STONE_SLAB);
        }
        // Throne: iron block seat + chiselled back + slab armrests
        add(q, cx,     y + 3, z1 + 3, Material.IRON_BLOCK);
        add(q, cx,     y + 4, z1 + 2, Material.CHISELED_STONE_BRICKS);
        add(q, cx - 1, y + 3, z1 + 3, Material.STONE_SLAB);
        add(q, cx + 1, y + 3, z1 + 3, Material.STONE_SLAB);
        // Carpet path from steps to throne
        for (int bz = z1 + 4; bz <= z2 - 1; bz++)
            add(q, cx, y + 3, bz, Material.PURPLE_CARPET);
        // Pillars flanking throne
        for (int by = y + 3; by <= y + 9; by++) {
            add(q, x1 + 1, by, z1 + 1, Material.PURPUR_PILLAR);
            add(q, x2 - 1, by, z1 + 1, Material.PURPUR_PILLAR);
        }
        // Soul torches
        add(q, x1 + 1, y + 10, z1 + 2, Material.SOUL_TORCH);
        add(q, x2 - 1, y + 10, z1 + 2, Material.SOUL_TORCH);
    }

    private void stable(List<int[]> q, int cx, int y, int cz) {
        int x1 = cx - 32, x2 = cx - 12, z1 = cz - 10, z2 = cz + 10;
        wallsOnly(q, x1, y + 1, z1, x2, y + 7, z2, Material.OAK_LOG);
        floor(q, x1 + 1, y, z1 + 1, x2 - 1, z2 - 1, Material.DIRT);
        // Hay bales + fence stalls
        add(q, x1 + 2, y + 1, z1 + 2, Material.HAY_BLOCK);
        add(q, x1 + 2, y + 2, z1 + 2, Material.HAY_BLOCK);
        add(q, x1 + 2, y + 1, z1 + 3, Material.HAY_BLOCK);
        for (int bz = z1 + 3; bz <= z2 - 3; bz += 4)
            for (int bx = x1 + 4; bx <= x2 - 4; bx += 4)
                add(q, bx, y + 1, bz, Material.OAK_FENCE);
        // Slab roof + entrance
        floor(q, x1, y + 8, z1, x2, z2, Material.OAK_SLAB);
        air(q, cx - 22, y + 1, z2); air(q, cx - 22, y + 2, z2);
        air(q, cx - 22, y + 3, z2);
    }

    private void treasury(List<int[]> q, int cx, int y, int cz) {
        int x1 = cx + 12, x2 = cx + 32, z1 = cz - 32, z2 = cz - 14;
        // Double-thick walls
        wallsOnly(q, x1, y + 1, z1, x2, y + 8, z2, Material.STONE_BRICKS);
        wallsOnly(q, x1 + 1, y + 1, z1 + 1, x2 - 1, y + 8, z2 - 1, Material.STONE_BRICKS);
        // Gold ring floor
        for (int bx = x1 + 2; bx <= x2 - 2; bx++)
            for (int bz = z1 + 2; bz <= z2 - 2; bz++) {
                boolean edge = (bx == x1+2 || bx == x2-2 || bz == z1+2 || bz == z2-2);
                add(q, bx, y, bz, edge ? Material.GOLD_BLOCK : Material.STONE_BRICKS);
            }
        // Entrance + slab roof
        air(q, cx + 22, y + 1, z2); air(q, cx + 22, y + 2, z2);
        floor(q, x1, y + 9, z1, x2, z2, Material.STONE_BRICK_SLAB);
    }

    private void genericBuilding(List<int[]> q,
                                  int x1, int y, int z1, int x2, int y2, int z2,
                                  Material wall, Material floorMat) {
        wallsOnly(q, x1, y + 1, z1, x2, y2, z2, wall);
        floor(q, x1 + 1, y, z1 + 1, x2 - 1, z2 - 1, floorMat);
        floor(q, x1, y2 + 1, z1, x2, z2, wall); // roof
    }

    private void dragonCourtyard(List<int[]> q, int cx, int y, int cz) {
        int x1 = cx - 8, x2 = cx + 8, z1 = cz - 8, z2 = cz + 20;
        // Obsidian border
        for (int bx = x1; bx <= x2; bx++) {
            add(q, bx, y, z1, Material.OBSIDIAN);
            add(q, bx, y, z2, Material.OBSIDIAN);
        }
        for (int bz = z1 + 1; bz <= z2 - 1; bz++) {
            add(q, x1, y, bz, Material.OBSIDIAN);
            add(q, x2, y, bz, Material.OBSIDIAN);
        }
        // Smooth stone interior + magma X
        floor(q, x1 + 1, y, z1 + 1, x2 - 1, z2 - 1, Material.SMOOTH_STONE);
        int mx = (x1 + x2) / 2, mz = (z1 + z2) / 2;
        for (int d = -3; d <= 3; d++) {
            add(q, mx + d, y, mz + d, Material.MAGMA_BLOCK);
            add(q, mx + d, y, mz - d, Material.MAGMA_BLOCK);
        }
        // Iron bar fence
        for (int bx = x1; bx <= x2; bx++) {
            add(q, bx, y + 1, z1, Material.IRON_BARS);
            add(q, bx, y + 1, z2, Material.IRON_BARS);
        }
        for (int bz = z1 + 1; bz <= z2 - 1; bz++) {
            add(q, x1, y + 1, bz, Material.IRON_BARS);
            add(q, x2, y + 1, bz, Material.IRON_BARS);
        }
    }

    private void bannerTower(List<int[]> q, int tx, int y, int tz) {
        box(q, tx, y + 1, tz, tx + 3, y + 20, tz + 3, Material.STONE_BRICKS);
        fill(q, tx + 1, y + 2, tz + 1, tx + 2, y + 19, tz + 2, Material.AIR);
        // Battlements
        add(q, tx,     y + 21, tz,     Material.STONE_BRICK_WALL);
        add(q, tx + 3, y + 21, tz,     Material.STONE_BRICK_WALL);
        add(q, tx,     y + 21, tz + 3, Material.STONE_BRICK_WALL);
        add(q, tx + 3, y + 21, tz + 3, Material.STONE_BRICK_WALL);
    }

    // ── Banner placement ──────────────────────────────────────────────────────

    private void placeBannersOnTowers(World world, int cx, int baseY, int cz, Nation nation) {
        int[][] tops = {
            {cx - 24, baseY + 21, cz - 24},
            {cx + 24, baseY + 21, cz - 24},
            {cx - 24, baseY + 21, cz + 24},
            {cx + 24, baseY + 21, cz + 24}
        };
        List<int[]> topList = new ArrayList<>();
        ItemStack banner = nation.buildBannerItem();
        for (int[] t : tops) {
            placeBannerAt(world.getBlockAt(t[0], t[1], t[2]), banner);
            topList.add(t);
        }
        towerTops.put(nation.getPlayerUUID(), topList);
    }

    @SuppressWarnings("deprecation")
    private void placeBannerAt(Block block, ItemStack bannerItem) {
        block.setType(Material.WHITE_BANNER, false);
        BlockState state = block.getState();
        if (state instanceof Banner bannerBlock && bannerItem.hasItemMeta()
                && bannerItem.getItemMeta() instanceof BannerMeta meta) {
            bannerBlock.setPatterns(meta.getPatterns());
            DyeColor base = dyeColorFromBannerMaterial(bannerItem.getType());
            bannerBlock.setBaseColor(base);
            bannerBlock.update(true, false);
        }
    }

    // ── Land claiming ─────────────────────────────────────────────────────────

    private void claimCastleChunks(World world, int cx, int cz, Player player, Nation nation) {
        int minCX = (cx - HALF - 4) >> 4;
        int maxCX = (cx + HALF + 4) >> 4;
        int minCZ = (cz - HALF - 4) >> 4;
        int maxCZ = (cz + HALF + 4) >> 4;
        for (int chX = minCX; chX <= maxCX; chX++)
            for (int chZ = minCZ; chZ <= maxCZ; chZ++)
                plugin.getLandManager().claimChunk(player, world.getChunkAt(chX, chZ));
    }

    // ── Block primitives ──────────────────────────────────────────────────────

    private void box(List<int[]> q, int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++)
                    add(q, x, y, z, m);
    }

    private void fill(List<int[]> q, int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        box(q, x1, y1, z1, x2, y2, z2, m);
    }

    private void floor(List<int[]> q, int x1, int y, int z1, int x2, int z2, Material m) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                add(q, x, y, z, m);
    }

    private void wallsOnly(List<int[]> q, int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++)
                    if (x == x1 || x == x2 || z == z1 || z == z2)
                        add(q, x, y, z, m);
    }

    private void add(List<int[]> q, int x, int y, int z, Material m) {
        q.add(new int[]{x, y, z, m.ordinal()});
    }

    private void air(List<int[]> q, int x, int y, int z) {
        add(q, x, y, z, Material.AIR);
    }

    /** Extracts DyeColor from a banner Material (WHITE_BANNER -> WHITE, etc.) */
    private static DyeColor dyeColorFromBannerMaterial(Material mat) {
        String name = mat.name().replace("_WALL_BANNER","").replace("_BANNER","");
        try { return DyeColor.valueOf(name); } catch (Exception e) { return DyeColor.WHITE; }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void loadOrigins() {
        var cfg = plugin.getDataManager().getLegendaryConfig();
        var sec = cfg.getConfigurationSection("castleOrigins");
        if (sec == null) return;
        for (String uuid : sec.getKeys(false)) {
            var s = cfg.getConfigurationSection("castleOrigins." + uuid);
            if (s == null) continue;
            castleOrigins.put(uuid, new String[]{
                s.getString("world", "world"),
                String.valueOf(s.getInt("cx")),
                String.valueOf(s.getInt("by")),
                String.valueOf(s.getInt("cz"))
            });
        }
    }

    private void saveOrigins() {
        var cfg = plugin.getDataManager().getLegendaryConfig();
        castleOrigins.forEach((uuid, o) -> {
            cfg.set("castleOrigins." + uuid + ".world", o[0]);
            cfg.set("castleOrigins." + uuid + ".cx",    Integer.parseInt(o[1]));
            cfg.set("castleOrigins." + uuid + ".by",    Integer.parseInt(o[2]));
            cfg.set("castleOrigins." + uuid + ".cz",    Integer.parseInt(o[3]));
        });
        plugin.getDataManager().saveLegendary();
    }
}
