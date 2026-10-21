package net.exylia.exyliaPlayerVaults.database;

import net.exylia.lib.database.Column;
import net.exylia.lib.database.Id;
import net.exylia.lib.database.Indexed;
import net.exylia.lib.database.Table;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * One player's one vault.
 *
 * <p>The table keeps the name ExyliaSurvivalCore gave it, {@code sc_vaults}, so
 * a server that ran the vaults there on a shared database finds every vault
 * where it was.
 *
 * <p>The column is called {@code vault_number} rather than {@code index},
 * which is a reserved word in more than one of the engines this plugin runs
 * on and would fail at {@code CREATE TABLE} rather than at compile time.
 *
 * <p>{@code used} is stored next to the contents so the list screen can read
 * it through {@link VaultSummary} without deserialising any stack. Rows written
 * before the column existed read it as {@code null} and are counted from their
 * contents until their next write stores it.
 *
 * <p>{@code version} is a random number written with every write, so a server
 * writes only over the row it last read or wrote and can recognise its own
 * write in the row when the answer to it never came back. {@code null} on rows
 * from before the column, until their next write.
 */
@Table("sc_vaults")
public record VaultEntity(
        @Id(length = 48) String id,
        @Indexed @Column(length = 36) String ownerUuid,
        @Column("vault_number") int number,
        @Column(length = Column.UNBOUNDED) ItemStack[] contents,
        @Column Integer used,
        @Column("updated_at") long updatedAt,
        @Column Long version) {

    public VaultEntity(UUID owner, int number, ItemStack[] contents, long version) {
        this(id(owner, number), owner.toString(), number, contents, count(contents), System.currentTimeMillis(), version);
    }

    /** The row id of one player's vault. */
    public static String id(UUID owner, int number) {
        return owner + "|" + number;
    }

    public UUID owner() {
        return UUID.fromString(ownerUuid);
    }

    /** How many stacks are in it: the stored count, or counted on a row from before it was stored. */
    public int usedSlots() {
        return used != null ? used : count(contents);
    }

    /** How many slots of these contents hold something. */
    public static int count(ItemStack[] contents) {
        if (contents == null) return 0;
        int used = 0;
        for (ItemStack stack : contents) {
            if (stack != null && !stack.getType().isAir()) used++;
        }
        return used;
    }
}
