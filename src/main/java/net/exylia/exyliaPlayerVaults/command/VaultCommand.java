package net.exylia.exyliaPlayerVaults.command;

import net.exylia.exyliaPlayerVaults.ExyliaPlayerVaults;
import net.exylia.exyliaPlayerVaults.Permissions;
import net.exylia.exyliaPlayerVaults.database.VaultLook;
import net.exylia.exyliaPlayerVaults.config.VaultMessages;
import net.exylia.exyliaPlayerVaults.database.VaultEntity;
import net.exylia.exyliaPlayerVaults.manager.VaultSearch;
import net.exylia.exyliaPlayerVaults.manager.VaultsManager;
import net.exylia.exyliaPlayerVaults.menu.VaultListMenu;
import net.exylia.lib.text.Values;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    /**
     * Which vaults hold an item, by material or name, shulker boxes and bundles included.
     *
     * <p>Reads what is stored: a vault open right now counts as of its last write.
     */
    @Subcommand("search")
    @CommandPermission(Permissions.SEARCH)
    public void search(Player player, String query) {
        ExyliaPlayerVaults core = ExyliaPlayerVaults.get();
        if (!core.getAccess().maySearch(player)) {
            core.getMessages().send(player, VaultMessages.get().cooldown().replace("%time%", "3s"));
            return;
        }
        int unlocked = core.getAccess().unlocked(player);
        core.getVaults().all(player.getUniqueId())
                .thenCombine(core.getLooks().of(player.getUniqueId()), (vaults, looks) -> {
                    List<Hit> hits = new ArrayList<>();
                    vaults.stream().sorted(Comparator.comparingInt(VaultEntity::number)).forEach(vault -> {
                        int amount = VaultSearch.count(vault.contents(), query);
                        if (vault.number() > unlocked || amount == 0) return;
                        VaultLook look = looks.get(vault.number());
                        hits.add(new Hit(VaultsManager.displayName(look == null ? null : look.name(), vault.number()), amount));
                    });
                    return hits;
                })
                .whenComplete((hits, failure) -> core.getTasks().runAtEntity(player, () -> {
                    VaultMessages text = VaultMessages.get();
                    if (failure != null) {
                        core.getMessages().send(player, text.unreadable());
                        return;
                    }
                    if (hits.isEmpty()) {
                        core.getMessages().send(player, text.searchNone(), Values.of().putText("query", query));
                        return;
                    }
                    int total = hits.stream().mapToInt(Hit::amount).sum();
                    core.getMessages().send(player, text.searchHeader(), Values.of().putText("query", query).put("amount", total));
                    for (Hit hit : hits) {
                        core.getMessages().send(player, text.searchLine(), Values.of().putText("name", hit.name()).put("amount", hit.amount()));
                    }
                }));
    }

    /** One vault holding what was searched for. */
    private record Hit(String name, int amount) {
    }
}
