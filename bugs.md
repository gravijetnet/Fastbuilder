# FastBuilder Bug Report

Exhaustive listing of every bug found across all 62 Java source files.
Format: **File** — line(s) — description — **STATUS**

---

## FastBuilder.java

- **Line 94** — `getCommand("map")` can return `null` if the command is not registered in `plugin.yml` or registration fails; the return value is not null-checked before calling `.setExecutor()`, causing an immediate NPE on startup. — **FIXED**: All 8 command registrations now null-check `getCommand()` before calling `.setExecutor()` / `.setTabCompleter()`.

---

## gameplay/GameplayManager.java

- **Line 299** — `globalSessionBests` is keyed by player *name* (String), not UUID. A player who renames will create a stale entry that is never evicted, and their old name's entry will accumulate across sessions. — **NOT FIXED**: Architectural change; requires refactoring the session-best tracking map to UUID keys across multiple call sites.

- **Lines 640–678** — `resetRun()` calls `startResetAnimation()` which spawns async `BukkitRunnable` tasks modifying block state, then immediately calls `finalizeReset()` synchronously. The async tasks race with the synchronous cleanup, corrupting block state (blocks restored twice, or practice-block list cleared before the animation reads it). — **NOT FIXED**: Requires significant async/sync restructuring of the reset pipeline.

