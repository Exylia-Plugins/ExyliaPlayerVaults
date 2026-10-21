package net.exylia.exyliaPlayerVaults.action;

import net.exylia.exyliaPlayerVaults.ExyliaPlayerVaults;
import net.exylia.exyliaPlayerVaults.Permissions;
import net.exylia.exyliaPlayerVaults.config.VaultMessages;
import net.exylia.exyliaPlayerVaults.config.VaultSettings;
import net.exylia.exyliaPlayerVaults.database.VaultSnapshot;
import net.exylia.exyliaPlayerVaults.manager.ReadOnlyHolder;
import net.exylia.exyliaPlayerVaults.manager.VaultHistory;
import net.exylia.exyliaPlayerVaults.menu.HistoryMenu;
import net.exylia.exyliaPlayerVaults.menu.SearchMenu;
import net.exylia.exyliaPlayerVaults.menu.VaultListMenu;
import net.exylia.lib.action.ActionArguments;
import net.exylia.lib.action.ActionResult;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.format.Formats;
import net.exylia.lib.text.Values;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * The {@code exyliaplayervaults:*} actions the screens bind, and the checks
 * every way of opening a vault goes through.
 *
 * <p>A button names whose vault it acts on, so the same list serves a player
 * and staff looking at somebody else's. The owner is never trusted from the
 * button: acting on another player's vault always asks for the staff
 * permission again.
 */
public final class VaultActions {

    private final ExyliaPlayerVaults core;

    public VaultActions(ExyliaPlayerVaults core) {
        this.core = core;
    }

    public void registerAll() {
        PluginActions actions = core.getActions();
        actions.registerSync("open", (ctx, args) -> {
            target(args).ifPresent(owner -> {
                Player player = ctx.player();
                int number = args.integer(1, 0);
                if (owner.equals(player.getUniqueId())) {
                    openOwn(player, number);
                } else if (player.hasPermission(Permissions.ADMIN_EDIT)) {
                    core.getMenus().close(player);
                    core.getVaults().open(player, owner, name(owner), number, true);
                } else if (player.hasPermission(Permissions.ADMIN_VIEW)) {
                    core.getMenus().close(player);
                    core.getVaults().view(player, owner, name(owner), number);
                }
            });
            return ActionResult.success();
        });
        actions.registerSync("rename", (ctx, args) -> {
            target(args).ifPresent(owner -> rename(ctx.player(), owner, args.integer(1, 0)));
            return ActionResult.success();
        });
        actions.registerSync("icon", (ctx, args) -> {
            target(args).ifPresent(owner -> icon(ctx.player(), owner, args.integer(1, 0)));
            return ActionResult.success();
        });
        actions.registerSync("buy", (ctx, args) -> {
            Player player = ctx.player();
            core.getAccess().buy(player, () -> VaultListMenu.open(player));
            return ActionResult.success();
        });
        actions.registerSync("list", (ctx, args) -> {
            target(args).ifPresent(owner -> {
                Player player = ctx.player();
                if (owner.equals(player.getUniqueId())) VaultListMenu.open(player);
                else if (player.hasPermission(Permissions.ADMIN_VIEW)) VaultListMenu.open(player, owner, name(owner));
            });
            return ActionResult.success();
        });
        actions.registerSync("search", (ctx, args) -> {
            target(args).ifPresent(owner -> search(ctx.player(), owner));
            return ActionResult.success();
        });
        actions.registerSync("history", Permissions.ADMIN_HISTORY, (ctx, args) -> {
            target(args).ifPresent(owner -> HistoryMenu.open(ctx.player(), owner, name(owner), args.integer(1, 0)));
            return ActionResult.success();
        });
        actions.registerSync("clear", Permissions.ADMIN_CLEAR, (ctx, args) -> {
            target(args).ifPresent(owner -> {
                Player player = ctx.player();
                String ownerName = name(owner);
                core.getMenus().close(player);
                clear(player, owner, ownerName, args.integer(1, 0), () -> VaultListMenu.open(player, owner, ownerName));
            });
            return ActionResult.success();
        });
        actions.registerSync("snapshot_view", Permissions.ADMIN_HISTORY, (ctx, args) -> {
            viewSnapshot(ctx.player(), args.string(0, "0"));
            return ActionResult.success();
        });
        actions.registerSync("snapshot_restore", Permissions.ADMIN_HISTORY, (ctx, args) -> {
            // Putting a copy back can bring back what the owner took out since: as strong as editing.
            if (!ctx.player().hasPermission(Permissions.ADMIN_EDIT)) {
                core.getMessages().send(ctx.player(), VaultMessages.get().noPermission());
                return ActionResult.success();
            }
            restore(ctx.player(), args.string(0, "0"));
            return ActionResult.success();
        });
    }

