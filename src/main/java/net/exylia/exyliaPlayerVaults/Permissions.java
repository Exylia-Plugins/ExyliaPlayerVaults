package net.exylia.exyliaPlayerVaults;

/**
 * The permission nodes this plugin checks, as plugin.yml declares them.
 */
public final class Permissions {

    /** Open your own vaults. */
    public static final String USE = "exyliaplayervaults.use";

    /** How many vaults a player has: {@code exyliaplayervaults.amount.<number>}, the largest wins. */
    public static final String AMOUNT_PREFIX = "exyliaplayervaults.amount.";

    /** How many rows each of their vaults has: {@code exyliaplayervaults.rows.<1-6>}, the largest wins. */
    public static final String ROWS_PREFIX = "exyliaplayervaults.rows.";

    /** Seconds between opening two vaults: {@code exyliaplayervaults.cooldown.<seconds>}, the smallest wins. */
    public static final String COOLDOWN_PREFIX = "exyliaplayervaults.cooldown.";

    /** The longest vault name they may write: {@code exyliaplayervaults.namelength.<n>}, the largest wins. */
    public static final String NAME_LENGTH_PREFIX = "exyliaplayervaults.namelength.";

    /** Find which vault holds an item. */
    public static final String SEARCH = "exyliaplayervaults.search";

    /** Buy more vaults. */
    public static final String BUY = "exyliaplayervaults.buy";

    /** Rename a vault. */
    public static final String CUSTOMIZE_NAME = "exyliaplayervaults.customize.name";

    /** Choose a vault's icon. */
    public static final String CUSTOMIZE_ICON = "exyliaplayervaults.customize.icon";

    /** Skip the open cooldown. */
    public static final String BYPASS_COOLDOWN = "exyliaplayervaults.bypass.cooldown";

    /** Open vaults where and when the restrictions would refuse. */
    public static final String BYPASS_RESTRICTIONS = "exyliaplayervaults.bypass.restrictions";

    /** Put blocked items in. Not granted by {@code *}: a blocked list is the owner's decision. */
    public static final String BYPASS_BLOCKED = "exyliaplayervaults.bypass.blocked";

    /** Look at anybody's vaults, read only. */
    public static final String ADMIN_VIEW = "exyliaplayervaults.admin.view";

    /** Change anybody's vaults. */
    public static final String ADMIN_EDIT = "exyliaplayervaults.admin.edit";

    /** Browse and restore a vault's history. */
    public static final String ADMIN_HISTORY = "exyliaplayervaults.admin.history";

    /** Empty a vault. */
    public static final String ADMIN_CLEAR = "exyliaplayervaults.admin.clear";

    /** Reload the files. */
    public static final String ADMIN_RELOAD = "exyliaplayervaults.admin.reload";

    private Permissions() {
        throw new AssertionError("No instances.");
    }
}