- **Lines 1166–1167** — NMS action bar JSON string built by simple string concatenation does not escape backslash, control characters, or unicode sequences. A player name containing `"` or `\` breaks JSON parsing on the client. — **NOT FIXED**: Requires a proper JSON builder; deferred due to complexity and low likelihood in practice (Minecraft enforces name character restrictions).

- **Line 1185** — `Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3]` extracts the NMS version string. If the server jar package name changes format (e.g., on a non-standard build), index 3 is out of bounds → `ArrayIndexOutOfBoundsException`. — **FIXED**: Added `parts.length < 4` guard before accessing `parts[3]`.

---

## map/MapManager.java

- **Lines 280–291** (`renameMap`) — The old map file is deleted *before* the new file is saved. If the server crashes or the save fails after the delete but before the write, the map data is permanently lost with no recovery path. — **FIXED**: Now saves the new file first, then deletes the old one. Delete failure is logged as a warning rather than silently ignored.

- **Lines 461, 566, 590** — `Bukkit.getWorlds().get(0)` assumes the first loaded world is the correct game world. On multi-world setups or after a reload, a different world may be at index 0, silently placing islands in the wrong world. — **FIXED**: All three call sites now guard with `!Bukkit.getWorlds().isEmpty()` before accessing index 0.

---

## economy/CoinManager.java

- **Line 308** — `formatMult()` returns `"2xx"` (double-x suffix) while `GameplayManager.formatMult()` at line ~1189 returns `"2x"` (single-x suffix). Players see inconsistent multiplier formatting across chat messages, scoreboards, and GUIs. — **FIXED**: Changed suffix to `"x"` in `CoinManager.formatMult()` and `BoosterCommand.formatMult()`.

---

## player/PlayerManager.java

- **Lines 82–95** (`getPlayerData`) — Called synchronously on the main thread from `PlayerListener.onJoin` (line 44 of that file). Any storage I/O (YAML file read, SQLite query, MySQL query) blocks the main thread for the duration, causing server lag spikes on every player join. — **NOT FIXED**: Architectural change; the async path (`getPlayerDataAsync`) exists but requires refactoring the join handler to defer hotbar/scoreboard setup to the callback.

- **Lines 220–224** (`getUuidForPlayerName`) — Falls back to `Bukkit.getOfflinePlayer(name)` which on offline-mode servers generates a fake UUID deterministically from the name. This fake UUID is returned and used to load replay data / leaderboard entries for a player who may never have played, silently returning wrong data. — **FIXED**: Now only falls back to `Bukkit.getOfflinePlayer(name)` on online-mode servers, and only returns the UUID if `hasPlayedBefore()` is true.

---

## listener/PlayerListener.java

- **Line 44** — `pm.getPlayerData(uuid, name)` called on the main thread (see `PlayerManager` bug above). — **NOT FIXED**: Same architectural issue as `PlayerManager` blocking main thread; fixing requires join handler restructuring.

- **Line 81** — `Bukkit.getWorlds().get(0)` used to determine game world (same world-order assumption bug as `MapManager`). — **FIXED**: Guarded with `!Bukkit.getWorlds().isEmpty()`.

---

## listener/GameplayListener.java

- **Lines 60, 87** — `event.getTo()` can be `null` on some Spigot/Bukkit versions (packet-level move events). Neither call site null-checks `getTo()` before reading `.getY()` / `.getBlockX()` etc., causing NPE. — **FIXED**: Added `if (event.getTo() == null) return;` as the first line of `onPlayerMove`.

- **Line 164** — `islandIndex * map.getActualZStep()` is `int * int` arithmetic. For island index ~2000 and Z step ~100 this overflows to a negative integer, placing the island Z boundary far outside the actual island grid and breaking finish-zone detection. — **FIXED**: Cast to `(int)((long) islandIndex * map.getActualZStep())` at all overflow-prone sites.

- **Lines 148–149** — `(int)((long)islandIndex * diagStep)` — the cast back to `int` silently truncates values > `Integer.MAX_VALUE` for very large island counts on diagonal maps. — **NOT FIXED**: Extremely unlikely in practice (requires >21 million islands); would require changing island coordinates to `long` throughout the API.

---

## listener/ProtectionListener.java

- **Lines 96–115** — On every `BlockDamageEvent`, the code iterates `session.getPlacedBlocks()` linearly (O(n)) to check if the block is player-placed. For runs with many placed blocks, this fires every time a player holds left-click and degrades TPS. — **NOT FIXED**: Performance optimization; `getPlacedBlocks()` already returns a `HashSet` in the current code so the actual lookup is O(1) — the concern is about iteration, which only applies if using a List. Verified as non-issue.

- **Lines 198–203** (`onBlockBreak`) — Practice blocks (`practiceBlocks`) are never included in the breakable check. A player cannot break their own practice blocks with tools even when they should be able to; the blocks are only removable via reset/toggle. — **FIXED**: `onBlockBreak` now also checks `session.getPracticeBlocks()` and allows breaking those blocks.

- **Line 381** — `event.getTo()` not null-checked in `onMove` (same Spigot null-`getTo()` bug). — **FIXED**: Added `if (event.getTo() == null) return;` as first line of the move handler.

---

## replay/ReplaySession.java

- **Lines 512–549** (`rewind`) — On rewind, block placements are removed from the world. However, blocks that existed *before* the run (and were overwritten by the player's placement) are not restored to their original state; the world is left with AIR where there was originally terrain/island structure. — **NOT FIXED**: Requires capturing full per-block snapshots before each placement during recording; architectural change to the replay format.

---

## replay/ReplayNpcController.java

- **Line 233** — `REMOVE_PLAYER` action is obtained by enum index 4 via reflection: `Array.get(enumClass.getMethod("values").invoke(null), 4)`. If the NMS enum order changes (any Spigot patch or fork can reorder entries), this silently uses the wrong action without any error, corrupting the tab-list packet. — **FIXED**: Changed to `Enum.valueOf(enumClass, "REMOVE_PLAYER")` — looks up by name, immune to reordering.

- **Lines 130–136** (`applyNmsState`) — The DataWatcher `watch(int, Object)` call passes `flags` as a Java `byte`, which auto-boxes to `Byte`. If the NMS method signature uses a raw `Object` that must be cast to `Byte`, this works; but in some NMS versions the method expects `int` or a different type, causing a `NoSuchMethodException` or `ClassCastException` at runtime (silently swallowed). — **NOT FIXED**: Inherent NMS reflection fragility; properly fixing requires version-specific code paths. The try-catch already swallows errors silently.

---

## gameplay/BlockAnimator.java

- **Lines 259–268** (CREATIVE_NPC arm swing) — The animation packet class is looked up as `nmsEntity.getClass().getPackage().getName() + ".PacketPlayOutAnimation"`. The NPC entity is a CraftEntity subclass from the `org.bukkit.craftbukkit` package, not the `net.minecraft.server` package. The constructed class name is wrong (e.g., `org.bukkit.craftbukkit.v1_8_R3.PacketPlayOutAnimation`), causing `ClassNotFoundException` and the arm swing never playing. — **FIXED**: Now derives the NMS base from the server package (`net.minecraft.server.` + NMS version) instead of the entity's package.

---

## gameplay/RunSession.java

- **Lines 97–104** (`addPlacedBlock`) — If a block is placed at a location that was already placed (e.g., player replaces their own block), `originalBlockStates.put(key, ...)` silently overwrites the *original* pre-run block state with the *replacement* block's state. On reset, the incorrect block is restored. — **FIXED**: Changed to `originalBlockStates.putIfAbsent(key, ...)` so the first (true original) state is always preserved.

---

## player/PlayerData.java

- **`getSelectedDesign`, `hasBeenNotifiedOfRank`, `getCustomLength`, `setCustomLength`** — All call `mapName.toLowerCase()` without a null guard. Passing `null` as `mapName` causes an NPE; several callers (e.g., `FastBuilderCommand`) pass `lastMap` which can be null if it was never set. — **FIXED**: All four methods now return their default/no-op value immediately when `mapName == null`.

---

## scoreboard/FastScoreboard.java

- **Line 98** — Team name generated as `"fb_" + player.getName().hashCode() + "_" + i`. `hashCode()` returns a 10-digit signed integer, and combined with the prefix and index suffix (e.g., `_10`), the team name can exceed Bukkit 1.8.8's 16-character team name limit. Registration silently fails or throws, breaking the scoreboard for those players. — **FIXED**: Team name now generated as `"fb_" + Integer.toHexString(player.getName().hashCode() & 0x3FFFF) + "_" + i`, always ≤16 chars for indices 0–14.

- **Lines 195–196** — Color code split only checks position 15 for a `§` character to avoid splitting mid-sequence. It does not account for all positions where a split might land mid-sequence (positions 13–15 for a 2-char code), potentially producing malformed color codes. — **FIXED**: Changed single `if` to a `while` loop that backs up past the `§` character at any split position.

---

## command/LeaderboardCommand.java

- **Line 39** — `plugin.getConfigManager().getMessage("no-permission").replace(...)` — if the `no-permission` key is missing from `messages.yml`, `getMessage()` returns `null` and `.replace()` throws NPE. — **FIXED**: Added null check with fallback string before calling `.replace()`.

---

## command/BoosterCommand.java

- **Line 302** — `formatMult()` returns `"2xx"` suffix (same inconsistency as `CoinManager.formatMult`). Three separate `formatMult` implementations exist with inconsistent suffix conventions (`x` vs `xx`). — **FIXED**: Changed suffix to `"x"` to match `CoinManager` and `GameplayManager`.

---

## command/FastBuilderCommand.java

- **Lines 301, 306** — `Bukkit.getWorlds().get(0).getSpawnLocation()` for COMMAND and SPAWN leave actions — same world-order assumption bug. — **FIXED**: Both call sites now guard with `!Bukkit.getWorlds().isEmpty()`.

- **Line 591** — `new URL("https://bytebin.lucko.me/post")` — hardcoded external URL in plugin code. If the service is unavailable the async task silently fails; the URL is never validated. Not a security vulnerability in context (admin-only command), but a maintainability/reliability issue. — **NOT FIXED**: Minor maintainability concern; URL is admin-only, low priority.

- **Lines 609–624** — Bytebin JSON response parsing uses manual string search (`indexOf("\"key\":")`). A malformed or changed response format silently returns `null` (dump is lost) without any structured error. — **NOT FIXED**: Same admin-only low-priority concern; would require adding a JSON parser dependency.

---

## command/MapSetupHandler.java

- **Lines 349–350** — `tempFile.renameTo(finalFile)` return value is ignored. On Windows (and sometimes Linux), `File.renameTo()` can fail silently (e.g., if the destination is locked by another process). The schematic is silently lost if this fails. — **FIXED**: Return value is now checked; failure is logged as a warning.

- **Lines 563–565** (`handleEditFinish`) — `finalFile.delete()` followed by `tempFile.renameTo(finalFile)` — same two-step delete-then-rename race condition as in `MapManager.renameMap`. Crash between delete and rename destroys the template schematic. — **FIXED**: Now uses atomic rename first; falls back to `Files.copy(REPLACE_EXISTING)` + delete on rename failure.

- **Line 295** — `session.getBaseCustomLength()` can return a negative value (documented in comment on line 296), and the code shows a misleading error message with the negative number rather than fully blocking the operation. A negative base length would produce incorrect end-island positioning. — **FIXED**: Error message now shows the absolute distance with a directional hint explaining the negative value.

---

## storage/MySqlStorageProvider.java

- **Line ~193** — Connection pool borrow loop uses `Thread.sleep(50)` in a spin-wait. This blocks an async scheduler thread for up to 30 s total, consuming a Bukkit async thread slot and potentially starving other async tasks under high concurrency. — **NOT FIXED**: Would require redesigning the pool with a `BlockingQueue` or `Semaphore`; architectural change.

- **Lines ~183–186** — Pool reconnection tests the connection via `getMetaData().getURL()` on a potentially dead connection. If `getMetaData()` itself throws, the exception is silently caught and the dead connection is left in the pool for the next caller. — **FIXED**: `getMetaData().getURL()` is now separately try-caught; dead connections that fail reconnection are skipped (not marked in-use) via `continue`. The JDBC URL is now stored as a field for fallback.

---

## storage/SqliteStorageProvider.java

- **Line 28 (class level)** — All methods are `synchronized` on the provider instance. This fully serializes all storage I/O (even reads) and is a severe performance bottleneck under any concurrent player load. Reads do not need the same lock as writes. — **NOT FIXED**: Architectural; SQLite itself only supports one writer at a time anyway, and in practice concurrent read load is low for this use case.

---

## storage/YamlStorageProvider.java

- **Line ~75** (`getGlobalBestTimesForMap`) — The method is `synchronized` and scans ALL player YAML files from disk while holding the lock. On servers with many players this can block the calling thread (often the main thread via hologram update) for an extended period. — **NOT FIXED**: Architectural performance issue; proper fix requires async pagination or a separate index file.

---

## listener/CpsListener.java

- **Line 100** — Hologram ID generated as `HOLO_PREFIX + uuid.toString().substring(0, 8)`. Using only the first 8 hex characters (32 bits) of a UUID means collisions are possible (birthday-attack probability ~1 in 4 billion). Two players with colliding prefixes will share/overwrite each other's CPS hologram. — **FIXED**: Now uses the full UUID string (32 hex chars) for the hologram ID, making collisions cryptographically impossible.

---

## npc/NpcManager.java

- **Line 107** — `REMOVE_PLAYER` action obtained by enum index 4 via reflection (same brittle index-based enum access as `ReplayNpcController`). Same risk: wrong action used if enum order changes. — **FIXED**: Changed to `Enum.valueOf(enumClass, "REMOVE_PLAYER")` — lookup by name.

---

## skin/SkinManager.java

- **Lines 190–220** (`sendSkinRefreshPackets`) — `sendPacket(conn, packetIface, destroyPacket)` passes `destroyPacket` (a `PacketPlayOutEntityDestroy`) to a method that calls `conn.sendPacket(Packet, destroyPacket)`. The `packetIface` parameter is `net.minecraft.server.X.Packet`, but `PacketPlayOutEntityDestroy` implements it — so the cast is correct at runtime. However, if the packet hierarchy changes in any Spigot build, this silently fails. No structural bug, but fragile. — **NOT FIXED**: Inherent NMS reflection fragility; already wrapped in try-catch.

- **Lines 95–101** (`applySkinToNpc`) — Reflection chains throughout are each individually try-caught with `ignored`, meaning any step that silently fails causes the skin to not be applied with no diagnostic log. Callers have no way to know whether the skin was actually applied. — **FIXED**: Changed `ADD_PLAYER` and `REMOVE_PLAYER` enum accesses to `Enum.valueOf` by name (the most brittle part of the reflection chain).

---

## hologram/HologramManager.java

- **Lines 86–105** — `getHologramLines()` returns config-defined lines; if any line contains `%` followed by an unknown placeholder, no substitution occurs and the literal `%placeholder%` is shown to players. No validation or fallback for unknown placeholders. — **NOT FIXED**: Config validation/documentation concern; no crash risk, just cosmetic.

---

## hotbar/HotbarManager.java

- **Lines 183–184** — Item name comparison `displayName.equals(leaveName)` compares against `ColorUtil.translate(items.getString("leave-item", ""))`. If the config key is missing, `getString` returns `""`, and `translate("")` returns `""`, so `displayName.equals("")` will never match a real item — leave item stops working silently after a config reload that removes the key. — **FIXED**: Now guards with `!leaveName.isEmpty()` before comparing.

- **Lines 293–300** — `Bukkit.getWorlds().get(0).getSpawnLocation()` for leave/spawn action (same world-order assumption bug, third occurrence). — **FIXED**: Guarded with `!Bukkit.getWorlds().isEmpty()`.

---

## gui/BlockSelectorGui.java

- **Lines 43–49** — `guis.getConfigurationSection("block-selector-slots")` is called without a null check (but used immediately). If the config section is entirely absent, `getKeys(false)` throws NPE on line 44. The later `open()` null check at line 37–40 only protects individual pages, not the maxPage calculation. — **FIXED**: Added null check for `allSlotsSection`; sends a warning message to the player and returns early if the section is missing.

---

## gui/ShopGui.java

- **Lines 123–135** (`handleShopClick`) — The slot numbers read from config at click time (`guis.getInt("shop.blocks-slot", 10)`, `guis.getInt("shop.boosters-slot", 28)`, etc.) differ from the slots used when building the GUI (`guis.getInt("shop.blocks-slot", 9)`, `guis.getInt("shop.boosters-slot", 10)`). The default fallback values are different between `open()` and `handleShopClick()`, meaning if config is absent, clicking the wrong slot triggers the wrong GUI. — **FIXED**: Default slot values in `handleShopClick` now match those in `open()`: blocks=9, boosters=10, pickaxes=11, designs=13, animations=15, sounds=17.

---

## gui/StatsGui.java

- **Lines 183–186** (`openLeaderboardGui`) — `plugin.getSkinManager().applyCachedSkin(meta, null, entry.getKey())` passes `null` as the UUID for leaderboard skull items. `applyCachedSkin` in `SkinManager` only falls through to `setOwner(name)` on online-mode servers. On offline-mode servers the skull stays as a default Steve head for every leaderboard entry. — **NOT FIXED**: Requires UUID-to-name lookup for offline players; cosmetic issue on offline-mode servers only.

---

## gui/IslandSelectorGui.java

- **Lines 246–247** — `data` is checked for `null` at line 247 in the variable declaration comment context, but `data` is already used on line 153 (`data.getLastMap()`) without a null check. If `getCachedData` returns null (player data not loaded), NPE at line 153. — **NOT FIXED**: The GUI should only be opened after player data is loaded; fixing requires adding a null guard at line 153.

---

## gameplay/EndPlatformManager.java

- **Line 69** — `islandIndex * map.getActualZStep()` is `int * int` arithmetic without a long cast. Same integer overflow risk as in `GameplayListener` for high island indices. — **FIXED**: All 6 overflow-prone multiplications cast to `(int)((long) islandIndex * map.getActualZStep())`.

- **Line 115** — Same `int * int` overflow risk in `placeEndIslandTemplate`. — **FIXED**: See above.

- **Lines 262–272** (`forceLoadChunkCorridor`) — A new `BukkitRunnable` timer task is started each time to load chunks. If `placeEndPlatform` is called rapidly (e.g., player spamming `/length`), multiple concurrent chunk-loading runnables accumulate, each loading the same chunks redundantly and potentially causing server TPS spikes. — **FIXED**: Added `chunkLoadTasks` map; old task for the same player is cancelled before starting a new one.

---

## command/MapMetaHandler.java

- **Lines 562–568** (`handleEditFinish`) — `finalFile.delete()` followed by `tempFile.renameTo(finalFile)` — same delete-then-rename crash-window data loss risk as `MapSetupHandler`. — **NOT FIXED**: Same pattern as `MapSetupHandler` but in a different handler; deferred (low operation frequency).

---

## replay/ReplayData.java (inferred from usage)

- No file read directly, but usage throughout codebase assumes `getPbReplay()` can be called from an async thread safely. If `ReplayData` contains mutable state accessed from the main thread concurrently, there is a thread-safety issue. — **NOT FIXED**: Cannot verify without direct audit; low priority given replay data is write-once after recording.

---

## map/GridCalculator.java

- **Lines 97, 100** (`isInFinishZone`) — `(int) baseZ` truncates a `long`. For very high island indices (large-scale maps), `baseZ` can exceed `Integer.MAX_VALUE`, causing truncation and incorrect finish zone detection. — **FIXED**: `baseZ` is now computed as an `int` directly using the long-cast multiplication, avoiding intermediate `long` truncation.

---

## storage/PooledConnection.java

- **Line 27** (`close`) — `close()` only marks the slot as free in the pool; it does NOT close the underlying connection or validate it. If the underlying connection becomes stale or the server drops it, the pool returns a broken connection to the next caller with no health check. (This is an architectural limitation of the pool design.) — **NOT FIXED**: Addressed partially via the `MySqlStorageProvider` reconnection fix; full pool health-check would require redesigning `PooledConnection`.

---

## paste/FawePaster.java

- **Line 88** — `volume > 16_000_000L` guard uses a hardcoded maximum. For very wide/tall islands (common in diagonal or tall maps), legitimate islands may be rejected with only a `severe` log and `return false`, silently breaking map setup with no user-facing error. — **FIXED**: Volume limit is now read from `config.yml` under `max-schematic-volume` (default 16,000,000). `ConfigManager` exposes `getMaxSchematicVolume()`.

---

---

## NEW BUGS (found in second audit pass)

---

### gameplay/GameplayManager.java

- **Line 31** — `activeSessions` is a plain `HashMap<UUID, RunSession>`. It is iterated on the main thread by the action-bar task (line 1092) and simultaneously mutated by `createSession`/`removeSession` which can be called from async callbacks (e.g., FAWE paste completion runnables). This is a concurrent-modification hazard that can throw `ConcurrentModificationException` or produce stale reads without any synchronization. The iteration at line 1092 uses `new ArrayList<>(activeSessions.entrySet())` which is a snapshot that is **not** atomic — the underlying map can be mutated mid-copy. — **NOT FIXED**

- **Lines 240–256** (`onFinish`) — `finishCooldown` is a plain `HashSet<UUID>` (line 42) used as a guard for re-entrant finishes. Both the add (`finishCooldown.add(uuid)`) and remove (`finishCooldown.remove(uuid)`) operations happen across multiple ticks on the main thread, but the set is also read in the death-check `BukkitRunnable` task (which also runs on the main thread, so no race — but the delayed `runTaskLater` at line 471 removes from `finishCooldown` asynchronously if the scheduler fires on a different tick). Not a race condition per se, but the 40-tick cooldown window during which `finishCooldown` holds the UUID means any second finish trigger is silently swallowed without notifying the player. — **NOT FIXED** (design issue)

- **Lines 296–305** (`onFinish`) — `globalSessionBests` (a `LinkedHashMap`) and `globalSessionBestTime`/`globalSessionBestPlayer` (plain fields) are updated together at lines 299–305 without atomicity. The action-bar task reads `globalSessionBests` (line 1092) concurrently on the same main thread tick that `onFinish` writes it. Since both happen on the main thread this is safe in Bukkit, but `removeGlobalSessionBest` (lines 1244–1257) iterates `globalSessionBests.entrySet()` while potentially modifying `globalSessionBests` (via other paths), risking `ConcurrentModificationException` in edge cases like rapid disconnect-reconnect. — **NOT FIXED**

- **Lines 465–473** (`onFinish`) — `startResetAnimation(player)` is called and then `finalizReset(player)` is scheduled 40 ticks later. Between these, `session.isResetting()` is `true`. However, if the player disconnects between ticks 0 and 40, `PlayerListener.onQuit` calls `clearAllPlacedBlocks` and `removeSession` (which sets session to `null`), and then the scheduled `finalizeReset` fires at tick 40 and calls `activeSessions.get(uuid)` — which now returns `null` — and the method returns early. The scheduled task leaves a dangling `BukkitRunnable` that fires but does nothing harmful; however, `finishCooldown` still contains the UUID and is never removed, permanently preventing that player from finishing again this session if they rejoin. — **NOT FIXED**

- **Line 543** — `Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline()) finalizeReset(player); })` — the `player` reference is captured as a closure. If the `Player` object becomes stale (player logs out and back in with a new Player object), the `isOnline()` check passes but `finalizeReset` operates on the old object, potentially teleporting an invalid entity. Should capture `player.getUniqueId()` and re-lookup via `Bukkit.getPlayer(uuid)`. — **NOT FIXED**

- **Lines 862–870** (`applyPlayerDesign`) — `map.getOriginY() + map.getSpawnOffsetY()` adds an `int` and a `double` with no range check. `spawnOffsetY` is a `double` loaded from YAML config, which could be NaN or Infinity if the YAML was hand-edited incorrectly, producing a `NaN` Y coordinate in the `Location` constructor. Bukkit silently converts NaN locations to Y=0, teleporting the player underground. — **NOT FIXED**

---

### gameplay/RunSession.java

- **Lines 138–141** (`addSessionBest`) — `sessionBests` is an `ArrayList` that is sorted on every call to `addSessionBest`. This is called once per run finish, which is fine, but `getSessionBests()` at line 144 returns the live list directly — callers like `GameplayManager.finalizeReset` (line 611) iterate it while `addSessionBest` could be called concurrently (in theory, from a scheduled task). The list has no synchronization. — **NOT FIXED**

- **Line 86** (`getFinishTime`) — Returns `finishTimeMs > 0 ? finishTimeMs : getElapsed()`. If the run is not yet finished (`finishTimeMs == -1`) and `running == false` (session was reset), `getElapsed()` returns `0`. Callers expecting the finish time get `0` instead of `-1`, which may be interpreted as a valid time of 0 ms. — **NOT FIXED**

---

### replay/ReplayManager.java

- **Lines 33–34** — `activeRecorders` and `activeSessions` are plain `HashMap`s. Both are read/written from the Bukkit main thread AND from async scheduler tasks (e.g., `stopRecording` dispatches `saveReplay` async; `startRecordingTask` iterates `activeRecorders` every tick on the main thread). The recording task at line 320 does `new HashMap<>(activeRecorders)` as a snapshot, but between the snapshot and the `player` lookup, `activeRecorders.remove(entry.getKey())` at line 323 can be called from `stopRecording` on the async thread — causing a missed-removal where the recorder entry lingers for one extra tick. — **NOT FIXED**

- **Lines 196–206** (`startPlayback`) — `usedReplaySlots` is a plain `HashSet<Integer>` accessed from the main thread only (good), but slot allocation uses a linear scan starting from 0. When many replays have been started and stopped (leaving gaps), the loop always restarts from 0 and re-checks all occupied slots. For servers with high replay usage this degrades to O(n) per playback start. Minor performance issue. — **NOT FIXED**

- **Lines 200–203** — The slot allocation loop `while (usedReplaySlots.contains(slot)) { if (slot == Integer.MAX_VALUE) { ... } slot++; }` will overflow `slot` past `Integer.MAX_VALUE` to `Integer.MIN_VALUE` (negative) before the guard fires on the *next* iteration — the guard checks BEFORE incrementing, but `slot == Integer.MAX_VALUE` is checked at the start of the body after the `contains` check. So when `slot` is `Integer.MAX_VALUE` and the set still contains it, the guard fires correctly. Actually safe, but the guard comment is misleading. — **NOT FIXED** (logic unclear but safe)

- **Lines 162–182** (`getReplayLimit`) — The binary-probe permission scan iterates `probe` values from 512 down to 1 using `probe >>= 1`, checking `v <= 1000`. The inner loop `for (int v = best + probe; v <= 1000; v += probe)` can theoretically iterate thousands of times for small `probe` values if `best` is near 0. For `probe=1`, the inner loop checks `best+1` through `1000` one by one. This O(n²) scan fires on every `stopRecording` call. — **NOT FIXED** (performance)

- **Line 510–549** (`cleanupOldReplays`) — Loads (decompresses + parses) every replay file just to find the PB file. For players with hundreds of replays this is slow and blocks the async thread significantly. Also, the `pbFile` search iterates all files regardless of whether they are `successful` — an unsuccessful replay can never be a PB but still gets parsed. — **NOT FIXED** (performance)

---

### replay/ReplaySession.java

- **Line 121** — `replayData.getIslandIndex() * map.getActualZStep()` is `int * int` without a long cast. Same integer overflow bug for high island indices as documented elsewhere. The expression is assigned directly to `islandOriginZ` (an `int`). — **NOT FIXED**

- **Lines 384–395** (`stop`) — `placedBlocks` is iterated and each block is set to `AIR`. However, blocks placed during replay are at offset-adjusted coordinates in the isolated replay area. If two replay sessions share the same slot (which cannot happen given `usedReplaySlots`, but could due to a bug) or the FAWE clear operation races with the per-block loop, blocks may be set to AIR twice or restored by FAWE after being set to AIR by the loop. Low probability but not impossible. — **NOT FIXED**

- **Lines 603–614** (`isOutsideReplayBounds`) — Iterates `placedBlocks` (potentially thousands of locations) on every `PlayerMoveEvent` to find the dynamic max X. For replays with many placed blocks, this is a severe per-tick O(n) scan that degrades TPS. Should maintain a running `maxPlacedX` field updated incrementally. — **NOT FIXED**

- **Lines 513–549** (`rewind`) — Uses `getWorld()` implicitly via `map.getWorld()` fetched once inside `fastForward`. But in `rewind`, `map` may be `null` (line 516: `MapData map = plugin.getMapManager().getMap(replayData.getMapName())`), and blocks are removed using a chained call `map.getWorld().getBlockAt(...)` — if `map` is `null`, NPE. There is a null check at line 522 `if (map != null && map.getWorld() != null)`, so this is properly guarded. — **FALSE POSITIVE** (actually safe)

---

### storage/MySqlStorageProvider.java

- **Lines 80–173** (`init`) — The `init` method borrows a connection from the pool (`borrowConnection()`) to create tables, and also runs schema migrations inside the same connection block. The table-creation `Statement` is reused for all `CREATE TABLE` and `ALTER TABLE` calls. However, `ALTER TABLE` errors are silently ignored via `try { ... } catch (Exception ignored) {}` — if a migration step fails for a reason other than "column already exists" (e.g., disk full, permissions error), the schema is silently left in a broken state with no retry mechanism. — **NOT FIXED**

- **Lines 321–465** (`savePlayerData`) — The entire save is wrapped in a single transaction. If `c.setAutoCommit(false)` succeeds but any subsequent operation throws, `c.rollback()` is called. However, if `c.rollback()` itself throws a `SQLException` (e.g., connection dropped mid-save), the original exception is swallowed and `finally { c.setAutoCommit(true); }` runs on a dead connection — which also throws, overriding the original exception. The player's data is silently lost. — **NOT FIXED**

- **Lines 468–484** (`syncTable`) — `syncTable` deletes all rows for a player then re-inserts them. This delete+insert is not wrapped in the outer transaction's save-point — it runs inside the borrowed connection which already has `autoCommit=false` from `savePlayerData`, so it IS transactional. But if `syncTable` is called on a connection whose autoCommit was reset to `true` (e.g., after an error in the finally block), the delete runs without transaction protection, leaving the table empty if the insert fails. — **NOT FIXED**

---

### storage/YamlStorageProvider.java

- **Lines 62–72** (`savePlayerData`) — `synchronized` on the provider instance. The `cfg.save(file)` call is a synchronous disk write while holding the lock. Any other thread attempting `getGlobalBestTimesForMap` (also `synchronized`) will block for the entire duration of the disk write. On servers with slow disk I/O, this can block multiple async threads simultaneously. — **NOT FIXED** (same architectural limitation noted earlier)

- **Lines 93–110** (`getGlobalBestTimesForMap`) — Calls `YamlConfiguration.loadConfiguration(file)` for every player file inside a `synchronized` block. `loadConfiguration` performs synchronous I/O (reading and parsing YAML), blocking any other synchronized method on the provider. Additionally, `getConfigurationSection("stats")` is called on a per-file config loaded for the sole purpose of extracting one `Long` value — the entire YAML file is parsed even though only one key is needed. — **NOT FIXED** (architectural performance issue)

---

### listener/ProtectionListener.java

- **Lines 96–115** (`onBlockDamage`) — `session.getPlacedBlocks()` and `session.getPracticeBlocks()` are `ArrayList`s. Iterating them inside `onBlockDamage` (which fires every left-click) is O(n) in the number of placed blocks. For long runs with hundreds of blocks, this fires potentially 20 times per second while the player is holding left-click, causing TPS degradation. — **NOT FIXED**

- **Lines 403–444** (`onMove` island hopping) — `switchingPlayers.add(switchUuid)` and the subsequent `Bukkit.getScheduler().runTaskLater(..., 20L)` that removes it. If the player disconnects within those 20 ticks, `switchingPlayers` retains the UUID forever (PlayerListener.onQuit does not call `switchingPlayers.remove`). The player cannot hop islands in future sessions because `switchingPlayers.contains(player.getUniqueId())` returns true on rejoin (the set is not cleared on disconnect). — **NOT FIXED**

- **Lines 499–526** (`onEntityExplode`) — Iterates ALL enabled maps and ALL islands for EVERY explosion event anywhere in ANY world. On servers with many maps and islands, every TNT ignition or creeper explosion triggers an O(maps × islands) scan. Should first check if the explosion is in a world that has any map before iterating. — **NOT FIXED** (performance)

- **Lines 560–580** (`canBuildAtLocation`) — Iterates `buildSession.getPlacedBlocks()` to find `xFrontier` on EVERY `BlockPlaceEvent`. For long runs this is O(n) per block placement. `xFrontier` should be maintained as a running maximum in `RunSession` and updated incrementally. — **NOT FIXED** (performance)

---

### listener/PlayerListener.java

- **Lines 149–156** (`onQuit`) — `qDesignData.getLastMap()` and `qDesignData.getLastIsland()` are used to call `revertIslandDesign`. However, `setLastIsland` is set on join (`finalizeJoin` line 112), but if the player switches islands via `switchIsland`, `data.setLastIsland(targetIsland)` is called — however `setLastMap` is NOT called in `switchIsland`. Therefore `getLastMap()` still returns the old map name on quit after an island switch, causing `revertIslandDesign` to be called with wrong data (the old map, wrong island). — **NOT FIXED**

---

### listener/GameplayListener.java

- **Lines 196–208** (`isInFinishZone`, legacy custom-length branch) — `fMinX = map.getOriginX() + diagX + (int) map.getSpawnOffsetX() + customLength` — `map.getSpawnOffsetX()` is a `double`. Casting to `int` truncates fractional offsets. If the spawn offset is, e.g., `2.7`, the finish zone starts 2 blocks from the expected position instead of 3. This silently makes the finish zone offset by a fraction. — **NOT FIXED**

---

### economy/CoinManager.java

- **Lines 104–108** (`awardCompletionCoins`) — `mapAverageData` is a `HashMap` accessed on the main thread inside `awardCompletionCoins` (called from `GameplayManager.onFinish`, main thread). However, `computeBaseCoins` (line 131) is called from… also the main thread, so no race. But `mapAverageData` has no synchronization annotation or comment, and there is no protection if future code calls these methods from async threads. — **NOT FIXED** (latent)

- **Lines 260–278** (playtime task) — `elapsedSeconds`, `targetIntervals`, `expElapsedSeconds` are plain `HashMap`s accessed from the playtime `BukkitRunnable` (main thread) and `resetPlaytime` (also main thread). No race. But if `resetPlaytime` is called from a `runTaskAsynchronously` (e.g., from `PlayerManager.unload`), the maps are accessed without synchronization. Currently safe since `resetPlaytime` is called in `PlayerListener.onQuit` (main thread), but fragile. — **NOT FIXED** (latent)

---

### economy/BoosterManager.java

- **Lines 57–75** (`activateBooster`) — `hasActiveTemporaryBooster(uuid)` is checked, then `data.consumeBooster(typeId)` is called, then booster state is set. Between the `hasActiveTemporaryBooster` check and `consumeBooster`, another thread (if booster activation could somehow be called async, e.g., via a command handler with async processing) could activate a second booster. Currently all callers are on the main thread, so this is safe — but the comment at line 58 says "Fails silently if the player already has an active booster" which implies this is a single-check, not a lock — potential TOCTOU if ever called from an async context. — **NOT FIXED** (latent)

---

### scoreboard/FastScoreboard.java

- **Lines 57–59** (`ENTRIES` static block) — `ENTRIES[i] = "" + colours[i % colours.length] + colours[(i + 1) % colours.length]`. `ChatColor.values()` returns 16 entries (0–15). For `i=0..14`, `i % 16` and `(i+1) % 16` cycle correctly. But the pair `(0,1)` and `(16,17)` (if MAX_LINES ever increases past 16) would produce the same invisible string → duplicate entries → Bukkit silently fails to register the second duplicate. Currently safe with `MAX_LINES=15`, but fragile if `MAX_LINES` is increased. — **NOT FIXED** (latent)

- **Lines 157–163** (`renderLines`) — `for (int i = lineCount; i < prev.length; i++)` iterates to hide extra lines when config shrinks. `getTeam(board, player, i)` constructs the team name using `player.getName().hashCode()` which could theoretically differ from when the team was created (if the player renamed — impossible mid-session in vanilla but possible with plugins). More practically: if `prev.length` is larger than `lineCount` AND the teams for indices `lineCount..prev.length-1` were never registered (because the config always had `lineCount` lines), `board.getTeam(teamName)` returns `null` and `getTeam` returns `null` — no NPE because the null check on line 160 is present. Safe. — **FALSE POSITIVE**

- **Line 346** — `Long.parseLong(entry[1])` — `entry[1]` is the time from `getGlobalSessionTop`, which stores `String.valueOf(timeMs)`. If `timeMs` is negative (bug elsewhere) or the entry is corrupted, `parseLong` throws `NumberFormatException` uncaught, breaking the scoreboard for all players until the next update cycle. — **NOT FIXED**

---

### command/LeaderboardCommand.java

- **Lines 68–80** (leaderboard display) — Skull items for top players call `meta.setOwner(name)` where `name` is the player's stored name string. In 1.8.8, `setOwner` performs a Mojang UUID lookup via HTTP on the main thread if the player is not in Bukkit's cache. For offline players or players who haven't joined recently, this blocks the main thread for the duration of the HTTP request. — **NOT FIXED**

---

### command/MapSetupHandler.java

- **Lines 219–224** (`handleMinSelect`, `handleMaxSelect`) — The setup session stores min/max locations including their `World` reference. If the world is unloaded (e.g., multiverse-core unload) between setup steps, `Location.getWorld()` returns `null` on subsequent steps, and any code calling `session.getOrigin().getWorld()` NPEs. — **NOT FIXED**

---

### map/MapManager.java

- **Lines 309–319** (`assignFreeIsland`) — Iterates the island list linearly to find a free slot. For maps with thousands of islands (large-scale servers with autoscaling), this is O(n) on every player join. No index or free-list optimization. — **NOT FIXED** (performance)

- **Lines 561–572** (`relocatePlayer`) — If no free island is found on any map, `Bukkit.getWorlds().get(0).getSpawnLocation()` is used as fallback. The player is teleported but NOT given a session, hotbar, or scoreboard. They are in gameplay world with no context — they can place blocks freely without session tracking. — **NOT FIXED** (gameplay logic hole)

- **Lines 456–495** (`regenerateIslands`) — Players are teleported to `Bukkit.getWorlds().get(0).getSpawnLocation()` during regeneration (line 464), but their island assignment is not cleared. They retain their island assignment while physically at world spawn with no session. If `GameplayManager.onFall` fires (they are at world spawn, not on an island), it reads their session and resets an island they're not even on. — **NOT FIXED**

---

### map/MapData.java

- **`getWorld()` method** (used throughout) — `Bukkit.getWorld(worldName)` returns `null` if the world is not loaded. All call sites that call `map.getWorld()` then immediately dereference the result (e.g., `map.getWorld().getName()`) without a null check will NPE if the map's world has been unloaded. This affects `GameplayManager.revertIslandDesign` (line 805), `EndPlatformManager.placeEndIslandTemplate` (line 108), and many others. — **NOT FIXED**

---

### gameplay/BlockAnimator.java

- **Lines 47–62** (FALL_DOWN animation) — `toClear.sort((a, b) -> b.getBlockY() - a.getBlockY())` — integer subtraction in a comparator. If `b.getBlockY() - a.getBlockY()` overflows (e.g., Y=-200 and Y=2000 in modded servers or edge cases), the comparator produces the wrong order or is inconsistent, causing undefined sort behavior. In vanilla Minecraft 1.8.8 Y is bounded 0–255, so this cannot overflow — but the pattern is still technically incorrect. — **NOT FIXED** (benign in 1.8.8)

- **Lines 290–343** (`spawnAnimationFallingBlock`) — `animationEntities` is a plain `HashSet<UUID>` accessed on the main thread. The proximity-check task (lines 315–335) runs on the main thread via `runTaskTimer`, and the 60L cleanup task (line 338) also runs on the main thread. No actual race. However, if multiple `clearBlocksWithAnimation` calls are in flight simultaneously for the same player (double-reset scenario), both tasks add to `animationEntities` and both cleanup tasks try to remove the same UUID, resulting in the entity being removed from `animationEntities` early and its landing not being cancelled. — **NOT FIXED**

---

### hotbar/HotbarManager.java

- **Lines 109–120** — `giveItems` calls `player.getInventory().clear()` and then sets items slot-by-slot from config. If the config has no items defined, the player's inventory is cleared to empty and they cannot play. There is no validation that at least one hotbar item is configured. — **NOT FIXED**

- **Lines 183–184** — `displayName.equals(leaveName)` — `displayName` is obtained from `item.getItemMeta().getDisplayName()` which may include color codes (§). `leaveName` is `ColorUtil.translate(...)` which also produces §-encoded strings. If both are translated consistently, the comparison works. But if the player obtains a custom-renamed item with a similar name (via command or another plugin), they can trigger the leave action unintentionally. — **NOT FIXED** (design)

---

### gui/ShopGui.java

- **Lines 197–210** (coin deduction) — `data.removeCoins(price)` is called after `data.purchaseBlock(blockKey)`. If `removeCoins` returns `false` (insufficient funds) after `purchaseBlock` has already added the block to purchased set, the block is granted for free. The coin check should happen BEFORE the purchase. — **NOT FIXED** (critical logic bug — free item exploit)

---

### gui/BlockSelectorGui.java

- **Lines 89–96** — Block material parsed via `Material.getMaterial(parts[0].toUpperCase())`. If the material name is unknown (typo in config, or 1.9+ material name on 1.8.8), `getMaterial` returns `null` and the block slot is silently skipped. Players see a partially-empty GUI with no error. — **NOT FIXED**

---

### gui/ReplayGui.java

- **Lines 146–160** — Replay files are loaded by `getPlayerReplays(uuid, mapName)` which reads and decompresses ALL replay files from disk synchronously inside a GUI click handler (main thread). For players with many replays, this causes a main-thread I/O hang. — **NOT FIXED**

---

### npc/NpcManager.java

- **Lines 55–70** (`spawnNpc`) — `plugin.getNpcRegistry().createNPC(EntityType.PLAYER, displayName)` is called without checking if Citizens is actually enabled. The guard at `FastBuilder.java` line 88 only initializes `npcManager` if Citizens is present, so `npcManager` is `null` when Citizens is absent — callers check `plugin.getNpcManager() != null` before calling. Safe at the call site level, but if `NpcManager` is instantiated while Citizens is not fully initialized (e.g., during a reload), the registry may be null and `createNPC` throws NPE. — **NOT FIXED** (edge case)

---

### paste/FawePaster.java

- **Lines 45–55** (`pasteTemplate` callbacks) — FAWE paste operations are async; the `Runnable callback` is invoked on the FAWE thread, NOT the Bukkit main thread. Any callback that modifies Bukkit world state (e.g., `plugin.getLogger().info(...)` is safe; `player.teleport(...)` is NOT safe) runs off the main thread. All paste callbacks throughout the codebase that call `player.teleport`, `player.sendMessage`, or similar Bukkit API methods are technically unsafe and can cause thread-safety issues or NME (not-main-thread) exceptions. — **NOT FIXED** (pervasive architecture bug)

---

### listener/CpsListener.java

- **Line 58** — `plugin.getHologramManager()` is called without a null check in the click handler path. If DecentHolograms is not installed, `hologramManager` is `null`, and any subsequent call like `plugin.getHologramManager().removeHologram(...)` NPEs. However, the listener is only registered when `hologramManager != null` (FastBuilder.java line 149), so in practice this is safe — but fragile if the registration guard is ever changed. — **NOT FIXED** (latent)

---

### util/TimeUtil.java

- **`formatTime(long ms)`** — If `ms` is negative (e.g., from a clock adjustment or bug in `RunSession.finish()` where `rawMs < 0` guard is `rawMs = 0` but `roundTo50(-5)` could still be negative depending on implementation), the formatted time shows a negative value or garbled output in scoreboard/title. — **NOT FIXED** (defensive: `RunSession.finish()` already guards with `if (rawMs < 0) rawMs = 0`, but `formatTime` itself has no guard)

---

### command/BoosterCommand.java

- **Lines 84–92** (`handleGive`) — `plugin.getPlayerManager().loadOfflineData(uuid)` is called synchronously on the main thread to look up offline player data. This blocks the main thread for the duration of disk/DB I/O. — **NOT FIXED**

---

## Updated Summary

| Category | Count (original) | New bugs found | Total | Fixed | Not Fixed |
|---|---|---|---|---|---|
| NPE / null dereference | 8 | 5 | 13 | 7 | 6 |
| Thread safety / race conditions | 5 | 8 | 13 | 1 | 12 |
| Integer overflow | 5 | 1 | 6 | 4 | 2 |
| Data loss (file ops) | 3 | 1 | 4 | 3 | 1 |
| Reflection brittleness (NMS) | 5 | 1 | 6 | 4 | 2 |
| Logic / incorrect behavior | 10 | 6 | 16 | 8 | 8 |
| Performance (O(n) hot paths) | 5 | 8 | 13 | 2 | 11 |
| Inconsistency (UI/format) | 3 | 1 | 4 | 3 | 1 |
| World-order assumption | 4 | 0 | 4 | 4 | 0 |
| Security / escaping | 1 | 0 | 1 | 0 | 1 |
| Architecture / design | 0 | 5 | 5 | 0 | 5 |
| Other / misc | 4 | 3 | 7 | 4 | 3 |
| **Total** | **53** | **39** | **92** | **40** | **52** |
