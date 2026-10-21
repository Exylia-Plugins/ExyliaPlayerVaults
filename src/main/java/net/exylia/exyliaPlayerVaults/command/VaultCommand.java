package net.exylia.exyliaPlayerVaults.command;

import net.exylia.exyliaPlayerVaults.ExyliaPlayerVaults;
import net.exylia.exyliaPlayerVaults.Permissions;
import net.exylia.exyliaPlayerVaults.menu.SearchMenu;
import net.exylia.exyliaPlayerVaults.menu.VaultListMenu;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;

@Command({"pv", "playervault", "playervaults", "vault", "vaults"})
@CommandPermission(Permissions.USE)
public class VaultCommand {

    /**
     * Opens a vault, or the list of them.
     *
     * <p>{@code /pv} with nothing after it is the list, because a player
     * with six of them does not remember which one the spare tools are in.
     */
    @CommandPlaceholder
    public void vault(Player player, @Optional Integer number) {
        ExyliaPlayerVaults core = ExyliaPlayerVaults.get();
        if (number == null) {
            VaultListMenu.open(player);
            return;
        }
        core.getVaultActions().openOwn(player, number);
    }

    /** Which vaults hold an item, by material or name; each result opens its vault. */
    @Subcommand("search")
    @CommandPermission(Permissions.SEARCH)
    public void search(Player player, String query) {
        SearchMenu.open(player, player.getUniqueId(), player.getName(), query, () -> {
        });
    }
}
