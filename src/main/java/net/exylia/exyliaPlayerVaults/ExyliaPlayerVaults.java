package net.exylia.exyliaPlayerVaults;

import net.exylia.exyliaPlayerVaults.action.VaultActions;
import net.exylia.exyliaPlayerVaults.command.PlayerArguments;
import net.exylia.exyliaPlayerVaults.command.VaultAdminCommand;
import net.exylia.exyliaPlayerVaults.command.VaultCommand;
import net.exylia.exyliaPlayerVaults.config.VaultMessages;
import net.exylia.exyliaPlayerVaults.config.VaultSettings;
import net.exylia.exyliaPlayerVaults.listener.VaultsListener;
import net.exylia.exyliaPlayerVaults.manager.VaultAccess;
import net.exylia.exyliaPlayerVaults.manager.VaultHistory;
import net.exylia.exyliaPlayerVaults.manager.VaultLooks;
import net.exylia.exyliaPlayerVaults.manager.VaultsManager;
import net.exylia.exyliaPlayerVaults.menu.VaultMenus;
import net.exylia.exyliaPlayerVaults.placeholder.VaultPlaceholders;
import net.exylia.lib.action.Actions;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.config.Configs;
import net.exylia.lib.database.Databases;
import net.exylia.lib.database.PluginDatabase;
import net.exylia.lib.debug.Debug;
import net.exylia.lib.input.Inputs;
import net.exylia.lib.input.PluginInputs;
import net.exylia.lib.log.Logs;
import net.exylia.lib.reload.Reloads;
import net.exylia.lib.task.TaskScheduler;
import net.exylia.lib.task.Tasks;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.text.Prefixes;
import net.exylia.lib.text.Values;
import net.exylia.lib.util.reward.OverflowPolicy;
import net.exylia.lib.util.reward.PendingRewards;
import net.exylia.lib.util.reward.PluginRewards;
import net.exylia.lib.util.reward.Rewards;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import revxrsal.commands.bukkit.BukkitLamp;

/**
 * ExyliaPlayerVaults: personal vaults kept in the database, open in exactly
 * one window anywhere on the network.
 *
 * <p>Not the Bukkit plugin itself: {@link ExyliaPlayerVaultsPlugin} is, so it
 * can install ExyliaLib when the server lacks it. Every library view is asked
 * for with {@link #getPlugin()}, because the library keys its per-plugin views
 * by Bukkit plugin.
 */
public final class ExyliaPlayerVaults {

    /** The namespace the menu buttons' actions are written against. */
    public static final String NAMESPACE = "exyliaplayervaults";

    private static ExyliaPlayerVaults instance;

    private JavaPlugin plugin;
    private TaskScheduler tasks;
    private PluginMessages messages;
    private Debug debug;
    private PluginInputs inputs;
    private PluginRewards rewards;
    private PluginActions actions;
    private Logs logs;
    private VaultMenus menus;
    private Reloads reloads;
    private VaultsManager vaults;
    private VaultAccess access;
    private VaultHistory history;
    private VaultLooks looks;
    private VaultActions vaultActions;

    /** The running instance; only valid between {@link #start} and {@link #shutdown}. */
    public static ExyliaPlayerVaults get() {
        return instance;
    }

    /** Starts everything on the Bukkit plugin that loaded this core. */
    public void start(JavaPlugin plugin) {
        instance = this;
        this.plugin = plugin;

        debug = Debug.of(plugin);
        messages = PluginMessages.of(plugin);
        tasks = Tasks.of(plugin);
        inputs = Inputs.of(plugin);
        logs = Logs.of(plugin)
                .action("vault-open", "CHEST")
                .action("vault-edit", "CHEST")
                .action("vault-view", "ENDER_EYE")
                .action("vault-clear", "LAVA_BUCKET")
                .action("vault-restore", "CLOCK")
                .action("vault-buy", "EMERALD")
                .action("vault-conflict", "REDSTONE");
        // What a vault hands back — stacks a smaller vault no longer shows, stacks a conflict
        // settled — goes into the inventory, or waits in the table for the player's next join.
        rewards = Rewards.of(plugin)
                .overflow(OverflowPolicy.QUEUE)
                .pending(PendingRewards.database(plugin))
                .claimOnJoin((viewer, delivery) -> {
                    if (delivery.given() <= 0) return;
                    messages.send(viewer, VaultMessages.get().rewardsDelivered(),
                            Values.of().put("amount", delivery.given()));
                });

        loadConfigs();

        PluginDatabase database = Databases.of(plugin);
        looks = new VaultLooks(database);
        access = new VaultAccess(this, database);
        history = new VaultHistory(this, database);
        vaults = new VaultsManager(this, database);

        actions = Actions.of(plugin, NAMESPACE);
        vaultActions = new VaultActions(this);
        vaultActions.registerAll();
        // After the actions: compiling a menu resolves every action its buttons name.
        menus = new VaultMenus(this, NAMESPACE);
        menus.load();

        new VaultsListener(this);
        VaultPlaceholders.register(this);
        // A reload, or a plugin manager enabling this mid-game, finds players already online.
        // After the first tick: database futures never start before it on Paper.
        tasks.run(() -> {
            for (Player online : plugin.getServer().getOnlinePlayers()) access.load(online.getUniqueId());
        });

        var lamp = PlayerArguments.install(BukkitLamp.builder(plugin)).build();
        lamp.register(new VaultCommand());
        lamp.register(new VaultAdminCommand());

        reloads = Reloads.of(plugin)
                .step("configs", () -> {
                    Configs.reloadAll(plugin);
                    applyConfigs();
                    vaults.schedule();
                })
                .stepAlsoOnLibraryReload("menus", menus::reload);

        debug.motd();
    }

    /** Writes every open vault and waits for it, before the library lets the database go. */
    public void shutdown() {
        if (vaults != null) vaults.shutdown();
        // Everything else the library owns for this plugin — menus, actions, inputs, placeholders,
        // tasks, channels — is released by the library on plugin disable.
        instance = null;
    }

    /**
     * Reads the files.
     *
     * <p>{@code config.yml} first: its {@code language} decides which folder the messages and
     * menus come from.
     */
    private void loadConfigs() {
        VaultSettings.install(Configs.define(plugin, "config", VaultSettings.class).version(1).load());
        VaultMessages.install(Configs.define(plugin, "messages", VaultMessages.class).translated().load());
        applyConfigs();
    }

    /** What has to follow every read of the files. */
    private void applyConfigs() {
        Prefixes.set(plugin, VaultMessages.get().prefix());
    }

    /** Reloads this plugin's files and menus. */
    public void reload(CommandSender sender) {
        reloads.run(sender);
    }

    public JavaPlugin getPlugin() { return plugin; }
    public TaskScheduler getTasks() { return tasks; }
    public PluginMessages getMessages() { return messages; }
    public Debug getDebug() { return debug; }
    public PluginInputs getInputs() { return inputs; }
    public PluginRewards getRewards() { return rewards; }
    public PluginActions getActions() { return actions; }
    public Logs getLogs() { return logs; }
    public VaultMenus getMenus() { return menus; }
    public VaultsManager getVaults() { return vaults; }
    public VaultAccess getAccess() { return access; }
    public VaultHistory getHistory() { return history; }
    public VaultLooks getLooks() { return looks; }
    public VaultActions getVaultActions() { return vaultActions; }
}
