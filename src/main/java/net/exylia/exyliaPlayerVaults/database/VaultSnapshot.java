package net.exylia.exyliaPlayerVaults.database;

import net.exylia.lib.database.Column;
import net.exylia.lib.database.DataKind;
import net.exylia.lib.database.Id;
import net.exylia.lib.database.Index;
import net.exylia.lib.database.Table;
import org.bukkit.inventory.ItemStack;

/**
 * An earlier copy of a vault, kept for staff to look at and put back.
 *
 * @param vault     the vault's row id, {@code <owner>|<number>}
 * @param cause     why it was taken: {@code CLOSED}, {@code EDITED}, {@code CLEARED} or {@code RESTORED}
 * @param actorName who caused it, for the list
 * @param createdAt when, in epoch milliseconds
 */
@Table(value = "playervaults_history", data = DataKind.HISTORY)
@Index(columns = {"vault", "created_at"}, descending = {"created_at"})
public record VaultSnapshot(
        @Id(generated = true) long id,
        @Column(length = 48) String vault,
        @Column(length = Column.UNBOUNDED) ItemStack[] contents,
        @Column int used,
        @Column(length = 16) String cause,
        @Column(value = "actor_name", length = 32) String actorName,
        @Column("created_at") long createdAt) {
}
