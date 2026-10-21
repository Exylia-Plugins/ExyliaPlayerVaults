package net.exylia.exyliaPlayerVaults.menu;

import net.exylia.exyliaPlayerVaults.ExyliaPlayerVaults;
import net.exylia.exyliaPlayerVaults.config.VaultMessages;
import net.exylia.exyliaPlayerVaults.database.VaultEntity;
import net.exylia.exyliaPlayerVaults.database.VaultSnapshotSummary;
import net.exylia.lib.format.Formats;
import net.exylia.lib.text.Values;
import net.exylia.lib.ui.UiEntry;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The earlier copies of one vault, newest first: look at one, or put it back.
 */
public final class HistoryMenu {

    /** The menu's file, as {@link VaultMenus} registers it. */
    public static final String ID = "menus/admin/history";

    private HistoryMenu() {
        throw new AssertionError("No instances.");
    }

    public static void open(Player viewer, UUID owner, String ownerName, int number) {
        ExyliaPlayerVaults core = ExyliaPlayerVaults.get();
        Values values = Values.of().put("number", number).put("player", ownerName);
        core.getHistory().list(VaultEntity.id(owner, number)).whenComplete((copies, failure) ->
                core.getTasks().runAtEntity(viewer, () -> {
                    if (!viewer.isOnline()) return;
                    if (failure != null) {
                        core.getMessages().send(viewer, VaultMessages.get().unreadable(), values);
                        return;
                    }
                    if (copies.isEmpty()) {
                        core.getMessages().send(viewer, VaultMessages.get().historyEmpty(), values);
                        return;
                    }
                    List<UiEntry> rows = new ArrayList<>(copies.size());
                    for (VaultSnapshotSummary copy : copies) {
                        rows.add(UiEntry.of(copy.id())
                                .withFormatted("snapshot_id", copy.id())
                                .withFormatted("snapshot_date", Formats.date(copy.createdAt()))
                                .withFormatted("snapshot_when", Formats.relative(copy.createdAt()))
                                .withFormatted("snapshot_cause", copy.cause())
                                .with("snapshot_actor", copy.actorName())
                                .withFormatted("snapshot_used", copy.used())
                                .build());
                    }
                    Values context = Values.of()
                            .put("owner_name", ownerName)
                            .put("vault_number", number)
                            .put("history_size", copies.size());
                    core.getMenus().open(viewer, ID, context.map(), rows);
                }));
    }
}
