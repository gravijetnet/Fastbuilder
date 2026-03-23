package de.fastbuilder.command;

import de.fastbuilder.manager.GameManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Befehlsverarbeitung für /f (FastBuilder Hauptbefehl).
 *
 * Unterbefehle:
 *   /f setspawn    → Setzt Spawnpunkt auf aktuelle Position
 *   /f spawn       → Teleportiert zum Insel-Spawnpunkt
 *   /f help        → Zeigt Hilfe an
 *
 * Hinweis: Die Island-ID in der Anforderung (/f setspawn 1) wird ignoriert,
 * da jeder Spieler seine eigene Insel hat und diese automatisch zugeordnet wird.
 */
public class FastBuilderCommand implements CommandExecutor {

    private final GameManager gameManager;

    public FastBuilderCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        // Nur Spieler können Befehle nutzen
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Dieser Befehl kann nur von Spielern genutzt werden!");
            return true;
        }

        Player player = (Player) sender;

        // Kein Unterbefehl → Hilfe anzeigen
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "setspawn":
                // Nur Admins dürfen den Spawn setzen
                if (!player.hasPermission("fastbuilder.admin")) {
                    player.sendMessage(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl!");
                    return true;
                }
                gameManager.setSpawn(player);
                break;

            case "spawn":
            case "tp":
                gameManager.teleportToSpawn(player);
                break;

            case "help":
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    /** Zeigt die Hilfe-Nachricht an */
    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "━━━ FastBuilder Befehle ━━━");
        player.sendMessage(ChatColor.YELLOW + "/f spawn" + ChatColor.GRAY
                + " → Teleportiert zu deinem Spawnpunkt");
        if (player.hasPermission("fastbuilder.admin")) {
            player.sendMessage(ChatColor.YELLOW + "/f setspawn" + ChatColor.GRAY
                    + " → Setzt deinen Spawnpunkt hier");
        }
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}
