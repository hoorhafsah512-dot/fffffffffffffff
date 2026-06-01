package com.nationssmp.managers;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import org.bukkit.*;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Full banner customisation editor — added as Step 6 of the nation setup wizard.
 *
 * Flow:
 *   openBaseColorPicker(player)
 *     → player clicks a colour → openPatternMenu(player)
 *       → player clicks a pattern type → openPatternColorPicker(player)
 *         → player clicks a colour → layer saved → back to openPatternMenu
 *       → player clicks DONE → finalizeBanner(player)
 *
 * Each PatternType represents specific "pixels" on the banner face.
 * By layering up to 6 patterns in different colours over the base colour,
 * players achieve full pixel-level control of their banner design.
 * All 41 Minecraft banner patterns are available across two pages.
 */
public class BannerManager {

    // Inventory titles (used as identifiers in InventoryClickListener)
    public static final String TITLE_BASE_COLOR    = ChatColor.DARK_PURPLE + "✦ Banner — Choose Base Colour";
    public static final String TITLE_PATTERN       = ChatColor.DARK_PURPLE + "✦ Banner — Add Pattern Layer";
    public static final String TITLE_PATTERN_COLOR = ChatColor.DARK_PURPLE + "✦ Banner — Choose Pattern Colour";

    // Action slots in the pattern menu
    private static final int SLOT_PREV     = 45;
    private static final int SLOT_PREVIEW  = 49;
    private static final int SLOT_NEXT     = 53;
    private static final int SLOT_UNDO     = 46;
    private static final int SLOT_DONE     = 52;

    // All 41 PatternType values (excluding BASE which is the background)
    private static final PatternType[] ALL_PATTERNS = org.bukkit.Registry.BANNER_PATTERN
        .stream()
        .filter(p -> !p.getKey().getKey().equals("base"))
        .toArray(PatternType[]::new);

    private final NationsSMP plugin;

    // Per-player state
    private final Map<UUID, DyeColor>       baseColor    = new ConcurrentHashMap<>();
    private final Map<UUID, List<Pattern>>  layers       = new ConcurrentHashMap<>();
    private final Map<UUID, Integer>        page         = new ConcurrentHashMap<>();
    private final Map<UUID, PatternType>    pendingType  = new ConcurrentHashMap<>();
    private final Map<UUID, BannerStage>    stage        = new ConcurrentHashMap<>();

    public enum BannerStage { BASE_COLOR, PATTERN, PATTERN_COLOR }

