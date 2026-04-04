Please implement the following extensive list of bug fixes, features, and system overhauls. This is a comprehensive update; ensure all logic is strictly reviewed and everything is strictly in English.
🛠️ Command & Setup Overhaul

    New Setup Workflow: * To set up an infinite map, use /map setup --infinite.

        Combine naming and finishing: Change /map setup finish to /map setup finish <name>. The separate name command is no longer needed.

    New Commands: * /map rename <newname>: To rename an existing map.

        /map regen: To regenerate the map's schematic/islands.

        /fb dump: Create a diagnostic log including plugin version, hooked plugins, server/Java/OS versions, all configs, and RAM/CPU usage. Upload this to Bytebin and return a clickable, copyable URL to the admin.

        Remove Command: Delete the /fb info command entirely.

    Help Menu: Paginate the /map help message (maximum 10 commands per page).

    Permissions: Add a unique permission node for every single command and feature in the plugin.

🎮 Game Modes & Join Logic

    Infinite & Custom Length: * Ensure "Custom Length" is fully configurable in the settings menu (it is currently non-functional).

        No Stats: Disable statistics tracking (successes, times, etc.) for Infinite and Custom Length modes.

    Server Join Logic: When a player joins, they must be sent to the "default" map. If the default map is full, automatically move them to the next available free map.

💄 Cosmetics, Shop & Animations

    Pickaxe Shop Expansion:

        One-Click Pick: This should be a Diamond Axe that allows instant block breaking.

        Additions: Add Shears, all types of Axes, a Wooden Pickaxe, all types of Shovels, and all types of Hoes to the shop.

    Island Designs: Players must be able to select their preferred island design/style directly within the Shop menu.

    Reset Animations (Must be sequential and fast):

        Rename "Slide Down" to "Fall Down".

        Sequential Destruction: For "Item Drop", "Ice Melt", and "No Animation", blocks must be destroyed one-by-one in a fast sequence (not all at once).

        Ice Melt Details: Blocks turn to ice and "melt" away sequentially with a proper ice-melting sound effect.

        Timing: Animations must start the exact moment the goal is reached.

        Cleanup: Ensure "Item Drop" does not apply Unbreaking I to the dropped items.

    Death Sounds: Use high-quality, short, "punchy" sounds (e.g., Creeper prime, Anvil land) rather than long, annoying clips.

📊 Scoreboard, Actionbar & Placeholders

    Scoreboard Logic: * Support up to 15 lines (the Minecraft maximum) without cutting off lines.

        Ensure color codes (e.g., &a) work perfectly for placeholders (e.g., if %blocks% is just a number, it should be formattable like &a%blocks%).

    Actionbar: * Default format: &7Time &8» &c%time%.

        Visibility: Only show during an active run. Make the timing and general visibility fully configurable.

    Economy Scaling: Coins rewarded for a success must scale dynamically based on the completion time (faster = significantly more coins). This scaling should be internal logic and not rely on static config values.

⚙️ System, Config & Bug Fixes

    Reload System (/fb reload): Fix the reload logic. Currently, after a reload, players are often removed from their islands and can no longer place blocks. This must be 100% stable.

    100% Configurability: Every detail of the Actionbar, Scoreboard, and Item names/lore must be configurable and update instantly upon /fb reload.

    Boundary Reset: If a player crosses the map boundaries, reset them immediately. Ensure they do not get "stuck" in a reset loop or in blocks.

    Finish Zone: Ensure regular blocks (not just pressure plates) can be used as the finish detection zone.