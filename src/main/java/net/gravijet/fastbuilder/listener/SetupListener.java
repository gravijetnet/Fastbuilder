package net.gravijet.fastbuilder.listener;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.SetupSession;
import net.gravijet.fastbuilder.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles Blaze Rod interactions during admin map setup.
 */
public class SetupListener implements Listener {

    private final FastBuilder plugin;
    private final Map<UUID, Integer> particleTasks = new HashMap<>();

    public SetupListener(FastBuilder plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (plugin.getMapManager().getSetupSession(player.getUniqueId()) != null
                && !player.hasPermission("fastbuilder.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (plugin.getMapManager().getSetupSession(player.getUniqueId()) != null
                && !player.hasPermission("fastbuilder.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getItemInHand();

        if (item == null || item.getType() != Material.BLAZE_ROD) return;

        SetupSession session = plugin.getMapManager().getSetupSession(player.getUniqueId());
        if (session == null) return;

        event.setCancelled(true);

        Action action = event.getAction();
        Block clicked = event.getClickedBlock();

        switch (session.getState()) {
            case SELECTING_ISLAND:
                handleIslandSelection(player, session, action, clicked);
                break;
            case SELECTING_DIAGONAL:
                handleDiagonalSelection(player, session, action, clicked);
                break;
            case SELECTING_SPAWN:
                handleSpawnSelection(player, session, action);
                break;
            case SELECTING_NPC:
                handleNpcSelection(player, session, action);
                break;
            case SELECTING_HOLOGRAM:
                handleHologramSelection(player, session, action);
                break;
            case SELECTING_FINISH:
                handleFinishSelection(player, session, action, clicked);
                break;
            case SELECTING_END_ISLAND:
                handleEndIslandSelection(player, session, action, clicked);
                break;
            default:
                break;
        }
    }

    private void handleIslandSelection(Player player, SetupSession session, Action action, Block clicked) {
        if (clicked == null) return;
        Location loc = clicked.getLocation();

        String prefix = plugin.getConfigManager().getPrefix();
        if (action == Action.LEFT_CLICK_BLOCK) {
            session.setIslandPos1(loc);
            String raw = plugin.getConfigManager().getAdminMessage("setup-pos1");
            raw = raw.replace("%x%", String.valueOf(loc.getBlockX()))
                    .replace("%y%", String.valueOf(loc.getBlockY()))
                    .replace("%z%", String.valueOf(loc.getBlockZ()))
                    .replace("%prefix%", prefix);
            player.sendMessage(ColorUtil.translate(raw));
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            session.setIslandPos2(loc);
            String raw = plugin.getConfigManager().getAdminMessage("setup-pos2");
            raw = raw.replace("%x%", String.valueOf(loc.getBlockX()))
                    .replace("%y%", String.valueOf(loc.getBlockY()))
                    .replace("%z%", String.valueOf(loc.getBlockZ()))
                    .replace("%prefix%", prefix);
            player.sendMessage(ColorUtil.translate(raw));
        }

        startParticleTask(player, session);

        if (session.getIslandPos1() != null && session.getIslandPos2() != null) {
            String msg = plugin.getConfigManager().getAdminMessage("setup-select-both")
                    .replace("%prefix%", prefix);
            player.sendMessage(ColorUtil.translate(msg));
            sendClickableContinue(player);
        }
    }

    /**
     * Diagonal selection: the admin right-clicks a block to indicate where island slot 1's
     * minimum X corner should be. The plugin computes diagonalStepX = clickedX - islandMinX
     * and advances to spawn selection.
     */
    private void handleDiagonalSelection(Player player, SetupSession session, Action action, Block clicked) {
        if (action != Action.RIGHT_CLICK_BLOCK || clicked == null) return;
        String prefix = plugin.getConfigManager().getPrefix();
        int clickedX = clicked.getX();
        if (session.finalizeDiagonal(clickedX)) {
            stopParticleTask(player.getUniqueId());
            player.sendMessage(ColorUtil.translate(prefix
                    + "&aDiagonal step set: &fX+" + session.getDiagonalStepX()
                    + " per island. &7Now right-click to set the spawn point."));
        }
    }

    private void handleSpawnSelection(Player player, SetupSession session, Action action) {
        if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
            Location loc = player.getLocation();
            session.setSpawnPoint(loc);
            String raw = plugin.getConfigManager().getAdminMessage("setup-spawn-saved");
            raw = raw.replace("%prefix%", plugin.getConfigManager().getPrefix());
            player.sendMessage(ColorUtil.translate(raw));
            sendClickableContinue(player);
        }
    }

    private void handleNpcSelection(Player player, SetupSession session, Action action) {
        if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
            Location loc = player.getLocation();
            session.setNpcPoint(loc);
            String raw = plugin.getConfigManager().getAdminMessage("setup-npc-saved");
            if (raw == null || raw.isEmpty()) {
                raw = "%prefix%&aNPC location saved at &c"
                        + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "&a.";
            }
            raw = raw.replace("%prefix%", plugin.getConfigManager().getPrefix());
            player.sendMessage(ColorUtil.translate(raw));
            sendClickableContinue(player);
        }
    }

    private void handleHologramSelection(Player player, SetupSession session, Action action) {
        if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
            Location loc = player.getLocation().clone();
            loc.setY(loc.getY() + 3);
            session.setHologramPoint(loc);
            String prefix = plugin.getConfigManager().getPrefix();
            player.sendMessage(ColorUtil.translate(
                    prefix + "&fHologram location saved at &c" +
                    loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "&f."));
            sendClickableContinue(player);
        }
    }

