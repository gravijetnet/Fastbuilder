package net.gravijet.fastbuilder.replay;

/**
 * A single tick frame in a replay recording.
 * Stores the player's full visual state: position, body/head rotation,
 * sneak/sprint flags, arm-swing, and any block placement this tick.
 */
public class ReplayFrame {

    private final int tick;

    // Player position
    private final double x, y, z;
    // Body rotation (sent via EntityLook / EntityTeleport)
    private final float yaw, pitch;
    // Head-only yaw (sent via PacketPlayOutEntityHeadRotation)
    private final float headYaw;

    // Entity state flags
    private final boolean sneaking;
    private final boolean sprinting;
    // True if the player swung their arm this tick (PacketPlayOutAnimation id=0)
    private final boolean swingingArm;

    // Block placement (null if none this tick)
    private final BlockPlacement blockPlacement;

    /** Full constructor used by the recorder. */
    public ReplayFrame(int tick, double x, double y, double z,
                       float yaw, float pitch, float headYaw,
                       boolean sneaking, boolean sprinting, boolean swingingArm,
                       BlockPlacement blockPlacement) {
        this.tick = tick;
        this.x = x; this.y = y; this.z = z;
        this.yaw = yaw; this.pitch = pitch;
        this.headYaw = headYaw;
        this.sneaking = sneaking;
        this.sprinting = sprinting;
        this.swingingArm = swingingArm;
        this.blockPlacement = blockPlacement;
    }

    /** Legacy constructor for loading v1/v2 replay files (no new fields). */
    public ReplayFrame(int tick, double x, double y, double z, float yaw, float pitch,
                       BlockPlacement blockPlacement) {
        this(tick, x, y, z, yaw, pitch, yaw, false, false, false, blockPlacement);
    }

    public int getTick()              { return tick; }
    public double getX()              { return x; }
    public double getY()              { return y; }
    public double getZ()              { return z; }
    public float getYaw()             { return yaw; }
    public float getPitch()           { return pitch; }
    public float getHeadYaw()         { return headYaw; }
    public boolean isSneaking()       { return sneaking; }
    public boolean isSprinting()      { return sprinting; }
    public boolean isSwingingArm()    { return swingingArm; }
    public BlockPlacement getBlockPlacement() { return blockPlacement; }
    public boolean hasBlockPlacement() { return blockPlacement != null; }

    /**
     * Represents a block placement at a specific location.
     */
    public static class BlockPlacement {
        private final int blockX, blockY, blockZ;
        private final int blockId;
        private final byte blockData;

        public BlockPlacement(int blockX, int blockY, int blockZ, int blockId, byte blockData) {
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
            this.blockId = blockId;
            this.blockData = blockData;
        }

        public int getBlockX() { return blockX; }
        public int getBlockY() { return blockY; }
        public int getBlockZ() { return blockZ; }
        public int getBlockId() { return blockId; }
        public byte getBlockData() { return blockData; }
    }
}
