package net.exylia.exyliaPlayerVaults.manager;

import net.exylia.exyliaPlayerVaults.config.VaultSettings;
import net.exylia.exyliaPlayerVaults.database.VaultBuyer;
import net.exylia.exyliaPlayerVaults.testing.TestServer;
import net.exylia.lib.database.Databases;
import net.exylia.lib.database.MemoryDatabase;
import net.exylia.lib.database.Repository;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rules a purchase, a name and a search lean on. */
class VaultRulesTest {

    private static Repository<VaultBuyer> buyers;

    @BeforeAll
    static void server() {
        TestServer.install();
        Plugin plugin = TestServer.plugin("VaultRulesServer");
        MemoryDatabase.install(plugin, "vault-rules");
        buyers = Databases.of(plugin).repository(VaultBuyer.class);
    }

    @Test
    @DisplayName("two purchases racing for the same slot count once")
    void racingPurchasesCountOnce() throws Exception {
        String player = UUID.randomUUID().toString();
        buyers.increment(new VaultBuyer(player, 0), "purchased").get(30, TimeUnit.SECONDS);

        CompletableFuture<Boolean> first = buyers.updateIf(new VaultBuyer(player, 1), "purchased", 0);
        CompletableFuture<Boolean> second = buyers.updateIf(new VaultBuyer(player, 1), "purchased", 0);

        int won = (first.get(30, TimeUnit.SECONDS) ? 1 : 0) + (second.get(30, TimeUnit.SECONDS) ? 1 : 0);
        assertEquals(1, won, "exactly one purchase may take the slot; the other is refunded");
        assertEquals(1, buyers.findFresh(player).get(30, TimeUnit.SECONDS).orElseThrow().purchased());
    }

    @Test
    @DisplayName("creating a buyer row never resets one another server already counted")
    void creatingKeepsTheCount() throws Exception {
        String player = UUID.randomUUID().toString();
        buyers.increment(new VaultBuyer(player, 0), "purchased").get(30, TimeUnit.SECONDS);
        buyers.updateIf(new VaultBuyer(player, 3), "purchased", 0).get(30, TimeUnit.SECONDS);

        buyers.increment(new VaultBuyer(player, 0), "purchased").get(30, TimeUnit.SECONDS);

        assertEquals(3, buyers.findFresh(player).get(30, TimeUnit.SECONDS).orElseThrow().purchased());
    }

    @Test
    @DisplayName("each bought vault costs the multiplier times the one before")
    void pricesGrow() {
        VaultSettings.Purchase purchase = new VaultSettings.Purchase(true, "", 1000.0, 2.0, 5);
        assertEquals(1000.0, purchase.priceAfter(0));
        assertEquals(4000.0, purchase.priceAfter(2));
        assertEquals(1000.0, new VaultSettings.Purchase(true, "", 1000.0, 1.0, 5).priceAfter(4));
    }

    @Test
    @DisplayName("a vault name can carry no colours, tags or placeholders")
    void namesAreInert() {
        assertTrue(VaultSettings.Customization.allowed("Spare tools #2"));
        assertTrue(VaultSettings.Customization.allowed("Minería (fase 1)"));
        assertFalse(VaultSettings.Customization.allowed("{error}Red"));
        assertFalse(VaultSettings.Customization.allowed("<click:run_command:/op me>x"));
        assertFalse(VaultSettings.Customization.allowed("%player_ip%"));
        assertFalse(VaultSettings.Customization.allowed("&cRed"));
        assertFalse(VaultSettings.Customization.allowed("§cRed"));
    }

    @Test
    @DisplayName("a search reads materials the way players write them")
    void searchNormalises() {
        assertEquals("diamond sword", VaultSearch.normalise("DIAMOND_SWORD"));
        assertEquals("diamond sword", VaultSearch.normalise("  Diamond_Sword "));
        assertEquals(0, VaultSearch.count(null, "dirt"));
    }
}
