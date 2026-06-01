package com.nationssmp.managers;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages formal war declarations between nations.
 *
 *  • /war declare <nation>   — broadcasts a declaration; target player is notified
 *  • /war peace  <nation>    — proposes peace; target must /war peace back to accept
 *  • /war status             — lists all active wars involving your nation
 *  • /war list               — lists all current server wars
 *
 *  Active war effects:
 *  ‣ PvP always enabled between warring nations (no oathbreaker penalty)
 *  ‣ Killing an enemy at war grants normal land+trophy rewards
 *  ‣ No loyalty restriction on legendary items applies to declared enemies
 *
 *  Cooldown: 30 minutes between ending one war and declaring a new one on the same nation.
 */
public class WarManager {

    /** Canonical war key: smaller UUID + ":" + larger UUID */
    private final Set<String>         activeWars    = ConcurrentHashMap.newKeySet();
    /** uuid → Set of nationNames they have proposed peace to */
    private final Map<String, Set<String>> peaceProposals = new ConcurrentHashMap<>();
    /** "uuidA:uuidB" → timestamp of when war ended (for cooldown) */
    private final Map<String, Long>   warEndTimes   = new ConcurrentHashMap<>();

    private static final long WAR_COOLDOWN_MS = 30L * 60 * 1000;

    private final NationsSMP    plugin;
    private final NationManager nationManager;

    public WarManager(NationsSMP plugin, NationManager nationManager) {
        this.plugin        = plugin;
        this.nationManager = nationManager;
        load();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean declareWar(Player declarer, String targetNationName) {
        Nation self   = nationManager.getNation(declarer.getUniqueId());
        Nation target = nationManager.getNationByName(targetNationName);
        if (self == null || !self.isSetupComplete()) {
            declarer.sendMessage(ChatColor.RED + "You have no nation.");
            return false;
        }
        if (target == null) {
            declarer.sendMessage(ChatColor.RED + "No nation named '" + targetNationName + "' found.");
            return false;
        }
        if (target.getPlayerUUID().equals(self.getPlayerUUID())) {
            declarer.sendMessage(ChatColor.RED + "You cannot declare war on yourself.");
            return false;
        }
        if (atWar(self.getPlayerUUID(), target.getPlayerUUID())) {
            declarer.sendMessage(ChatColor.RED + "You are already at war with " + targetNationName + ".");
            return false;
        }
        // Cooldown check
        String key = warKey(self.getPlayerUUID(), target.getPlayerUUID());
        Long ended = warEndTimes.get(key);
        if (ended != null && System.currentTimeMillis() - ended < WAR_COOLDOWN_MS) {
            long remaining = (WAR_COOLDOWN_MS - (System.currentTimeMillis() - ended)) / 60000;
            declarer.sendMessage(ChatColor.RED + "You must wait " + remaining + " more minutes before declaring war again.");
            return false;
        }

        activeWars.add(key);
        save();

        // Log history
        plugin.getNationHistoryManager().log(self,
            "⚔ War declared on " + target.getNationName());
        plugin.getNationHistoryManager().log(target,
            "⚔ War declared by " + self.getNationName());

        // Notify
        String msg = ChatColor.DARK_RED + "⚔ WAR DECLARED ⚔  "
            + ChatColor.RED + self.getNationName().toUpperCase()
            + ChatColor.DARK_RED + " vs "
            + ChatColor.RED + target.getNationName().toUpperCase();
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(msg);
        Bukkit.broadcastMessage(ChatColor.GRAY
            + "Use /war peace <nation> to end the conflict.");
        Bukkit.broadcastMessage("");

        // Sound for all online players
        Bukkit.getOnlinePlayers().forEach(p ->
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.4f, 0.8f));

        // Notify target player if online
        Player targetPlayer = Bukkit.getPlayer(UUID.fromString(target.getPlayerUUID()));
        if (targetPlayer != null)
            targetPlayer.sendTitle(ChatColor.DARK_RED + "⚔ WAR DECLARED",
                ChatColor.RED + self.getNationName() + " has declared war on you!", 10, 80, 20);
        return true;
    }

