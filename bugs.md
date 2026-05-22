# Fastbuilder Bug Report

Exhaustive listing of bugs found across all 63 Java source files.
Format: `File:Line — Type — Description — Status`

---

## CRITICAL

### Null Pointer Exceptions

**1. `command/MapCommandMessages.java:33`** — NPE — FIXED  
`msgAdmin(Player, String, String, String)`: if both `getAdminMessage()` and `getMessage()` return null,
`raw` was null and `raw.replace()` would throw NPE. Added `if (raw == null) raw = ""` guard.

**2. `command/MapCommandMessages.java:39-41`** — NPE — FIXED  
`msgMap()`: `getMessage(key)` can return null; added `if (raw == null) raw = ""` guard before `.replace()`.

**3. `listener/PlayerListener.java:93-94`** — NPE — FIXED  
`getMessage("no-free-islands")` could return null; extracted to local variable with null guard before calling `.replace()`.

**4. `listener/PlayerListener.java:97-98`** — NPE — FIXED  
Same issue on the `player.kickPlayer(…)` path. Same fix applied.

**5. `gameplay/GameplayManager.java:291`** — NPE / logic — FIXED  
`getCachedData()` returned null, but `finishCooldown.remove(uuid)` was never called, permanently locking
the player out of finishing. Added `finishCooldown.remove(uuid)` before the early return.

**6. `replay/ReplayManager.java:132`** — NPE — FIXED  
`stopRecording()` called `plugin.getGameplayManager().getSession(…)` without null-checking
`getGameplayManager()`. Added ternary null guard.

---

## Race Conditions / Concurrency

**7. `player/PlayerManager.java:82-92`** — Race condition — FIXED  
`getPlayerData()` had a non-atomic check-then-act (cache.get → put). Wrapped in `synchronized(cache)`
block so both steps are atomic under the map's own monitor.

**8. `player/PlayerManager.java:95-109`** — Race condition — FIXED  
`getPlayerDataAsync()` had the same check-then-act gap on cache miss. On the main-thread callback,
now re-checks under `synchronized(cache)` before inserting, and uses the already-cached object if
a concurrent load won the race.

**9. `replay/ReplayManager.java:189-191`** — Potential infinite loop — FIXED  
`startPlayback()`: `while (usedReplaySlots.contains(slot)) slot++` could loop forever if all int slots
were exhausted. Added guard: logs a severe error and returns early if `slot == Integer.MAX_VALUE`.

**10. `gameplay/GameplayManager.java:31`** — Thread-safety note — NOT FIXED (by design)  
`activeSessions` is a plain `HashMap` accessed only from Bukkit's main thread via tasks and event
handlers. Since Bukkit guarantees single-threaded event dispatch and all BukkitRunnable tasks run on
the main thread, this is safe as-is. No change needed.

**11. `economy/CoinManager.java:49-53`** — Thread-safety note — NOT FIXED (by design)  
All four maps are only read/written from the Bukkit main-thread scheduler task and main-thread event
handlers. Safe under Bukkit's single-threaded model.

---

## Logic Errors

**12. `economy/CoinManager.java:213`** — Division by zero — FIXED  
`computeTierCoins()`: `averageMs <= 0` guard added (returns 15 coins baseline). Also guards `ratio <= 0`
(returns the 30-coin cap).

**13. `economy/CoinManager.java:228`** — Biased / non-random interval — FIXED  
Replaced `System.currentTimeMillis() % range` with `ThreadLocalRandom.current().nextInt(range)`.

**14. `economy/CoinManager.java:304`** — Floating-point equality — FIXED  
`formatMult()`: replaced `mult == Math.floor(mult)` with `Math.abs(mult - Math.floor(mult)) < 1e-9`.
Same fix applied to the identical method in `GameplayManager.java`.

**15. `gameplay/GameplayManager.java:291`** — Logic ordering — FIXED  
See bug #5 above (same site).

**16. `gameplay/GameplayManager.java:1163-1165`** — NMS action-bar JSON injection — FIXED  
`sendActionBar()`: the message is now escaped (backslash and double-quote) before embedding in the JSON
literal. Also replaced silent `catch (Exception ignored)` with `plugin.getLogger().fine(…)` so errors
are diagnosable.

**17. `gameplay/RunSession.java:53-59`** — Negative elapsed time from clock skew — FIXED  
`finish()`: added `if (rawMs < 0) rawMs = 0` guard before rounding, preventing negative finish times
from NTP clock adjustments.

**18. `replay/ReplayManager.java:169-172`** — O(1000) permission check per run end — FIXED  
`getReplayLimit()` previously iterated `for (i = 1000; i >= 1; i--)` (1000 permission checks).
Replaced with a descending power-of-two binary probe that finds the highest granted permission in
~20 checks, then a short fine-scan to confirm exact value.

