package net.exylia.exyliaPlayerVaults.manager;

import net.exylia.exyliaPlayerVaults.ExyliaPlayerVaults;
import net.exylia.exyliaPlayerVaults.config.VaultMessages;
import net.exylia.exyliaPlayerVaults.config.VaultSettings;
import net.exylia.exyliaPlayerVaults.database.VaultEntity;
import net.exylia.exyliaPlayerVaults.database.VaultSummary;
import net.exylia.lib.database.PluginDatabase;
import net.exylia.lib.database.Repository;
import net.exylia.lib.database.RowLocks;
import net.exylia.lib.effect.Effects;
import net.exylia.lib.item.Containers;
import net.exylia.lib.log.Logs;
import net.exylia.lib.redis.Channel;
import net.exylia.lib.redis.Channels;
import net.exylia.lib.task.TaskHandle;
import net.exylia.lib.text.Values;
import net.exylia.lib.util.reward.RewardEntry;
import net.exylia.lib.util.reward.Rewards;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Opening a vault, and making sure what came out of it went back in.
 *
 * <h2>One window at a time, anywhere on the network</h2>
 * A vault is not an item list drawn onto buttons: it is a real container, and
 * the items in it are real items a player put there. That means the ordinary
 * duplication bugs of chest-shaped storage all apply, and all of them come from
 * the same mistake — two windows built from the same stored contents. Take one
 * stack out of each and the stack exists twice.
 *
 * <p>So a window holds its vault's lock row ({@link RowLocks}, shared by every
 * server on the database) for as long as it is open. A second opener — the same
 * player twice in a tick, an admin looking at somebody's vault here or on
 * another server — is told it is in use rather than handed a second copy. Staff
 * editing a vault that is open elsewhere ask the server holding it, over Redis,
 * to write and close it first.
 *
 * <h2>Nothing is read while a write is in flight, and nothing from a cache</h2>
 * The lock is given back only once the window's last write has landed. A
 * reopen on this server queues behind that hold, and one on another server
 * cannot take the lock until then. The read that fills the next window goes
 * past every cache ({@link Repository#findFresh}), so it always sees the row
 * the previous window wrote, even when that server's invalidation message has
 * not arrived yet.
 *
 * <h2>Written while open, not only on close</h2>
 * Every open window is written on each checkpoint when it changed, so a crash
 * costs at most that much; a close writes it once more. A window that did not
 * change writes nothing. A failed write is retried a few times, then logged.
 * After a write that changed something the viewer's own player file is saved
 * too, so a crash can never leave one item both in the vault and in the
 * inventory the server reloads.
 *
 * <h2>The lease</h2>
 * A crashed server cannot give its locks back; the lease runs out instead and
 * another server takes the vault over. The lease is short and renewed for as
 * long as the window is open, so a window may stay open as long as its viewer
 * likes and a crash keeps the vault shut for one lease at most. When a renewal
 * finds the lease lost — the database out of reach for most of a lease — the
 * window is written, closed, and its viewer told, since another server may
 * hand the vault out from then on.
 *
 * <h2>Written only over the row it read</h2>
 * Each write carries a new random {@code version} and lands only while the row
 * still holds the version this window last read or wrote; a vault's first write
 * creates the row only while there is none. That is what stops the write after
 * a lost lease from landing over a row another server has written since. Such a
 * window is closed, the newer row is kept whole, and what was put into the
 * window meanwhile is settled by {@link #settle}. A write whose answer never
 * came back is remembered, so a later write that finds it in the row carries on
 * from it instead of taking it for another server's.
 */
public final class VaultsManager {

    /**
     * How long a lock lasts without a renewal, renewed every third of it. Also
     * how long a vault stays locked after the server holding it crashed.
     */
    private static final Duration LEASE = Duration.ofSeconds(60);

    /** Long enough for a player switching servers to find the vault written by the one they left. */
    private static final int LOCK_ATTEMPTS = 20;

    private static final int WRITE_ATTEMPTS = 5;

    /** The largest window: what staff see, so nothing of a vault is out of their sight. */
    public static final int MAX_SLOTS = 54;

    private final ExyliaPlayerVaults core;
    private final Repository<VaultEntity> repo;
    private final Repository<VaultSummary> summaries;
    private final RowLocks locks;
    private final Channel takeovers;

    /** The window each open vault is being looked at through, by its key. */
    private final Map<String, VaultHolder> windows = new ConcurrentHashMap<>();
    /** What a vault held when staff opened it, to log what they changed. */
    private final Map<VaultHolder, ItemStack[]> inspected = new ConcurrentHashMap<>();

    /** Vaults whose window is on its way up from the database, with whose click it is. */
    private final Map<String, Object> opening = new ConcurrentHashMap<>();

    /** Every hold this server has on a vault, until its lock is given back. */
    private final Set<CompletableFuture<?>> holds = ConcurrentHashMap.newKeySet();

    /**
     * Which vault each viewer has open.
     *
     * <p>Kept here rather than asked of the player, because asking means
     * reading another player's open inventory from whatever thread happens to
     * be running — which on Folia is a different region's thread and is not
     * something a plugin may do.
     */
    private final Map<UUID, String> viewing = new ConcurrentHashMap<>();

    private TaskHandle checkpoints;
    private volatile boolean stopping;

    public VaultsManager(ExyliaPlayerVaults core, PluginDatabase database) {
        this.core = core;
        this.repo = database.repository(VaultEntity.class);
        this.summaries = database.repository(VaultSummary.class);
        this.locks = RowLocks.of(database, "vaults").lease(LEASE).attempts(LOCK_ATTEMPTS, Duration.ofMillis(150));
        this.takeovers = Channels.of(core.getPlugin()).channel("takeover");
        takeovers.subscribe(message -> {
            if (message.local()) return;
            VaultHolder holder = windows.get(message.payload());
            if (holder != null) takeOver(holder);
        });
        schedule();
    }

    /** (Re)starts the checkpoint timer at the configured period. */
    public void schedule() {
        if (checkpoints != null) checkpoints.cancel();
        long ticks = Math.max(20L, VaultSettings.get().vaults().checkpoint().toMillis() / 50L);
        checkpoints = core.getTasks().runTimer(ticks, ticks, this::checkpoint);
    }

    // ------------------------------------------------------------------ opening

    /**
     * Puts a vault on screen.
     *
     * @param viewer    who is looking
     * @param owner     whose vault it is
     * @param ownerName their name, for the title an admin sees
     * @param number    which of their vaults
     * @param admin     whether staff are editing somebody's vault: the whole vault is shown, and a
     *                  vault open somewhere else is closed for them and opened again
     */
    public void open(Player viewer, UUID owner, String ownerName, int number, boolean admin) {
        open(viewer, owner, ownerName, number, admin, admin);
    }

    private void open(Player viewer, UUID owner, String ownerName, int number, boolean admin, boolean takeOver) {
        String key = VaultEntity.id(owner, number);
        Values values = Values.of().put("number", number).put("player", ownerName);

        VaultHolder held = windows.get(key);
        if (held != null && !isStale(held)) {
            if (takeOver && !held.viewer().equals(viewer.getUniqueId())) {
                takeOver(held);
                reopen(viewer, owner, ownerName, number, 10L);
                return;
            }
            tell(viewer, VaultMessages.get().inUse(), values);
            return;
        }
        if (held != null) {
            // A window nobody is actually looking at: a close that never
            // reached us, or a viewer who left in a way that did not fire one.
            // Written and released rather than left blocking the vault.
            releaseOnViewerThread(held);
        }
        Object click = new Object();
        if (opening.putIfAbsent(key, click) != null) {
            return;
        }

        CompletableFuture<Optional<Boolean>> hold = core.getLooks().name(owner, number)
                .exceptionally(failure -> null)
                .thenCompose(name -> locks.holdRenewing(key, lease -> repo.findFresh(key)
                        .thenCompose(found -> {
                            CompletableFuture<Boolean> closed = new CompletableFuture<>();
                            // The retired callback matters: a viewer who quit or died while
                            // the row was loading must still give the lock back.
                            core.getTasks().runAtEntity(viewer, () -> {
                                if (stopping || !viewer.isOnline() || viewer.isDead()) {
                                    closed.complete(true);
                                    return;
                                }
                                try {
                                    VaultHolder holder = show(viewer, owner, ownerName, number, admin, name,
                                            found.orElse(null), closed, click);
                                    lease.lost().thenRun(() -> lost(holder));
                                } catch (RuntimeException failure) {
                                    closed.complete(true);
                                    throw failure;
                                }
                            }, () -> closed.complete(true));
                            return closed;
                        })));
        holds.add(hold);
        hold.whenComplete((done, failure) -> {
            holds.remove(hold);
            opening.remove(key, click);
            if (failure != null) {
                core.getDebug().error("Could not read the vault " + key, failure);
                tell(viewer, VaultMessages.get().unreadable(), values);
            } else if (done.isEmpty()) {
                // Another server held the lock for every attempt.
                if (takeOver) {
                    publishTakeOver(key);
                    tell(viewer, VaultMessages.get().takingOver(), values);
                    reopen(viewer, owner, ownerName, number, 40L);
                } else {
                    tell(viewer, VaultMessages.get().inUse(), values);
                }
            }
        });
    }

    /** Tries once more after a takeover, without asking for another. */
    private void reopen(Player viewer, UUID owner, String ownerName, int number, long delay) {
        core.getTasks().runAsyncLater(delay, () -> {
            if (viewer.isOnline()) open(viewer, owner, ownerName, number, true, false);
        });
    }

    /** Builds the window and opens it. Registers it last, so nothing before can leave a half-open vault. */
    private VaultHolder show(Player viewer, UUID owner, String ownerName, int number, boolean admin, String name,
                             VaultEntity stored, CompletableFuture<Boolean> closed, Object click) {
        int size = admin ? MAX_SLOTS : core.getAccess().rows(viewer) * 9;
        ItemStack[] contents = stored == null || stored.contents() == null ? new ItemStack[0] : stored.contents();

        boolean keep = VaultSettings.get().vaults().hiddenSlots() == VaultSettings.HiddenSlots.KEEP;
        ItemStack[] hidden = keep && contents.length > size
                ? Arrays.copyOfRange(contents, size, contents.length)
                : new ItemStack[0];
        if (VaultEntity.count(hidden) == 0) hidden = new ItemStack[0];

        VaultHolder holder = new VaultHolder(owner, ownerName, number, viewer.getUniqueId(), admin, closed,
                contents, hidden, stored != null && stored.used() != null,
                stored != null, stored == null ? null : stored.version());
        VaultMessages text = VaultMessages.get();
        Values titleValues = Values.of().put("number", number).put("player", ownerName)
                .putText("name", displayName(name, number));
        Inventory inventory = Bukkit.createInventory(holder, size,
                core.getMessages().text(admin ? text.adminTitle() : text.title(), titleValues).forPlayer(viewer).build());
        holder.bind(inventory);
        for (int slot = 0; slot < Math.min(size, contents.length); slot++) {
            inventory.setItem(slot, contents[slot]);
        }
        List<RewardEntry> owed = keep ? List.of() : overflow(contents, size);

        windows.put(holder.key(), holder);
        viewing.put(viewer.getUniqueId(), holder.key());
        if (!owner.equals(viewer.getUniqueId())) {
            inspected.put(holder, snapshot(holder));
            core.getLogs().record("vault-open", viewer, owner, ownerName, "vault #" + number);
        }
        opening.remove(holder.key(), click);
        play(viewer, VaultSettings.get().sounds().opened());
        viewer.openInventory(inventory);
        if (viewer.getOpenInventory().getTopInventory() != inventory) {
            // Another plugin cancelled the open: nobody is looking, so nothing holds the vault.
            release(holder, true);
            return holder;
        }

        if (hidden.length > 0 && !admin) {
            core.getMessages().send(viewer, text.hiddenStacks(), Values.of().put("count", VaultEntity.count(hidden)));
        }
        if (!owed.isEmpty()) {
            // The vault got smaller. What no longer fits is handed back only
            // once the row stops holding it: paid before the shrunken write
            // lands, a failed write would pay it again on the next open.
            // Whichever write of this window lands first pays it, the close
            // included.
            holder.owe(owed);
            write(holder, snapshot(holder), false);
            core.getMessages().send(viewer, text.returned(), Values.of().put("count", owed.size()).put("number", number));
        }
        return holder;
    }

    /** The name a vault is shown with: the one its owner gave it, or the default one. */
    public static String displayName(String name, int number) {
        if (name != null && !name.isBlank()) return name;
        return VaultMessages.get().defaultName().replace("%number%", String.valueOf(number));
    }

    /** What a smaller vault can no longer hold, as rewards. */
    private static List<RewardEntry> overflow(ItemStack[] contents, int size) {
        List<RewardEntry> owed = new ArrayList<>();
        for (int slot = size; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType().isAir()) continue;
            owed.add(RewardEntry.item(Rewards.snapshot(stack)).fixedAmount(stack.getAmount()).build());
        }
        return owed;
    }

    // ------------------------------------------------------------------ closing

    /**
     * Writes a vault and lets go of it.
     *
     * <p>Called by the close listener, by a quit, by a lost lease, by a
     * takeover and by the plugin shutting down. The lock goes back only after
     * the write: a vault released before it is written could be reopened from
     * the row it has not yet been saved into.
     *
     * @param holder the window
     * @param quiet  whether to skip the sound, for a close nobody is watching
     */
    public void release(VaultHolder holder, boolean quiet) {
        if (!windows.remove(holder.key(), holder)) {
            // Already released: two paths reaching the same close, which is
            // normal on a quit.
            return;
        }
        viewing.remove(holder.viewer(), holder.key());
        Inventory inventory = holder.getInventory();
        ItemStack[] contents = inventory == null ? null : snapshot(holder);
        String viewerName = Optional.ofNullable(Bukkit.getOfflinePlayer(holder.viewer()).getName()).orElse("?");
        ItemStack[] before = inspected.remove(holder);
        if (before != null && contents != null) {
            List<Logs.Group> changed = Logs.changed(before, contents);
            if (!changed.isEmpty()) {
                core.getLogs().record("vault-edit", holder.viewer(), viewerName, holder.owner(), holder.ownerName(),
                        "vault #" + holder.number() + " | " + Logs.text(changed), null, changed);
            }
        }
        CompletableFuture<Boolean> written = contents == null
                ? CompletableFuture.completedFuture(true)
                : write(holder, contents, true);
        if (contents != null && !sameContents(contents, holder.opened())) {
            // What the vault held before this window, kept once it is no longer in the row.
            written.thenAccept(landed -> {
                if (landed) core.getHistory().capture(holder.key(), holder.opened(),
                        holder.admin() ? VaultHistory.EDITED : VaultHistory.CLOSED, viewerName);
            });
        }
        written.whenComplete((ignored, failure) -> holder.closed().complete(true));

        if (!quiet) {
            Player viewer = Bukkit.getPlayer(holder.viewer());
            if (viewer != null && viewer.isOnline()) {
                play(viewer, VaultSettings.get().sounds().closed());
            }
        }
    }

    /**
     * A window whose lease was lost: written, closed and explained on its
     * viewer's thread, before another server can hand the vault out again.
     */
    private void lost(VaultHolder holder) {
        close(holder, "Lost the lock of the vault " + holder.key() + " while it was open; closing it.",
                VaultMessages.get().lockLost());
    }

    /** Staff need this vault: written and closed here, so their server can take the lock. */
    private void takeOver(VaultHolder holder) {
        holder.takeOver();
        close(holder, "Closing the vault " + holder.key() + " for a member of staff.", VaultMessages.get().takenOver());
    }

    /** Asks every other server to write and close this vault, off the calling thread. */
    private void publishTakeOver(String key) {
        core.getTasks().runAsync(() -> takeovers.publish(key));
    }

    /** Writes, closes and explains a window on its viewer's thread; released at once when they are gone. */
    private void close(VaultHolder holder, String why, String message) {
        Player viewer = Bukkit.getPlayer(holder.viewer());
        if (viewer == null) {
            release(holder, true);
            return;
        }
        core.getTasks().runAtEntity(viewer, () -> {
            if (windows.get(holder.key()) != holder) return;
            core.getDebug().warn(why);
            // Released before the close, so the close event finds it already written.
            release(holder, true);
            viewer.closeInventory();
            core.getMessages().send(viewer, message,
                    Values.of().put("number", holder.number()).put("player", holder.ownerName()));
        }, () -> release(holder, true));
    }

    // ------------------------------------------------------------------ writing

    /**
     * Writes a window's contents after every write it already started, and
     * skips the write when the row already holds exactly this.
     *
     * <p>A write that finds another server's row closes the window, and a
     * window's last write settles it once another server has written the vault.
     *
     * @param last whether these are the contents the window closed with
     * @return completes with whether the row now holds these contents
     */
    private CompletableFuture<Boolean> write(VaultHolder holder, ItemStack[] contents, boolean last) {
        synchronized (holder) {
            CompletableFuture<Boolean> next = holder.writes().thenCompose(ignored -> {
                if (holder.conflicted()) {
                    if (last) settle(holder, contents);
                    return CompletableFuture.completedFuture(false);
                }
                if (holder.counted() && sameContents(contents, holder.stored())) {
                    return CompletableFuture.completedFuture(true);
                }
                VaultEntity row = new VaultEntity(holder.owner(), holder.number(), contents,
                        ThreadLocalRandom.current().nextLong());
                return (last ? saveUntilLanded(holder, row) : save(holder, row, 1)).thenApply(outcome -> switch (outcome) {
                    case WRITTEN -> {
                        holder.stored(contents, row.version());
                        savePlayer(holder.viewer());
                        yield true;
                    }
                    case CONFLICT -> {
                        holder.conflict();
                        if (last) {
                            settle(holder, contents);
                        } else {
                            close(holder, "The vault " + holder.key()
                                    + " was written by another server while open here; closing it.",
                                    VaultMessages.get().lockLost());
                        }
                        yield false;
                    }
                    case FAILED -> false;
                });
            });
            holder.writes(next);
            // Beside the chain, so a delivery that throws never stops the writes behind it.
            next.thenAccept(written -> {
                if (written) {
                    List<RewardEntry> owed = holder.takeOverflow();
                    if (!owed.isEmpty()) core.getRewards().deliver(holder.owner(), owed);
                }
            });
            return next;
        }
    }

    /**
     * Saves the viewer's player file after a write that changed the vault.
     *
     * <p>The vault is in the database the moment the write lands; the
     * inventory the item came from or went to is only on disk at the server's
     * next autosave. A crash between the two reloads the old inventory, and
     * the item is then in both places.
     */
    private void savePlayer(UUID viewer) {
        if (stopping || !VaultSettings.get().vaults().savePlayerOnWrite()) return;
        Player player = Bukkit.getPlayer(viewer);
        if (player == null) return;
        core.getTasks().runAtEntity(player, () -> {
            if (player.isOnline()) player.saveData();
        });
    }

    /** How a save ended. */
    enum Outcome {
        /** The row holds this write. */
        WRITTEN,
        /** Another server wrote the row since this window read it; nothing was written. */
        CONFLICT,
        /** The database never answered; the write may or may not be in the row. */
        FAILED
    }

    /**
     * Saves a row over the version this window last read or wrote, and only
     * over that, trying again a few times, a second longer each time.
     */
    CompletableFuture<Outcome> save(VaultHolder holder, VaultEntity row, int attempt) {
        CompletableFuture<Outcome> tried;
        try {
            tried = holder.exists()
                    ? repo.updateIf(row, "version", holder.version()).thenCompose(won -> won
                            ? CompletableFuture.completedFuture(Outcome.WRITTEN)
                            : readBack(holder, row, attempt, false))
                    : readBack(holder, row, attempt, true);
        } catch (RuntimeException failure) {
            tried = CompletableFuture.failedFuture(failure);
        }
        return tried.exceptionallyCompose(failure -> retry(holder, row, attempt, failure));
    }

    /**
     * What a write that did not land found in the row: the write itself, landed
     * by an attempt whose answer was lost; an earlier unconfirmed write of this
     * window, carried on from; no row, for a vault's first write; or another
     * server's row.
     */
    private CompletableFuture<Outcome> readBack(VaultHolder holder, VaultEntity row, int attempt, boolean creating) {
        return repo.findFresh(row.id()).thenCompose(found -> {
            if (found.isEmpty()) {
                if (!creating) return CompletableFuture.completedFuture(Outcome.CONFLICT);
                // An increment stores the row as given only when there is none;
                // a row created meanwhile has its version added to instead, and
                // reads back as not this write.
                return repo.increment(row, "version").thenCompose(created -> readBack(holder, row, attempt, false));
            }
            Long stored = found.get().version();
            if (Objects.equals(stored, row.version())) return CompletableFuture.completedFuture(Outcome.WRITTEN);
            if (holder.adopt(stored)) return save(holder, row, attempt);
            return CompletableFuture.completedFuture(Outcome.CONFLICT);
        });
    }

    /**
     * A window's last write, tried until the database answers.
     *
     * <p>The lock goes back only after this completes, so while the database
     * is out of reach the vault stays shut — on this server the next open
     * queues behind the hold — instead of being reopened from the row before
     * this write, which would hand out again whatever was taken out. Only a
     * shutdown gives up, and then the contents are written to the log in full
     * for staff to put back.
     */
    private CompletableFuture<Outcome> saveUntilLanded(VaultHolder holder, VaultEntity row) {
        return save(holder, row, 1).thenCompose(outcome -> {
            if (outcome != Outcome.FAILED) return CompletableFuture.completedFuture(outcome);
            if (stopping) {
                core.getDebug().error("GAVE UP writing the vault " + row.id() + " on shutdown. What it should hold: "
                        + describe(present(row.contents())));
                return CompletableFuture.completedFuture(outcome);
            }
            CompletableFuture<Outcome> later = new CompletableFuture<>();
            try {
                core.getTasks().runAsyncLater(100L, () -> saveUntilLanded(holder, row)
                        .whenComplete((again, ignored) -> later.complete(again == null ? Outcome.FAILED : again)));
            } catch (RuntimeException stopped) {
                later.complete(Outcome.FAILED);
            }
            return later;
        });
    }

    private static List<ItemStack> present(ItemStack[] contents) {
        List<ItemStack> stacks = new ArrayList<>();
        if (contents != null) for (ItemStack stack : contents) if (stack != null && !stack.getType().isAir()) stacks.add(stack);
        return stacks;
    }

    private CompletableFuture<Outcome> retry(VaultHolder holder, VaultEntity row, int attempt, Throwable failure) {
        // Whether it landed is unknown until a later write finds out.
        holder.unconfirmed(row.version(), row.contents());
        if (stopping || attempt >= WRITE_ATTEMPTS) {
            core.getDebug().error("Could not write the vault " + row.id() + " (" + row.usedSlots()
                    + " stack(s)) after " + attempt + " attempt(s); the row may keep what it had.", failure);
            return CompletableFuture.completedFuture(Outcome.FAILED);
        }
        core.getDebug().warn("Could not write the vault " + row.id() + ", trying again: " + failure.getMessage());
        CompletableFuture<Outcome> retried = new CompletableFuture<>();
        try {
            core.getTasks().runAsyncLater(20L * attempt, () -> save(holder, row, attempt + 1)
                    .whenComplete((outcome, ignored) -> retried.complete(outcome == null ? Outcome.FAILED : outcome)));
        } catch (RuntimeException stopped) {
            retried.complete(Outcome.FAILED);
        }
        return retried;
    }

    /**
     * Settles a closed window whose vault another server wrote while it was open.
     *
     * <p>The newer row is kept whole: overwriting it would undo whatever was
     * done on that server. The other server read the row this window last
     * wrote, so the stacks put into this window since then are in no row and
     * are handed back to whoever had it open — here or, when they are gone, on
     * their next join. Stacks taken out since then are already with that player
     * and still in the kept row, which nothing here can take back, so they are
     * only logged. When one of this window's writes may have landed without an
     * answer, what the other server read is unknown and handing anything back
     * could duplicate it: then nothing is handed back and the stacks are logged
     * for staff instead.
     */
    private void settle(VaultHolder holder, ItemStack[] contents) {
        String what = "The vault " + holder.key() + " was written by another server while open here; its newer row is kept.";
        List<ItemStack> taken = difference(holder.stored(), contents);
        if (!taken.isEmpty()) {
            core.getDebug().warn(what + " Taken out here since the last write, and possibly still in that row: "
                    + describe(taken));
        }
        List<ItemStack> added = difference(contents, holder.stored());
        if (!taken.isEmpty() || !added.isEmpty()) {
            List<Logs.Group> groups = new ArrayList<>();
            if (!taken.isEmpty()) groups.add(Logs.Group.of("Taken out, maybe still in the vault", taken));
            if (!added.isEmpty()) groups.add(Logs.Group.of("Put in, not in the vault", added));
            core.getLogs().record("vault-conflict", holder.viewer(),
                    Optional.ofNullable(Bukkit.getOfflinePlayer(holder.viewer()).getName()).orElse("?"),
                    holder.owner(), holder.ownerName(), "vault #" + holder.number()
                            + " was written by another server while open here", null, groups);
        }
        if (added.isEmpty()) return;
        if (holder.unsure()) {
            core.getDebug().error(what + " NOT handed back to " + holder.viewer() + ", because an unanswered write"
                    + " may already have put them in that row: " + describe(added));
            return;
        }
        List<RewardEntry> back = new ArrayList<>();
        for (ItemStack stack : added) {
            back.add(RewardEntry.item(Rewards.snapshot(stack)).fixedAmount(stack.getAmount()).build());
        }
        core.getRewards().deliver(holder.viewer(), back);
        core.getDebug().warn(what + " Handed back to " + holder.viewer() + ", put in here since the last write: "
                + describe(added));
    }

    /** The stacks of {@code from} that {@code minus} does not hold, compared by kind and amount rather than slot. */
    static List<ItemStack> difference(ItemStack[] from, ItemStack[] minus) {
        List<ItemStack> kinds = new ArrayList<>();
        List<Integer> amounts = new ArrayList<>();
        if (minus != null) {
            for (ItemStack stack : minus) {
                if (stack == null || stack.getType().isAir()) continue;
                kinds.add(stack);
                amounts.add(stack.getAmount());
            }
        }
        List<ItemStack> left = new ArrayList<>();
        if (from == null) return left;
        for (ItemStack stack : from) {
            if (stack == null || stack.getType().isAir()) continue;
            int amount = stack.getAmount();
            for (int kind = 0; kind < kinds.size() && amount > 0; kind++) {
                if (amounts.get(kind) == 0 || !kinds.get(kind).isSimilar(stack)) continue;
                int matched = Math.min(amount, amounts.get(kind));
                amounts.set(kind, amounts.get(kind) - matched);
                amount -= matched;
            }
            if (amount > 0) left.add(stack.asQuantity(amount));
        }
        return left;
    }

    private static String describe(List<ItemStack> stacks) {
        return stacks.stream().map(stack -> stack.getAmount() + "x " + stack.getType()).collect(Collectors.joining(", "));
    }

    /** On each checkpoint: writes each open window that changed, and lets go of the abandoned ones. */
    private void checkpoint() {
        for (VaultHolder holder : List.copyOf(windows.values())) {
            Player viewer = Bukkit.getPlayer(holder.viewer());
            if (viewer == null || isStale(holder)) {
                releaseOnViewerThread(holder);
                continue;
            }
            core.getTasks().runAtEntity(viewer, () -> {
                if (windows.get(holder.key()) != holder) return;
                write(holder, snapshot(holder), false);
            }, () -> release(holder, true));
        }
    }

    /** Releases a window from the thread that owns its viewer, or at once when they are gone. */
    private void releaseOnViewerThread(VaultHolder holder) {
        Player viewer = Bukkit.getPlayer(holder.viewer());
        if (viewer == null) {
            release(holder, true);
            return;
        }
        core.getTasks().runAtEntity(viewer, () -> release(holder, true), () -> release(holder, true));
    }

    /** Closes this player's vault window when they enter combat; the close writes it. */
    public void closeForCombat(Player player) {
        String key = viewing.get(player.getUniqueId());
        VaultHolder holder = key == null ? null : windows.get(key);
        if (holder == null || holder.admin() || !holder.viewer().equals(player.getUniqueId())) return;
        core.getTasks().runAtEntity(player, () -> {
            if (windows.get(holder.key()) == holder) player.closeInventory();
        });
    }

    /**
     * Writes and releases whatever vault this viewer had open.
     *
     * <p>A second line of defence behind the close event, for the endings that
     * do not produce one.
     */
    public void releaseViewer(UUID viewer) {
        String key = viewing.get(viewer);
        if (key == null) return;
        VaultHolder holder = windows.get(key);
        if (holder != null && holder.viewer().equals(viewer)) {
            release(holder, true);
            return;
        }
        viewing.remove(viewer);
    }

    // ------------------------------------------------------------------ staff

    /** How a staff replacement of a vault ended. */
    public enum Replaced {
        /** The vault now holds what was asked. */
        DONE,
        /** Somebody has it open; it was asked to close, so trying again shortly works. */
        IN_USE,
        /** The database did not answer, or another server wrote it at the same moment; nothing changed. */
        FAILED
    }

    /**
     * Replaces everything in a vault, for a clear or a restore.
     *
     * <p>Done under the vault's lock, so never while a window has it open:
     * one that is open is asked to close instead and the caller told to try
     * again. What the vault held goes to its history first.
     *
     * @param contents  what it holds from now on
     * @param cause     what to file the old contents under in the history
     * @param actorName who asked, for the history
     */
    public CompletableFuture<Replaced> replace(UUID owner, int number, ItemStack[] contents, String cause, String actorName) {
        String key = VaultEntity.id(owner, number);
        VaultHolder held = windows.get(key);
        if (held != null) {
            takeOver(held);
            return CompletableFuture.completedFuture(Replaced.IN_USE);
        }
        if (opening.containsKey(key)) return CompletableFuture.completedFuture(Replaced.IN_USE);

        return locks.hold(key, () -> repo.findFresh(key).thenCompose(found -> {
            ItemStack[] before = found.map(VaultEntity::contents).orElse(new ItemStack[0]);
            if (found.isEmpty() && VaultEntity.count(contents) == 0) return CompletableFuture.completedFuture(true);
            VaultEntity row = new VaultEntity(owner, number, contents, ThreadLocalRandom.current().nextLong());
            CompletableFuture<Boolean> wrote = found.isPresent()
                    ? repo.updateIf(row, "version", found.get().version())
                    : repo.increment(row, "version").thenCompose(created -> repo.findFresh(key))
                            .thenApply(now -> now.map(stored -> Objects.equals(stored.version(), row.version())).orElse(false));
            return wrote.thenApply(landed -> {
                if (landed && VaultEntity.count(before) > 0) core.getHistory().capture(key, before, cause, actorName);
                return landed;
            });
        })).thenApply(done -> {
            if (done.isEmpty()) {
                publishTakeOver(key);
                return Replaced.IN_USE;
            }
            return done.get() ? Replaced.DONE : Replaced.FAILED;
        }).exceptionally(failure -> {
            core.getDebug().error("Could not replace the vault " + key, failure);
            return Replaced.FAILED;
        });
    }

    /**
     * Shows a vault as it is stored right now, read only, without taking its
     * lock: nothing can leave a read-only window, so a second copy on screen
     * costs nothing.
     */
    public void view(Player viewer, UUID owner, String ownerName, int number) {
        String key = VaultEntity.id(owner, number);
        repo.findFresh(key).whenComplete((found, failure) -> core.getTasks().runAtEntity(viewer, () -> {
            if (!viewer.isOnline()) return;
            Values values = Values.of().put("number", number).put("player", ownerName);
            if (failure != null) {
                core.getMessages().send(viewer, VaultMessages.get().unreadable(), values);
                return;
            }
            ItemStack[] contents = found.map(VaultEntity::contents).orElse(new ItemStack[0]);
            ReadOnlyHolder.open(viewer, core.getMessages().text(VaultMessages.get().viewTitle(), values)
                    .forPlayer(viewer).build(), contents);
            core.getLogs().record("vault-view", viewer, owner, ownerName, "vault #" + number);
        }));
    }

    /**
     * How many slots each of a player's vaults uses, by number, for the list
     * screen.
     *
     * <p>Read through {@link VaultSummary}, which never touches the contents.
     * Only a row written before the count was stored is read whole, until its
     * next write stores it.
     */
    public CompletableFuture<Map<Integer, Integer>> usedByNumber(UUID owner) {
        return summaries.where("ownerUuid", owner.toString()).find().thenCompose(rows -> {
            Map<Integer, Integer> used = new HashMap<>();
            boolean uncounted = false;
            for (VaultSummary row : rows) {
                if (row.used() == null) uncounted = true;
                else used.put(row.number(), row.used());
            }
            if (!uncounted) return CompletableFuture.completedFuture(used);
            return repo.where("ownerUuid", owner.toString()).find().thenApply(full -> {
                full.forEach(vault -> used.putIfAbsent(vault.number(), vault.usedSlots()));
                return used;
            });
        });
    }

    /** Every vault a player has stored, contents included, for a search. */
    public CompletableFuture<List<VaultEntity>> all(UUID owner) {
        return repo.where("ownerUuid", owner.toString()).find();
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Whether a window is one nobody is looking at any more.
     *
     * <p>A lock whose viewer has left is stale. Everything else releases itself
     * through the close event, which Bukkit fires for a quit as well as for a
     * player pressing escape.
     */
    private boolean isStale(VaultHolder holder) {
        Player viewer = Bukkit.getPlayer(holder.viewer());
        if (viewer == null || !viewer.isOnline()) return true;
        return !holder.key().equals(viewing.get(holder.viewer()));
    }

    /** A deep copy of a window's contents and its hidden slots, safe to write while the window stays open. */
    private static ItemStack[] snapshot(VaultHolder holder) {
        ItemStack[] shown = holder.getInventory().getContents();
        ItemStack[] hidden = holder.hidden();
        ItemStack[] contents = Arrays.copyOf(shown, shown.length + hidden.length);
        System.arraycopy(hidden, 0, contents, shown.length, hidden.length);
        for (int slot = 0; slot < contents.length; slot++) {
            if (contents[slot] != null) contents[slot] = contents[slot].clone();
        }
        return contents;
    }

    /** Whether two contents hold the same stacks in the same slots; missing trailing slots are empty. */
    static boolean sameContents(ItemStack[] first, ItemStack[] second) {
        int length = Math.max(first == null ? 0 : first.length, second == null ? 0 : second.length);
        for (int slot = 0; slot < length; slot++) {
            ItemStack x = first != null && slot < first.length ? first[slot] : null;
            ItemStack y = second != null && slot < second.length ? second[slot] : null;
            boolean xEmpty = x == null || x.getType().isAir();
            boolean yEmpty = y == null || y.getType().isAir();
            if (xEmpty && yEmpty) continue;
            if (xEmpty != yEmpty || !x.equals(y)) return false;
        }
        return true;
    }

    private void tell(Player viewer, String message, Values values) {
        core.getTasks().runAtEntity(viewer, () -> core.getMessages().send(viewer, message, values));
    }

    private static void play(Player player, String sound) {
        if (sound == null || sound.isBlank()) return;
        Effects.soundFrom(sound).show(player);
    }

    /** Whether an item may not be put into a vault, itself or through what it carries. */
    public static boolean isBlocked(ItemStack stack) {
        return Containers.isBlocked(stack, VaultSettings.get().vaults().blockedMaterials());
    }

    /** Whether this viewer has a vault window open here. */
    public boolean isViewing(UUID viewer) {
        return viewing.containsKey(viewer);
    }

    /**
     * Writes and closes every open vault, and waits for the writes and for
     * the locks to go back.
     *
     * <p>Waited on, unlike every other write this plugin makes: a disable hands
     * the database pool back the moment it returns, and a write still in flight
     * at that point is a vault's worth of items lost, while a lock never given
     * back keeps the vault shut for a whole lease. Bounded, because a shutdown
     * that hangs on a database is worse than one that reports it gave up.
     */
    public void shutdown() {
        stopping = true;
        if (checkpoints != null) checkpoints.cancel();
        for (VaultHolder holder : List.copyOf(windows.values())) {
            release(holder, true);
            Player viewer = Bukkit.getPlayer(holder.viewer());
            if (viewer != null && viewer.isOnline()) {
                viewer.closeInventory();
            }
        }
        List<CompletableFuture<?>> pending = List.copyOf(holds);
        if (!pending.isEmpty()) {
            try {
                CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException failure) {
                core.getDebug().error("Not every open vault could be written and unlocked on shutdown.", failure);
            }
        }
        windows.clear();
        viewing.clear();
        opening.clear();
    }
}
