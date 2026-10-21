package net.exylia.exyliaPlayerVaults.config;

import net.exylia.lib.config.Comment;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.config.Languages;

import java.time.Duration;
import java.util.List;

/**
 * Settings stored in {@code config.yml}.
 *
 * <p>Read as fields of the loaded snapshot, never looked up by key: the vault
 * size and the blocked list are asked on every click inside a vault.
 */
public record VaultSettings(
        @Comment("Language of this plugin's menus and messages. 'default' follows the language set in")
        @Comment("ExyliaLib's config.yml; en, es, pt, fr, ru or tr set this plugin alone. Each one is a folder under")
        @Comment("lang/ you can edit.")
        String language,

        @Comment("")
        @Comment("How many vaults a player has, and how big they are.")
        Vaults vaults,

        @Comment("")
        @Comment("Where and when a vault may be opened.")
        Restrictions restrictions,

        @Comment("")
        @Comment("Extra vaults bought from the vault list with money.")
        Purchase purchase,

        @Comment("")
        @Comment("Earlier copies of each vault, for staff to look at and put back with /pvadmin history.")
        History history,

        @Comment("")
        @Comment("What players may change about their vaults.")
        Customization customization,

        @Comment("")
        Sounds sounds) {

    public VaultSettings() {
        this(Languages.DEFAULT, new Vaults(), new Restrictions(), new Purchase(), new History(),
                new Customization(), new Sounds());
    }

    /** What happens to the slots of a vault that became smaller than what it holds. */
    public enum HiddenSlots {
        /** Kept in the vault, out of sight, until the vault is big enough again. */
        KEEP,
        /** Handed back to the owner, straight away or on their next join. */
        RETURN
    }

    /** The {@code vaults} section. */
    public record Vaults(
            @Comment("How many vaults a player has with no permission of their own.")
            @Comment("exyliaplayervaults.amount.<number> raises it; the largest number a player holds wins.")
            int defaultAmount,

            @Comment("The most vaults anybody may have, bought ones included, whatever their permissions.")
            int maxAmount,

            @Comment("How many rows (1 to 6) each vault has with no permission of its own.")
            @Comment("exyliaplayervaults.rows.<1-6> changes it; the largest number a player holds wins.")
            int defaultRows,

            @Comment("A vault holding more than its rows can show, because the player lost a rank or the rows")
            @Comment("were lowered: KEEP leaves those stacks stored out of sight until the vault grows again,")
            @Comment("RETURN hands them back to the player (into their inventory, or on their next join).")
            HiddenSlots hiddenSlots,

            @Comment("Materials that may not be put into a vault, by name. Shulker boxes and bundles holding")
            @Comment("one are refused too.")
            List<String> blockedMaterials,

            @Comment("How often an open vault is written while it stays open, so a crash costs at most this much.")
            Duration checkpoint,

            @Comment("Saves the player's own inventory file each time a vault they changed is written, so a crash")
            @Comment("right after can never leave the same item both in the vault and in their inventory.")
            boolean savePlayerOnWrite) {

        public Vaults() {
            this(1, 9, 6, HiddenSlots.KEEP, List.of(), Duration.ofSeconds(30), true);
        }
    }

    /** The {@code restrictions} section. */
    public record Restrictions(
            @Comment("How long a player waits between opening two vaults. 0 turns it off.")
            Duration openCooldown,

            @Comment("Refuses to open a vault while the player is in combat (any combat plugin ExyliaLib knows).")
            boolean blockInCombat,

            @Comment("Worlds in which vaults cannot be opened, by name.")
            List<String> disabledWorlds,

            @Comment("Game modes in which vaults cannot be opened: SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR.")
            List<String> disabledGameModes) {

        public Restrictions() {
            this(Duration.ofMillis(500), true, List.of(), List.of("SPECTATOR"));
        }
    }

    /** The {@code purchase} section. */
    public record Purchase(
            @Comment("Lets players buy vaults past what their rank gives them, from the vault list.")
            boolean enabled,

            @Comment("The currency they are paid in, by id. Empty uses the server's default one.")
            String currency,

            @Comment("What the first bought vault costs.")
            double basePrice,

            @Comment("Each later one costs this many times the one before: 1.0 keeps every vault at base-price.")
            double priceMultiplier,

            @Comment("The most vaults one player may buy. max-amount still caps the total.")
            int maxPurchases) {

        public Purchase() {
            this(false, "", 5000.0, 1.5, 9);
        }

        /** What the next vault costs a player who already bought {@code bought}. */
        public double priceAfter(int bought) {
            return basePrice * Math.pow(Math.max(0.0, priceMultiplier), Math.max(0, bought));
        }
    }

    /** The {@code history} section. */
    public record History(
            @Comment("How many earlier copies of each vault are kept, newest first. 0 keeps none.")
            @Comment("A copy is taken when a player closes a vault they changed, and before any admin edit,")
            @Comment("clear or restore.")
            int keep) {

        public History() {
            this(10);
        }
    }

    /** The {@code customization} section. */
    public record Customization(
            @Comment("The longest name a player may give a vault. Names hold letters, digits, spaces")
            @Comment("and - _ ' . ! ? # ( ) only, so a name can never carry colours or placeholders.")
            int nameMaxLength,

            @Comment("What a vault is drawn as in the list until its owner picks an icon: a menu material,")
            @Comment("such as CHEST, playerhead-<name> or basehead-<texture>.")
            String defaultIcon) {

        public Customization() {
            this(24, "BARREL");
        }

        /** Whether a typed name is one a vault may carry. */
        public static boolean allowed(String name) {
            return name != null && name.matches("[\\p{L}\\p{N} _\\-'.!?#()]+");
        }
    }

    /** The {@code sounds} section. */
    public record Sounds(
            @Comment("Played when a vault is opened.")
            String opened,

            @Comment("Played when a vault is closed.")
            String closed) {

        public Sounds() {
            this("BLOCK_BARREL_OPEN|1.0|1.2", "BLOCK_BARREL_CLOSE|1.0|1.2");
        }
    }

    private static ConfigFile<VaultSettings> file;

    /** Keeps the loaded file so {@link #get()} reads the current snapshot. */
    public static void install(ConfigFile<VaultSettings> loaded) {
        file = loaded;
    }

    /** The current snapshot: a field access, never a re-parse. */
    public static VaultSettings get() {
        return file == null ? new VaultSettings() : file.get();
    }
}
