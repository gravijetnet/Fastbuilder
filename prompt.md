Hier ist der finale, ultimativ verbesserte Prompt für Claude Code. Er ist hochpräzise, technisch detailliert und darauf ausgelegt, dass die KI keine deiner Anforderungen überspringt.

Prompt for Claude Code:

CRITICAL INSTRUCTION: This is a comprehensive architectural and functional overhaul. Previous attempts missed several core features. You must implement EVERY point listed below with production-grade logic. Do not use placeholder code. Language: English only.
🛠️ 1. Setup, Admin Commands & Help Menus

    Interactive Help Pagination: Rewrite /map help with a clickable pagination system.

        Format: &fShowing page &c1 &fof &c3. &f(&714 results&f). &a»

        Use ClickEvent to allow clicking the arrows (» or «) to switch pages instantly.

        Only show commands the player has permission for. Sub-commands (like /map setup) need their own formatted help.

    Map Setup Logic: * Final Step: Use ClickEvent.Action.SUGGEST_COMMAND for /map setup finish  so the admin only needs to type the name.

        REMOVAL: Remove the "Set island spacing" message and the /map setinfinite command.

    GameMode Force: Force Creative Mode for admins/players with setup permissions upon joining or finishing a setup.

    New Command: /map customlength <true/false> [minBlocks] to enable custom distance scaling.

🎮 2. Gameplay Logic & Island Mechanics

    Flawless Reset Logic:

        Single Reset: Fix the bug where players reset twice. Reset once immediately upon finish/death; do not reset again after the animation.

        Full Clearing: Ensure 100% of placed blocks are removed.

        Out of Bounds: If a player leaves their island zone, trigger an immediate teleport and a full block reset.

    Build Protection: Strictly prevent placing/breaking blocks while a reset animation or replay is active.

    Join Logic: If the "default" map is disabled or full, automatically send joining players to the first available map.

    Leave System: Replace the "Bungee" config section with a "Leave-Item" system (Configurable material/slot/command/server).

🛡️ 3. GUI, Shop & Permissions (CRITICAL FIXES)

    Pickaxe Shop Fix (TOP PRIORITY): * Include the One-Click Pick (Diamond Axe).

        Fix the "Moving Items" bug: Strictly cancel InventoryClickEvent to prevent players from dragging or stealing items.

        Ensure buying works correctly and grants the item/permission.

    Shop Layout (3x9): * Sandstone on Slot 9, Pickaxe on Slot 11, etc. (Make it look professional).

        Island Design: Replace glass panes/books with Paper items. No enchantments.

        Island Design Logic: Changing a design must apply instantly and trigger a block reset on the island.

    Block Selector: Add a "Back to Shop" button.

    Settings Menu: Center all items (shift 1 slot left). Players configure "Custom Length" here; the end-island must physically move/spawn at the selected distance.

    Permissions: Every shop item needs a unique permission. If a player has the permission, the item should show as "Owned" and be unpurchasable.

🎬 4. Animations & Replays

    Creative NPC Evolution: * The NPC must have the exact Skin and Name of the player who performed the run.

        Ensure the mining animation is visible and lasts long enough to be impactful.

    Death Sounds: Fix the bug where no sounds play. Remove the "Portal" sound.

    Item Drop Fix: Eliminate "purple-black" (missing texture) entities.

    Replay Rewind: Clicking "Rewind" at the end of a replay must go back 5 seconds, not restart the whole session.

    Efficiency: Store Replays, Templates, and Schematics using heavy compression (e.g., GZIP/NBT) to save disk space. Integrate permission-based replay limits.

📊 5. Scoreboard & Placeholders

    Formatting: Empty Top 3 slots must show -,--- only (no colon).

    Fix "6c": Remove the random "6c" string appearing in session lines.

    PAPI & Colors: Ensure PlaceholderAPI works for the viewing player. Color codes (like &c%blocks%) must be respected by parsing the placeholder as a raw string first.

⚙️ 6. System Diagnostics & Config

    Extreme /fb dump: Log everything: Server/Java/OS info, all installed plugins, and the entire content of every config file. Upload to Bytebin and provide a copyable link.

    100% Configurability: Every message, every GUI (size/items/slots), every sound, and every item must be configurable. This is the "Absolute Configurability" mandate.

IMPLEMENT ALL LOGIC INTERNALLY. NO PLACEHOLDER COMMENTS.