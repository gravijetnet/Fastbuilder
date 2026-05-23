package net.gravijet.fastbuilder.gameplay;

import net.gravijet.fastbuilder.FastBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles all block-clearing animations: FALL_DOWN, EXPLODE, ITEM_DROP, ICE_MELT, CREATIVE_NPC, NONE.
 */
class BlockAnimator {

    private final FastBuilder plugin;
    private final Set<UUID> animationEntities = new HashSet<>();

    BlockAnimator(FastBuilder plugin) {
        this.plugin = plugin;
    }

    boolean isAnimationEntity(UUID entityId) { return animationEntities.contains(entityId); }
    void removeAnimationEntity(UUID entityId) { animationEntities.remove(entityId); }

    @SuppressWarnings("deprecation")
    void clearBlocksWithAnimation(final Player player, List<Location> blocks,
                                   List<Location> practiceBlocks,
                                   Map<String, int[]> origStates,
                                   boolean isPracticeMode, String animation) {
        final int batchSize  = plugin.getConfigManager().getAnimationBlocksPerTick();
        final long tickDelay = plugin.getConfigManager().getAnimationTickInterval();

        if ("FALL_DOWN".equalsIgnoreCase(animation) || "SLIDE_DOWN".equalsIgnoreCase(animation)) {
            List<Location> toClear = new ArrayList<>();
            for (Location loc : blocks) {
                if (isPracticeMode && practiceBlocks.contains(loc)) continue;
                toClear.add(loc);
            }
            toClear.sort((a, b) -> b.getBlockY() - a.getBlockY());
            final int[] idx = {0};
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (int i = 0; i < batchSize && idx[0] < toClear.size(); i++, idx[0]++) {
                        Location loc = toClear.get(idx[0]);
                        Block block = loc.getBlock();
                        if (block != null && block.getType() != Material.AIR) {
                            spawnAnimationFallingBlock(loc, block.getTypeId(), block.getData(),
                                    new org.bukkit.util.Vector(0.0, -0.1 - Math.random() * 0.25, 0.0), null);
                            restoreBlock(block, loc, origStates);
                        }
                    }
                    if (idx[0] >= toClear.size()) this.cancel();
                }
            }.runTaskTimer(plugin, 0L, tickDelay);

        } else if ("EXPLODE".equalsIgnoreCase(animation)) {
            for (Location loc : blocks) {
                if (isPracticeMode && practiceBlocks.contains(loc)) continue;
                Block block = loc.getBlock();
                if (block != null && block.getType() != Material.AIR) {
                    double vx = (Math.random() - 0.5) * 1.0;
                    double vy = 0.3 + Math.random() * 0.6;
                    double vz = (Math.random() - 0.5) * 1.0;
                    spawnAnimationFallingBlock(loc, block.getTypeId(), block.getData(),
                            new org.bukkit.util.Vector(vx, vy, vz), player.getUniqueId());
                    restoreBlock(block, loc, origStates);
                }
            }

        } else if ("ITEM_DROP".equalsIgnoreCase(animation)) {
            clearBlocksItemDrop(blocks, practiceBlocks, origStates, isPracticeMode);

        } else if ("ICE_MELT".equalsIgnoreCase(animation)) {
            clearBlocksIceMelt(blocks, practiceBlocks, origStates, isPracticeMode);

        } else if ("CREATIVE_NPC".equalsIgnoreCase(animation)) {
            clearBlocksCreativeNpcWithPlayer(player, blocks, practiceBlocks, origStates, isPracticeMode);

        } else {
            // NONE: flash as BARRIER for one tick then restore
            List<Location> toClear = new ArrayList<>();
            for (Location loc : blocks) {
                if (isPracticeMode && practiceBlocks.contains(loc)) continue;
                toClear.add(loc);
            }
            for (Location loc : toClear) {
                Block block = loc.getBlock();
                if (block != null && block.getType() != Material.AIR) block.setType(Material.BARRIER);
            }
            new BukkitRunnable() {
                @Override
                @SuppressWarnings("deprecation")
                public void run() {
                    for (Location loc : toClear) {
                        Block block = loc.getBlock();
                        if (block != null) restoreBlock(block, loc, origStates);
                    }
                }
            }.runTaskLater(plugin, 1L);
        }
    }

    @SuppressWarnings("deprecation")
    private void clearBlocksItemDrop(List<Location> blocks, List<Location> practiceBlocks,
                                      Map<String, int[]> origStates, boolean isPracticeMode) {
        List<Location> toClear = new ArrayList<>();
        for (Location loc : blocks) {
            if (isPracticeMode && practiceBlocks.contains(loc)) continue;
            Block block = loc.getBlock();
            if (block == null || block.getType() == Material.AIR) continue;
            toClear.add(loc);
        }

        final int dropBatch = plugin.getConfigManager().getAnimationBlocksPerTick();
        final long dropInterval = plugin.getConfigManager().getAnimationTickInterval();
        final int[] idx = {0};
        new BukkitRunnable() {
            @Override
            @SuppressWarnings("deprecation")
            public void run() {
                for (int i = 0; i < dropBatch && idx[0] < toClear.size(); i++, idx[0]++) {
                    Location loc = toClear.get(idx[0]);
                    Block block = loc.getBlock();
                    if (block == null || block.getType() == Material.AIR) continue;

                    Material mat = block.getType();
                    boolean hasItem;
                    try {
                        new org.bukkit.inventory.ItemStack(mat, 1);
                        hasItem = mat != Material.AIR && mat.getId() < 256;
                    } catch (Exception ex) { hasItem = false; }

                    if (!hasItem) { restoreBlock(block, loc, origStates); continue; }

                    short durability = block.getData();
                    Location center = loc.clone().add(0.5, 0.5, 0.5);
                    try {
                        org.bukkit.inventory.ItemStack stack = new org.bukkit.inventory.ItemStack(mat, 1, durability);
                        org.bukkit.entity.Item item = loc.getWorld().dropItem(center, stack);
                        item.setPickupDelay(32767);
                        item.setVelocity(new org.bukkit.util.Vector(
                                (Math.random() - 0.5) * 0.25, 0.15 + Math.random() * 0.25, (Math.random() - 0.5) * 0.25));
                        final org.bukkit.entity.Item ref = item;
                        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (!ref.isDead()) ref.remove(); }, 40L);
                    } catch (Exception ignored) {}

                    restoreBlock(block, loc, origStates);
                }
                if (idx[0] >= toClear.size()) this.cancel();
            }
        }.runTaskTimer(plugin, 0L, dropInterval);
    }

    @SuppressWarnings("deprecation")
    private void clearBlocksIceMelt(List<Location> blocks, List<Location> practiceBlocks,
                                     Map<String, int[]> origStates, boolean isPracticeMode) {
        List<Location> toClear = new ArrayList<>();
        for (Location loc : blocks) {
            if (isPracticeMode && practiceBlocks.contains(loc)) continue;
            Block block = loc.getBlock();
            if (block == null || block.getType() == Material.AIR) continue;
            toClear.add(loc);
        }
        if (toClear.isEmpty()) return;

        final int[] idx = {0};
        new BukkitRunnable() {
            @Override
            @SuppressWarnings("deprecation")
            public void run() {
                for (int i = 0; i < 3 && idx[0] < toClear.size(); i++, idx[0]++) {
                    Location loc = toClear.get(idx[0]);
                    Block block = loc.getBlock();
                    if (block == null || block.getType() == Material.AIR) continue;
                    block.setTypeIdAndData(79, (byte) 0, false);
                    try { loc.getWorld().playSound(loc, org.bukkit.Sound.FIZZ, 0.4f, 1.8f); } catch (Exception ignored) {}
                    final Location frozenLoc = loc.clone();
                    final int[] origArr = origStates.get(loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        Block b = frozenLoc.getBlock();
                        if (b == null) return;
                        if (origArr != null && origArr[0] != 0) b.setTypeIdAndData(origArr[0], (byte) origArr[1], false);
                        else b.setType(Material.AIR);
                    }, 3L);
                }
                if (idx[0] >= toClear.size()) this.cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @SuppressWarnings("deprecation")
    private void clearBlocksCreativeNpcWithPlayer(Player owner, List<Location> blocks, List<Location> practiceBlocks,
                                                   Map<String, int[]> origStates, boolean isPracticeMode) {
        List<Location> toClear = new ArrayList<>();
        for (Location loc : blocks) {
            if (isPracticeMode && practiceBlocks.contains(loc)) continue;
            Block block = loc.getBlock();
            if (block == null || block.getType() == Material.AIR) continue;
            toClear.add(loc);
        }
        if (toClear.isEmpty()) return;

        net.citizensnpcs.api.npc.NPC[] npcRef = new net.citizensnpcs.api.npc.NPC[1];
        try {
            net.citizensnpcs.api.npc.NPCRegistry reg = net.citizensnpcs.api.CitizensAPI.getNPCRegistry();
            String npcName = owner != null ? owner.getName() : "Builder";
            net.citizensnpcs.api.npc.NPC npc = reg.createNPC(org.bukkit.entity.EntityType.PLAYER, npcName);
            if (owner != null) {
                npc.data().set("player-skin-uuid", owner.getUniqueId().toString());
                npc.data().set("player-skin-name", owner.getName());
            }
            Location spawnLoc = toClear.get(0).clone().add(0.5, 0, 0.5);
            npc.spawn(spawnLoc);
            npcRef[0] = npc;

            if (npc.isSpawned() && npc.getEntity() instanceof org.bukkit.entity.Player) {
                try {
                    org.bukkit.entity.Player npcEntity = (org.bukkit.entity.Player) npc.getEntity();
                    String ver = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
                    Object nmsPlayer = npcEntity.getClass().getMethod("getHandle").invoke(npcEntity);
                    Class<?> pktClass = Class.forName("net.minecraft.server." + ver + ".PacketPlayOutPlayerInfo");
                    Class<?> enumClass = Class.forName("net.minecraft.server." + ver + ".PacketPlayOutPlayerInfo$EnumPlayerInfoAction");
                    Class<?> entityPlayerClass = Class.forName("net.minecraft.server." + ver + ".EntityPlayer");
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Object removeAction = Enum.valueOf((Class<Enum>) enumClass, "REMOVE_PLAYER");
                    Object entityPlayerArr = java.lang.reflect.Array.newInstance(entityPlayerClass, 1);
                    java.lang.reflect.Array.set(entityPlayerArr, 0, nmsPlayer);
                    Object removePacket = pktClass.getDeclaredConstructors()[0].newInstance(removeAction, entityPlayerArr);
                    for (org.bukkit.entity.Player viewer : spawnLoc.getWorld().getPlayers()) {
                        Object handle = viewer.getClass().getMethod("getHandle").invoke(viewer);
                        Object conn = handle.getClass().getField("playerConnection").get(handle);
                        Class<?> packetIface = Class.forName("net.minecraft.server." + ver + ".Packet");
                        conn.getClass().getMethod("sendPacket", packetIface).invoke(conn, removePacket);
                    }
                } catch (Exception ignored) {}
            }
        } catch (NoClassDefFoundError | Exception ignored) {}

        final int[] idx = {0};
        new BukkitRunnable() {
            @Override
            public void run() {
                for (int i = 0; i < 3 && idx[0] < toClear.size(); i++, idx[0]++) {
                    Location loc = toClear.get(idx[0]);
                    Block block = loc.getBlock();
                    if (block == null || block.getType() == Material.AIR) continue;
                    if (npcRef[0] != null && npcRef[0].isSpawned()) {
                        try {
                            org.bukkit.entity.Entity e = npcRef[0].getEntity();
                            e.teleport(loc.clone().add(0.5, 0, 0.5));
                            try {
                                Object nmsEntity = e.getClass().getMethod("getHandle").invoke(e);
                                // Build the NMS package from the server class, not from the CraftBukkit entity class
                                String[] pkgParts = org.bukkit.Bukkit.getServer().getClass().getPackage().getName().split("\\.");
                                String nmsBase = pkgParts.length >= 4 ? "net.minecraft.server." + pkgParts[3] : nmsEntity.getClass().getPackage().getName();
                                Object packet = Class.forName(nmsBase + ".PacketPlayOutAnimation")
                                        .getConstructor(nmsEntity.getClass(), int.class).newInstance(nmsEntity, 0);
                                for (org.bukkit.entity.Player viewer : e.getWorld().getPlayers()) {
                                    Object conn = viewer.getClass().getMethod("getHandle").invoke(viewer);
                                    Object playerConn = conn.getClass().getField("playerConnection").get(conn);
                                    playerConn.getClass().getMethod("sendPacket", Class.forName(
                                            nmsBase + ".Packet")).invoke(playerConn, packet);
                                }
                            } catch (Exception ignored) {}
                        } catch (Exception ignored) {}
                    }
                    try {
                        loc.getWorld().playEffect(loc, org.bukkit.Effect.STEP_SOUND, block.getTypeId());
                        loc.getWorld().playSound(loc, org.bukkit.Sound.DIG_STONE, 0.5f, 1.0f + (float)(Math.random() * 0.4f));
                    } catch (Exception ignored) {}
                    restoreBlock(block, loc, origStates);
                }
                if (idx[0] >= toClear.size()) {
                    if (npcRef[0] != null) { try { npcRef[0].destroy(); } catch (Exception ignored) {} }
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    @SuppressWarnings("deprecation")
    void spawnAnimationFallingBlock(Location loc, int typeId, byte data,
                                     org.bukkit.util.Vector velocity, UUID ownerUuid) {
        try {
            if (typeId == 0) return;
            Material mat = Material.getMaterial(typeId);
            if (mat == null || mat == Material.AIR) return;

            Location spawnLoc = loc.clone().add(0.5, 1.0, 0.5);
            org.bukkit.entity.FallingBlock fb = loc.getWorld().spawnFallingBlock(spawnLoc, mat, data);
            fb.setDropItem(false);
            fb.setVelocity(velocity);
            animationEntities.add(fb.getUniqueId());

            try {
                Object handle = fb.getClass().getMethod("getHandle").invoke(fb);
                java.lang.reflect.Field f = handle.getClass().getDeclaredField("dontSetBlock");
                f.setAccessible(true);
                f.set(handle, true);
            } catch (Exception ignored) {}

            final org.bukkit.entity.FallingBlock fbRef = fb;
            final UUID fbEntityId = fb.getUniqueId();

            if (ownerUuid != null) {
                final int[] checksLeft = {12};
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (fbRef.isDead() || checksLeft[0] <= 0) { this.cancel(); return; }
                        checksLeft[0]--;
                        Location fbLoc = fbRef.getLocation();
                        for (org.bukkit.entity.Player nearby : fbLoc.getWorld().getPlayers()) {
                            if (nearby.getUniqueId().equals(ownerUuid)) continue;
                            Location pLoc = nearby.getLocation();
                            if (Math.abs(pLoc.getX() - fbLoc.getX()) <= 2.5
                                    && Math.abs(pLoc.getY() - fbLoc.getY()) <= 4.0
                                    && Math.abs(pLoc.getZ() - fbLoc.getZ()) <= 2.5) {
                                animationEntities.remove(fbEntityId);
                                fbRef.remove();
                                this.cancel();
                                return;
                            }
                        }
                    }
                }.runTaskTimer(plugin, 5L, 5L);
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                animationEntities.remove(fbEntityId);
                if (!fbRef.isDead()) fbRef.remove();
            }, 60L);
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("deprecation")
    private static void restoreBlock(Block block, Location loc, Map<String, int[]> origStates) {
        String key = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
        int[] orig = origStates.get(key);
        if (orig != null && orig[0] != 0) block.setTypeIdAndData(orig[0], (byte) orig[1], false);
        else block.setType(Material.AIR);
    }
}
