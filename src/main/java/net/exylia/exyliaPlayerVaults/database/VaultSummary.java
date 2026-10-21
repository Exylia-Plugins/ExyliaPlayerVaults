package net.exylia.exyliaPlayerVaults.database;

import net.exylia.lib.database.Column;
import net.exylia.lib.database.Id;
import net.exylia.lib.database.Indexed;
import net.exylia.lib.database.Table;

/**
 * The same row as {@link VaultEntity}, read without its contents.
 *
 * <p>The list screen only needs how full each vault is. Reading the whole row
 * deserialises every stack of every vault a player has, shulker contents
 * included, to count slots; this record names the columns that answer it and
 * nothing else. It is only ever read: every write goes through
 * {@link VaultEntity}.
 *
 * @param used how many slots hold something, or {@code null} for a row written
 *             before the column existed, whose contents have to be counted
 */
@Table("sc_vaults")
public record VaultSummary(
        @Id(length = 48) String id,
        @Indexed @Column(length = 36) String ownerUuid,
        @Column("vault_number") int number,
        @Column Integer used) {
}
