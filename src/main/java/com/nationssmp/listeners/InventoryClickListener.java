package com.nationssmp.listeners;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;

/**
 * Handles clicks in the animal-selection and block-selection GUIs
 * during the first-login setup flow.
 */
public class InventoryClickListener implements Listener {

    private static final String ANIMAL_TITLE = ChatColor.DARK_PURPLE + "Choose Your Nation Animal";
    private static final String BLOCK_TITLE  = ChatColor.DARK_AQUA   + "Choose Your Nation Block";

    private final NationsSMP plugin;

    public InventoryClickListener(NationsSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;

        String title = event.getView().getTitle();

        // ── Animal selection GUI ──────────────────────────────────────────────
        if (ANIMAL_TITLE.equals(title)) {
            event.setCancelled(true);
            Nation nation = plugin.getNationManager().getNation(player.getUniqueId());
            if (nation == null || nation.isSetupComplete()) return;
            if (event.getCurrentItem().getType() == Material.BARRIER) {
                player.sendMessage(ChatColor.RED + "That animal is already taken!");
                return;
            }
            plugin.getNationManager().handleAnimalChoice(player, event.getSlot());
        }

        // ── Block selection GUI ───────────────────────────────────────────────
        if (BLOCK_TITLE.equals(title)) {
            event.setCancelled(true);
            Nation nation = plugin.getNationManager().getNation(player.getUniqueId());
            if (nation == null || nation.isSetupComplete()) return;
            ItemStack clicked = event.getCurrentItem();
            if (clicked.getType() == Material.BARRIER) {
                player.sendMessage(ChatColor.RED + "That block is already taken!");
                return;
            }
            plugin.getNationManager().handleBlockChoice(player, clicked.getType());
        }

        // ── Banner: base colour picker ────────────────────────────────────────
        if (com.nationssmp.managers.BannerManager.TITLE_BASE_COLOR.equals(title)) {
            event.setCancelled(true);
            plugin.getBannerManager().handleBaseColorClick(player, event.getSlot());
        }

        // ── Banner: pattern selector ──────────────────────────────────────────
        if (com.nationssmp.managers.BannerManager.TITLE_PATTERN.equals(title)) {
            event.setCancelled(true);
            plugin.getBannerManager().handlePatternClick(player, event.getSlot());
        }

        // ── Banner: pattern colour picker ─────────────────────────────────────
        if (com.nationssmp.managers.BannerManager.TITLE_PATTERN_COLOR.equals(title)) {
            event.setCancelled(true);
            plugin.getBannerManager().handlePatternColorClick(player, event.getSlot());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = event.getView().getTitle();
        Nation nation = plugin.getNationManager().getNation(player.getUniqueId());
        if (nation == null || nation.isSetupComplete()) return;

        // If they closed mid-setup, reopen after a tick
        if (ANIMAL_TITLE.equals(title) &&
                nation.getPendingStep() == Nation.SetupStep.AWAITING_ANIMAL_SELECTION) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(ChatColor.RED + "You must choose an animal to continue setup.");
                plugin.getNationManager().openAnimalGUI(player);
            }, 20L);
        }
        if (BLOCK_TITLE.equals(title) &&
                nation.getPendingStep() == Nation.SetupStep.AWAITING_BLOCK_SELECTION) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(ChatColor.RED + "You must choose a building block to continue setup.");
                plugin.getNationManager().openBlockGUI(player);
            }, 20L);
        }
        // If banner editor closed mid-setup, reopen it
        if ((com.nationssmp.managers.BannerManager.TITLE_BASE_COLOR.equals(title)
          || com.nationssmp.managers.BannerManager.TITLE_PATTERN.equals(title)
          || com.nationssmp.managers.BannerManager.TITLE_PATTERN_COLOR.equals(title))
          && nation != null && !nation.isSetupComplete()
          && nation.getPendingStep() == Nation.SetupStep.AWAITING_BANNER_DESIGN) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(ChatColor.RED + "You must design your banner to complete setup.");
                com.nationssmp.managers.BannerManager.BannerStage s =
                    plugin.getBannerManager().getStage(player.getUniqueId());
                if (s == com.nationssmp.managers.BannerManager.BannerStage.PATTERN_COLOR)
                    plugin.getBannerManager().openPatternColorPicker(player);
                else if (s == com.nationssmp.managers.BannerManager.BannerStage.PATTERN)
                    plugin.getBannerManager().openPatternMenu(player);
                else
                    plugin.getBannerManager().openBaseColorPicker(player);
            }, 20L);
        }
    }
}
