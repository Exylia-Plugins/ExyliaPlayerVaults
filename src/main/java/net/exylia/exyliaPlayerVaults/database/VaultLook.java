package net.exylia.exyliaPlayerVaults.database;

import net.exylia.lib.database.Column;
import net.exylia.lib.database.Id;
import net.exylia.lib.database.Indexed;
import net.exylia.lib.database.Table;

import java.util.UUID;

/**
 * The name and icon a player gave one of their vaults.
 *
 * <p>A table of its own rather than two columns on the vault: a vault's row is
 * written whole while its window is open, and a rename from the list screen in
 * the meantime would be overwritten by the next write of the contents.
 *
 * @param name what the player typed, kept literal; {@code null} for the default name
 * @param icon a menu {@code material} value; {@code null} for the default icon
 */
@Table("playervaults_looks")
public record VaultLook(
        @Id(length = 48) String id,
        @Indexed @Column(length = 36) String ownerUuid,
        @Column("vault_number") int number,
        @Column(length = 64) String name,
        @Column(length = 512) String icon) {

    public static VaultLook of(UUID owner, int number, String name, String icon) {
        return new VaultLook(VaultEntity.id(owner, number), owner.toString(), number, name, icon);
    }

    public VaultLook withName(String name) {
        return new VaultLook(id, ownerUuid, number, name, icon);
    }

    public VaultLook withIcon(String icon) {
        return new VaultLook(id, ownerUuid, number, name, icon);
    }
}
