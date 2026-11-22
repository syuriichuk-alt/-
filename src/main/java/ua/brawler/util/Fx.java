package ua.brawler.util;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import ua.brawler.BrawlerPlugin;

/** Звуки та частинки з урахуванням налаштувань. Звуки — за ключем (стабільно між версіями). */
public final class Fx {
    private final BrawlerPlugin plugin;

    public Fx(BrawlerPlugin plugin) { this.plugin = plugin; }

    public void sound(Location l, String key, float volume, float pitch) {
        if (!plugin.getConfig().getBoolean("effects.sounds", true)) return;
        World w = l.getWorld();
        if (w == null) return;
        float vol = volume * (float) plugin.getConfig().getDouble("effects.sound-volume", 1.0);
        w.playSound(l, key, SoundCategory.PLAYERS, vol, pitch);
    }

    public void particle(Location l, Particle p, int count, double dx, double dy, double dz, double speed) {
        if (!plugin.getConfig().getBoolean("effects.particles", true)) return;
        World w = l.getWorld();
        if (w == null) return;
        w.spawnParticle(p, l, count, dx, dy, dz, speed);
    }

    /** Кільце частинок, що розходиться від центру (ударна хвиля). */
    public void ring(Location c, Particle p, int points, double speed) {
        if (!plugin.getConfig().getBoolean("effects.particles", true)) return;
        World w = c.getWorld();
        if (w == null) return;
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            double dx = Math.cos(a), dz = Math.sin(a);
            w.spawnParticle(p, c.getX() + dx * 0.5, c.getY() + 0.15, c.getZ() + dz * 0.5, 0, dx, 0.0, dz, speed);
        }
    }
}