    private void handleFinishSelection(Player player, SetupSession session, Action action, Block clicked) {
        if (clicked == null) return;
        Location loc = clicked.getLocation();

        String prefix = plugin.getConfigManager().getPrefix();
        if (action == Action.LEFT_CLICK_BLOCK) {
            session.setFinishPos1(loc);
            String raw = plugin.getConfigManager().getAdminMessage("setup-finish-pos1");
            raw = raw.replace("%x%", String.valueOf(loc.getBlockX()))
                    .replace("%y%", String.valueOf(loc.getBlockY()))
                    .replace("%z%", String.valueOf(loc.getBlockZ()))
                    .replace("%prefix%", prefix);
            player.sendMessage(ColorUtil.translate(raw));
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            session.setFinishPos2(loc);
            String raw = plugin.getConfigManager().getAdminMessage("setup-finish-pos2");
            raw = raw.replace("%x%", String.valueOf(loc.getBlockX()))
                    .replace("%y%", String.valueOf(loc.getBlockY()))
                    .replace("%z%", String.valueOf(loc.getBlockZ()))
                    .replace("%prefix%", prefix);
            player.sendMessage(ColorUtil.translate(raw));
        }

        if (session.getFinishPos1() != null && session.getFinishPos2() != null) {
            String msg = plugin.getConfigManager().getAdminMessage("setup-finish-ready")
                    .replace("%prefix%", prefix);
            player.sendMessage(ColorUtil.translate(msg));
            sendClickableFinish(player);
        }
    }

    private void handleEndIslandSelection(Player player, SetupSession session, Action action, Block clicked) {
        if (clicked == null) return;
        Location loc = clicked.getLocation();

        String prefix = plugin.getConfigManager().getPrefix();
        if (action == Action.LEFT_CLICK_BLOCK) {
            session.setEndIslandPos1(loc);
            player.sendMessage(ColorUtil.translate(prefix + "&aEnd Island Pos 1 set at &c"
                    + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "&a."));
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            session.setEndIslandPos2(loc);
            player.sendMessage(ColorUtil.translate(prefix + "&aEnd Island Pos 2 set at &c"
                    + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "&a."));
        }

        if (session.getEndIslandPos1() != null && session.getEndIslandPos2() != null) {
            int baseLen = session.getBaseCustomLength();
            player.sendMessage(ColorUtil.translate(prefix + "&aBoth corners set! "
                    + "&7Base distance: &f" + baseLen + " blocks."));
            sendClickableContinue(player);
        }
    }