    /**
     * Opens one of the player's own vaults, after every check a player is held to.
     *
     * @return whether the open was started
     */
    public boolean openOwn(Player player, int number) {
        if (!player.hasPermission(Permissions.USE)) {
            core.getMessages().send(player, VaultMessages.get().noPermission());
            return false;
        }
        int unlocked = core.getAccess().unlocked(player);
        if (number < 1 || number > unlocked) {
            core.getMessages().send(player, VaultMessages.get().notUnlocked(), Values.of().put("number", number));
            return false;
        }
        String refusal = core.getAccess().refusal(player);
        if (refusal != null) {
            core.getMessages().send(player, refusal);
            return false;
        }
        core.getMenus().close(player);
        core.getVaults().open(player, player.getUniqueId(), player.getName(), number, false);
        return true;
    }

    /** Whether the player may change this vault's name or icon: their own, unlocked one, or staff. */
    private boolean mayCustomize(Player player, UUID owner, int number, String permission) {
        if (!owner.equals(player.getUniqueId())) return player.hasPermission(Permissions.ADMIN_EDIT);
        return player.hasPermission(permission) && number >= 1 && number <= core.getAccess().unlocked(player);
    }

    private void rename(Player player, UUID owner, int number) {
        if (!mayCustomize(player, owner, number, Permissions.CUSTOMIZE_NAME)) {
            core.getMessages().send(player, VaultMessages.get().noPermission());
            return;
        }
        VaultMessages text = VaultMessages.get();
        Values values = Values.of().put("number", number);
        core.getMenus().close(player);
        core.getLooks().name(owner, number).whenComplete((current, failure) -> core.getTasks().runAtEntity(player, () ->
                core.getInputs().answered(player, core.getInputs().text(player, values.apply(text.renamePrompt()))
                                .maxLength(Math.max(1, VaultSettings.get().customization().nameMaxLength()))
                                .defaultValue(current)
                                .validate(typed -> typed.trim().equals("-") || VaultSettings.Customization.allowed(typed.trim()),
                                        text.nameInvalid())
                                .open(),
                        typed -> {
                            String name = typed.trim().equals("-") ? null : typed.trim();
                            core.getLooks().rename(owner, number, name).whenComplete((done, error) ->
                                    core.getTasks().runAtEntity(player, () -> {
                                        if (error != null) {
                                            core.getMessages().send(player, text.unreadable());
                                        } else if (name == null) {
                                            core.getMessages().send(player, text.renameReset(), values);
                                        } else {
                                            core.getMessages().send(player, text.renamed(), Values.of().put("number", number).putText("name", name));
                                        }
                                        reopen(player, owner);
                                    }));
                        },
                        () -> reopen(player, owner))));
    }

    private void icon(Player player, UUID owner, int number) {
        if (!mayCustomize(player, owner, number, Permissions.CUSTOMIZE_ICON)) {
            core.getMessages().send(player, VaultMessages.get().noPermission());
            return;
        }
        VaultMessages text = VaultMessages.get();
        Values values = Values.of().put("number", number);
        core.getMenus().close(player);
        core.getInputs().answered(player, core.getInputs().icon(player, values.apply(text.iconPrompt())).open(),
                icon -> core.getLooks().icon(owner, number, icon).whenComplete((done, error) ->
                        core.getTasks().runAtEntity(player, () -> {
                            core.getMessages().send(player, error != null ? text.unreadable() : text.iconChanged(), values);
                            reopen(player, owner);
                        })),
                () -> reopen(player, owner));
    }

    private void search(Player player, UUID owner) {
        boolean own = owner.equals(player.getUniqueId());
        if (!player.hasPermission(own ? Permissions.SEARCH : Permissions.ADMIN_VIEW)) {
            core.getMessages().send(player, VaultMessages.get().noPermission());
            return;
        }
        String ownerName = name(owner);
        core.getMenus().close(player);
        core.getInputs().answered(player, core.getInputs().text(player, VaultMessages.get().searchPrompt()).open(),
                typed -> {
                    String query = typed.trim();
                    if (query.isEmpty()) {
                        reopen(player, owner);
                        return;
                    }
                    SearchMenu.open(player, owner, ownerName, query, () -> reopen(player, owner));
                },
                () -> reopen(player, owner));
    }

