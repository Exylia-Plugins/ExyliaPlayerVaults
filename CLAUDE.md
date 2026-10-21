# ExyliaPlayerVaults

Personal vaults kept in the database, open in one window at a time across a network. Free and open source (GPL-3.0), public at `Exylia-Plugins/ExyliaPlayerVaults`. Extracted from ExyliaSurvivalCore's `vaults` module; the table name `sc_vaults` stays so shared databases keep their vaults.

## Releasing

- Every finished change is released, without asking and without `/exylia-release`: bump `version` in `build.gradle` (patch for fixes, minor for features, major for breaking commands or config), commit `chore: release <version>`, tag that commit `v<version>`, and push the branch and the tag.
- The release workflow runs only on `v*` tags and expects the tag to match the `build.gradle` version; it publishes the GitHub release and the fixed-name `ExyliaPlayerVaults.jar` that servers auto-update from, then mirrors it to Modrinth (skipped while `MODRINTH_TOKEN` or the `MODRINTH_PROJECT_ID` variable is unset). A push to the default branch without a tag only builds.

## ExyliaLib

- Always the newest: `build.gradle` resolves the latest GitHub release (or a newer one in mavenLocal) at build time, and that version becomes plugin.yml's `exylia-lib`. `-PexyliaLibVersion` pins one for a single build.
- `ExyliaPlayerVaultsPlugin` is the Bukkit entry point and touches no ExyliaLib type: without ExyliaLib it runs `bootstrap/ExyliaLibMissing` (logs where to download it; nothing is downloaded without the owner) and disables itself until the restart. With it, it hands over to the core, `ExyliaPlayerVaults`.

## Vault safety (read before touching `manager/`)

- A window holds `RowLocks` (`vaults:<owner>|<n>`) through `holdRenewing` until its last write lands. Reads under the lock use `findFresh`, never `find`: the Redis local cache can still hold the row from before another server's write.
- Every write is `updateIf(version)`; a first write creates with `increment` and reads back. Unanswered writes are remembered and adopted when found.
- Staff edits, clears and restores go through `VaultsManager.replace` (under the lock) or the admin window; a vault open elsewhere is closed through the Redis channel `takeover`.
- Read-only windows are `ReadOnlyHolder`: every click and drag is cancelled. Never give them a writable path.
