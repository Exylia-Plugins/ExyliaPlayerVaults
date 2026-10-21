package net.exylia.exyliaPlayerVaults.command;

import net.exylia.exyliaPlayerVaults.ExyliaPlayerVaults;
import net.exylia.exyliaPlayerVaults.Permissions;
import net.exylia.exyliaPlayerVaults.config.VaultMessages;
import net.exylia.exyliaPlayerVaults.menu.HistoryMenu;
import net.exylia.exyliaPlayerVaults.menu.PlayersMenu;
import net.exylia.exyliaPlayerVaults.menu.VaultListMenu;
import net.exylia.lib.player.ExyliaPlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.annotation.Range;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;

/**
 * Staff tools over anybody's vaults, online, offline or on another server.
 *
 * <p>Looking is read only and takes no lock. Editing, clearing and restoring
 * take the vault's lock like a player's window does, so they never run while
 * the vault is open anywhere: the window holding it is written and closed
 * first, wherever it is.
 */
@Command({"pvadmin", "playervaultsadmin"})
public class VaultAdminCommand {

    @CommandPlaceholder
    @CommandPermission(Permissions.ADMIN_VIEW)
    public void usage(CommandSender sender) {
        if (sender instanceof Player admin) {
            PlayersMenu.open(admin);
            return;
        }
        ExyliaPlayerVaults.get().getMessages().send(sender, VaultMessages.get().adminUsage(), null);
    }

    /** Somebody's vault list; each vault opens for editing or read only, as the sender may. */
    @Subcommand("list")
    @CommandPermission(Permissions.ADMIN_VIEW)
    public void list(Player admin, ExyliaPlayer target) {
        VaultListMenu.open(admin, target.id(), target.name());
    }

    /** A vault as it is stored, read only, without closing it for whoever has it open. */
    @Subcommand("view")
    @CommandPermission(Permissions.ADMIN_VIEW)
    public void view(Player admin, ExyliaPlayer target, @Range(min = 1) int number) {
        ExyliaPlayerVaults.get().getVaults().view(admin, target.id(), target.name(), number);
    }

    /**
     * Opens somebody's vault to change it.
     *
     * <p>The same single window the owner would get: a vault open somewhere
     * is written and closed there first, then opened here.
     */
    @Subcommand("edit")
    @CommandPermission(Permissions.ADMIN_EDIT)
    public void edit(Player admin, ExyliaPlayer target, @Range(min = 1) int number) {
        ExyliaPlayerVaults.get().getVaults().open(admin, target.id(), target.name(), number, true);
    }

    @Subcommand("history")
    @CommandPermission(Permissions.ADMIN_HISTORY)
    public void history(Player admin, ExyliaPlayer target, @Range(min = 1) int number) {
        HistoryMenu.open(admin, target.id(), target.name(), number);
    }

    /** Empties a vault after a confirmation; what it held goes to its history and can be put back. */
    @Subcommand("clear")
    @CommandPermission(Permissions.ADMIN_CLEAR)
    public void clear(Player admin, ExyliaPlayer target, @Range(min = 1) int number) {
        ExyliaPlayerVaults.get().getVaultActions().clear(admin, target.id(), target.name(), number, () -> {
        });
    }

    @Subcommand("reload")
    @CommandPermission(Permissions.ADMIN_RELOAD)
    public void reload(CommandSender sender) {
        ExyliaPlayerVaults core = ExyliaPlayerVaults.get();
        core.reload(sender);
        core.getMessages().send(sender, VaultMessages.get().reloaded());
    }
}
