package net.gravijet.fastbuilder.map;

import java.util.UUID;

/**
 * Represents a single island slot within a map.
 * Each island instance has an index (0-based) and may be occupied by a player.
 */
public class IslandInstance {

    private final int index;
    private UUID occupantUuid;
    private String occupantName;

    public IslandInstance(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    public boolean isOccupied() {
        return occupantUuid != null;
    }

    public UUID getOccupantUuid() {
        return occupantUuid;
    }

    public String getOccupantName() {
        return occupantName;
    }

    public void setOccupant(UUID uuid, String name) {
        this.occupantUuid = uuid;
        this.occupantName = name;
    }

    public void clearOccupant() {
        this.occupantUuid = null;
        this.occupantName = null;
    }
}
