package com.nationssmp.managers;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two responsibilities:
 *
 *  1. NATION HISTORY — timestamped event log per nation (max 50 entries, rolling).
 *     Auto-logged by all managers; readable with /nation history.
 *
 *  2. THRONE HISTORY — ordered list of every player who claimed the Iron Throne,
 *     with timestamp and how long they held it. Readable with /throne history.
 *
 *  Both are persisted in legendary.yml under "history" and "throne".
 */
public class NationHistoryManager {

    private static final int   MAX_HISTORY = 50;
    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final NationsSMP plugin;

    /** playerUUID → ordered list of log entries */
    private final Map<String, Deque<String>> nationLogs = new ConcurrentHashMap<>();

    /** List of throne records: "playerName|nationName|timestamp" */
    private final Deque<String> throneHistory = new ArrayDeque<>();

    /** uuid → timestamp when they sat on throne (to compute duration) */
    private final Map<String, Long> throneSitTime = new ConcurrentHashMap<>();

    public NationHistoryManager(NationsSMP plugin) {
        this.plugin = plugin;
        load();
    }

    // ── Nation history ────────────────────────────────────────────────────────

    /** Log an event for a nation. Silently ignores null nations. */
    public void log(Nation nation, String event) {
        if (nation == null) return;
        String uuid = nation.getPlayerUUID();
        Deque<String> log = nationLogs.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        String entry = ChatColor.GRAY + "[" + FMT.format(Instant.now()) + "] "
            + ChatColor.WHITE + event;
        log.addLast(entry);
        while (log.size() > MAX_HISTORY) log.removeFirst();
        save();
    }

    /** Returns the last N entries for the given nation (newest last). */
    public List<String> getHistory(Nation nation, int count) {
        if (nation == null) return Collections.emptyList();
        Deque<String> log = nationLogs.get(nation.getPlayerUUID());
        if (log == null || log.isEmpty())
            return Collections.singletonList(ChatColor.GRAY + "(No events recorded yet)");
        List<String> list = new ArrayList<>(log);
        return list.subList(Math.max(0, list.size() - count), list.size());
    }

    // ── Throne history ────────────────────────────────────────────────────────

    /**
     * Called by DragonManager.onThroneInteract every time a player sits.
     * Records the end time for the previous sitter and the start time for the new one.
     */
    public void recordThroneSit(Player newOwner, String previousOwnerUUID) {
        long now = System.currentTimeMillis();

        // Close the previous entry with duration
        if (previousOwnerUUID != null) {
            Long start = throneSitTime.remove(previousOwnerUUID);
            if (start != null) {
                long minutes = (now - start) / 60000;
                // Update last throne entry with duration
                String lastEntry = throneHistory.peekLast();
                if (lastEntry != null && lastEntry.contains(previousOwnerUUID.substring(0, 8))) {
                    throneHistory.pollLast();
                    throneHistory.addLast(lastEntry + " (" + minutes + " min)");
                }
            }
        }

        // Record the new sitter
        Nation n = plugin.getNationManager().getNation(newOwner.getUniqueId());
        String nationName = n != null ? n.getNationName() : "Unknown";
        String entry = newOwner.getName() + "|" + nationName
            + "|" + FMT.format(Instant.now())
            + "|" + newOwner.getUniqueId().toString().substring(0, 8);
        throneHistory.addLast(entry);
        if (throneHistory.size() > MAX_HISTORY) throneHistory.removeFirst();

        throneSitTime.put(newOwner.getUniqueId().toString(), now);

        if (n != null) log(n, "👑 Claimed the Iron Throne");
        save();
    }

    /** Returns formatted throne history lines for /throne history. */
    public List<String> getThroneHistory(int count) {
        List<String> entries = new ArrayList<>(throneHistory);
        List<String> raw = entries.subList(Math.max(0, entries.size() - count), entries.size());
        List<String> result = new ArrayList<>();
        Collections.reverse(raw); // newest first
        for (int i = 0; i < raw.size(); i++) {
            String[] parts = raw.get(i).split("\\|");
            String player = parts.length > 0 ? parts[0] : "?";
            String nation = parts.length > 1 ? parts[1] : "?";
            String time   = parts.length > 2 ? parts[2] : "?";
            String dur    = parts.length > 4 ? " " + parts[4] : "";
            result.add(ChatColor.GRAY + "" + (i + 1) + ". "
                + ChatColor.YELLOW + player
                + ChatColor.GRAY   + " of " + ChatColor.WHITE + nation
                + ChatColor.DARK_GRAY + " — " + time + dur);
        }
        if (result.isEmpty())
            result.add(ChatColor.GRAY + "(No throne history yet)");
        return result;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void load() {
        var cfg = plugin.getDataManager().getLegendaryConfig();

        // Nation logs
        var logSec = cfg.getConfigurationSection("history.nations");
        if (logSec != null) {
            for (String uuid : logSec.getKeys(false)) {
                Deque<String> deque = new ArrayDeque<>(
                    cfg.getStringList("history.nations." + uuid));
                nationLogs.put(uuid, deque);
            }
        }

        // Throne history
        throneHistory.addAll(cfg.getStringList("history.throne"));
    }

    public void save() {
        var cfg = plugin.getDataManager().getLegendaryConfig();
        nationLogs.forEach((uuid, deque) ->
            cfg.set("history.nations." + uuid, new ArrayList<>(deque)));
        cfg.set("history.throne", new ArrayList<>(throneHistory));
        plugin.getDataManager().saveLegendary();
    }
}
