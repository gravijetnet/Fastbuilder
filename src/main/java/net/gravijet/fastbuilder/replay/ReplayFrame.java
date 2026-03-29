package net.gravijet.fastbuilder.replay;

/**
 * A single tick frame in a replay recording.
 * Stores the player's position/rotation and any block placement that happened on this tick.
 */
public class ReplayFrame {

    private final int tick;

    // Player position
    private final double x, y, z;
    private final float yaw, pitch;

    // Block placement (null if none this tick)
    private final BlockPlacement blockPlacement;

    public ReplayFrame(int tick, double x, double y, double z, float yaw, float pitch,
                       BlockPlacement blockPlacement) {
        this.tick = tick;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.blockPlacement = blockPlacement;
    }

    public int getTick() { return tick; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
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
