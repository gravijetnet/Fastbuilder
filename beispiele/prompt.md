# CRITICAL DIRECTIVE
You are an expert Spigot 1.8.8 developer specializing in NMS, FAWE, and complex server architectures.
Task: Create a flawless, highly performant FastBuilder plugin that is a 1:1 clone of the McPlayHD system.
State: The existing codebase is garbage. DELETE ALL CURRENT SOURCE FILES and start entirely from scratch.
Style & Language: The entire codebase, comments, and in-game messages MUST be in English.

# 1. CORE ARCHITECTURE & DEPENDENCIES
- API/Version: Spigot 1.8.8 (Java 8).
- Dependencies: FastAsyncWorldEdit (FAWE) for performant pasting, Citizens for NPCs, DecentHolograms for stats.
- World Join Logic: NO auto-generated stone islands. When joining the server, the player is routed to the map they last played. If none, route them to the default map defined in `config.yml` (designed for a BungeeCord network).

# 2. MAP & GRID SYSTEM
- Spacing: Different map types must be placed exactly 2000 blocks apart on a single axis (e.g., Map A at 2000 15 0, Map B at 2000 15 2000, Map C at 4000 15 2000).
- Island Instances: A single map type consists of multiple identical islands pasted next to each other.
- Building Rules: Players can only build/interact on their own assigned island instance. Total protection against interacting with other players' islands.

# 3. ADMIN SETUP COMMANDS (/map)
Full tab-completion is required for every command and sub-command.
- `/map setup`: Teleports admin to the current map origin (e.g., -1000 15 -1000). Clears inventory, sets Gamemode Creative & Fly. Gives a Blazerod as the selection tool.
  -> Step 1 (Island Area): Left-click block for Pos1, Right-click block for Pos2.
  -> Step 2 (Spawn): Admin types `/map setup continue`. Admin walks to desired spawn point, right-clicks the Blazerod to save the exact, relative location for all future island pastes.
  -> Step 3 (Finish Zone): Admin types `/map setup continue`. Uses Blazerod to select Pos1/Pos2 of the finish area (must be pressure plates, any type). Touching a pressure plate in this zone finishes the map.
  -> Step 4 (Finalize): `/map setup finish` followed by `/map setup name <name>`. Map is saved but disabled.
- `/map setname <old> <new>`: Renames a map.
- `/map seticon <map> <block>`: Sets the display item for the Map Selector GUI.
- `/map enable <map>`: Opens map for players.
- `/map disable <map>`: Closes map. Active players are sent to the default map. If full, send to another active map.
- `/map scale <map> <count>`: Defines total island instances for this map. Updates automatically. New ones are pasted via FAWE, removed ones send players away and replace the island with air.
- `/map distance <map> <blocks>`: The exact block distance between island instances of the same map. Calculate this precisely based on the Pos1/Pos2 of the map to avoid overlaps or excessive gaps.
- `/map autoscale <map> <true/false>`: Always maintain at least 15 island instances. Automatically scale up (paste more) when 80% capacity is reached.

# 4. GAMEPLAY & UI FEATURES
- The Map Selector NPC: Spawn a Citizens NPC for every player directly on their island. The NPC MUST have the exact skin of the player. If the player leaves the map, the NPC disappears. Right-clicking the NPC opens the Map Selector GUI (fully configurable, McPlayHD style).
- The Island Selector: A hotbar item (Player Head). Right-clicking opens a GUI with heads representing islands. Empty islands show as a number (e.g., 1 for island 1). Occupied islands show the skin of the player currently playing there. You cannot click/join an occupied island.
- Holograms (DecentHolograms): One hologram per island displaying the island owner's: Top Time, Total Attempts, Total Successful Runs for this specific map.
- Block Selector & Economy: GUI to select blocks. Every block needs a specific permission node, plus a wildcard permission for all. Players can buy blocks with Coins. Coins are earned via playtime and scoring good times.
- Menus: Include GUIs for Settings, Stats-Reset, and general Reset-Animations.

# 5. VISUALS & MESSAGES
- Format Strictness: NO emojis. NO AI-style formatting. Use colors `&c` (Primary), `&f` (Secondary), `&7` / `&8` (Accents).
- Example Format:
  `&c&lGraviJet &7» &f&lAdmin JoinMe &8- &7Commands`
  `&4● &c/adminjoinme tokens <player> &7» &fView token details`
- Scoreboard: Fully configurable via config. Must include a live Timer and a "Top 3 Session Best" section (McPlayHD style).
- Actionbar: Active live timer showing current run time.
- Templates: Base your structure and message keys heavily on the existing files in `/beispiele/config.yml`, `/beispiele/guis.yml`, `/beispiele/items.yml`, and `/beispiele/messages.yml`.

# 6. REPLAY SYSTEM
- Recording: Log every block placement and exact player movement per tick.
- GUI: Menu to select past replays (split by Successful and Failed runs), displaying date and time.
- Playback: When playing a replay, the player enters fly mode. A Citizens NPC spawns to replicate the exact movements, while blocks place themselves according to the record.
- Controls: Hotbar items (Heads/Icons) to control playback: Rewind, Fast-Forward, Stop, Change Playback Speed (Slower/Faster), and Exit Replay to return to bridging.

# 7. STATISTICS
- `/stats <player> [map]`: Shows global stats (Top time per map, total attempts, total successful runs). If the optional `[map]` argument is provided, show stats only for that specific map. Tab-completion required.

Now, initialize the project structure, set up the Maven dependencies (FAWE, Citizens, DecentHolograms), and begin by implementing the Map/Grid math and FAWE pasting logic.