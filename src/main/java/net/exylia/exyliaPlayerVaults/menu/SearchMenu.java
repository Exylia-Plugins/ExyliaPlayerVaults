package net.exylia.exyliaPlayerVaults.menu;

import net.exylia.exyliaPlayerVaults.ExyliaPlayerVaults;
import net.exylia.exyliaPlayerVaults.config.VaultMessages;
import net.exylia.exyliaPlayerVaults.config.VaultSettings;
import net.exylia.exyliaPlayerVaults.database.VaultEntity;
import net.exylia.exyliaPlayerVaults.database.VaultLook;
import net.exylia.exyliaPlayerVaults.manager.VaultSearch;
import net.exylia.exyliaPlayerVaults.manager.VaultsManager;
import net.exylia.lib.text.Values;
import net.exylia.lib.ui.UiEntry;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Which vaults hold an item, by material or name, shulker boxes and bundles
 * included; each one opens from here.
 *
 * <p>Reads what is stored: a vault open right now counts as of its last write.
 * Staff search somebody else's vaults with every stored vault counted, as the
 * vault list draws them.
 */
public final class SearchMenu {

    /** The menu's file, as {@link VaultMenus} registers it. */
    public static final String ID = "menus/user/search";

    private SearchMenu() {
        throw new AssertionError("No instances.");
    }

    /**
     * Searches and opens the results, or says nothing matched and runs {@code none}.
     */
    public static void open(Player viewer, UUID owner, String ownerName, String query, Runnable none) {
        ExyliaPlayerVaults core = ExyliaPlayerVaults.get();
        if (!core.getAccess().maySearch(viewer)) {
            core.getMessages().send(viewer, VaultMessages.get().cooldown().replace("%time%", "3s"));
            none.run();
            return;
        }
        int unlocked = viewer.getUniqueId().equals(owner) ? core.getAccess().unlocked(viewer) : Integer.MAX_VALUE;
        String defaultIcon = VaultSettings.get().customization().defaultIcon();
        core.getVaults().all(owner)
                .thenCombine(core.getLooks().of(owner), (vaults, looks) -> {
                    List<UiEntry> rows = new ArrayList<>();
                    vaults.stream().sorted(Comparator.comparingInt(VaultEntity::number)).forEach(vault -> {
                        int amount = VaultSearch.count(vault.contents(), query);
                        if (vault.number() > unlocked || amount == 0) return;
                        VaultLook look = looks.get(vault.number());
                        rows.add(UiEntry.of(vault.number())
                                .withFormatted("vault_number", vault.number())
                                .withFormatted("vault_owner", owner.toString())
                                .with("vault_name", VaultsManager.displayName(look == null ? null : look.name(), vault.number()))
                                .with("vault_icon", look == null || look.icon() == null ? defaultIcon : look.icon())
                                .withFormatted("vault_amount", amount)
                                .build());
                    });
                    return rows;
                })
                .whenComplete((rows, failure) -> core.getTasks().runAtEntity(viewer, () -> {
                    if (!viewer.isOnline()) return;
                    if (failure != null) {
                        core.getMessages().send(viewer, VaultMessages.get().unreadable());
                        none.run();
                        return;
                    }
                    if (rows.isEmpty()) {
                        core.getMessages().send(viewer, VaultMessages.get().searchNone(), Values.of().putText("query", query));
                        none.run();
                        return;
                    }
                    Values context = Values.of()
                            .put("owner_name", ownerName)
                            .put("owner_id", owner.toString())
                            .putText("search_query", query)
                            .put("search_hits", rows.size());
                    core.getMenus().open(viewer, ID, context.map(), rows);
                }));
    }
}
