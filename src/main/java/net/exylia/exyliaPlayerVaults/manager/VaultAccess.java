package net.exylia.exyliaPlayerVaults.manager;

import net.exylia.exyliaPlayerVaults.ExyliaPlayerVaults;
import net.exylia.exyliaPlayerVaults.Permissions;
import net.exylia.exyliaPlayerVaults.config.VaultMessages;
import net.exylia.exyliaPlayerVaults.config.VaultSettings;
import net.exylia.exyliaPlayerVaults.database.VaultBuyer;
import net.exylia.lib.database.PluginDatabase;
import net.exylia.lib.database.Repository;
import net.exylia.lib.economy.Economy;
import net.exylia.lib.text.Values;
import net.exylia.lib.util.PermissionNumbers;
import net.exylia.lib.util.TimeFormats;
import net.exylia.lib.util.combat.Combat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who may open how many vaults of what size, where and when — and buying more.
 *
 * <p>How many a player bought is read when they join and kept while they are
 * here, because the vault list and the placeholders ask for it on the spot.
 * Every change to it is a compare-and-set on the row, so a purchase made on
 * two servers at once counts once.
 */
public final class VaultAccess {

    private final ExyliaPlayerVaults core;
    private final Repository<VaultBuyer> buyers;
    private final Map<UUID, Integer> purchased = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastOpen = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSearch = new ConcurrentHashMap<>();
    /** Players whose purchase is on its way, so a double click cannot pay twice. */
    private final Map<UUID, Boolean> buying = new ConcurrentHashMap<>();

    public VaultAccess(ExyliaPlayerVaults core, PluginDatabase database) {
        this.core = core;
        this.buyers = database.repository(VaultBuyer.class);
    }

    // ------------------------------------------------------------------ limits

    /** How many vaults this player may open: their rank's, plus what they bought, under the cap. */
    public int unlocked(Player player) {
        VaultSettings.Vaults vaults = VaultSettings.get().vaults();
        int fromRank = PermissionNumbers.highest(player, Permissions.AMOUNT_PREFIX, vaults.defaultAmount());
        return clamp(fromRank + purchased(player.getUniqueId()), 0, Math.max(0, vaults.maxAmount()));
    }

    /** How many rows each of this player's vaults has, 1 to 6. */
    public int rows(Player player) {
        return clamp(PermissionNumbers.highest(player, Permissions.ROWS_PREFIX, VaultSettings.get().vaults().defaultRows()), 1, 6);
    }

    /** How many vaults this player bought, as last read; 0 before the read arrives. */
    public int purchased(UUID player) {
        return purchased.getOrDefault(player, 0);
    }

    /**
     * Why this player may not open a vault right now, as a message, or
     * {@code null} when they may. Starts the cooldown when they may.
     */
    public String refusal(Player player) {
        VaultMessages text = VaultMessages.get();
        VaultSettings.Restrictions rules = VaultSettings.get().restrictions();
        if (!player.hasPermission(Permissions.BYPASS_RESTRICTIONS)) {
            if (rules.disabledWorlds().stream().anyMatch(world -> world.equalsIgnoreCase(player.getWorld().getName()))) {
                return text.disabledWorld();
            }
            if (rules.disabledGameModes().stream().anyMatch(mode -> mode.equalsIgnoreCase(player.getGameMode().name()))) {
                return text.disabledGameMode();
            }
            if (rules.blockInCombat() && Combat.isTagged(player)) {
                return text.inCombat();
            }
        }
        if (!player.hasPermission(Permissions.BYPASS_COOLDOWN)) {
            long cooldown = rules.openCooldown().toMillis();
            long now = System.currentTimeMillis();
            Long last = lastOpen.get(player.getUniqueId());
            if (cooldown > 0 && last != null && now - last < cooldown) {
                return text.cooldown().replace("%time%", TimeFormats.render((cooldown - (now - last)) / 1000.0));
            }
            lastOpen.put(player.getUniqueId(), now);
        }
        return null;
    }

    // ------------------------------------------------------------------ buying

    /** What the next vault costs this player, or {@code null} when they cannot buy one. */
    public BigDecimal nextPrice(Player player) {
        VaultSettings settings = VaultSettings.get();
        VaultSettings.Purchase purchase = settings.purchase();
        int bought = purchased(player.getUniqueId());
        if (!purchase.enabled() || bought >= purchase.maxPurchases()) return null;
        if (unlocked(player) >= settings.vaults().maxAmount()) return null;
        return BigDecimal.valueOf(purchase.priceAfter(bought)).setScale(2, RoundingMode.HALF_UP);
    }

    /** The configured currency, or {@code null} for the server's default one. */
    public static String currency() {
        String currency = VaultSettings.get().purchase().currency();
        return currency == null || currency.isBlank() ? null : currency;
    }

    /** A price as the currency writes it. */
    public static String format(BigDecimal price) {
        return Economy.info(currency()).format(price);
    }