**19. `listener/GameplayListener.java:148`** — Integer cast truncation for diagX — FIXED  
Changed `int diagX = (int)((long) islandIndex * map.getDiagonalStepX())` to compute the product into
a `long diagXLong` first, then cast. For normal server scale values the product fits in int, but the
intermediate step is now explicit.

**20. `map/GridCalculator.java:118`** — Integer overflow in island coordinate arithmetic — FIXED  
`getIslandBounds()`: `minZ` was computed as `originZ + index * zStep` in pure `int`. Changed to
`(int)(map.getOriginZ() + (long) index * map.getActualZStep())` to prevent overflow at large scales.

**21. `paste/FawePaster.java:87-88`** — Unguarded array allocation — FIXED  
`saveTemplate()`: added volume check (`(long)w*h*l`) before allocating `byte[]`. Returns `false` with
a severe log if volume is ≤ 0 or > 16,000,000 blocks.

**22. `paste/FawePaster.java:333`** — Incorrect bitmask `0xFFFF` — FIXED  
Changed `e.blockId & 0xFFFF` to `e.blockId & 0xFF`. Block IDs in 1.8.8 are 0–255; the previous mask
would pass values up to 65535 to `setTypeIdAndData`, causing invalid block placements.

**23. `replay/ReplayManager.java:241-243`** — Case-sensitive replay file filter on Linux — FIXED  
Changed `name.contains(…)` to `name.toLowerCase().contains(…)` (all three occurrences) so replay
files are found regardless of the original map name casing.

**24. `storage/MySqlStorageProvider.java:150`** — `CREATE INDEX IF NOT EXISTS` not supported in MySQL 5.x — FIXED  
Replaced with a bare `CREATE INDEX` wrapped in `try { … } catch (SQLException ignored) {}` to skip
gracefully if the index already exists, consistent with the pattern used for `ALTER TABLE` migrations.

---

## Resource Leaks

**25. `paste/FawePaster.java:326-341`** — BukkitRunnable not stored (`placeBlocksBatched`) — NOT FIXED  
The runnable correctly calls `cancel()` on itself when the block list is exhausted. The task will
complete naturally; not storing the reference only prevents external cancellation. Under the plugin
lifecycle (tasks are cancelled on disable by Bukkit), this is acceptable. Flagging as low-risk.

**26. `paste/FawePaster.java:345-362`** — BukkitRunnable not stored (`clearBlocksBatched`) — NOT FIXED  
Same reasoning as #25.

**27. `replay/ReplayManager.java:298-311`** — Recording task iteration/removal note — NOT FIXED  
The task iterates a snapshot copy of the map (`new HashMap<>(activeRecorders)`) and removes from the
original. A recorder added between the snapshot and removal would only be missed for one tick then
caught on the next. This is a cosmetic tick-lag, not a data loss issue.

**28. `replay/ReplayManager.java:387`** — InputStream not closed on GZIPInputStream constructor failure — FIXED  
Wrapped the `new GZIPInputStream(rawStream)` call in a `try { … } catch (IOException e) { rawStream.close(); throw e; }`
block so `rawStream` is always closed if the GZIP constructor throws on corrupt data.

**29. `storage/MySqlStorageProvider.java:175-194`** — Busy-wait in pool borrowing — NOT FIXED  
The `Thread.sleep(50)` busy-wait is only entered from async storage threads. While a condition
variable would be cleaner, replacing the busy-wait requires a significant restructure of the pool
and the risk outweighs the benefit here.

---

## Security Issues

**30. `economy/CoinManager.java:289-294`** — Command injection via player name — FIXED  
Added `name.matches("[a-zA-Z0-9_]{1,16}")` validation before dispatching the console command.
Non-matching names (e.g. from offline-mode servers) are silently skipped.

**31. `command/MapCommandMessages.java:109`** — Click-event uses map name in RUN_COMMAND — NOT FIXED  
Map names are admin-configured values validated on creation; exploiting this requires admin access
which already implies full server control. Risk is negligible.

---

## Deprecated API / Compatibility

**32. `paste/FawePaster.java:333`, `gameplay/GameplayManager.java:1063`** — Deprecated `setTypeIdAndData` — NOT FIXED  
The plugin targets Spigot 1.8.8 where this is the correct API. Removing it would require a Material
enum refactor for a future MC version that is not currently in scope.