    public boolean proposePeace(Player proposer, String targetNationName) {
        Nation self   = nationManager.getNation(proposer.getUniqueId());
        Nation target = nationManager.getNationByName(targetNationName);
        if (self == null || target == null) {
            proposer.sendMessage(ChatColor.RED + "Nation not found.");
            return false;
        }
        if (!atWar(self.getPlayerUUID(), target.getPlayerUUID())) {
            proposer.sendMessage(ChatColor.RED + "You are not at war with " + targetNationName + ".");
            return false;
        }

        // Check if target has already proposed peace (mutual = end war)
        Set<String> targetProposals = peaceProposals
            .getOrDefault(target.getPlayerUUID(), Collections.emptySet());
        if (targetProposals.contains(self.getNationName().toLowerCase())) {
            endWar(self, target, proposer);
            return true;
        }

        // Record this proposal
        peaceProposals.computeIfAbsent(self.getPlayerUUID(), k -> ConcurrentHashMap.newKeySet())
            .add(target.getNationName().toLowerCase());

        proposer.sendMessage(ChatColor.GREEN
            + "☮ Peace proposed to " + target.getNationName()
            + ". They must type /war peace " + self.getNationName() + " to accept.");

        Player targetPlayer = Bukkit.getPlayer(UUID.fromString(target.getPlayerUUID()));
        if (targetPlayer != null) {
            targetPlayer.sendTitle(ChatColor.GREEN + "☮ PEACE OFFERED",
                ChatColor.YELLOW + self.getNationName() + " wants peace",
                5, 80, 20);
            targetPlayer.sendMessage(ChatColor.GREEN + "☮ " + self.getNationName()
                + " has offered peace. Type " + ChatColor.WHITE
                + "/war peace " + self.getNationName()
                + ChatColor.GREEN + " to accept.");
        }
        return true;
    }

    private void endWar(Nation a, Nation b, Player proposer) {
        String key = warKey(a.getPlayerUUID(), b.getPlayerUUID());
        activeWars.remove(key);
        warEndTimes.put(key, System.currentTimeMillis());
        peaceProposals.getOrDefault(a.getPlayerUUID(), Collections.emptySet())
            .remove(b.getNationName().toLowerCase());
        peaceProposals.getOrDefault(b.getPlayerUUID(), Collections.emptySet())
            .remove(a.getNationName().toLowerCase());
        save();

        plugin.getNationHistoryManager().log(a, "☮ Peace made with " + b.getNationName());
        plugin.getNationHistoryManager().log(b, "☮ Peace made with " + a.getNationName());

        Bukkit.broadcastMessage(ChatColor.GREEN + "☮ "
            + a.getNationName().toUpperCase() + " and "
            + b.getNationName().toUpperCase() + " have made PEACE.");
        Bukkit.getOnlinePlayers().forEach(p ->
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, 0.5f, 1.0f));
    }

    public boolean atWar(String uuidA, String uuidB) {
        return activeWars.contains(warKey(uuidA, uuidB));
    }

    public boolean atWar(UUID a, UUID b) {
        return atWar(a.toString(), b.toString());
    }

    public List<String> getWarsFor(String uuid) {
        return activeWars.stream()
            .filter(k -> k.startsWith(uuid + ":") || k.endsWith(":" + uuid))
            .map(k -> {
                String[] parts = k.split(":");
                String otherUUID = parts[0].equals(uuid) ? parts[1] : parts[0];
                Nation n = nationManager.getNationByUUID(otherUUID);
                return n != null ? n.getNationName() : otherUUID.substring(0, 8);
            })
            .collect(Collectors.toList());
    }

    public List<String> getAllWars() {
        return activeWars.stream().map(k -> {
            String[] parts = k.split(":");
            if (parts.length != 2) return k;
            Nation a = nationManager.getNationByUUID(parts[0]);
            Nation b = nationManager.getNationByUUID(parts[1]);
            String nameA = a != null ? a.getNationName() : parts[0];
            String nameB = b != null ? b.getNationName() : parts[1];
            return ChatColor.RED + nameA + ChatColor.DARK_RED + " ⚔ " + ChatColor.RED + nameB;
        }).collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String warKey(String a, String b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void load() {
        var cfg = plugin.getDataManager().getLegendaryConfig();
        activeWars.addAll(cfg.getStringList("wars.active"));
        var sec = cfg.getConfigurationSection("wars.ended");
        if (sec != null)
            for (String k : sec.getKeys(false))
                // Convert "_vs_" back to ":" for the runtime key
                warEndTimes.put(k.replace("_vs_", ":"), cfg.getLong("wars.ended." + k));
    }

    private void save() {
        var cfg = plugin.getDataManager().getLegendaryConfig();
        cfg.set("wars.active", new ArrayList<>(activeWars));
        // Use "_vs_" as separator because ":" is special in YAML config paths
        warEndTimes.forEach((k, v) -> cfg.set("wars.ended." + k.replace(":", "_vs_"), v));
        plugin.getDataManager().saveLegendary();
    }
}