    /**
     * Sells this player their next vault: asks first, charges, then counts it.
     *
     * <p>Charged before it is counted and refunded when the count was taken by
     * another purchase or did not answer: a vault given before the money is
     * taken is a vault for free whenever the charge fails.
     */
    public void buy(Player player, Runnable after) {
        VaultSettings.Purchase purchase = VaultSettings.get().purchase();
        if (!purchase.enabled()) {
            core.getMessages().send(player, VaultMessages.get().buyDisabled());
            return;
        }
        if (!player.hasPermission(Permissions.BUY)) {
            core.getMessages().send(player, VaultMessages.get().noPermission());
            return;
        }
        BigDecimal price = nextPrice(player);
        if (price == null) {
            core.getMessages().send(player, VaultMessages.get().buyLimit());
            return;
        }
        int bought = purchased(player.getUniqueId());
        Values values = Values.of().put("price", format(price)).put("number", unlocked(player) + 1);
        core.getMenus().close(player);
        core.getInputs().answered(player, core.getInputs().confirm(player, values.apply(VaultMessages.get().buyPrompt())).open(),
                yes -> {
                    if (Boolean.TRUE.equals(yes)) charge(player, bought, price, values, after);
                    else after.run();
                },
                after);
    }

    private void charge(Player player, int bought, BigDecimal price, Values values, Runnable after) {
        UUID id = player.getUniqueId();
        // The price was shown for this count; a purchase in between changes both.
        if (purchased(id) != bought || buying.putIfAbsent(id, true) != null) {
            core.getMessages().send(player, VaultMessages.get().buyFailed());
            return;
        }
        String currency = currency();
        if (!Economy.of(currency).charge(id, price)) {
            buying.remove(id);
            core.getMessages().send(player, VaultMessages.get().buyTooPoor(), values);
            return;
        }
        buyers.updateIf(new VaultBuyer(id.toString(), bought + 1), "purchased", bought)
                .exceptionallyCompose(failure -> {
                    // The answer was lost; the write may still have landed.
                    core.getDebug().warn("A vault purchase by " + player.getName() + " went unanswered; reading it back.");
                    return readBack(id, bought, 3);
                })
                .whenComplete((won, failure) -> core.getTasks().runAtEntity(player, () -> {
                    buying.remove(id);
                    if (failure != null) {
                        // Neither charged-and-counted nor refused for sure: refunding could hand out
                        // a vault for free, keeping the money could take it for nothing. Staff decide.
                        core.getDebug().error("A vault purchase by " + player.getName() + " (" + id + ") for "
                                + Economy.info(currency).format(price) + " could not be confirmed: the money was taken."
                                + " Check playervaults_buyers (expected " + (bought + 1) + ") and refund if it is " + bought + ".", failure);
                        core.getMessages().send(player, VaultMessages.get().unreadable());
                        load(id);
                        return;
                    }
                    if (Boolean.TRUE.equals(won)) {
                        purchased.put(id, bought + 1);
                        core.getMessages().send(player, VaultMessages.get().bought(), values);
                        core.getLogs().record("vault-buy", player, id, player.getName(),
                                "vault #" + values.map().get("number") + " for " + Economy.info(currency).format(price));
                        after.run();
                    } else {
                        Economy.of(currency).pay(id, price);
                        core.getMessages().send(player, VaultMessages.get().buyFailed());
                        load(id);
                    }
                }, () -> {
                    buying.remove(id);
                    if (failure == null && !Boolean.TRUE.equals(won)) Economy.of(currency).pay(id, price);
                }));
    }

    /** Whether the row holds the count a purchase wrote, asking again while the database does not answer. */
    private CompletableFuture<Boolean> readBack(UUID id, int bought, int attempts) {
        return buyers.findFresh(id.toString())
                .thenApply(row -> row.map(found -> found.purchased() == bought + 1).orElse(false))
                .exceptionallyCompose(failure -> attempts <= 1
                        ? CompletableFuture.failedFuture(failure)
                        : readBack(id, bought, attempts - 1));
    }

    /**
     * Whether this player may search their vaults now; a search reads every
     * vault they have, so it is held to the open cooldown's length, at least
     * three seconds.
     */
    public boolean maySearch(Player player) {
        if (player.hasPermission(Permissions.BYPASS_COOLDOWN)) return true;
        long wait = Math.max(3000L, VaultSettings.get().restrictions().openCooldown().toMillis());
        long now = System.currentTimeMillis();
        Long last = lastSearch.get(player.getUniqueId());
        if (last != null && now - last < wait) return false;
        lastSearch.put(player.getUniqueId(), now);
        return true;
    }

    // ------------------------------------------------------------------ lifecycle

    /** Reads how many vaults a player bought; creates their row so every later change is a compare-and-set. */
    public CompletableFuture<Void> load(UUID player) {
        return buyers.increment(new VaultBuyer(player.toString(), 0), "purchased")
                .thenCompose(created -> buyers.findFresh(player.toString()))
                .thenAccept(row -> {
                    if (Bukkit.getPlayer(player) != null) purchased.put(player, row.map(VaultBuyer::purchased).orElse(0));
                })
                .exceptionally(failure -> {
                    core.getDebug().error("Could not read how many vaults " + player + " bought.", failure);
                    return null;
                });
    }

    /** Forgets a player who left. */
    public void forget(UUID player) {
        purchased.remove(player);
        lastOpen.remove(player);
        lastSearch.remove(player);
        buying.remove(player);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
