CRITICAL SYSTEM OVERRIDE INSTRUCTION: You are acting as a Senior Java/Spigot Developer. This is a massive architectural overhaul of the "Fastbuilder" plugin. Your previous iterations skipped crucial details, used placeholders, or implemented superficial fixes. This time, you must read EVERY SINGLE WORD of this specification. Do not output placeholder code (// do something here). Implement the actual, production-ready, highly optimized logic.

Every single string, item, size, and feature must be 100% configurable in English via YAML. Treat this prompt as a strict technical specification document.
🏛️ 1. Core State Machine & Reset Logic (CRITICAL)

    The Double-Reset Bug: Ensure resetPlayer() is locked by the state machine. If a player finishes a run, trigger the reset animation. Once the animation finishes, teleport them exactly once. Never trigger a second reset.

    100% Block Clearance: During any reset (finish, death, or out-of-bounds), iterate and force-clear every single placed block. Ghost blocks are unacceptable.

    Out-of-Bounds (OOB) Handling: If a player steps out of their island boundaries, instantly trigger the full block reset (clear all their blocks) AND teleport them back.

    Strict Build Protection: Intercept BlockPlaceEvent and BlockBreakEvent. If a reset animation is playing or a replay is active, absolutely cancel the event so players cannot place phantom blocks.

👥 2. NPC, Tablist & Visuals

    Creative NPC Identity: The reset NPC must perfectly mimic the player who just finished the run. Fetch the player's exact Skin and Username and apply it to the NPC. DO NOT use generic names like "&6Builder". The current animation speed is perfect, keep it.

    Zero-Tick Tablist Hiding: The NPC must never flash on the Tablist, not even for a millisecond. Use PacketPlayOutPlayerInfo with REMOVE_PLAYER in the exact same tick it spawns, or use Bukkit Teams to hide it completely.

    Death Sounds: Audio is currently completely broken. Fix the death sounds so they actually play. Remove the "Portal" sound entirely. Use punchy, short sounds.

🎥 3. High-Performance Replay Engine

    5-Second Rewind Math: Rebuild the "Rewind" button. It must not restart the replay from the beginning. It must jump back exactly 5 seconds (100 ticks) from the current timestamp and reconstruct the block state accordingly.

    Extreme Compression Storage: Serialize replay data and templates into a highly compressed format (e.g., custom binary or GZIP). They must be extremely space-efficient.

    Permission-Based Limits: Implement configurable replay limits (e.g., fastbuilder.limit.50). -1 means infinite.

🛡️ 4. GUI Framework & Inventory Hardening (CRITICAL BUGS)

    Inventory Stealing Bug: The Pickaxe Shop is completely broken. Players can move items around and put them in their own inventory. You MUST cancel InventoryClickEvent (event.setCancelled(true)) for all custom menus.

    Pickaxe Shop Fixes: Integrate the "One-Click Pick" (Diamond Axe) directly into this menu. Ensure the purchase logic works and players cannot buy items they already own.

    Main Shop Layout: Make it visually appealing. Put Sandstone (Block Selector) exactly on Slot 9, and the Pickaxe Shop exactly on Slot 11.

    Block Selector: Add a "Back to Shop" item to return to the main menu.

    Island Selector Menu:

        Must be exactly 3x9 (27 slots).

        Place a "Back to Shop" button in the bottom center slot.

        Use Material.PAPER for the designs. Do NOT use enchanted books or glass panes. No enchantments.

        Instant Update: Clicking a design must instantly change the island and clear all blocks immediately.

⚙️ 5. Commands, Setup & Admin Workflow

    Command Removals: Completely delete /map setinfinite and /length.

    Setup Cleanup: Remove the confusing message: » Set island spacing: /map distance aaa 50 when a setup finishes.

    Interactive Help Menu: Format /map help exactly like this: &fShowing page &c1 &fof &c3. &f(&714 results&f). &a». The » and « must be clickable via ClickEvent.Action.RUN_COMMAND to actually turn the pages.

    Custom Length Feature: * Add the command /map customlength <true/false> [minblocksfromspawntopressureplates].

        Players must be able to configure this in the settings menu.

        Physical Movement: The end island (with the pressure plates) must actually physically move or generate closer/further away based on this setting. (Developer note: Implement a setup step where the admin explicitly defines the "End Island Schematic" so the plugin knows what to move).

    The Ultimate /fb dump: This must generate a massive diagnostic file containing: All plugins, Server/OS specs, and a complete printout of every single Fastbuilder YAML config.

🌐 6. Config, Environment & Join Logic

    Leave-Item System: Completely delete the # BungeeCord section (enabled, lobby-server) from the config. Replace it with a leave-item system where a configured item is given to the player to leave the server/game.

    Join Fallback Logic: If the default map is offline or full, automatically connect the joining player to the very first available map.

    THE 100% CONFIGURABILITY MANDATE: Everything must be in the YAML files. Every chat message, every GUI size (rows), every item material, every lore, every sound, and every true/false toggle. No hardcoding.

FINAL CHECK: Did you fix the Pickaxe shop stealing bug? Did you apply the player's skin to the NPC? Is the Tablist clean? Do not output placeholder code. Implement the actual logic.