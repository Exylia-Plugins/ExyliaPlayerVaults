package net.exylia.exyliaPlayerVaults.placeholder;

import net.exylia.exyliaPlayerVaults.ExyliaPlayerVaults;
import net.exylia.exyliaPlayerVaults.config.VaultSettings;
import net.exylia.exyliaPlayerVaults.manager.VaultAccess;
import net.exylia.lib.placeholder.Placeholders;
import org.bukkit.entity.Player;

import java.math.BigDecimal;

/**
 * {@code %playervaults_...%}: what a player has, read from memory.
 *
 * <pre>
 * %playervaults_unlocked%    how many vaults they may open
 * %playervaults_max%         the most anybody may have
 * %playervaults_bought%      how many they bought
 * %playervaults_rows%        how many rows each of their vaults has
 * %playervaults_next_price%  what the next vault costs, or empty when none can be bought
 * </pre>
 */
public final class VaultPlaceholders {

    private VaultPlaceholders() {
        throw new AssertionError("No instances.");
    }

    public static void register(ExyliaPlayerVaults core) {
        VaultAccess access = core.getAccess();
        Placeholders.group(core.getPlugin(), "playervaults")
                .describe("Vaults a player has, their size and the price of the next one")
                .add("unlocked", request -> String.valueOf(access.unlocked(request.requireViewer())))
                .add("max", request -> String.valueOf(VaultSettings.get().vaults().maxAmount()))
                .add("bought", request -> String.valueOf(access.purchased(request.requireViewer().getUniqueId())))
                .add("rows", request -> String.valueOf(access.rows(request.requireViewer())))
                .add("next_price", request -> {
                    Player viewer = request.requireViewer();
                    BigDecimal price = access.nextPrice(viewer);
                    return price == null ? "" : VaultAccess.format(price);
                })
                .register();
    }
}
