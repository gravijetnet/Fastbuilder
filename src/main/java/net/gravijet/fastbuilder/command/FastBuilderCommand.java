package net.gravijet.fastbuilder.command;

import net.gravijet.fastbuilder.manager.GameManager;
import net.gravijet.fastbuilder.manager.MapManager;
import net.gravijet.fastbuilder.manager.SchematicManager;
import net.gravijet.fastbuilder.model.BridgeDistance;
import net.gravijet.fastbuilder.model.MapTemplate;
import net.gravijet.fastbuilder.model.PlayerStats;
import net.gravijet.fastbuilder.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FastBuilderCommand implements CommandExecutor {

    private final GameManager gameManager;

    // Per-admin session data for map creation
    private final Map<UUID, Location>       pos1    = new HashMap<>();
    private final Map<UUID, Location>       pos2    = new HashMap<>();
    private final Map<UUID, Location>       spawn   = new HashMap<>();
    private final Map<UUID, List<Location>> targets = new HashMap<>();
    private final Map<UUID, String>         mapName = new HashMap<>();

    public FastBuilderCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "spawn": case "tp":
                requirePlayer(sender, () -> gameManager.teleportToSpawn((Player) sender));
                break;

            case "reset":
                requirePlayer(sender, () -> gameManager.resetAttempt((Player) sender));
                break;

            case "stats":
                handleStats(sender, args);
                break;

            case "setspawn":
                if (!sender.hasPermission("fastbuilder.admin")) { noPerms(sender); return true; }
                requirePlayer(sender, () -> gameManager.setSpawn((Player) sender));
                break;

            case "reload":
                if (!sender.hasPermission("fastbuilder.admin")) { noPerms(sender); return true; }
                net.gravijet.fastbuilder.Main.getInstance().reloadConfig();
                gameManager.getMapManager().reload();
                sender.sendMessage(CC.SUCCESS + "Config & Maps neu geladen.");
                break;

            case "map":
                if (!sender.hasPermission("fastbuilder.admin")) { noPerms(sender); return true; }
                if (!(sender instanceof Player)) { sender.sendMessage(CC.ERROR + "Nur für Spieler."); return true; }
                handleMap((Player) sender, args);
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    // ── /fb map ... ───────────────────────────────────────────────

    private void handleMap(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(CC.PREFIX + CC.c("&7/fb map <create|pos1|pos2|setspawn|settarget|cleartargets|save|list|delete|icon|displayname>"));
            return;
        }

        UUID uuid = p.getUniqueId();
        String sub = args[1].toLowerCase();

        switch (sub) {
            case "create":
                if (args.length < 3) { p.sendMessage(CC.ERROR + "Benutzung: /fb map create <name>"); return; }
                mapName.put(uuid, args[2].toLowerCase());
                pos1.remove(uuid); pos2.remove(uuid); spawn.remove(uuid);
                targets.put(uuid, new ArrayList<>());
                p.sendMessage(CC.SUCCESS + "Map-Session gestartet: &f" + args[2] + CC.c("&a. Setze jetzt pos1, pos2, spawn und target(s)."));
                break;

            case "pos1":
                if (!hasSession(p)) return;
                pos1.put(uuid, p.getLocation().clone());
                p.sendMessage(CC.SUCCESS + "Pos1 gesetzt: " + formatLoc(p.getLocation()));
                break;

            case "pos2":
                if (!hasSession(p)) return;
                pos2.put(uuid, p.getLocation().clone());
                p.sendMessage(CC.SUCCESS + "Pos2 gesetzt: " + formatLoc(p.getLocation()));
                break;

            case "setspawn":
                if (!hasSession(p)) return;
                spawn.put(uuid, p.getLocation().clone());
                p.sendMessage(CC.SUCCESS + "Spawn gesetzt: " + formatLoc(p.getLocation()));
                break;

            case "settarget":
                if (!hasSession(p)) return;
                targets.computeIfAbsent(uuid, k -> new ArrayList<>()).add(p.getLocation().clone());
                p.sendMessage(CC.SUCCESS + "Zielblock hinzugefügt: " + formatLoc(p.getLocation())
                        + CC.c(" &7(Gesamt: " + targets.get(uuid).size() + ")"));
                break;

            case "cleartargets":
                if (!hasSession(p)) return;
                targets.getOrDefault(uuid, new ArrayList<>()).clear();
                p.sendMessage(CC.SUCCESS + "Zielblöcke geleert.");
                break;

            case "save":
                if (!hasSession(p)) return;
                Location p1 = pos1.get(uuid), p2 = pos2.get(uuid), sp = spawn.get(uuid);
                List<Location> tgts = targets.getOrDefault(uuid, new ArrayList<>());
                if (p1 == null || p2 == null) { p.sendMessage(CC.ERROR + "Bitte erst pos1 und pos2 setzen."); return; }
                if (sp == null)  { p.sendMessage(CC.ERROR + "Bitte erst einen Spawnpunkt setzen."); return; }
                if (tgts.isEmpty()) { p.sendMessage(CC.ERROR + "Bitte mindestens einen Zielblock setzen."); return; }
                MapTemplate saved = gameManager.getSchematicManager().saveSchematic(
                        mapName.get(uuid), p.getWorld(), p1, p2, sp, tgts);
                gameManager.getMapManager().registerTemplate(saved);
                p.sendMessage(CC.SUCCESS + "Map &f" + saved.getName() + CC.c("&a gespeichert ("
                        + saved.getBlocks().size() + " Blöcke)."));
                pos1.remove(uuid); pos2.remove(uuid); spawn.remove(uuid);
                targets.remove(uuid); mapName.remove(uuid);
                break;

            case "list":
                p.sendMessage(CC.c("&c&lMaps:"));
                for (MapTemplate t : gameManager.getMapManager().getAllTemplates()) {
                    p.sendMessage(CC.c("  &7- &f" + t.getName() + " &8(" + t.getDisplayName() + "&8)"));
                }
                if (gameManager.getMapManager().getAllTemplates().isEmpty())
                    p.sendMessage(CC.c("  &7Keine Maps gefunden."));
                break;

            case "delete":
                if (args.length < 3) { p.sendMessage(CC.ERROR + "Benutzung: /fb map delete <name>"); return; }
                String delName = args[2].toLowerCase();
                if (!gameManager.getSchematicManager().exists(delName)) {
                    p.sendMessage(CC.ERROR + "Map &f" + delName + CC.c(" &cnicht gefunden.")); return;
                }
                gameManager.getSchematicManager().delete(delName);
                gameManager.getMapManager().removeTemplate(delName);
                p.sendMessage(CC.SUCCESS + "Map &f" + delName + CC.c(" &agelöscht."));
                break;

            case "displayname":
                if (args.length < 4) { p.sendMessage(CC.ERROR + "Benutzung: /fb map displayname <name> <anzeigename>"); return; }
                MapTemplate dtm = gameManager.getMapManager().getTemplate(args[2].toLowerCase());
                if (dtm == null) { p.sendMessage(CC.ERROR + "Map nicht gefunden."); return; }
                String displayName = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
                dtm.setDisplayName(CC.c(displayName));
                gameManager.getSchematicManager().persist(dtm);
                p.sendMessage(CC.SUCCESS + "Anzeigename gesetzt.");
                break;

            case "icon":
                if (args.length < 4) { p.sendMessage(CC.ERROR + "Benutzung: /fb map icon <name> <material>"); return; }
                MapTemplate itm = gameManager.getMapManager().getTemplate(args[2].toLowerCase());
                if (itm == null) { p.sendMessage(CC.ERROR + "Map nicht gefunden."); return; }
                Material mat = Material.getMaterial(args[3].toUpperCase());
                if (mat == null) { p.sendMessage(CC.ERROR + "Unbekanntes Material."); return; }
                itm.setIconMaterial(mat);
                gameManager.getSchematicManager().persist(itm);
                p.sendMessage(CC.SUCCESS + "Icon gesetzt.");
                break;

            default:
                p.sendMessage(CC.PREFIX + CC.c("&7/fb map <create|pos1|pos2|setspawn|settarget|cleartargets|save|list|delete|icon|displayname>"));
        }
    }

    private boolean hasSession(Player p) {
        if (!mapName.containsKey(p.getUniqueId())) {
            p.sendMessage(CC.ERROR + "Keine aktive Map-Session. Starte mit &f/fb map create <name>");
            return false;
        }
        return true;
    }

    private String formatLoc(Location l) {
        return CC.c("&7(" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + ")");
    }

    // ── Stats ─────────────────────────────────────────────────────

    private void handleStats(CommandSender sender, String[] args) {
        UUID targetUuid;
        String targetName;

        if (args.length >= 2) {
            if (!sender.hasPermission("fastbuilder.stats.others")) {
                sender.sendMessage(CC.ERROR + "Keine Berechtigung.");
                return;
            }
            @SuppressWarnings("deprecation")
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) { sender.sendMessage(CC.ERROR + "Spieler &f" + args[1] + " &cnicht gefunden."); return; }
            targetUuid = target.getUniqueId();
            targetName = target.getName();
        } else {
            if (!(sender instanceof Player)) { sender.sendMessage(CC.ERROR + "Benutzung: /fb stats <spieler>"); return; }
            Player player = (Player) sender;
            targetUuid = player.getUniqueId();
            targetName = player.getName();
        }

        PlayerStats stats = gameManager.getStatsManager().get(targetUuid);
        sender.sendMessage(CC.c("&c&lFastBuilder &7\u00bb &f&lStats &8- &7" + targetName));
        sender.sendMessage(CC.c(" &4\u25cf &7Versuche: &f" + stats.getTotalAttempts()));
        sender.sendMessage(CC.c(" &4\u25cf &7Erfolge: &a" + stats.getTotalSuccesses()));
        sender.sendMessage(CC.c(" "));
        for (BridgeDistance d : BridgeDistance.values()) {
            PlayerStats.DistanceStats ds = stats.getStats(d);
            if (ds.attempts == 0) continue;
            sender.sendMessage(CC.c("   &8- " + d.getColorCode() + d.getDisplayName()
                    + " &8| &7Versuche: &f" + ds.attempts
                    + " &8| &7Erfolge: &a" + ds.successes
                    + " &8| &7Best: &e" + ds.getFormattedBestTime()));
        }
    }

    // ── Help ──────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        boolean admin = sender.hasPermission("fastbuilder.admin");
        sender.sendMessage(CC.c("&c&lFastBuilder &7\u00bb &f&lBefehle"));
        sender.sendMessage(CC.c(" &4\u25cf &c/fb spawn &7\u00bb &fZum Spawn teleportieren"));
        sender.sendMessage(CC.c(" &4\u25cf &c/fb reset &7\u00bb &fVersuch zurücksetzen"));
        sender.sendMessage(CC.c(" &4\u25cf &c/fb stats [spieler] &7\u00bb &fStatistiken anzeigen"));
        if (admin) {
            sender.sendMessage(CC.c(" &4\u25cf &c/fb setspawn &7\u00bb &fSpawnpunkt setzen"));
            sender.sendMessage(CC.c(" &4\u25cf &c/fb map ... &7\u00bb &fMap-Verwaltung"));
            sender.sendMessage(CC.c(" &4\u25cf &c/fb reload &7\u00bb &fConfig neu laden"));
        }
    }

    private void noPerms(CommandSender s) { s.sendMessage(CC.ERROR + "Keine Berechtigung."); }

    private void requirePlayer(CommandSender sender, Runnable action) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(CC.ERROR + "Nur für Spieler.");
            return;
        }
        action.run();
    }
}
