package net.exylia.exyliaPlayerVaults.database;

import net.exylia.lib.database.Column;
import net.exylia.lib.database.DataKind;
import net.exylia.lib.database.Owner;
import net.exylia.lib.database.Id;
import net.exylia.lib.database.Table;

/**
 * How many vaults a player bought.
 *
 * <p>Changed only by a compare-and-set on {@code purchased}, so two purchases
 * racing on two servers cannot both count the same slot.
 */
@Table(value = "playervaults_buyers", data = DataKind.ACCOUNT)
public record VaultBuyer(
        @Owner @Id(length = 36) String uuid,
        @Column int purchased) {
}
