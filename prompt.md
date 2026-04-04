

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

Please rewrite the recording and playback logic to ensure the replay is an exact 1:1 visual replica of the player's performance, including head movements and crouching.