package net.exylia.exyliaPlayerVaults.manager;

import net.exylia.exyliaPlayerVaults.database.VaultEntity;
import net.exylia.exyliaPlayerVaults.database.VaultLook;
import net.exylia.lib.database.PluginDatabase;
import net.exylia.lib.database.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** The names and icons players gave their vaults. */
public final class VaultLooks {

    private final Repository<VaultLook> repo;

    public VaultLooks(PluginDatabase database) {
        this.repo = database.repository(VaultLook.class);
    }

    /** The name one vault was given, or {@code null}. */
    public CompletableFuture<String> name(UUID owner, int number) {
        return repo.find(VaultEntity.id(owner, number)).thenApply(found -> found.map(VaultLook::name).orElse(null));
    }

    /** Every look a player set, by vault number. */
    public CompletableFuture<Map<Integer, VaultLook>> of(UUID owner) {
        return repo.where("ownerUuid", owner.toString()).find().thenApply(rows -> {
            Map<Integer, VaultLook> looks = new HashMap<>();
            rows.forEach(row -> looks.put(row.number(), row));
            return looks;
        });
    }

    /** Gives a vault a name, or its default one back with {@code null}. */
    public CompletableFuture<Void> rename(UUID owner, int number, String name) {
        return change(owner, number, look -> look.withName(name));
    }

    /** Gives a vault an icon, or its default one back with {@code null}. */
    public CompletableFuture<Void> icon(UUID owner, int number, String icon) {
        return change(owner, number, look -> look.withIcon(icon));
    }

    private CompletableFuture<Void> change(UUID owner, int number, java.util.function.UnaryOperator<VaultLook> edit) {
        String key = VaultEntity.id(owner, number);
        return repo.findFresh(key).thenCompose(found -> {
            VaultLook look = edit.apply(found.orElse(VaultLook.of(owner, number, null, null)));
            return look.name() == null && look.icon() == null
                    ? repo.delete(key).thenApply(ignored -> null)
                    : repo.save(look);
        });
    }
}