    public BannerManager(NationsSMP plugin) {
        this.plugin = plugin;
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public void openBaseColorPicker(Player player) {
        stage.put(player.getUniqueId(), BannerStage.BASE_COLOR);
        baseColor.putIfAbsent(player.getUniqueId(), DyeColor.WHITE);
        layers.putIfAbsent(player.getUniqueId(), new ArrayList<>());
        page.put(player.getUniqueId(), 0);

        Inventory inv = Bukkit.createInventory(null, 27, TITLE_BASE_COLOR);
        DyeColor[] colours = DyeColor.values();
        for (int i = 0; i < 16; i++) {
            DyeColor c = colours[i];
            ItemStack wool = new ItemStack(woolOf(c));
            ItemMeta meta = wool.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.RESET + "" + ChatColor.BOLD
                    + wordCase(c.name()));
                wool.setItemMeta(meta);
            }
            inv.setItem(i, wool);
        }
        // Info slot
        inv.setItem(22, hint(Material.PAPER,
            ChatColor.YELLOW + "⬆ Click a colour for your banner base",
            ChatColor.GRAY + "You can add up to 6 pattern layers next."));

        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(inv));
    }

    public void openPatternMenu(Player player) {
        stage.put(player.getUniqueId(), BannerStage.PATTERN);
        int pg   = page.getOrDefault(player.getUniqueId(), 0);
        int layerCount = layers.getOrDefault(player.getUniqueId(), Collections.emptyList()).size();

        Inventory inv = Bukkit.createInventory(null, 54, TITLE_PATTERN);
        // Pattern previews in slots 0-35 (36 per page)
        int start = pg * 36;
        for (int i = start; i < Math.min(start + 36, ALL_PATTERNS.length); i++) {
            PatternType type = ALL_PATTERNS[i];
            ItemStack preview = makeBannerPreview(player, type);
            ItemMeta meta = preview.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + wordCase(type.getKey().getKey()));
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Click to add this pattern");
                lore.add(ChatColor.DARK_GRAY + "Pattern ID: " + type.getKey().getKey());
                meta.setLore(lore);
                preview.setItemMeta(meta);
            }
            inv.setItem(i - start, preview);
        }
        // Navigation
        if (pg > 0)
            inv.setItem(SLOT_PREV, hint(Material.ARROW,
                ChatColor.AQUA + "◄ Previous page", ""));
        if (start + 36 < ALL_PATTERNS.length)
            inv.setItem(SLOT_NEXT, hint(Material.ARROW,
                ChatColor.AQUA + "► Next page", ""));

        // Current banner preview (all layers applied)
        inv.setItem(SLOT_PREVIEW, makeFullPreview(player));

        // Undo last layer
        if (layerCount > 0)
            inv.setItem(SLOT_UNDO, hint(Material.RED_DYE,
                ChatColor.RED + "✖ Undo last layer",
                ChatColor.GRAY + "Removes the most recent pattern"));

        // Done button (always available — even 0 layers = just base colour)
        inv.setItem(SLOT_DONE, hint(Material.LIME_DYE,
            ChatColor.GREEN + "✔ Finish banner design",
            ChatColor.GRAY + "Layers added: " + layerCount + "/6"));

        // Layer counter in slot 48
        inv.setItem(48, hint(Material.BOOK,
            ChatColor.YELLOW + "Layers: " + layerCount + " / 6",
            ChatColor.GRAY + (layerCount >= 6 ? "Maximum layers reached!" : "Click a pattern to add a layer")));

        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(inv));
    }

    public void openPatternColorPicker(Player player) {
        stage.put(player.getUniqueId(), BannerStage.PATTERN_COLOR);

        Inventory inv = Bukkit.createInventory(null, 27, TITLE_PATTERN_COLOR);
        DyeColor[] colours = DyeColor.values();
        for (int i = 0; i < 16; i++) {
            DyeColor c = colours[i];
            ItemStack wool = new ItemStack(woolOf(c));
            ItemMeta meta = wool.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.RESET + "" + ChatColor.BOLD + wordCase(c.name()));
                wool.setItemMeta(meta);
            }
            inv.setItem(i, wool);
        }
        PatternType pending = pendingType.getOrDefault(player.getUniqueId(), PatternType.CROSS);
        inv.setItem(22, hint(Material.PAPER,
            ChatColor.YELLOW + "Colouring: " + wordCase(pending.getKey().getKey()),
            ChatColor.GRAY + "Pick a colour for this pattern layer."));

        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(inv));
    }

    // ── Click handlers (called from InventoryClickListener) ───────────────────

    public void handleBaseColorClick(Player player, int slot) {
        if (slot < 0 || slot >= 16) return;
        DyeColor chosen = DyeColor.values()[slot];
        baseColor.put(player.getUniqueId(), chosen);
        player.sendMessage(ChatColor.GREEN + "Base colour set: " + wordCase(chosen.name()));
        openPatternMenu(player);
    }

    public void handlePatternClick(Player player, int slot) {
        if (slot == SLOT_PREV) {
            page.merge(player.getUniqueId(), -1, Integer::sum);
            openPatternMenu(player);
            return;
        }
        if (slot == SLOT_NEXT) {
            page.merge(player.getUniqueId(), 1, Integer::sum);
            openPatternMenu(player);
            return;
        }
        if (slot == SLOT_DONE) {
            finalizeBanner(player);
            return;
        }
        if (slot == SLOT_UNDO) {
            List<Pattern> list = layers.getOrDefault(player.getUniqueId(), new ArrayList<>());
            if (!list.isEmpty()) list.remove(list.size() - 1);
            player.sendMessage(ChatColor.YELLOW + "Last pattern layer removed.");
            openPatternMenu(player);
            return;
        }
        // Pattern slot 0-35
        if (slot >= 36) return;
        List<Pattern> list = layers.getOrDefault(player.getUniqueId(), new ArrayList<>());
        if (list.size() >= 6) {
            player.sendMessage(ChatColor.RED + "Maximum 6 layers reached. Click ✔ Finish or remove a layer.");
            return;
        }
        int idx = page.getOrDefault(player.getUniqueId(), 0) * 36 + slot;
        if (idx >= ALL_PATTERNS.length) return;
        pendingType.put(player.getUniqueId(), ALL_PATTERNS[idx]);
        openPatternColorPicker(player);
    }

    public void handlePatternColorClick(Player player, int slot) {
        if (slot < 0 || slot >= 16) return;
        DyeColor chosen = DyeColor.values()[slot];
        PatternType type = pendingType.getOrDefault(player.getUniqueId(), PatternType.CROSS);
        layers.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>())
              .add(new Pattern(chosen, type));
        pendingType.remove(player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "Layer added: " + wordCase(type.getKey().getKey())
            + " in " + wordCase(chosen.name()));
        openPatternMenu(player);
    }

    // ── Finalize ──────────────────────────────────────────────────────────────

    private void finalizeBanner(Player player) {
        UUID uid = player.getUniqueId();
        Nation nation = plugin.getNationManager().getNation(uid);
        if (nation == null) return;

        DyeColor base = baseColor.getOrDefault(uid, DyeColor.WHITE);
        List<Pattern> playerLayers = layers.getOrDefault(uid, Collections.emptyList());

        // Store in nation
        nation.setBannerBase(base.name());
        List<String> encoded = new ArrayList<>();
        for (Pattern p : playerLayers)
            encoded.add(p.getPattern().getKey().getKey() + ":" + p.getColor().name());
        nation.setBannerPatterns(encoded);

        // Give player a copy of their banner
        player.getInventory().addItem(nation.buildBannerItem());

        // Clean up
        baseColor.remove(uid); layers.remove(uid);
        page.remove(uid);      pendingType.remove(uid); stage.remove(uid);

        player.closeInventory();
        player.sendMessage(ChatColor.GOLD + "✦ Banner designed! Your capital is being built…");

        // Complete the nation setup now that banner is done
        plugin.getNationManager().completeNationSetup(player, nation);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Creates a banner preview showing just the given pattern type on white base. */
    private ItemStack makeBannerPreview(Player player, PatternType type) {
        DyeColor base = baseColor.getOrDefault(player.getUniqueId(), DyeColor.WHITE);
        ItemStack banner = new ItemStack(bannerMaterialOf(base));
        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        if (meta != null) {
            meta.addPattern(new Pattern(DyeColor.WHITE, type));
            banner.setItemMeta(meta);
        }
        return banner;
    }

    /** Creates a banner showing all currently added layers (full preview). */
    private ItemStack makeFullPreview(Player player) {
        UUID uid = player.getUniqueId();
        DyeColor base = baseColor.getOrDefault(uid, DyeColor.WHITE);
        ItemStack banner = new ItemStack(bannerMaterialOf(base));
        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        if (meta != null) {
            for (Pattern p : layers.getOrDefault(uid, Collections.emptyList()))
                meta.addPattern(p);
            meta.setDisplayName(ChatColor.AQUA + "✦ Your Banner Preview");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Base: " + wordCase(base.name()));
            lore.add(ChatColor.GRAY + "Layers: " + layers.getOrDefault(uid, Collections.emptyList()).size() + "/6");
            meta.setLore(lore);
            banner.setItemMeta(meta);
        }
        return banner;
    }

    public BannerStage getStage(UUID uid) {
        return stage.getOrDefault(uid, null);
    }

    private ItemStack hint(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static Material woolOf(DyeColor c) {
        return switch (c) {
            case WHITE      -> Material.WHITE_WOOL;
            case ORANGE     -> Material.ORANGE_WOOL;
            case MAGENTA    -> Material.MAGENTA_WOOL;
            case LIGHT_BLUE -> Material.LIGHT_BLUE_WOOL;
            case YELLOW     -> Material.YELLOW_WOOL;
            case LIME       -> Material.LIME_WOOL;
            case PINK       -> Material.PINK_WOOL;
            case GRAY       -> Material.GRAY_WOOL;
            case LIGHT_GRAY -> Material.LIGHT_GRAY_WOOL;
            case CYAN       -> Material.CYAN_WOOL;
            case PURPLE     -> Material.PURPLE_WOOL;
            case BLUE       -> Material.BLUE_WOOL;
            case BROWN      -> Material.BROWN_WOOL;
            case GREEN      -> Material.GREEN_WOOL;
            case RED        -> Material.RED_WOOL;
            case BLACK      -> Material.BLACK_WOOL;
        };
    }

    public static Material bannerMaterialOf(DyeColor c) {
        return switch (c) {
            case WHITE      -> Material.WHITE_BANNER;
            case ORANGE     -> Material.ORANGE_BANNER;
            case MAGENTA    -> Material.MAGENTA_BANNER;
            case LIGHT_BLUE -> Material.LIGHT_BLUE_BANNER;
            case YELLOW     -> Material.YELLOW_BANNER;
            case LIME       -> Material.LIME_BANNER;
            case PINK       -> Material.PINK_BANNER;
            case GRAY       -> Material.GRAY_BANNER;
            case LIGHT_GRAY -> Material.LIGHT_GRAY_BANNER;
            case CYAN       -> Material.CYAN_BANNER;
            case PURPLE     -> Material.PURPLE_BANNER;
            case BLUE       -> Material.BLUE_BANNER;
            case BROWN      -> Material.BROWN_BANNER;
            case GREEN      -> Material.GREEN_BANNER;
            case RED        -> Material.RED_BANNER;
            case BLACK      -> Material.BLACK_BANNER;
        };
    }

    private static String wordCase(String s) {
        if (s == null || s.isEmpty()) return s;
        return Arrays.stream(s.replace("_", " ").split(" "))
            .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase())
            .reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b);
    }
}
