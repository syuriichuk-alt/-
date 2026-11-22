package ua.brawler;

import org.bukkit.entity.Player;
import ua.brawler.data.PlayerData;

import java.util.Locale;
import java.util.Map;

/** Персональні перезарядки (зберігаються між перезаходами). */
public final class CooldownManager {
    private final BrawlerPlugin plugin;

    public CooldownManager(BrawlerPlugin plugin) { this.plugin = plugin; }

    /** Залишок у мс (0 — готово). */
    public long remaining(Player p, String key) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        Map<String, Long> map = d.getCooldowns();
        Long until = map.get(key);
        if (until == null) return 0L;
        long r = until - System.currentTimeMillis();
        if (r <= 0) {
            map.remove(key);
            return 0L;
        }
        return r;
    }

    public void start(Player p, String key, double seconds) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        d.getCooldowns().put(key, System.currentTimeMillis() + (long) (seconds * 1000.0));
        plugin.data().save(p.getUniqueId());
    }

    public String format(long ms) {
        return String.format(Locale.US, "%.1f с", ms / 1000.0);
    }
}
