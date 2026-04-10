Task: Implement Real-time Custom Island Length and Advanced Island Hopping mechanics.

Requirements:
1. Custom Length Overhaul (Completely replace current logic):
    - Setup Command: Use `/map setup --customlength`.
    - Setup Process: The setup must allow the admin to select the Start Island and End Island SEPARATELY (so the End Island becomes a distinct, movable entity). Set a default base distance.
    - Settings UI adjustments:
        - Left-click: +1 block
        - Shift + Left-click: +10 blocks
        - Right-click: -1 block
        - Shift + Right-click: -10 blocks
    - REAL-TIME MOVEMENT: When distance is adjusted in the settings, the End Island must physically move/shift in the world in real-time.
    - Reset Logic: Provide a way to reset the distance. It MUST automatically reset to default when the player leaves the server, leaves the map, or rejoins.
2. Advanced Island Hopping:
    - If a player physically jumps/moves from their island to an adjacent, non-empty island (without using the menu), trigger a switch to that island.
    - Clear blocks and handle state transfer accordingly.
    - If the target island is already owned/occupied by someone else, teleport the player back to their own island (like a normal reset).
    - At the edges of the overall map, hopping only works in the valid inward direction.
3. Custom Island Design Persistence:
    - When a user selects a custom island design, it must remain active for that user even if they switch islands or leave the server (saved per map).
    - If a design has the exact same name across multiple maps, unlocking/buying it on one map must grant access to it on the others.
    - Auto-activate the design by default if the user has it toggled on.
    - Revert: When a player switches maps, changes islands, or leaves the server, the PHYSICAL island itself must revert to the normal/default design for the next player.

Coding Style: Write clean, highly optimized Java. Handling real-time schematic shifting requires performance-conscious block updates.


sonnet, effort auf high!!!