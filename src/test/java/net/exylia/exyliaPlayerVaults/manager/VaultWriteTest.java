package net.exylia.exyliaPlayerVaults.manager;

import net.exylia.exyliaPlayerVaults.database.VaultEntity;
import net.exylia.exyliaPlayerVaults.testing.TestServer;
import net.exylia.lib.database.Databases;
import net.exylia.lib.database.MemoryDatabase;
import net.exylia.lib.database.Repository;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A vault window's writes land only over the row it read.
 *
 * <p>The contents are left empty: stacks need a running server to be stored,
 * and what is compared is the version, not the contents.
 */
class VaultWriteTest {

    private static Plugin plugin;
    private static Repository<VaultEntity> repo;
    private static VaultsManager manager;

    @BeforeAll
    static void server() throws Exception {
        TestServer.install();
        plugin = TestServer.plugin("VaultsServer");
        MemoryDatabase.install(plugin, "vault-writes");
        repo = Databases.of(plugin).repository(VaultEntity.class);
        manager = TestServer.allocate(VaultsManager.class);
        TestServer.set(manager, "repo", repo);
    }

    /** A window on a vault, as it was when its row was read. */
    private static VaultHolder window(UUID owner, boolean exists, Long version) {
        return new VaultHolder(owner, "Owner", 1, UUID.randomUUID(), false, new CompletableFuture<>(),
                new ItemStack[0], new ItemStack[0], true, exists, version);
    }

    private static VaultEntity row(UUID owner, long version) {
        return new VaultEntity(owner, 1, null, version);
    }

    private static Long stored(UUID owner) throws Exception {
        return repo.find(VaultEntity.id(owner, 1)).get(30, TimeUnit.SECONDS).map(VaultEntity::version).orElse(null);
    }

    @Test
    @DisplayName("a write over a row another server wrote since is refused, and the newer row kept")
    void newerRowKept() throws Exception {
        UUID owner = UUID.randomUUID();
        repo.save(row(owner, 100L)).get(30, TimeUnit.SECONDS);
        VaultHolder window = window(owner, true, 100L);
        repo.save(row(owner, 200L)).get(30, TimeUnit.SECONDS);

        VaultsManager.Outcome outcome = manager.save(window, row(owner, 300L), 1).get(30, TimeUnit.SECONDS);

        assertEquals(VaultsManager.Outcome.CONFLICT, outcome);
        assertEquals(200L, stored(owner), "the other server's vault was overwritten");
    }

    @Test
    @DisplayName("a vault's first write does not overwrite a row another server created meanwhile")
    void createdMeanwhileKept() throws Exception {
        UUID owner = UUID.randomUUID();
        VaultHolder window = window(owner, false, null);
        repo.save(row(owner, 200L)).get(30, TimeUnit.SECONDS);

        VaultsManager.Outcome outcome = manager.save(window, row(owner, 300L), 1).get(30, TimeUnit.SECONDS);

        assertEquals(VaultsManager.Outcome.CONFLICT, outcome);
        assertEquals(200L, stored(owner), "the other server's vault was overwritten");
    }

    @Test
    @DisplayName("a new vault's first write creates its row")
    void firstWrite() throws Exception {
        UUID owner = UUID.randomUUID();

        VaultsManager.Outcome outcome = manager.save(window(owner, false, null), row(owner, 300L), 1)
                .get(30, TimeUnit.SECONDS);

        assertEquals(VaultsManager.Outcome.WRITTEN, outcome);
        assertEquals(300L, stored(owner));
    }

    @Test
    @DisplayName("a write finding an earlier unanswered write of its own carries on from it")
    void unansweredWriteAdopted() throws Exception {
        UUID owner = UUID.randomUUID();
        repo.save(row(owner, 100L)).get(30, TimeUnit.SECONDS);
        VaultHolder window = window(owner, true, 100L);
        window.unconfirmed(500L, new ItemStack[0]);
        repo.save(row(owner, 500L)).get(30, TimeUnit.SECONDS);

        VaultsManager.Outcome outcome = manager.save(window, row(owner, 600L), 1).get(30, TimeUnit.SECONDS);

        assertEquals(VaultsManager.Outcome.WRITTEN, outcome);
        assertEquals(600L, stored(owner));
    }
}
