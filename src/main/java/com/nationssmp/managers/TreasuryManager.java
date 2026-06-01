package com.nationssmp.managers;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-nation gold treasury.
 *
 *  • /treasury balance              — view your treasury
 *  • /treasury deposit <amount>     — put gold nuggets from inventory into treasury
 *  • /treasury withdraw <amount>    — take gold nuggets out of treasury
 *  • /treasury raid                 — (auto) triggered on conquest: loser's treasury
 *                                     is halved and winner gets the difference
 *
 *  Treasury is persisted in nations.yml via DataManager.
 *  A physical gold block is placed in the treasury room of the castle as a marker
 *  (already part of the CastleManager build — this tracks the balance numerically).
 *
 *  On player death during war: 10% of treasury leaks as gold nuggets dropped at
 *  the death location (handled by PlayerDeathListener).
 */
public class TreasuryManager {

    /** playerUUID → gold nugget balance */
    private final Map<String, Long> balances = new ConcurrentHashMap<>();
    private final NationsSMP plugin;

    public TreasuryManager(NationsSMP plugin) {
        this.plugin = plugin;
        load();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns current treasury balance for the player's nation. */
    public long getBalance(Player player) {
        return balances.getOrDefault(player.getUniqueId().toString(), 0L);
    }

    public long getBalance(String uuid) {
        return balances.getOrDefault(uuid, 0L);
    }

    /** Deposit gold nuggets from the player's inventory into their treasury. */
    public void deposit(Player player, int amount) {
        if (amount <= 0) { player.sendMessage(ChatColor.RED + "Amount must be positive."); return; }
        Nation nation = plugin.getNationManager().getNation(player.getUniqueId());
        if (nation == null || !nation.isSetupComplete()) {
            player.sendMessage(ChatColor.RED + "You have no nation."); return;
        }
        int inInventory = countGold(player);
        if (inInventory < amount) {
            player.sendMessage(ChatColor.RED + "You only have " + inInventory + " gold nuggets.");
            return;
        }
        removeGold(player, amount);
        balances.merge(player.getUniqueId().toString(), (long) amount, Long::sum);
        save();
        player.sendMessage(ChatColor.GOLD + "💰 Deposited " + amount + " gold nuggets. "
            + "Treasury: " + getBalance(player));
        plugin.getNationHistoryManager().log(nation,
            "💰 Treasury deposit: +" + amount + " (total: " + getBalance(player) + ")");
    }

    /** Withdraw gold nuggets from treasury into player's inventory. */
    public void withdraw(Player player, int amount) {
        if (amount <= 0) { player.sendMessage(ChatColor.RED + "Amount must be positive."); return; }
        Nation nation = plugin.getNationManager().getNation(player.getUniqueId());
        if (nation == null || !nation.isSetupComplete()) {
            player.sendMessage(ChatColor.RED + "You have no nation."); return;
        }
        long balance = getBalance(player);
        if (balance < amount) {
            player.sendMessage(ChatColor.RED + "Treasury only holds " + balance + " gold nuggets.");
            return;
        }
        balances.put(player.getUniqueId().toString(), balance - amount);
        save();
        player.getInventory().addItem(new ItemStack(Material.GOLD_NUGGET, amount));
        player.sendMessage(ChatColor.GOLD + "💰 Withdrew " + amount + " gold nuggets. "
            + "Treasury: " + getBalance(player));
    }

    /**
     * War conquest transfer: steal half the loser's treasury.
     * Called from PlayerDeathListener when a war kill occurs.
     */
    public void onWarKill(Player killer, Player loser) {
        String loserUUID  = loser.getUniqueId().toString();
        String killerUUID = killer.getUniqueId().toString();
        long loserBalance = balances.getOrDefault(loserUUID, 0L);
        if (loserBalance <= 0) return;

        long stolen = Math.max(1, loserBalance / 10); // steal 10% per death
        balances.put(loserUUID,  loserBalance - stolen);
        balances.merge(killerUUID, stolen, Long::sum);
        save();

        // Drop half the stolen amount physically at death location
        int dropped = (int) Math.min(stolen / 2, 64);
        if (dropped > 0)
            loser.getWorld().dropItemNaturally(
                loser.getLocation(), new ItemStack(Material.GOLD_NUGGET, dropped));

        killer.sendMessage(ChatColor.GOLD + "💰 War plunder! +" + stolen
            + " gold taken from " + loser.getName() + "'s treasury.");
        loser.sendMessage(ChatColor.RED + "💰 " + stolen
            + " gold stolen from your treasury by " + killer.getName() + "!");

        Nation loserNation  = plugin.getNationManager().getNation(loser.getUniqueId());
        Nation killerNation = plugin.getNationManager().getNation(killer.getUniqueId());
        if (loserNation  != null)
            plugin.getNationHistoryManager().log(loserNation,
                "💰 Treasury raided by " + killer.getName() + ": -" + stolen);
        if (killerNation != null)
            plugin.getNationHistoryManager().log(killerNation,
                "💰 Plundered " + loser.getName() + "'s treasury: +" + stolen);
    }

    // ── Inventory helpers ─────────────────────────────────────────────────────

    private int countGold(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.GOLD_NUGGET)
                count += item.getAmount();
        }
        return count;
    }

    private void removeGold(Player player, int amount) {
        ItemStack[] contents = player.getInventory().getContents();
        int remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != Material.GOLD_NUGGET) continue;
            if (item.getAmount() <= remaining) {
                remaining -= item.getAmount();
                contents[i] = null;
            } else {
                item.setAmount(item.getAmount() - remaining);
                remaining = 0;
            }
        }
        player.getInventory().setContents(contents);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void load() {
        var cfg = plugin.getDataManager().getLegendaryConfig();
        var sec = cfg.getConfigurationSection("treasury");
        if (sec == null) return;
        for (String uuid : sec.getKeys(false))
            balances.put(uuid, cfg.getLong("treasury." + uuid, 0L));
    }

    public void save() {
        var cfg = plugin.getDataManager().getLegendaryConfig();
        balances.forEach((uuid, bal) -> cfg.set("treasury." + uuid, bal));
        plugin.getDataManager().saveLegendary();
    }
}
