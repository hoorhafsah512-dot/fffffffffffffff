package com.nationssmp.managers;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tracks server-wide kill and achievement leaderboards:
 *
 *  ■ Dragon kills   — who has slain the Ender Dragon most times
 *  ■ Land conquered — most chunks ever taken
 *  ■ Enemies slain  — total war/PvP kills
 *
 *  Stored in legendary.yml under "leaderboard".
 *  Accessible via /leaderboard [dragons|land|kills]
 */
public class LeaderboardManager {

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    // ── Stat maps: playerUUID → count ─────────────────────────────────────────
    private final Map<String, Integer> dragonKills   = new ConcurrentHashMap<>();
    private final Map<String, Integer> landConquered = new ConcurrentHashMap<>();
    private final Map<String, Integer> enemiesSlain  = new ConcurrentHashMap<>();

    /** Last dragon kill: uuid → ISO timestamp */
    private final Map<String, String>  lastDragonKill = new ConcurrentHashMap<>();

    private final NationsSMP plugin;

    public LeaderboardManager(NationsSMP plugin) {
        this.plugin = plugin;
        load();
    }

    // ── Record events ─────────────────────────────────────────────────────────

    public void recordDragonKill(Player player) {
        String uid = player.getUniqueId().toString();
        dragonKills.merge(uid, 1, Integer::sum);
        lastDragonKill.put(uid, FMT.format(Instant.now()));
        save();

        Nation n = plugin.getNationManager().getNation(player.getUniqueId());
        plugin.getNationHistoryManager().log(n,
            "🐉 Dragon slain! Total kills: " + dragonKills.get(uid));

        // Announce new record if applicable
        int count = dragonKills.get(uid);
        if (count >= 3 && count % 3 == 0) {
            String nationName = n != null ? n.getNationName() : player.getName();
            Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "🐉 " + player.getName()
                + " of " + nationName + " has slain the Dragon "
                + ChatColor.GOLD + count + ChatColor.DARK_PURPLE + " times!");
        }
    }

    public void recordLandConquered(Player player, int chunks) {
        landConquered.merge(player.getUniqueId().toString(), chunks, Integer::sum);
        save();
    }

    public void recordEnemySlain(Player player) {
        enemiesSlain.merge(player.getUniqueId().toString(), 1, Integer::sum);
        save();
    }

    // ── Leaderboard display ───────────────────────────────────────────────────

    public List<String> getDragonLeaderboard() {
        return buildBoard(dragonKills, "🐉", "dragon kill", lastDragonKill);
    }

    public List<String> getLandLeaderboard() {
        return buildBoard(landConquered, "🗺", "chunk conquered", null);
    }

    public List<String> getKillsLeaderboard() {
        return buildBoard(enemiesSlain, "⚔", "enemy slain", null);
    }

    private List<String> buildBoard(Map<String, Integer> map, String emoji,
                                     String unit, Map<String, String> extra) {
        List<Map.Entry<String, Integer>> sorted = map.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .limit(10)
            .collect(Collectors.toList());

        List<String> lines = new ArrayList<>();
        String[] medals = {"🥇", "🥈", "🥉"};
        for (int i = 0; i < sorted.size(); i++) {
            String uuid  = sorted.get(i).getKey();
            int    score = sorted.get(i).getValue();
            Nation n     = plugin.getNationManager().getNationByUUID(uuid);
            String name  = n != null ? n.getPlayerName() : uuid.substring(0, 8);
            String nation = n != null ? " of " + n.getNationName() : "";
            String medal = i < 3 ? medals[i] : ChatColor.GRAY + String.valueOf(i + 1) + ".";
            String extraInfo = (extra != null && extra.containsKey(uuid))
                ? ChatColor.DARK_GRAY + "  (last: " + extra.get(uuid) + ")" : "";
            lines.add(medal + " " + ChatColor.YELLOW + name + ChatColor.GRAY
                + nation + ChatColor.WHITE + " — " + score + " " + emoji + extraInfo);
        }
        if (lines.isEmpty()) lines.add(ChatColor.GRAY + "(No records yet)");
        return lines;
    }

    public int getDragonKills(Player player) {
        return dragonKills.getOrDefault(player.getUniqueId().toString(), 0);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void load() {
        var cfg = plugin.getDataManager().getLegendaryConfig();
        loadMap(cfg, "leaderboard.dragons",   dragonKills);
        loadMap(cfg, "leaderboard.land",      landConquered);
        loadMap(cfg, "leaderboard.kills",     enemiesSlain);
        ConfigurationSection ldc = cfg.getConfigurationSection("leaderboard.dragonDates");
        if (ldc != null) for (String k : ldc.getKeys(false))
            lastDragonKill.put(k, ldc.getString(k, ""));
    }

    private void loadMap(org.bukkit.configuration.file.FileConfiguration cfg,
                         String path, Map<String, Integer> map) {
        ConfigurationSection sec = cfg.getConfigurationSection(path);
        if (sec == null) return;
        for (String k : sec.getKeys(false))
            map.put(k, sec.getInt(k));
    }

    public void save() {
        var cfg = plugin.getDataManager().getLegendaryConfig();
        dragonKills.forEach((k, v)   -> cfg.set("leaderboard.dragons." + k, v));
        landConquered.forEach((k, v) -> cfg.set("leaderboard.land."    + k, v));
        enemiesSlain.forEach((k, v)  -> cfg.set("leaderboard.kills."   + k, v));
        lastDragonKill.forEach((k, v) ->
            cfg.set("leaderboard.dragonDates." + k, v));
        plugin.getDataManager().saveLegendary();
    }
}
