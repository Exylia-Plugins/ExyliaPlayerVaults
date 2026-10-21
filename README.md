# ExyliaPlayerVaults

Free, open-source personal vaults for Paper and Folia: **a real container per player, kept in the database, open in exactly one place at a time across a whole network**.

Built so a vault can never duplicate an item: not with two windows, not across a server switch, not after a crash.

## Features

- **Vaults by rank**: `exyliaplayervaults.amount.<n>` sets how many vaults a player has and `exyliaplayervaults.rows.<1-6>` how big they are. A vault that becomes smaller keeps what no longer fits out of sight (or hands it back, if you prefer).
- **Vault list** (`/pv`): every vault with how full it is, the locked ones a rank would unlock, and the next one for sale.
- **Rename and icons**: players name their vaults and pick an icon (a material, an item they hold up, a head) from the list.
- **Buy more vaults** with any currency ExyliaLib knows (Vault, ExyliaEconomy, PlayerPoints...), each one pricier than the last.
- **Search**: `/pv search diamond` says which vaults hold it, shulker boxes and bundles included.
- **Restrictions**: open cooldown, no vaults in combat, disabled worlds and game modes, blocked materials (also inside shulker boxes and bundles).
- **Staff tools**: browse anybody's vaults, read only or to edit, online, offline or on another server; empty one; browse its **history** and put an earlier copy back. Every staff action goes to the ExyliaLib audit log (`/exylialib logs`).
- **Placeholders**: `%playervaults_unlocked%`, `%playervaults_max%`, `%playervaults_bought%`, `%playervaults_rows%`, `%playervaults_next_price%`.
- **Six languages**: English, Spanish, Portuguese, French, Russian and Turkish, every message and menu editable.

## How it stays dupe-free

- A vault is open in **one window anywhere on the network**. The window holds a lock row every server sharing the database sees, renewed while it is open; a second opener is told it is in use.
- The lock is given back **only after the last write landed**, and the next window reads the row **past every cache**, so a player switching servers always gets what the previous server wrote.
- Every write is a **compare-and-set on a version**: a server that lost its lock can never write over a newer row.
- Open vaults are written every 30 seconds and on close; after a change the player's own file is saved too, so a crash cannot leave an item both in the vault and in the inventory.
- Read-only views cancel every click, and blocked items are refused by every route into a container (clicks, shift-clicks, number keys, the offhand key, drags, bundles).

## Requirements

- Paper or Folia 1.21+, Java 21
- [ExyliaLib](https://modrinth.com/plugin/exylialib) ([GitHub releases](https://github.com/DiGround-s/ExyliaLib/releases)). Install it first; without it the plugin stays off and says where to get it.
- For a network: every server on the same database (MySQL, MariaDB, PostgreSQL or MongoDB) in `plugins/ExyliaPlayerVaults/database.yml`, ideally with Redis enabled there too so staff can close a vault open on another server.

## Updates

ExyliaLib checks this plugin's GitHub releases and announces a new version in the console and to admins. Nothing is downloaded on its own: run `/exylialib plugins` to download the update, which is applied on the next restart. A new major version waits for you to install it by hand, and a release that needs a newer ExyliaLib waits until the library has updated. Tune it under `plugin-updates` in `plugins/ExyliaLib/config.yml`.

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/pv` | `exyliaplayervaults.use` | the vault list |
| `/pv <number>` | `exyliaplayervaults.use` | opens a vault |
| `/pv search <item>` | `exyliaplayervaults.search` | which vaults hold an item |
| `/pvadmin list <player>` | `exyliaplayervaults.admin.view` | somebody's vault list |
| `/pvadmin view <player> <number>` | `exyliaplayervaults.admin.view` | a vault, read only |
| `/pvadmin edit <player> <number>` | `exyliaplayervaults.admin.edit` | a vault, to change it (closed wherever it is open) |
| `/pvadmin history <player> <number>` | `exyliaplayervaults.admin.history` | earlier copies, to look at or put back |
| `/pvadmin clear <player> <number>` | `exyliaplayervaults.admin.clear` | empties a vault (kept in its history) |
| `/pvadmin reload` | `exyliaplayervaults.admin.reload` | reloads the files |

Aliases: `/playervault`, `/playervaults`, `/vault`, `/vaults`, `/playervaultsadmin`.

## Permissions

- `exyliaplayervaults.amount.<n>`: how many vaults (largest wins; `vaults.default-amount` without one).
- `exyliaplayervaults.rows.<1-6>`: rows per vault (largest wins; `vaults.default-rows` without one).
- `exyliaplayervaults.buy`, `exyliaplayervaults.customize.name`, `exyliaplayervaults.customize.icon`: on by default.
- `exyliaplayervaults.bypass.cooldown`, `exyliaplayervaults.bypass.restrictions`: operators by default.
- `exyliaplayervaults.bypass.blocked`: nobody by default, not even `*`.
- `exyliaplayervaults.admin`: every admin command; `exyliaplayervaults.*`: everything above except `bypass.blocked`.

## Configuration

- **`config.yml`**: vault amounts and rows, hidden slots (`KEEP` or `RETURN`), blocked materials, checkpoint period, saving the player on write, the open cooldown, combat, worlds and game modes, purchases (currency, base price, multiplier, limit), how many history copies to keep, name length, default icon and sounds. `language` picks the language (`default` follows ExyliaLib's).
- **`lang/<code>/messages.yml`** and **`lang/<code>/menus/`**: every message and screen. The vault list is yours to restyle; the admin screens are rewritten on every start.
- **`database.yml`**: where vaults are stored. Tables: `sc_vaults` (the same table ExyliaSurvivalCore used, so a shared database keeps every vault), `playervaults_looks`, `playervaults_buyers`, `playervaults_history`.

## Building

```
./gradlew build
```

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[GNU General Public License v3.0](LICENSE).
