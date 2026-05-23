package net.gravijet.fastbuilder.gameplay;

import net.gravijet.fastbuilder.FastBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.Random;

/**
 * Launches the firework celebration shown when a player finishes a run.
 * PB runs: full firework entities (server-wide). Non-PB: sound only for the runner.
 */
class FinishCelebration {

    private final FastBuilder plugin;

    FinishCelebration(FastBuilder plugin) {
        this.plugin = plugin;
    }

    void launch(final Player player, final Location location, final boolean isPB) {
        final Random rand = new Random();

        for (int wave = 0; wave < 8; wave++) {
            final int delay = wave * 7;
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    if (isPB) {
                        if (!player.isOnline()) return;
                        spawnFirework(location, rand);
                        Location off1 = location.clone().add(
                                (rand.nextDouble() - 0.5) * 6, 0, (rand.nextDouble() - 0.5) * 6);
                        Location off2 = location.clone().add(
                                (rand.nextDouble() - 0.5) * 6, 0, (rand.nextDouble() - 0.5) * 6);
                        spawnFirework(off1, rand);
                        spawnFirework(off2, rand);
                    } else {
                        if (!player.isOnline()) return;
                        try {
                            player.playSound(location,
                                    org.bukkit.Sound.valueOf("FIREWORK_LAUNCH"), 1.0f, 1.0f);
                            player.playSound(location,
                                    org.bukkit.Sound.valueOf("FIREWORK_BLAST"), 1.0f, 1.0f);
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }, delay);
        }
    }

    private void spawnFirework(Location location, Random rand) {
        try {
            Firework fw = location.getWorld().spawn(location, Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();
            Color[] colors = {Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN,
                              Color.AQUA, Color.BLUE, Color.PURPLE, Color.WHITE};
            Color primary = colors[rand.nextInt(colors.length)];
            Color fade    = colors[rand.nextInt(colors.length)];
            FireworkEffect.Type[] types = {
                FireworkEffect.Type.BALL_LARGE,
                FireworkEffect.Type.BALL,
                FireworkEffect.Type.STAR,
                FireworkEffect.Type.BURST
            };
            meta.addEffect(FireworkEffect.builder()
                    .withColor(primary, Color.WHITE)
                    .withFade(fade)
                    .with(types[rand.nextInt(types.length)])
                    .flicker(true)
                    .trail(true)
                    .build());
            meta.setPower(1 + rand.nextInt(2));
            fw.setFireworkMeta(meta);
        } catch (Exception ignored) {}
    }
}