    /**
     * Empties a vault after a confirmation; what it held goes to its history and can be put back.
     *
     * @param after runs on the admin's thread once it is answered, done or not
     */
    public void clear(Player admin, UUID owner, String ownerName, int number, Runnable after) {
        Values values = Values.of().put("number", number).put("player", ownerName);
        core.getInputs().answered(admin, core.getInputs().confirm(admin, values.apply(VaultMessages.get().clearPrompt()))
                        .dangerous().open(),
                yes -> {
                    if (!Boolean.TRUE.equals(yes)) {
                        after.run();
                        return;
                    }
                    core.getVaults().replace(owner, number, new ItemStack[0], VaultHistory.CLEARED, admin.getName())
                            .thenAccept(result -> core.getTasks().runAtEntity(admin, () -> {
                                switch (result) {
                                    case DONE -> {
                                        core.getMessages().send(admin, VaultMessages.get().cleared(), values);
                                        core.getLogs().record("vault-clear", admin, owner, ownerName, "vault #" + number);
                                    }
                                    case IN_USE -> core.getMessages().send(admin, VaultMessages.get().takingOver(), values);
                                    case FAILED -> core.getMessages().send(admin, VaultMessages.get().unreadable(), values);
                                }
                                after.run();
                            }));
                },
                after);
    }

    private void reopen(Player player, UUID owner) {
        if (owner.equals(player.getUniqueId())) VaultListMenu.open(player);
        else VaultListMenu.open(player, owner, name(owner));
    }

    private void viewSnapshot(Player player, String id) {
        snapshot(player, id, copy -> {
            VaultKey key = VaultKey.of(copy.vault());
            Values values = Values.of().put("number", key.number()).put("player", name(key.owner()));
            ReadOnlyHolder.open(player, core.getMessages().text(VaultMessages.get().snapshotTitle(), values)
                    .forPlayer(player).build(), copy.contents() == null ? new ItemStack[0] : copy.contents());
        });
    }

    private void restore(Player player, String id) {
        snapshot(player, id, copy -> {
            VaultKey key = VaultKey.of(copy.vault());
            String ownerName = name(key.owner());
            Values values = Values.of().put("number", key.number()).put("player", ownerName)
                    .put("when", Formats.relative(copy.createdAt()));
            core.getMenus().close(player);
            core.getInputs().answered(player, core.getInputs().confirm(player, values.apply(VaultMessages.get().restorePrompt()))
                            .dangerous().open(),
                    yes -> {
                        if (!Boolean.TRUE.equals(yes)) {
                            HistoryMenu.open(player, key.owner(), ownerName, key.number());
                            return;
                        }
                        core.getVaults().replace(key.owner(), key.number(), copy.contents(), VaultHistory.RESTORED, player.getName())
                                .thenAccept(result -> core.getTasks().runAtEntity(player, () -> {
                                    switch (result) {
                                        case DONE -> {
                                            core.getMessages().send(player, VaultMessages.get().restored(), values);
                                            core.getLogs().record("vault-restore", player, key.owner(), ownerName,
                                                    "vault #" + key.number() + " to the copy of " + Formats.date(copy.createdAt()));
                                        }
                                        case IN_USE -> core.getMessages().send(player, VaultMessages.get().takingOver(), values);
                                        case FAILED -> core.getMessages().send(player, VaultMessages.get().unreadable(), values);
                                    }
                                }));
                    },
                    () -> HistoryMenu.open(player, key.owner(), ownerName, key.number()));
        });
    }

    /** Reads one copy and hands it over on the player's thread, or says it is gone. */
    private void snapshot(Player player, String id, java.util.function.Consumer<VaultSnapshot> then) {
        long parsed;
        try {
            parsed = Long.parseLong(id);
        } catch (NumberFormatException notANumber) {
            return;
        }
        core.getHistory().find(parsed).whenComplete((found, failure) -> core.getTasks().runAtEntity(player, () -> {
            if (failure != null || found.isEmpty()) {
                core.getMessages().send(player, VaultMessages.get().snapshotMissing());
                return;
            }
            then.accept(found.get());
        }));
    }

    /** The owner a button names, when it names a real one. */
    private static Optional<UUID> target(ActionArguments args) {
        try {
            return Optional.of(UUID.fromString(args.string(0, "")));
        } catch (IllegalArgumentException notAnId) {
            return Optional.empty();
        }
    }

    /** A player's last known name, for titles and logs. */
    public static String name(UUID player) {
        String name = Bukkit.getOfflinePlayer(player).getName();
        return name == null ? player.toString().substring(0, 8) : name;
    }

    /** A vault row id split back into its owner and number. */
    record VaultKey(UUID owner, int number) {
        static VaultKey of(String id) {
            int bar = id.lastIndexOf('|');
            return new VaultKey(UUID.fromString(id.substring(0, bar)), Integer.parseInt(id.substring(bar + 1)));
        }
    }
}
