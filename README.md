# FastBuilder

A competitive bridging/building plugin for Spigot 1.8.8. Players race on isolated islands, placing blocks to reach a finish zone as fast as possible. Supports multiple maps, cosmetics, replays, a coin economy, and deep admin tooling.

---

## Table of Contents

- [Requirements](#requirements)
- [Gameplay Overview](#gameplay-overview)
- [Commands](#commands)
  - [Player Commands](#player-commands)
  - [Admin Map Commands](#admin-map-commands)
  - [Economy & Utility Commands](#economy--utility-commands)
- [Permissions](#permissions)
- [Gameplay Features](#gameplay-features)
- [Configuration Files](#configuration-files)

---

## Requirements

| Dependency | Type | Notes |
|---|---|---|
| Spigot 1.8.8 | Required | |
| WorldEdit | Required | Island schematic loading |
| Citizens | Required | Map selector NPCs |
| FastAsyncWorldEdit | Optional | Faster island generation |
| DecentHolograms | Optional | Per-island floating stats |
| PlaceholderAPI | Optional | Scoreboard/hologram placeholders |

---

## Gameplay Overview

Each **map** is a bridging course. Players join and are assigned a private **island** where they build across a gap to reach the **finish zone**. The run is timed from the first block placement to entering the finish zone. On completion, coins are awarded based on how the player's time compares to the server average. Players can reset their island at any time to try again.

---

## Commands

### Player Commands

| Command | Description | Permission |
|---|---|---|
| `/fb join <map>` | Join a map and be assigned a free island | `fastbuilder.command.fb.join` |
| `/fb leave` | Leave your current island and return to lobby | `fastbuilder.command.fb.leave` |
| `/fb reset` | Reset your island back to its template | `fastbuilder.command.fb.reset` |
| `/length <blocks>` | Set your run distance (custom-length maps only) | `fastbuilder.feature.custom_length` |
| `/length reset` | Reset your run distance to the map default | `fastbuilder.feature.custom_length` |
| `/stats` | View your own statistics in a GUI | `fastbuilder.command.stats` |
| `/stats <player>` | View another player's statistics | `fastbuilder.command.stats` |
| `/stats <player> <map>` | View statistics for a specific map | `fastbuilder.command.stats` |
| `/leaderboard` | View top times for all maps | `fastbuilder.command.leaderboard` |
| `/leaderboard <map>` | View top times for a specific map | `fastbuilder.command.leaderboard` |
| `/leaderboard <map> <limit>` | View top N times for a map | `fastbuilder.command.leaderboard` |
| `/coins` | View your coin balance | `fastbuilder.command.coins` |
| `/coins <player>` | View another player's balance | `fastbuilder.command.coins` |
| `/booster` | Open the booster menu | `fastbuilder.play` |

**Aliases:** `/lb` → `/leaderboard`

---

### Admin Map Commands

All `/map` subcommands require `fastbuilder.admin` or their specific permission listed below.

#### Setup & Editing

| Command | Description | Permission |
|---|---|---|
| `/map setup` | Start the interactive map setup wizard | `fastbuilder.command.map.setup` |
| `/map setup --infinite` | Set up an infinite (endless) map | `fastbuilder.command.map.setup` |
| `/map setup --customlength` | Set up a map with adjustable finish distance | `fastbuilder.command.map.setup` |
| `/map setup continue` | Resume a paused setup session | `fastbuilder.command.map.setup` |
| `/map setup finish <name>` | Complete setup and save the map as `<name>` | `fastbuilder.command.map.setup` |
| `/map setup cancel` | Abort the current setup session | `fastbuilder.command.map.setup` |
| `/map edit <map>` | Edit an existing map's spawn, zones, or template | `fastbuilder.command.map.setup` |

#### Map Properties

| Command | Description | Permission |
|---|---|---|
| `/map rename <old> <new>` | Rename a map | `fastbuilder.command.map.rename` |
| `/map enable <map>` | Make a map available to players | `fastbuilder.command.map.enable` |
| `/map disable <map>` | Prevent players from joining a map | `fastbuilder.command.map.disable` |
| `/map delete <map> confirm` | Permanently delete a map | `fastbuilder.command.map.delete` |
| `/map seticon <map> [block]` | Set the map's display icon in GUIs | `fastbuilder.command.map.seticon` |
| `/map info <map>` | Show full details for a map | `fastbuilder.admin` |
| `/map list` | List all available maps | `fastbuilder.play` |
| `/map help [page]` | Show paginated command help | — |

#### Island Scaling & Layout

| Command | Description | Permission |
|---|---|---|
| `/map scale <map> <count>` | Set the number of island slots | `fastbuilder.command.map.scale` |
| `/map distance <map> <blocks>` | Set the gap between adjacent islands | `fastbuilder.command.map.distance` |
| `/map regen <map>` | Clear all placed blocks on every island | `fastbuilder.command.map.regen` |
| `/map autoscale <map> <true\|false>` | Toggle automatic island scaling | `fastbuilder.command.map.autoscale` |

#### Gameplay Tuning

| Command | Description | Permission |
|---|---|---|
| `/map setdeathy <map> <Y>` | Set Y level below which players auto-fail | `fastbuilder.command.map.setdeathy` |
| `/map setmintime <map> <ms>` | Reject completions faster than this (milliseconds) | `fastbuilder.command.map.setmintime` |
| `/map setmaxtime <map> <ms>` | Auto-fail runs exceeding this time (0 = no limit) | `fastbuilder.command.map.setmaxtime` |
| `/map setrank <map> <tier> <ms>` | Set a rank threshold (`diamond`/`gold`/`silver`/`bronze`) | `fastbuilder.command.map.setrank` |
| `/map setcustomlength <map> <min> <max>` | Enable custom-length mode with an allowed range | `fastbuilder.command.map.setcustomlength` |

#### Island Designs

| Command | Description | Permission |
|---|---|---|
| `/map adddesign <map> [key]` | Add an alternative island schematic variant | `fastbuilder.command.map.adddesign` |
| `/map removedesign <map> <key>` | Remove an alternative design | `fastbuilder.command.map.removedesign` |
| `/map setdesignmeta <map> <key>` | Save spawn/finish/NPC positions for a design variant | `fastbuilder.command.map.adddesign` |

---

### Economy & Utility Commands

| Command | Description | Permission |
|---|---|---|
| `/coins add <player\|*> <amount>` | Add coins to a player (or all online players) | `fastbuilder.coins.admin` |
| `/coins remove <player\|*> <amount>` | Remove coins from a player | `fastbuilder.coins.admin` |
| `/coins set <player\|*> <amount>` | Set a player's coin balance | `fastbuilder.coins.admin` |
| `/booster give <player> <type> <amount>` | Grant a booster to a player | `fastbuilder.booster.admin` |
| `/booster take <player> <type> <amount>` | Remove a booster from a player | `fastbuilder.booster.admin` |
| `/booster clear <player>` | Clear all of a player's boosters | `fastbuilder.booster.admin` |
| `/booster info <player>` | View a player's active boosters | `fastbuilder.booster.admin` |
| `/build` | Toggle creative build mode on your island | `fastbuilder.admin` |
| `/fb reload` | Reload all configuration files live | `fastbuilder.command.fb.reload` |
| `/fb dump` | Upload a diagnostic report and return a URL | `fastbuilder.command.fb.dump` |

**Booster types:** `STARTER`, `SMALL`, `NORMAL`, `EXTENDED`, `EXTENDED_PLUS`, `MEGA`, `LARGE`, `ULTRA`, `SUPREME`

---

## Permissions

### Core

| Permission | Default | Description |
|---|---|---|
| `fastbuilder.admin` | op | Grants all admin commands and features |
| `fastbuilder.play` | true | Allows playing FastBuilder |

### Player Commands

| Permission | Default | Description |
|---|---|---|
| `fastbuilder.command.fb.join` | true | Use `/fb join` |
| `fastbuilder.command.fb.leave` | true | Use `/fb leave` |
| `fastbuilder.command.fb.reset` | true | Use `/fb reset` |
| `fastbuilder.command.fb.reload` | op | Use `/fb reload` |
| `fastbuilder.command.fb.dump` | op | Use `/fb dump` |
| `fastbuilder.command.stats` | true | View statistics |
| `fastbuilder.command.leaderboard` | true | View leaderboards |
| `fastbuilder.command.coins` | true | View coin balance |
| `fastbuilder.command.build` | op | Toggle creative build mode |
| `fastbuilder.stats.reset` | true | Reset personal statistics |
| `fastbuilder.feature.practice_mode` | true | Access practice mode in settings |
| `fastbuilder.feature.custom_length` | true | Adjust finish distance via `/length` |
| `fastbuilder.bypass.border` | op | Bypass island boundary restrictions |

### Map Admin Commands

| Permission | Default | Description |
|---|---|---|
| `fastbuilder.command.map` | op | Access the `/map` command |
| `fastbuilder.command.map.setup` | op | Create and edit maps |
| `fastbuilder.command.map.rename` | op | Rename maps |
| `fastbuilder.command.map.regen` | op | Regenerate all islands |
| `fastbuilder.command.map.seticon` | op | Set map GUI icon |
| `fastbuilder.command.map.enable` | op | Enable maps |
| `fastbuilder.command.map.disable` | op | Disable maps |
| `fastbuilder.command.map.delete` | op | Delete maps |
| `fastbuilder.command.map.scale` | op | Set island count |
| `fastbuilder.command.map.distance` | op | Set island gap |
| `fastbuilder.command.map.autoscale` | op | Toggle auto-scaling |
| `fastbuilder.command.map.setdeathy` | op | Set fall-death Y level |
| `fastbuilder.command.map.setmintime` | op | Set minimum valid run time |
| `fastbuilder.command.map.setmaxtime` | op | Set maximum run time |
| `fastbuilder.command.map.setrank` | op | Set rank time thresholds |
| `fastbuilder.command.map.adddesign` | op | Add island design variants |
| `fastbuilder.command.map.removedesign` | op | Remove island design variants |
| `fastbuilder.command.map.setcustomlength` | op | Enable custom-length mode |

### Economy

| Permission | Default | Description |
|---|---|---|
| `fastbuilder.coins.admin` | op | Modify other players' coin balances |
| `fastbuilder.booster.admin` | op | Manage player boosters |
| `fastbuilder.booster.1_5x` | false | Permanent 1.5× coin multiplier |
| `fastbuilder.booster.2x` | false | Permanent 2× coin multiplier |
| `fastbuilder.booster.3x` | false | Permanent 3× coin multiplier |

### Cosmetics & Shop

| Permission | Default | Description |
|---|---|---|
| `fastbuilder.blocks.*` | false | Unlock all building blocks without purchase |
| `fastbuilder.block.<material>.<data>` | false | Unlock a specific building block |
| `fastbuilder.pickaxe.*` | false | Unlock all pickaxes without purchase |
| `fastbuilder.pickaxe.<material>.<data>` | false | Unlock a specific pickaxe |
| `fastbuilder.animation.*` | false | Unlock all reset animations |
| `fastbuilder.animation.<name>` | false | Unlock a specific reset animation (e.g. `fall_down`) |
| `fastbuilder.sound.*` | false | Unlock all death sounds |
| `fastbuilder.sound.<name>` | false | Unlock a specific death sound (e.g. `creeper`) |
| `fastbuilder.cosmetic.autofill` | true | Auto-refill blocks when hotbar runs out |
| `fastbuilder.cosmetic.oneclickpick` | false | Grant One-Click Pick without purchase |
| `fastbuilder.cosmetic.infiniteblocks` | false | Grant Infinite Blocks without purchase |

### Replays

| Permission | Default | Description |
|---|---|---|
| `fastbuilder.replays.N` | — | Allow storing up to N replays per map (replace N with a number) |
| `fastbuilder.replays.unlimited` | false | No replay storage limit |

---

## Gameplay Features

### Islands & Maps
Each map has a configurable number of **island slots**. When a player joins, they are assigned a free slot and the template is loaded for them. Islands are isolated — players cannot see or interfere with one another's builds. Island dimensions, spawn/finish positions, and the gap between slots are all configurable per map.

**Auto-scaling** monitors occupancy and automatically adds more island slots when a threshold (default 80%) is reached, down to a minimum island count (default 15).

### Timer & Finish Detection
The run timer starts on the player's first block placement and stops when they enter the **finish zone** — a bounding box at the far end of the island. Runs outside configured minimum/maximum time thresholds are rejected. Players are ranked (diamond / gold / silver / bronze) based on configurable time tiers per map.

### Coins & Rewards
Players earn coins in two ways:
- **Completion reward** — 5–30 coins scaled to how their time compares to the server average.
- **Playtime reward** — a periodic coin drop for active players (configurable rate and interval).

Coins are spent in the **Shop** on cosmetics or temporary boosters.

### Coin Boosters
Players can activate a **temporary booster** (1.25×–5× coins, 15–60 minutes of active play). Nine purchasable tiers are available. Admins can assign permanent per-permission multipliers (1.5×, 2×, 3×). The effective multiplier is always the highest of any active permanent or temporary booster.

### Shop & Cosmetics
The **Shop GUI** lets players purchase:
- **Building blocks** — unlocks additional block materials for building.
- **Pickaxes** — unlocks faster or specialty pickaxe types.
- **Reset animations** — controls how blocks clear when resetting (e.g. *Fall Down*, *Item Drop*).
- **Death sounds** — a sound that plays when falling off the island.

All items can alternatively be unlocked via permissions without requiring a coin purchase.

### Practice Mode
Toggled from the **Settings GUI**. In practice mode, placed blocks are tracked separately (shown in lime clay) and persist across normal resets. Statistics are not recorded while practice mode is active. Useful for testing a technique before attempting a real run.

### Custom-Length Maps
On maps with custom-length enabled, players can use `/length <blocks>` or the **Settings GUI** to slide the finish island closer or further. The allowed range (e.g. 20–100 blocks) is set by admins. The finish platform repositions in real-time.

### Infinite Maps
Infinite maps have no finish zone. Players build indefinitely; progress is tracked by **distance traveled** rather than time.

### Alternative Island Designs
A single map can have multiple **design variants** (different schematics). Each variant has independent spawn, finish zone, NPC, and hologram positions. Players switch designs from the **Island Selector GUI** mid-session.

### Replay System
Every completed run is automatically saved as a replay. Players can open the **Replay Viewer** from their hotbar to watch a previous run replayed by an NPC (using their skin and name as recorded). Storage limits are configurable (default 20 per map; personal bests and favorited replays are always kept).

### Island Hopping
When enabled, a player who walks into an adjacent empty island is automatically switched to it, allowing continuous gameplay without rejoining.

### Fall Detection
Maps can define a **death Y level**. Falling below it counts as a failed run and immediately resets the player. Detection runs every tick for near-instant response.

### Scoreboards & Action Bar
Every player sees a **sidebar scoreboard** with their personal best, current session top-3 times, block count, and coin balance. The **action bar** displays a live run timer while a run is active. Both support PlaceholderAPI.

### Holograms (requires DecentHolograms)
A floating hologram above each occupied island shows the player's name, personal best, and attempt statistics. Hologram content and position are configurable per map and per design variant.

### NPCs (requires Citizens)
Each island spawns a **Map Selector NPC**. Right-clicking it opens the map list GUI. NPC name and position are configurable.

### Statistics
Per-player, per-map stats are stored and include: personal best time, average time, total attempts, successful attempts, and success rate. Players can view their own or others' stats via `/stats` and can reset their own stats from the Settings GUI (costs coins).

### Hotbar
Players spawn with a preset hotbar:

| Slot | Item | Function |
|---|---|---|
| 0–1 | Building blocks | Chosen block material, auto-refilled |
| 2 | Pickaxe | Unbreakable; breaks placed blocks |
| 3 | Practice blocks | Visible only in practice mode |
| 4 | Island Selector | Switch to a different island |
| 5 | Shop | Open the cosmetics shop |
| 6 | Replay Viewer | Watch previous runs |
| 7 | Settings | Toggle practice mode, infinite blocks, etc. |
| 8 | Leave | Return to lobby |

### Admin Build Mode
`/build` switches an admin to creative mode on their current island with flight enabled. Blocks placed in build mode persist until the map is rescaled or regenerated — useful for designing and testing layouts live.

### Diagnostic Dump
`/fb dump` collects server and plugin state (version, config, map data, loaded plugins) and uploads it to Bytebin, returning a URL for sharing with support.

---

## Configuration Files

| File | Purpose |
|---|---|
| `config.yml` | Core settings: storage backend, coin rates, timing, animations, leave behavior, autoscale thresholds |
| `messages.yml` | All player-facing chat messages and prefixes |
| `guis.yml` | GUI layouts: slot positions, page sizes, button items for all menus |
| `items.yml` | Hotbar item names and materials |
| `maps/<name>.yml` | Per-map data: world, origin, dimensions, spawn/finish, scale, rank times, design variants |
| `replays/<uuid>/` | Stored replay files (compressed binary, managed automatically) |

### Key `config.yml` Options

| Option | Description |
|---|---|
| `storage.type` | `yaml` / `sqlite` / `mysql` |
| `coins-per-hour` | Playtime coin reward rate |
| `reset-stats-cost` | Coins required to reset statistics |
| `replay.max-replays` | Max replays stored per player per map (`-1` = unlimited) |
| `replay.player-name-mode` | `recorded` (name at run time) or `current` (live username) |
| `island-hopping.enabled` | Allow auto-switching to adjacent empty islands |
| `autoscale.scale-threshold` | Occupancy % that triggers a new batch of islands |
| `leave-item.action` | `KICK` / `BUNGEE` / `SPAWN` / `COMMAND` |
| `holograms.enabled` | Enable DecentHolograms integration |
| `npcs.enabled` | Enable Citizens NPC integration |
| `animation.blocks-per-tick` | Reset animation speed (blocks cleared per tick) |
| `finish-trigger-mode` | `zone` (bounding box) or `touch` (pressure plate) |
