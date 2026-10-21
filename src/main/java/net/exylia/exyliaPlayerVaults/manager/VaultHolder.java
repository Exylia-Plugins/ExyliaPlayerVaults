package net.exylia.exyliaPlayerVaults.manager;

import net.exylia.lib.util.reward.RewardEntry;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * What a vault window is, so an event can tell whose it is.
 *
 * <p>The window carries the answer rather than a {@code Map<UUID, String>}
 * somewhere: a player with a chest open on top of a vault is looking at the
 * chest, and a map would still say the vault is theirs.
 *
 * <p>It also carries what the manager needs to write it safely: what the row
 * holds as far as this window knows and under which version, the stacks kept
 * out of sight past the window's last slot, the writes whose outcome never came
 * back, the tail of its writes so two of them never land out of order, and the
 * future that gives the vault's lock back.
 */
public final class VaultHolder implements InventoryHolder {

    private final UUID owner;
    private final String ownerName;
    private final int number;
    private final UUID viewer;
    private final boolean admin;
    private final CompletableFuture<Boolean> closed;
    /** The stored slots past the window's size, written back untouched with every write. */
    private final ItemStack[] hidden;
    /** What the row held when the window opened, for the history taken when it closes changed. */
    private final ItemStack[] opened;

    private Inventory inventory;
    private volatile ItemStack[] stored;
    private volatile boolean counted;
    private volatile boolean exists;
    private volatile Long version;
    private volatile boolean conflicted;
    private volatile boolean takenOver;
    /** Writes that failed without an answer, by version: any of them may be in the row. */
    private final Map<Long, ItemStack[]> unconfirmed = new ConcurrentHashMap<>();
    private CompletableFuture<Boolean> writes = CompletableFuture.completedFuture(true);
    /** What a shrunken vault can no longer hold, owed until a write without it lands. */
    private final AtomicReference<List<RewardEntry>> overflow = new AtomicReference<>(List.of());

    VaultHolder(UUID owner, String ownerName, int number, UUID viewer, boolean admin,
                CompletableFuture<Boolean> closed, ItemStack[] stored, ItemStack[] hidden,
                boolean counted, boolean exists, Long version) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.number = number;
        this.viewer = viewer;
        this.admin = admin;
        this.closed = closed;
        this.stored = stored;
        this.opened = stored;
        this.hidden = hidden;
        this.counted = counted;
        this.exists = exists;
        this.version = version;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    /** Whose vault this is. */
    public UUID owner() {
        return owner;
    }

    /** Their name, for the line an admin is shown. */
    public String ownerName() {
        return ownerName;
    }

    /** Which of their vaults. */
    public int number() {
        return number;
    }

    /** Who has it open. */
    public UUID viewer() {
        return viewer;
    }

    /** Whether staff opened it through {@code /pvadmin edit}, which skips the blocked list and logs what changed. */
    public boolean admin() {
        return admin;
    }

    /** The key this vault is stored and locked under. */
    public String key() {
        return owner + "|" + number;
    }

    /** Completed once the last write has landed or given up; the lock goes back then. */
    CompletableFuture<Boolean> closed() {
        return closed;
    }

    ItemStack[] hidden() {
        return hidden;
    }

    ItemStack[] opened() {
        return opened;
    }

    /** The contents the row holds, as far as this window knows. */
    ItemStack[] stored() {
        return stored;
    }

    /** Whether the row stores its used count; a row from before that column did not. */
    boolean counted() {
        return counted;
    }

    /** Whether the vault has a row, which a first write has to create rather than overwrite. */
    boolean exists() {
        return exists;
    }

    /** The version of the row this window last read or wrote; {@code null} for a row from before versions. */
    Long version() {
        return version;
    }

    /**
     * Records a write that landed.
     *
     * <p>Also forgets the unconfirmed writes: one landing over the version they
     * were all written against proves none of them got in, and none can later.
     */
    void stored(ItemStack[] contents, long version) {
        this.stored = contents;
        this.counted = true;
        this.exists = true;
        this.version = version;
        unconfirmed.clear();
    }

    /** Remembers a write that failed without an answer; it may still be in the row. */
    void unconfirmed(long version, ItemStack[] contents) {
        unconfirmed.put(version, contents);
    }

    /** Whether a write of this window may be in the row without this window knowing. */
    boolean unsure() {
        return !unconfirmed.isEmpty();
    }

    /**
     * Takes a row version as this window's own when it is one of its unconfirmed writes.
     *
     * @return whether it was, in which case that write is now what the row holds
     */
    synchronized boolean adopt(Long found) {
        ItemStack[] contents = found == null ? null : unconfirmed.get(found);
        if (contents == null) return false;
        stored(contents, found);
        return true;
    }

    /** Whether another server wrote the vault while this window was open. */
    boolean conflicted() {
        return conflicted;
    }

    void conflict() {
        this.conflicted = true;
    }

    /** Whether staff asked for this vault and it is being closed for them. */
    boolean takenOver() {
        return takenOver;
    }

    void takeOver() {
        this.takenOver = true;
    }

    void owe(List<RewardEntry> owed) {
        overflow.set(List.copyOf(owed));
    }

    /** What is owed from the overflow, handed out once: whoever takes it pays it. */
    List<RewardEntry> takeOverflow() {
        return overflow.getAndSet(List.of());
    }

    CompletableFuture<Boolean> writes() {
        return writes;
    }

    void writes(CompletableFuture<Boolean> tail) {
        this.writes = tail;
    }
}
