package net.exylia.exyliaPlayerVaults.manager;

import net.exylia.lib.item.Containers;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

/**
 * Counting what a search finds in a vault, shulker boxes and bundles included.
 *
 * <p>An item matches when its material, written the way a player says it
 * ({@code diamond sword}), or its display name contains what was typed,
 * ignoring case.
 */
public final class VaultSearch {

    /** How deep containers inside containers are opened; a bundle cannot nest further than this in survival. */
    private static final int DEPTH = 3;

    private VaultSearch() {
        throw new AssertionError("No instances.");
    }

    /** How many items in these contents match the query. */
    public static int count(ItemStack[] contents, String query) {
        String needle = normalise(query);
        if (needle.isEmpty() || contents == null) return 0;
        int found = 0;
        for (ItemStack stack : contents) found += count(stack, needle, 0);
        return found;
    }

    private static int count(ItemStack stack, String needle, int depth) {
        if (stack == null || stack.getType().isAir()) return 0;
        int found = matches(stack, needle) ? stack.getAmount() : 0;
        if (depth < DEPTH && Containers.isContainer(stack)) {
            for (ItemStack inner : Containers.contentsOf(stack)) found += count(inner, needle, depth + 1);
        }
        return found;
    }

    static boolean matches(ItemStack stack, String needle) {
        if (normalise(stack.getType().name()).contains(needle)) return true;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || meta.displayName() == null) return false;
        return normalise(PlainTextComponentSerializer.plainText().serialize(meta.displayName())).contains(needle);
    }

    static String normalise(String text) {
        return text == null ? "" : text.replace('_', ' ').trim().toLowerCase(Locale.ROOT);
    }
}