**33. `player/PlayerManager.java:212`** — Deprecated `Bukkit.getOfflinePlayer(String)` — NOT FIXED  
This is a known Bukkit limitation; no safe alternative exists for offline UUID lookup by name.
The `@SuppressWarnings` is correctly placed. Only called from command handlers, not hot paths.

---

## Performance Issues

**34. `gameplay/RunSession.java:102`** — String concatenation as HashMap key on every block place — NOT FIXED  
While packing coordinates into a `long` key would be faster, the current String key approach is
clear and the per-run block count is bounded (typically < 200 blocks). Not worth the obfuscation.

**35. `gameplay/GameplayManager.java:1196-1204`** — Linear sort on every scoreboard query — NOT FIXED  
Session bests are maintained at ≤ 3 entries each (per-player list), and global session bests are
sorted only when queried (typically once per scoreboard update tick). The sort cost is negligible.

**36. `replay/ReplayManager.java:241-258`** — Full directory scan + file deserialization for PB lookup — NOT FIXED  
This call is used only when a viewer requests to watch a PB replay (non-hot path). Acceptable as-is.

**37. `replay/ReplayManager.java:486-522`** — Cleanup re-reads all replay files after every save — NOT FIXED  
Called asynchronously after each run ends. With default limit of 20 replays this is at most 20 small
GZIP reads per run completion. Acceptable as-is for now.

**38. `economy/CoinManager.java:250`** — `Bukkit.getOnlinePlayers()` allocation every second — NOT FIXED  
One collection snapshot per second is negligible overhead for a Minecraft server.

---

## Logic / Off-by-One

**39. `listener/GameplayListener.java:62-65`** — Head-rotation-only move filtered before finish check — NOT FIXED  
This is an intentional optimization. The death-check task at 1 tick compensates for missed
PlayerMoveEvents. Changing this would process finish-zone checks on head-rotation packets (very
frequent), causing significant overhead.

**40. `gameplay/GameplayManager.java:425-426`** — `activeDist == 0` stored as custom-length best — FIXED  
Added a clamp of `activeDist` to `[effectiveMin, effectiveMax]` and a `statsMap != null` guard before
calling `updateCustomLengthBest`. Distance 0 is now never stored.

**41. `paste/FawePaster.java:255-264`** — Short overflow in island block offsets — FIXED  
`BlockEntry` fields `relX`, `relY`, `relZ` widened from `short` to `int`. All construction sites and
the `convertLegacyToSchematic` local max variables updated accordingly. Offset computation in
`pasteIslandQueue` now uses `int` arithmetic throughout.

---

## Input Validation

**42. `command/MapCommandMessages.java`** — No sanitization of map name in messages — NOT FIXED  
Map names are admin-controlled config values, not player-supplied input. The attack surface is limited
to admins, who already have full server access.

**43. `storage/MySqlStorageProvider.java:66`** — SSL disabled unconditionally — FIXED  
Connection URL now reads `storage.mysql.use-ssl` from config (defaults to `false` for backward
compatibility). Admins can enable SSL by setting `use-ssl: true` in `config.yml`.

---

## Minor / Code Quality

**44. `storage/SqliteStorageProvider.java`** — German inline comment — FIXED  
Translated to English: `// Load remaining sub-tables (already Java 8 compatible)`.

**45. `util/TimeUtil.java:74`** — `formatTimeFull` returned `"0,000"` for negative times — FIXED  
Changed to return `EMPTY_RAW` ("-,---") for negative input, consistent with `formatTime()`.

**46. `gameplay/GameplayManager.java:1160-1173`** — Silent swallow of all NMS exceptions — FIXED  
See bug #16. `catch (Exception ignored)` replaced with `plugin.getLogger().fine(…)`.

**47. `replay/ReplayManager.java:255`** — Exceptions silently ignored during PB replay load — FIXED  
`catch (Exception ignored)` replaced with a `plugin.getLogger().warning(…)` call.

**48. `replay/ReplayManager.java:516-520`** — `f.delete()` return value unchecked — FIXED  
Added `if (!f.delete()) plugin.getLogger().warning(…)` to surface deletion failures.

**49. `map/MapManager.java:29-31`** — HashMap without synchronization during reload — NOT FIXED  
`loadMaps()` is only called from the main thread (on enable and on `/fb reload`). All reads from
`getMap()` originate from Bukkit event handlers and tasks, also on the main thread. No async access
path exists. Safe as-is.

**50. `gameplay/BlockAnimator.java`** — FallingBlock entity UUID set grows unboundedly — NOT A BUG  
Upon further review, `spawnAnimationFallingBlock` already schedules a `runTaskLater(60L)` that
unconditionally removes the UUID from `animationEntities` and forces the entity dead if still alive.
This correctly handles the void-fall case. Closing as false positive.
