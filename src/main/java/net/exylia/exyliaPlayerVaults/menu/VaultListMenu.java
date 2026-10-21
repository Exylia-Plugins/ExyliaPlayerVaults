package net.exylia.exyliaPlayerVaults.menu;

import net.exylia.exyliaPlayerVaults.ExyliaPlayerVaults;
import net.exylia.exyliaPlayerVaults.config.VaultMessages;
import net.exylia.exyliaPlayerVaults.config.VaultSettings;
import net.exylia.exyliaPlayerVaults.database.VaultLook;
import net.exylia.exyliaPlayerVaults.manager.VaultAccess;
import net.exylia.exyliaPlayerVaults.manager.VaultsManager;
import net.exylia.lib.text.Values;
import net.exylia.lib.ui.UiEntry;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Which vaults a player has, how full each one is, and what more they could
 * have.
 *
 * <p>The ones they have not unlocked are drawn too, greyed out, and the next
 * one is drawn with its price when vaults can be bought: a player who cannot
 * see what a rank gives them has no reason to want it.
 *
 * <p>Staff open the same screen onto somebody else's vaults: every vault the
 * owner stored is drawn as open, because staff are not limited by the owner's
 * rank, and the buttons act on the owner's vaults.
 */
public final class VaultListMenu {

    /** The menu's file, as {@link VaultMenus} registers it. */
    public static final String ID = "menus/user/vaults";

    private VaultListMenu() {
        throw new AssertionError("No instances.");
    }

    /** A player's own vaults. */
    public static void open(Player player) {
        open(player, player.getUniqueId(), player.getName());
    }

    /** Somebody's vaults, as {@code viewer} may see them. */
    public static void open(Player viewer, UUID owner, String ownerName) {
        ExyliaPlayerVaults core = ExyliaPlayerVaults.get();
        core.getVaults().usedByNumber(owner)
                .thenCombine(core.getLooks().of(owner), Read::new)
                .whenComplete((read, failure) -> core.getTasks().runAtEntity(viewer, () -> {
                    if (!viewer.isOnline()) return;
                    if (failure != null) {
                        core.getMessages().send(viewer, VaultMessages.get().unreadable());
                        return;
                    }
                    render(core, viewer, owner, ownerName, read);
                }));
    }

    private record Read(Map<Integer, Integer> used, Map<Integer, VaultLook> looks) {
    }

    private static void render(ExyliaPlayerVaults core, Player viewer, UUID owner, String ownerName, Read read) {
        boolean own = viewer.getUniqueId().equals(owner);
        VaultAccess access = core.getAccess();
        VaultSettings settings = VaultSettings.get();
        int stored = read.used().keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        int unlocked = own ? access.unlocked(viewer) : Math.max(stored, settings.vaults().maxAmount());
        int ceiling = Math.max(unlocked, settings.vaults().maxAmount());
        int size = own ? access.rows(viewer) * 9 : VaultsManager.MAX_SLOTS;
        BigDecimal price = own ? access.nextPrice(viewer) : null;

        List<UiEntry> rows = new ArrayList<>(ceiling);
        for (int number = 1; number <= ceiling; number++) {
            int used = Math.min(size, read.used().getOrDefault(number, 0));
            VaultLook look = read.looks().get(number);
            String template = number <= unlocked ? null
                    : number == unlocked + 1 && price != null ? "buy" : "locked";
            UiEntry.Builder row = UiEntry.of(number)
                    .template(template)
                    .withFormatted("vault_number", number)
                    .withFormatted("vault_owner", owner.toString())
                    .with("vault_name", VaultsManager.displayName(look == null ? null : look.name(), number))
                    .with("vault_icon", look == null || look.icon() == null
                            ? settings.customization().defaultIcon() : look.icon())
                    .withFormatted("vault_used", used)
                    .withFormatted("vault_size", size)
                    .withFormatted("vault_free", Math.max(0, size - used));
            if ("buy".equals(template)) row.withFormatted("vault_price", VaultAccess.format(price));
            rows.add(row.build());
        }

        Values context = Values.of()
                .put("owner_name", ownerName)
                .put("vaults_unlocked", unlocked)
                .put("vaults_max", ceiling)
                .put("vaults_bought", access.purchased(owner))
                .put("vaults_rows", size / 9);

        core.getMenus().open(viewer, ID, context.map(), rows);
    }
}
