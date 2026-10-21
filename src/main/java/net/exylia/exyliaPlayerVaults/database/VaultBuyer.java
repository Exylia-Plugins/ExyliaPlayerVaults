package net.exylia.exyliaPlayerVaults.database;

import net.exylia.lib.database.Column;
import net.exylia.lib.database.Id;
import net.exylia.lib.database.Table;

/**
 * How many vaults a player bought.
 *
 * <p>Changed only by a compare-and-set on {@code purchased}, so two purchases
 * racing on two servers cannot both count the same slot.
 */
@Table("playervaults_buyers")
public record VaultBuyer(
        @Id(length = 36) String uuid,
        @Column int purchased) {
}
