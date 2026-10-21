package net.exylia.exyliaPlayerVaults.config;

import net.exylia.lib.config.Comment;
import net.exylia.lib.config.ConfigFile;

import java.util.List;

/** The messages of ExyliaPlayerVaults, in {@code lang/<code>/messages.yml}. */
@Comment("Messages sent by ExyliaPlayerVaults.")
@Comment("")
@Comment("Colours use palette tokens such as {primary}, {error} or {highlight},")
@Comment("so recolouring the server never means editing this file.")
@Comment("")
@Comment("A message may open with an effect tag, which is an instruction and never")
@Comment("reaches the screen: [sound:NAME|volume|pitch] plays a sound to the player.")
@Comment("An empty message is not sent at all.")
public record VaultMessages(
        @Comment("Opens every chat line through %prefix%.")
        String prefix,

        @Comment("")
        @Comment("Window titles. %number% is which vault, %name% its name, %player% whose it is.")
        String title,
        String adminTitle,
        String viewTitle,
        String snapshotTitle,

        @Comment("")
        @Comment("What a vault is called in titles and lists until its owner renames it.")
        String defaultName,

        @Comment("")
        String inUse,
        String notUnlocked,
        String unreadable,
        String blockedItem,
        String lockLost,
        String takenOver,
        String takingOver,
        String hiddenStacks,
        String returned,
        String rewardsDelivered,

        @Comment("")
        @Comment("Restrictions. %time% is what is left of the cooldown.")
        String cooldown,
        String inCombat,
        String disabledWorld,
        String disabledGameMode,

        @Comment("")
        @Comment("Renaming and icons, from the vault list.")
        String renamePrompt,
        String nameInvalid,
        String renamed,
        String renameReset,
        String iconPrompt,
        String iconChanged,
        String noPermission,

        @Comment("")
        @Comment("Buying vaults. %price% is formatted in the currency, %number% is the vault bought.")
        String buyPrompt,
        String bought,
        String buyTooPoor,
        String buyLimit,
        String buyDisabled,
        String buyFailed,

        @Comment("")
        @Comment("/pv search <item>, and the search button. %query% is what was searched.")
        String searchPrompt,
        String searchNone,

        @Comment("")
        @Comment("Admin commands.")
        List<String> adminUsage,
        String clearPrompt,
        String cleared,
        String restorePrompt,
        String restored,
        String historyEmpty,
        String snapshotMissing,
        String reloaded) {

    public VaultMessages() {
        this("{primary}&lVAULTS {letters_black}•<reset>",
                "{primary}&lVAULT {letters_black}» {highlight}%name%",
                "{primary}&lVAULT {letters_black}» {highlight}%player% {letters_black}#{highlight}%number%",
                "{primary}&lVIEW {letters_black}» {highlight}%player% {letters_black}#{highlight}%number%",
                "{primary}&lSNAPSHOT {letters_black}» {highlight}%player% {letters_black}#{highlight}%number%",
                "#%number%",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}That vault is open somewhere else right now. Try again in a moment.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}You do not have vault {highlight}%number%{error}.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}That vault could not be read. Nothing has been lost; try again.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}That item cannot go in a vault.",
                "%prefix% {warning}Vault {highlight}%number% {warning}was saved and closed after a connection hiccup. {letters}Open it again to carry on.",
                "%prefix% {warning}Vault {highlight}%number% {warning}was saved and closed because a member of staff is looking at it.",
                "%prefix% {letters}Vault {highlight}%number% {letters}was open on another server; it is being saved and closed.",
                "%prefix% {warning}%count% {letters}stack(s) of this vault are kept out of sight until it has more rows again.",
                "%prefix% {warning}%count% {letters}stack(s) no longer fit in vault {highlight}%number% {letters}and were handed back to you.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.2]%prefix% {success}%amount% {letters}item(s) from your vaults were handed back to you.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}Wait {highlight}%time% ⌚ {error}before opening another vault.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}You cannot open a vault while in combat.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}Vaults cannot be opened in this world.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}Vaults cannot be opened in this game mode.",
                "{primary}Name vault %number% {letters_black}(- to reset)",
                "{error}Letters, digits, spaces and - _ ' . ! ? # ( ) only.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.2]%prefix% {letters}Vault {highlight}%number% {letters}is now called {highlight}%name%{letters}.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.2]%prefix% {letters}Vault {highlight}%number% {letters}has its own name back.",
                "{primary}Choose an icon for vault %number%",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.2]%prefix% {letters}Vault {highlight}%number% {letters}has a new icon.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}You do not have permission to do that.",
                "{primary}Buy vault %number% for %price%?",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.2]%prefix% {success}Vault {highlight}%number% {success}is yours. {letters}Paid {highlight}%price%{letters}.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}You need {highlight}%price% {error}to buy that vault.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}You cannot buy any more vaults.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}Vaults cannot be bought on this server.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}The purchase did not go through and you were not charged. Try again.",
                "{primary}Search the vaults for an item",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}None of your vaults holds {highlight}%query%{error}.",
                List.of("%prefix% {letters}Usage{letters_black}:",
                        " {letters_black}▎ {highlight}/pvadmin list <player>",
                        " {letters_black}▎ {highlight}/pvadmin view <player> <number>",
                        " {letters_black}▎ {highlight}/pvadmin edit <player> <number>",
                        " {letters_black}▎ {highlight}/pvadmin history <player> <number>",
                        " {letters_black}▎ {highlight}/pvadmin clear <player> <number>",
                        " {letters_black}▎ {highlight}/pvadmin reload"),
                "{error}Empty vault %number% of %player%?",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.2]%prefix% {letters}Vault {highlight}%number% {letters}of {highlight}%player% {letters}was emptied. {letters_black}Its contents are in its history.",
                "{warning}Put vault %number% of %player% back as it was %when%?",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.2]%prefix% {letters}Vault {highlight}%number% {letters}of {highlight}%player% {letters}is back as it was {highlight}%when%{letters}.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}Vault {highlight}%number% {error}of {highlight}%player% {error}has no history yet.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}That copy is no longer in the history.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.2]%prefix% {success}Configuration, messages and menus reloaded.");
    }

    private static ConfigFile<VaultMessages> file;

    /** Keeps the loaded file so {@link #get()} reads the current snapshot. */
    public static void install(ConfigFile<VaultMessages> loaded) {
        file = loaded;
    }

    /** The current snapshot, or the defaults before the file loaded: never {@code null}. */
    public static VaultMessages get() {
        return file == null ? new VaultMessages() : file.get();
    }
}
