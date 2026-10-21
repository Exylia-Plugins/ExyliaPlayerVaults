package net.exylia.exyliaPlayerVaults.menu;

import net.exylia.exyliaPlayerVaults.ExyliaPlayerVaults;
import net.exylia.lib.ui.UiEntry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The staff entry point: everybody online here, each opening their vault list.
 *
 * <p>The list follows joins and quits while it is open. Players offline or on
 * another server are reached with {@code /pvadmin list <player>}.
 */
public final class PlayersMenu {

    /** The menu's file, as {@link VaultMenus} registers it. */
    public static final String ID = "menus/admin/players";

    private PlayersMenu() {
        throw new AssertionError("No instances.");
    }

    public static void open(Player admin) {
        ExyliaPlayerVaults.get().getMenus().open(admin, ID, Map.of(), PlayersMenu::rows);
    }

    private static List<UiEntry> rows() {
        return Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .map(player -> UiEntry.of(player.getUniqueId())
                        .with("player_name", player.getName())
                        .withFormatted("player_id", player.getUniqueId().toString())
                        .build())
                .toList();
    }
}
