# CLAUDE.md - Fastbuilder Master Specification

## Project Vision
- **Name:** "Fastbuilder" (Strictly case-sensitive).
- **Goal:** A 1:1 McPlayHD clone. Professional, high-performance, and bug-free.
- **Style:** "GraviJet" Branding. Prefix: `§c§lFastbuilder §7» §f`. No Emojis. No "---" AI-style lines in stats.

## Environment & Tech Stack
- **Version:** Spigot 1.8.8 (Custom Fork), **Java 21**.
- **Dependencies:** FastAsyncWorldEdit (FAWE), Citizens (NPCs), DecentHolograms.
- **Build:** `mvn clean package`. Output: `target/Fastbuilder.jar`.
- **Network:** BungeeCord compatible (Lobby-1 redirect).

## 1. MATHEMATICAL GRID & INSTANCING (CRITICAL)
- **Setup Template:** Admin setup at `-1000, 20, -1000`. After saving metadata/schematic, CLEAR this area (replace with air).
- **Global Map Grid (X-Axis):** Different map types are placed strictly along the positive X-axis.
    - Map 1: `2000, 20, 0` | Map 2: `4000, 20, 0` | Map 3: `6000, 20, 0`.
    - All maps MUST face **Positive X** (players build towards +X).
- **Island Scaling (Z-Axis):** Island instances for a map are pasted "to the right" along the **positive Z-axis**.
    - Map 1, Instance 2: `2000, 20, [distance]`. Instance 3: `2000, 20, [2*distance]`.
    - Use `/map distance` to prevent overlaps. Maintain min. 15 islands (Autoscale at 80% capacity).
- **Persistence:** On join, send player to their last island's spawn or the default map. On island switch: Clear old blocks, remove old Hologram/NPC, teleport to the new island's EXACT relative spawn.

## 2. GAMEPLAY MECHANICS & PHYSICS
- **Inventory & Items:** On island join: Clear INV, set Survival.
    - 2 Stacks of Blocks (Colored names, NOT italic `§r`).
    - 1 Diamond Pickaxe (expandable via NBT/Store).
    - Perk: "Auto-Refill" (purchasable with Coins) to never run out of blocks.
- **Protection:** No fall damage. No void death (teleport to start). Build only own blocks. No leaving island borders.
- **Success:** Reach Finish (Pressure Plates) -> Success Sound + Firework -> Wait 2s -> Reset & TP to start.
- **Practice Mode:** Blocks stay on death/fall. Time is NOT recorded. **Anti-Exploit:** ALL practice blocks MUST be cleared before a real run can start.

## 3. UI, SCOREBOARD & HOLOGRAMS
- **Scoreboard (Exact Implementation):**
  ```java
  objective.getScore("§7§m-------------------").setScore(score--);
  objective.getScore("§8» §cRank: §6" + getPlaceholder(p, "%phoenix_player_real_rank%")).setScore(score--);
  objective.getScore("§8» §cPlayers: §6" + getPlaceholder(p, "%online%")).setScore(score--);
  objective.getScore("§8» §cCoins: §6" + getPlaceholder(p, "%pxcosmetics_player_coins%")).setScore(score--);
  objective.getScore("§8» §cLevel: §6" + getPlaceholder(p, "%level%")).setScore(score--);
  objective.getScore("§8» §cPlaytime: §6" + h + "h").setScore(score--);
  objective.getScore("§f ").setScore(score--);
  objective.getScore("§7§ogravijet.net").setScore(score--);
  objective.getScore("§7§o§m-------------------").setScore(score--);