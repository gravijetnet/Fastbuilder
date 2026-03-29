# CRITICAL SYSTEM DIRECTIVE: FASTBUILDER PRO
You are a God-Tier Spigot Plugin Developer specializing in NMS, FastAsyncWorldEdit (FAWE) API, and highly optimized server architectures.
Target Environment: Custom Spigot 1.8.8 Fork running on **Java 21**. Use modern Java 21 features (records, enhanced switch expressions) but strictly adhere to the Spigot 1.8.8 API.
Dependencies: FastAsyncWorldEdit, Citizens, DecentHolograms.
State: Delete any existing source code. Start from an absolute blank slate.
Language: All code, variables, comments, and in-game messages MUST be strictly in English. Plugin name is strictly "Fastbuilder" (Case-sensitive).

# 1. MATHEMATICAL GRID & INSTANCING (CORE ENGINE)
- **The Template Zone:** `/map setup` ALWAYS teleports the admin to exactly `-1000 20 -1000`. This is just a temporary build zone. Once setup is complete, the template is saved/copied, and the physical blocks at the setup location MUST BE DELETED (replaced with air).
- **Global Map Axis (X-Axis):** Map types are pasted strictly along the positive X-axis.
    - Map 1 starts at `2000, 20, 0`. Map 2 at `4000, 20, 0`. Map 3 at `6000, 20, 0`.
    - ALL maps MUST face the positive X direction (players build towards +X).
- **Island Scaling Axis (Z-Axis):** When a map is scaled (`/map scale <map> <count>`), the individual island instances for players are pasted "to the right" of each other. Since they face +X, they must be pasted along the **positive Z-axis** on the same X and Y coordinates.
    - The distance is EXACTLY `/map distance <map> <blocks>`. Ensure bounding boxes are calculated perfectly so even diagonal maps NEVER overlap.
- **Switching Islands:** When a player switches to a new island: Clear all blocks on their old island. Completely REMOVE their old Hologram and NPC. Teleport them to the EXACT relative spawnpoint of the new island. Spawn the Hologram and NPC at the new island.

# 2. ADMIN SETUP STATE MACHINE (/map)
Requires full tab-completion. Teleport to `-1000 20 -1000`, clear inventory, set Creative & Fly, give Blazerod.
- **Step 1 (Area):** Left-click Pos1, Right-click Pos2 (Island boundaries).
- **Step 2 (Spawn):** Type `/map setup continue`. Go to exact spawn point, right-click Blazerod. Save relative location.
- **Step 3 (NPC):** Type `/map setup continue`. Go to NPC location, right-click Blazerod. Save relative location.
- **Step 4 (Finish):** Type `/map setup continue`. Select Pos1/Pos2 of the finish area (must be pressure plates).
- **Step 5 (Save):** Type `/map setup finish` then `/map setup name <name>`. Map is saved but disabled.

# 3. GAMEPLAY LOOP, PHYSICS & PROTECTION
- **Routing:** On BungeeCord join, teleport player to their last active map. If none, route to the default map (defined in config.yml). A default map MUST always exist. NO auto-generated stone islands.
- **Start State (On Join Island):** Clear inventory. Set Gamemode Survival. Give all items.
- **Physics/Damage:** NEVER take fall damage. NEVER die from the void (if Y falls too low, instantly teleport back to the island's spawn). If a player somehow manages to "die", teleport them to a free island on their current map.
- **Protection:** - Cannot break map blocks. Can ONLY break blocks they placed themselves.
    - Cannot enter other players' islands. If a player leaves their assigned island bounding box, instantly teleport them back to their island spawn.
- **Success Event:** Hitting the finish zone pressure plate -> Play success sound -> Launch Firework -> Wait exactly 2.0 seconds -> Teleport back to start and clear placed blocks.
- **Practice Mode:** - Player receives practice blocks. If they fall/die, placed practice blocks STAY on the map.
    - Practice time DOES NOT count towards the scoreboard or stats.
    - **CRITICAL ANTI-EXPLOIT:** Players cannot use practice blocks to shorten a real run. When the player disables Practice Mode, ALL practice blocks on the map MUST be completely cleared before a real run starts.

# 4. INVENTORY, ITEMS & UI
- **Inventory Layout:** - Exactly 2 Stacks of blocks. Add a setting/perk (buyable with Coins) to "Auto-Refill" blocks so they never run out.
    - 1 Pickaxe (Default: Diamond. Expandable via Store/NBT).
    - "Island Selector" (Player Head in hotbar).
    - "Leave Item" -> Executes BungeeCord send to server `Lobby-1`.
- **Island Selector GUI:** Opens via right-click. Centered, clean design. Empty islands show as numbered heads (1, 2, 3...). Occupied islands MUST show the actual Skin Head of the player currently there (NOT a Steve head). Cannot click occupied islands.
- **Block Selector GUI:** Block names MUST have colors and MUST NOT be italic (`&r` / no `§o`). Purchasable via Coins or permissions.
- **Map Selector GUI:** Large inventory (e.g., 54 slots). Must support adding custom "filler" items via config that do nothing, or changing the exact slot of a map item. Opened by clicking the Citizens NPC.
- **Citizens NPC:** Spawns exactly at the map's defined `npc-location`. MUST have the current player's exact skin.
- **DecentHolograms:** MUST spawn at every occupied island. Shows player's: Top Time, Total Attempts, Total Successful Runs.

# 5. HIGH-FIDELITY REPLAY SYSTEM
- **Recording:** Log per-tick data: X, Y, Z, Yaw (Head Movement), Pitch, Sneaking state, and Block Place/Break events. It must capture exact head rotations and sneaking, not just XYZ.
- **Replay GUI:** Centered, premium design. Separates Successful vs. Failed runs. Shows Date & Time.
- **Permissions:** Add permission nodes to limit how many replays a player can save/view.
- **Playback:** Fly mode for the spectator. A Citizens NPC mimics the recorded player perfectly. Hotbar items for playback control (Rewind, Fast-Forward, Speed, Exit).

# 6. ECONOMY, STATS & VISUALS
- **Scoreboard:** MUST be implemented and EXACTLY match this structure:
  ```java
  objective.getScore("§7§m-------------------").setScore(score--);
  objective.getScore("§8» §cRank: §6"    + getPlaceholder(player, "%phoenix_player_real_rank%")).setScore(score--);
  objective.getScore("§8» §cPlayers: §6" + getPlaceholder(player, "%phoenix_server_global_online%")).setScore(score--);
  objective.getScore("§8» §cCoins: §6"   + getPlaceholder(player, "%pxcosmetics_player_coins%")).setScore(score--);
  objective.getScore("§8» §cLevel: §6"   + getPlaceholder(player, "%phoenix_player_level_displayname%")).setScore(score--);
  objective.getScore("§8» §cPlaytime: §6" + playtime + "h").setScore(score--);
  objective.getScore("§f ").setScore(score--);
  objective.getScore("§7§ogravijet.net").setScore(score--);
  objective.getScore("§7§o§m-------------------").setScore(score--);