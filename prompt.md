Please implement the following updates, bug fixes, and system adjustments. Pay close attention to every detail provided, as they are crucial for the plugin's functionality.
🌍 Global Settings & Localization

    Strict English Localization: Ensure the entire plugin—including all text, messages, menus, and outputs—is translated to and strictly uses English.

    Global Build Mode: Modify "Build Mode" so that players have absolute freedom to place and break blocks anywhere in the entire world, ignoring all normal zone restrictions.

🐛 Core Building & Gameplay Fixes

    Track Boundary Bug: Fix a critical bug where players cannot place blocks if they build too far to the left or right of their track, or directly underneath themselves. Block placement must work flawlessly along the intended fastbuilder route.

    Backward Building Limit: Restrict players from building too far backwards from their Fastbuilder island spawn point. Limit backward block placement to a maximum of about 5 to 10 blocks.

    Broken Reset Animations: The block reset animations are completely non-functional (absolutely nothing happens to the blocks). Please investigate and fix this so the animations trigger properly.

📊 Scoreboard & Hologram Updates

    Scoreboard Overhaul: * Remove the four red statistics entirely (coins, level, rank, players).

        Add a new "Fastbuilder Coins" statistic.

        Add the "Top 3 Session Best" times directly to the scoreboard.

    Top Percentile Hologram: Update the hologram that displays a player's best time to include a percentile ranking placeholder. It must be formatted exactly like this: Personal best: 3.8s [Top 9.8%].

⚙️ Menus, Settings & Economy

    Infinite Blocks Toggle: * Remove the "Block Selector" completely from the Settings menu.

        Replace it with a new setting: an "Infinite Blocks On/Off" toggle.

        This toggle must cost exactly 1000 coins to unlock.

        When enabled, the player's block stack is permanently locked at 64. When disabled, blocks deplete normally as they build.

    Practice Blocks Icon: Change the item ID for the "Practice Blocks" toggle in the Settings menu to use data value/ID 5 (Green).

🛠️ Map Setup & Generation Logic

    Fall Death Height Fix: Ensure that the fall death height set during map setup translates to the actual Y-coordinate of the generated map in the live world, rather than the relative Y-coordinate of the setup environment.

    Selection Wand Prefix Bug: Fix the issue where the Fastbuilder prefix fails to display (showing raw placeholders instead) during the setup process, particularly on messages triggered by the Selection Wand.

    Island Distance Calculation: Update the island distance algorithm. The distance between islands must be calculated based on the island's width (North to South axis), rather than its length (East to West axis).

    Entity Deletion Bug: Fix a bug where all player NPCs and holograms permanently disappear whenever the islands are rescaled or the island distance value is changed. Ensure they respawn or persist correctly.

# IGNORIERE ALLES AB HIER!!!!!!!!! NICHT DURCHLESEN!!!!
replay system prompt:
I need to upgrade the Replay system to make it look exactly like the original player's movement. Currently, the replay NPC only moves to locations but doesn't rotate, sneak, or animate.

Objectives:

    Enhance ReplayFrame: Update the ReplayFrame class to store more than just XYZ coordinates. It must now include:

        float yaw and float pitch (Body rotation).

        float headYaw (Specific head rotation for 1.8.8).

        boolean isSneaking and boolean isSprinting.

        boolean isSwingingArm.

    Update ReplayRecorder: Modify the recording logic to capture these extra values every tick. For animations like arm swings, ensure they are captured even if they happen between location updates.

    Refactor ReplaySession (Playback): When playing back a frame, the NPC (EntityPlayer/NPC) must reflect all captured states:

        Use PacketPlayOutEntityLook and PacketPlayOutEntityHeadRotation to update the direction.

        Use PacketPlayOutEntityMetadata (DataWatcher) to set the sneaking and sprinting bits.

        Use PacketPlayOutAnimation (ID 0) if isSwingingArm is true.

    Smoothness: Ensure the NPC uses smooth interpolation (if possible) or at least updates every tick (20Hz) to avoid stuttering.

Files to check:

    net.gravijet.fastbuilder.replay.ReplayFrame

    net.gravijet.fastbuilder.replay.ReplayRecorder

    net.gravijet.fastbuilder.replay.ReplaySession

    net.gravijet.fastbuilder.replay.ReplayData

Please rewrite the recording and playback logic to ensure the replay is an exact 1:1 visual replica of the player's performance, including head movements and crouching.








Später nachdem alles funktioniert kommt der timer prompt:
I need to refactor my FastBuilder plugin to achieve professional-grade 1ms precision, completely bypassing the standard 50ms (20 TPS) server-tick limitation. We are targeting a 1.8.8 CarbonSpigot environment using the packetevents API.

1. Packet-Level Interception:

   Implement a PacketListener to intercept PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT for starting the run and measuring block placement speed.

   Intercept PacketType.Play.Client.PLAYER_FLYING (or Position packets) to detect the finish line. Compare player coordinates against the target area defined in IslandInstance/MapData.

   Crucial: Capture the high-resolution server-receive timestamp immediately upon packet arrival (asynchronous), before it reaches the Bukkit main thread.

2. Latency & Ping Compensation:

   For every measurement (Start and Finish), apply a 'Fairness-Calculation': Subtract half of the player's current ping (ping / 2.0) from the arrival timestamp to estimate the exact millisecond the player performed the action on their client.

   Handle edge cases where this adjustment might result in a timestamp earlier than the previous recorded action.

3. Asynchronous 100Hz HUD (Actionbar):

   Remove all BukkitRunnable HUD tasks.

   Implement a ScheduledExecutorService in GameplayManager that updates the active player's Actionbar every 10 milliseconds (100Hz).

   Use the packetevents User API to send the Actionbar packets asynchronously. This ensures the timer looks perfectly fluid (e.g., 00:01.458) without taxing the Main Thread.

4. Thread-Safe State Management:

   Update RunSession.java to handle startTimeAdjusted and finishTimeAdjusted using volatile or atomic references if necessary, as these will be accessed by multiple asynchronous threads (Netty threads and HUD executor).

   Strict Sync Rule: All world-modifying actions (block placements, clearing practice blocks, teleports) triggered by the PacketListener must be wrapped in Bukkit.getScheduler().runTask(plugin, () -> { ... }) to ensure they execute safely on the Main Thread.

5. Utility & Formatting:

   Update TimeUtil.java to format the elapsed time with exactly 3 decimal places.

   Ensure RunSession#getElapsed() calculates the difference based on the adjusted millisecond timestamps.

Your Task:
Please perform a deep-trace analysis of the packet-to-main-thread handoff. Provide the full updated code for the attached files. Ensure the system is memory-efficient, prevents race conditions, and provides the most precise FastBuilder experience possible in Minecraft 1.8.8.