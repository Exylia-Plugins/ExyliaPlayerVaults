package net.exylia.exyliaPlayerVaults.manager;

import net.exylia.exyliaPlayerVaults.ExyliaPlayerVaults;
import net.exylia.exyliaPlayerVaults.config.VaultSettings;
import net.exylia.exyliaPlayerVaults.database.VaultEntity;
import net.exylia.exyliaPlayerVaults.database.VaultSnapshot;
import net.exylia.exyliaPlayerVaults.database.VaultSnapshotSummary;
import net.exylia.lib.database.PluginDatabase;
import net.exylia.lib.database.Repository;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Earlier copies of each vault, newest first, a few per vault.
 *
 * <p>A copy is what the vault held <em>before</em> something changed it — a
 * player closing it changed, staff editing, clearing or restoring it — so the
 * newest copy is always one step behind the live row, and putting any copy
 * back is itself kept as a copy and can be undone.
 */
public final class VaultHistory {

    public static final String CLOSED = "CLOSED";
    public static final String EDITED = "EDITED";
    public static final String CLEARED = "CLEARED";
    public static final String RESTORED = "RESTORED";

    private final ExyliaPlayerVaults core;
    private final Repository<VaultSnapshot> repo;
    private final Repository<VaultSnapshotSummary> summaries;

    public VaultHistory(ExyliaPlayerVaults core, PluginDatabase database) {
        this.core = core;
        this.repo = database.repository(VaultSnapshot.class);
        this.summaries = database.repository(VaultSnapshotSummary.class);
    }

    /** Keeps a copy of these contents, then drops the copies past the configured number. */
    public void capture(String vault, ItemStack[] contents, String cause, String actorName) {
        int keep = VaultSettings.get().history().keep();
        if (keep <= 0) return;
        ItemStack[] copy = new ItemStack[contents == null ? 0 : contents.length];
        for (int slot = 0; slot < copy.length; slot++) {
            if (contents[slot] != null) copy[slot] = contents[slot].clone();
        }
        repo.insert(new VaultSnapshot(0L, vault, copy, VaultEntity.count(copy), cause,
                        actorName == null ? "?" : actorName, System.currentTimeMillis()))
                .thenCompose(id -> list(vault))
                .thenAccept(all -> all.stream().skip(keep).forEach(old -> repo.delete(old.id())))
                .exceptionally(failure -> {
                    core.getDebug().error("Could not keep a copy of the vault " + vault + " in its history.", failure);
                    return null;
                });
    }

    /** A vault's copies, newest first, without their contents. */
    public CompletableFuture<List<VaultSnapshotSummary>> list(String vault) {
        return summaries.where("vault", vault).orderByDescending("created_at").find();
    }

    /** One copy, contents included. */
    public CompletableFuture<Optional<VaultSnapshot>> find(long id) {
        return repo.find(id);
    }
}
