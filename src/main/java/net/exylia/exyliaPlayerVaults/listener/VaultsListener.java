package net.exylia.exyliaPlayerVaults.listener;

import net.exylia.exyliaPlayerVaults.ExyliaPlayerVaults;
import net.exylia.exyliaPlayerVaults.Permissions;
import net.exylia.exyliaPlayerVaults.config.VaultMessages;
import net.exylia.exyliaPlayerVaults.config.VaultSettings;
import net.exylia.lib.util.combat.Culprits;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import net.exylia.exyliaPlayerVaults.manager.ReadOnlyHolder;
import net.exylia.exyliaPlayerVaults.manager.VaultHolder;
import net.exylia.exyliaPlayerVaults.manager.VaultsManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * The events a vault window has to answer.
 *
 * <p>Closing writes it and gives its lock back. Every ending a player can
 * cause — escape, a command that opens something else, a disconnect, a death —
 * is the same ending: Bukkit fires a close for all of them.
 */
public final class VaultsListener implements Listener {

    private final ExyliaPlayerVaults core;

    public VaultsListener(ExyliaPlayerVaults core) {
        this.core = core;
        core.getPlugin().getServer().getPluginManager().registerEvents(this, core.getPlugin());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof VaultHolder holder) {
            core.getVaults().release(holder, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        core.getAccess().load(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        core.getVaults().releaseViewer(event.getPlayer().getUniqueId());
        core.getAccess().forget(event.getPlayer().getUniqueId());
    }

    /**
     * A read-only window: nothing moves, whichever inventory was clicked.
     *
     * <p>Cancelled at the highest priority, after any plugin that might
     * un-cancel it, and whichever inventory was clicked: a click in the
     * player's own inventory can still pull from the top one (a shift-click, a
     * double-click collecting matching stacks).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onReadOnlyClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof ReadOnlyHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onReadOnlyDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof ReadOnlyHolder) {
            event.setCancelled(true);
        }
    }

    /**
     * A player hit by or hitting another player closes their vault, when vaults
     * are off in combat: opened before the fight, it would otherwise be a place
     * to stash loot while being hit.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFight(EntityDamageByEntityEvent event) {
        if (!VaultSettings.get().restrictions().blockInCombat()) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = Culprits.playerBehind(event);
        if (attacker == null || attacker.equals(victim)) return;
        for (Player fighter : new Player[]{victim, attacker}) {
            if (!fighter.hasPermission(Permissions.BYPASS_RESTRICTIONS)) core.getVaults().closeForCombat(fighter);
        }
    }

    /**
     * Keeps a blocked item out, by every route there is into a container.
     *
     * <p>Placing it, shift-clicking it up from the player's own inventory, and
     * swapping it in with a number key or the offhand key are different events
     * to the server and one gesture to the player.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof VaultHolder holder) || holder.admin()) return;
        if (!(event.getWhoClicked() instanceof Player player) || player.hasPermission(Permissions.BYPASS_BLOCKED)) return;

        ItemStack incoming = incoming(event);
        if (incoming == null || !VaultsManager.isBlocked(incoming)) return;

        event.setCancelled(true);
        core.getMessages().send(player, VaultMessages.get().blockedItem());
    }

    /** Dragging a blocked item across the vault's slots. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof VaultHolder holder) || holder.admin()) return;
        if (!(event.getWhoClicked() instanceof Player player) || player.hasPermission(Permissions.BYPASS_BLOCKED)) return;

        if (!VaultsManager.isBlocked(event.getOldCursor())) return;
        boolean intoVault = event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize());
        if (!intoVault) return;

        event.setCancelled(true);
        core.getMessages().send(player, VaultMessages.get().blockedItem());
    }

    /**
     * What a click would put into the vault, or {@code null} when it puts
     * nothing in.
     *
     * <p>The offhand key is a keyboard click with no hotbar button, so it is
     * asked first: read as a number key, it would look like it moves nothing.
     */
    static ItemStack incoming(InventoryClickEvent event) {
        boolean intoTop = event.getClickedInventory() == event.getView().getTopInventory();

        if (event.getClick() == ClickType.SWAP_OFFHAND && intoTop) {
            return event.getWhoClicked().getInventory().getItemInOffHand();
        }
        if (event.getClick().isKeyboardClick() && intoTop) {
            int hotbar = event.getHotbarButton();
            return hotbar < 0 ? null : event.getWhoClicked().getInventory().getItem(hotbar);
        }
        if (event.isShiftClick() && !intoTop) {
            return event.getCurrentItem();
        }
        // Bundles (1.21.2+), by name so older servers still load this class:
        // the cursor's item going into a bundle stored in the vault, or a
        // bundle on the cursor emptying into a vault slot.
        String action = event.getAction().name();
        if (intoTop && (action.endsWith("INTO_BUNDLE") || action.equals("PLACE_FROM_BUNDLE"))) {
            return event.getCursor();
        }
        if (intoTop && (event.getAction() == InventoryAction.PLACE_ALL
                || event.getAction() == InventoryAction.PLACE_ONE
                || event.getAction() == InventoryAction.PLACE_SOME
                || event.getAction() == InventoryAction.SWAP_WITH_CURSOR)) {
            return event.getCursor();
        }
        return null;
    }
}
