package net.exylia.exyliaPlayerVaults.database;

import net.exylia.lib.database.Column;
import net.exylia.lib.database.Id;
import net.exylia.lib.database.Table;

/**
 * The same row as {@link VaultSnapshot}, read without its contents: what the
 * history list and the pruning need.
 */
@Table("playervaults_history")
public record VaultSnapshotSummary(
        @Id(generated = true) long id,
        @Column(length = 48) String vault,
        @Column int used,
        @Column(length = 16) String cause,
        @Column(value = "actor_name", length = 32) String actorName,
        @Column("created_at") long createdAt) {
}
