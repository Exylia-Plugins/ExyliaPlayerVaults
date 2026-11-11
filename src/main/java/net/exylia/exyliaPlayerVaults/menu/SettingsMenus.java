package net.exylia.exyliaPlayerVaults.menu;

import net.exylia.exyliaPlayerVaults.ExyliaPlayerVaults;
import net.exylia.exyliaPlayerVaults.Permissions;
import net.exylia.exyliaPlayerVaults.config.VaultSettings;
import net.exylia.lib.action.ActionResult;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.config.ConfigEdits;
import net.exylia.lib.ui.UiKeys;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings an admin really tunes, chosen and explained by hand: a hub with one card
 * per theme, each opening a page. Everything else stays in {@code config.yml}.
 *
 * <p>Every page is {@code menus/admin/settings_<page>}; its values are
 * {@code %<page>_<key>%} (the YAML key with dots and dashes as underscores) and its
 * cards run {@code exyliaplayervaults:setting <page> <key>}.
 */
public final class SettingsMenus {

    public static final String HUB = "menus/admin/settings";

    /** The page ids, in hub order, each with the keys its hub card shows. */
    private static final Map<String, List<String>> PAGES = new LinkedHashMap<>();

    static {
        PAGES.put("vaults", List.of("vaults.default-amount", "vaults.default-rows"));
        PAGES.put("access", List.of("restrictions.open-cooldown", "restrictions.block-in-combat"));
        PAGES.put("purchase", List.of("purchase.enabled", "purchase.base-price"));
    }

    /** Every settings page id, for the menu registration. */
    public static final List<String> IDS = java.util.stream.Stream.concat(
            java.util.stream.Stream.of(HUB), PAGES.keySet().stream().map(id -> HUB + "_" + id)).toList();

    private SettingsMenus() {
        throw new AssertionError("No instances.");
    }

    public static void openHub(Player admin) {
        Map<String, Object> context = new LinkedHashMap<>();
        PAGES.forEach((id, shown) -> context.putAll(edits().placeholders(id + "_", shown)));
        ExyliaPlayerVaults.get().getMenus().open(admin, HUB, context);
    }

    public static void openPage(Player admin, String id) {
        if (!PAGES.containsKey(id)) return;
        ExyliaPlayerVaults.get().getMenus().open(admin, HUB + "_" + id, new LinkedHashMap<>(edits().placeholders(id + "_")));
    }

    public static void register(PluginActions actions) {
        actions.registerSync("settings", Permissions.ADMIN, (ctx, args) -> {
            openHub(ctx.player());
            return ActionResult.success();
        });
        actions.registerSync("settings_hub", Permissions.ADMIN, (ctx, args) -> {
            openHub(ctx.player());
            return ActionResult.success();
        });
        actions.registerSync("admin_hub", Permissions.ADMIN_VIEW, (ctx, args) -> {
            PlayersMenu.open(ctx.player());
            return ActionResult.success();
        });
        actions.registerSync("settings_page", Permissions.ADMIN, (ctx, args) -> {
            openPage(ctx.player(), args.string(0, ""));
            return ActionResult.success();
        });
        actions.registerSync("setting", Permissions.ADMIN, (ctx, args) -> {
            Player admin = ctx.player();
            String id = args.string(0, "");
            if (!PAGES.containsKey(id)) return ActionResult.success();
            edits().card(admin, args.string(1, ""), ctx.get(UiKeys.CLICK).orElse(null), () -> openPage(admin, id));
            return ActionResult.success();
        });
    }

    private static ConfigEdits edits() {
        return ConfigEdits.of(ExyliaPlayerVaults.get().getPlugin(), VaultSettings::file);
    }
}