    /**
     * Send a clickable "/map setup continue" message.
     */
    private void sendClickableContinue(Player player) {
        try {
            net.md_5.bungee.api.chat.TextComponent prefix = new net.md_5.bungee.api.chat.TextComponent(
                    net.md_5.bungee.api.ChatColor.GRAY + "When ready: ");
            net.md_5.bungee.api.chat.TextComponent btn = new net.md_5.bungee.api.chat.TextComponent(
                    net.md_5.bungee.api.ChatColor.RED + "" + net.md_5.bungee.api.ChatColor.BOLD + "[/map setup continue]");
            btn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/map setup continue"));
            btn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.BaseComponent[]{
                            new net.md_5.bungee.api.chat.TextComponent(
                                    net.md_5.bungee.api.ChatColor.YELLOW + "Click to advance to the next step")}));
            prefix.addExtra(btn);
            player.spigot().sendMessage(prefix);
        } catch (Exception e) {
            player.sendMessage(ColorUtil.translate("&7When ready, type: &c/map setup continue"));
        }
    }

    /**
     * Send a clickable "/map setup finish <name>" suggestion.
     * Uses SUGGEST_COMMAND so the admin fills in the map name before pressing Enter.
     */
    private void sendClickableFinish(Player player) {
        try {
            net.md_5.bungee.api.chat.TextComponent msg = new net.md_5.bungee.api.chat.TextComponent(
                    net.md_5.bungee.api.ChatColor.GRAY + "When ready: "
                    + net.md_5.bungee.api.ChatColor.RED + "" + net.md_5.bungee.api.ChatColor.BOLD
                    + "[/map setup finish <name>]");
            msg.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND, "/map setup finish "));
            msg.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.BaseComponent[]{
                            new net.md_5.bungee.api.chat.TextComponent(
                                    net.md_5.bungee.api.ChatColor.YELLOW
                                    + "Click to fill — then type the map name and press Enter")}));
            player.spigot().sendMessage(msg);
        } catch (Exception e) {
            player.sendMessage(ColorUtil.translate("&7When ready, type: &c/map setup finish <name>"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        stopParticleTask(uuid);
        plugin.getMapManager().removeSetupSession(uuid);
    }

    private void startParticleTask(Player player, SetupSession session) {
        stopParticleTask(player.getUniqueId());
        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (session.getState() != SetupSession.State.SELECTING_ISLAND) {
                    stopParticleTask(player.getUniqueId());
                    cancel();
                    return;
                }
                Location p1 = session.getIslandPos1();
                Location p2 = session.getIslandPos2();
                if (p1 != null && p2 != null) {
                    spawnCuboidParticles(p1, p2);
                } else if (p1 != null) {
                    spawnPointParticle(p1);
                } else if (p2 != null) {
                    spawnPointParticle(p2);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L).getTaskId();
        particleTasks.put(player.getUniqueId(), taskId);
    }

    private void stopParticleTask(UUID uuid) {
        Integer taskId = particleTasks.remove(uuid);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private void spawnPointParticle(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        spawnBlueParticle(world, loc.clone().add(0.5, 0.5, 0.5));
    }

    /**
     * Renders the 12 edges of the selection cuboid as dense blue lines.
     * Step size 0.25 produces a continuous, non-moving outline.
     */
    private void spawnCuboidParticles(Location p1, Location p2) {
        World world = p1.getWorld();
        if (world == null) return;

        double minX = Math.min(p1.getBlockX(), p2.getBlockX());
        double minY = Math.min(p1.getBlockY(), p2.getBlockY());
        double minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        double maxX = Math.max(p1.getBlockX(), p2.getBlockX()) + 1.0;
        double maxY = Math.max(p1.getBlockY(), p2.getBlockY()) + 1.0;
        double maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ()) + 1.0;

        // Dense step = 0.25 blocks so the line appears continuous with no gaps
        double step = 0.25;

        // 4 edges parallel to X axis
        for (double x = minX; x <= maxX; x += step) {
            spawnBlueParticle(world, new Location(world, x, minY, minZ));
            spawnBlueParticle(world, new Location(world, x, maxY, minZ));
            spawnBlueParticle(world, new Location(world, x, minY, maxZ));
            spawnBlueParticle(world, new Location(world, x, maxY, maxZ));
        }
        // 4 edges parallel to Y axis
        for (double y = minY; y <= maxY; y += step) {
            spawnBlueParticle(world, new Location(world, minX, y, minZ));
            spawnBlueParticle(world, new Location(world, maxX, y, minZ));
            spawnBlueParticle(world, new Location(world, minX, y, maxZ));
            spawnBlueParticle(world, new Location(world, maxX, y, maxZ));
        }
        // 4 edges parallel to Z axis
        for (double z = minZ; z <= maxZ; z += step) {
            spawnBlueParticle(world, new Location(world, minX, minY, z));
            spawnBlueParticle(world, new Location(world, maxX, minY, z));
            spawnBlueParticle(world, new Location(world, minX, maxY, z));
            spawnBlueParticle(world, new Location(world, maxX, maxY, z));
        }
    }

    /**
     * Spawns a single blue dense particle at the given location.
     * Uses COLOURED_DUST via Spigot API (count=0, offsetZ=1 → pure blue).
     * Falls back to SMOKE if the particle type is unavailable.
     */
    private void spawnBlueParticle(World world, Location loc) {
        try {
            Effect coloured = Effect.valueOf("COLOURED_DUST");
            // count=0 with offsetX/Y/Z as RGB (0-1) gives a single colored particle
            // offsetX=0, offsetY=0, offsetZ=1 → blue (R=0, G=0, B=255)
            world.spigot().playEffect(loc, coloured, 0, 0, 0f, 0f, 1f, 1f, 0, 64);
        } catch (Exception e) {
            world.playEffect(loc, Effect.SMOKE, 0);
        }
    }
}
