package net.exylia.exyliaPlayerVaults.manager;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * A window onto vault contents that nothing can leave: a vault looked at
 * through {@code /pvadmin view}, or an earlier copy of one from its history.
 *
 * <p>The listener cancels every click and drag while one is on top, the
 * player's own inventory included, so not even a shift-click or a double-click
 * collecting matching stacks moves an item. The window is a copy and is never
 * written anywhere.
 */
public final class ReadOnlyHolder implements InventoryHolder {

    private Inventory inventory;

    private ReadOnlyHolder() {
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /** Opens a read-only copy of these contents, on the viewer's thread. */
    public static void open(Player viewer, Component title, ItemStack[] contents) {
        ReadOnlyHolder holder = new ReadOnlyHolder();
        holder.inventory = Bukkit.createInventory(holder, VaultsManager.MAX_SLOTS, title);
        for (int slot = 0; slot < Math.min(VaultsManager.MAX_SLOTS, contents.length); slot++) {
            if (contents[slot] != null) holder.inventory.setItem(slot, contents[slot].clone());
        }
        viewer.openInventory(holder.inventory);
    }
}
