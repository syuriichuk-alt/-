package ua.brawler.sprint;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.scheduler.BukkitTask;
import ua.brawler.BrawlerPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Розгін під час бігу. Використовується ОДИН модифікатор атрибута MOVEMENT_SPEED
 * (ADD_SCALAR = множення базової швидкості), тому ефекти Speed/Slowness і ванільний
 * бонус бігу залишаються незмінними й множаться разом з нашим.
 * Завдання працює лише за наявності бігунів у мапі.
 */
public final class SprintManager implements Listener {
    private final BrawlerPlugin plugin;
    private final NamespacedKey key;
    private final Map<UUID, Long> startMs = new HashMap<>();
    private final Map<UUID, Integer> lastStep = new HashMap<>();
    private final Map<UUID, Double> applied = new HashMap<>();
    private BukkitTask task;

    public SprintManager(BrawlerPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "brawler_sprint");
    }

    public void start() {
        if (task != null) task.cancel();
        int iv = Math.max(1, plugin.getConfig().getInt("sprint.update-interval-ticks", 2));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, iv, iv);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        for (Player p : Bukkit.getOnlinePlayers()) clearModifier(p);
        startMs.clear();
        lastStep.clear();
        applied.clear();
    }

    private boolean active(Player p) {
        return plugin.getConfig().getBoolean("abilities.sprint", true)
                && plugin.data().get(p.getUniqueId()).isBrawler();
    }

    public void begin(Player p) {
        if (!active(p)) return;
        startMs.putIfAbsent(p.getUniqueId(), System.currentTimeMillis());
        lastStep.putIfAbsent(p.getUniqueId(), 0);
    }

    public void stop(Player p) {
        UUID id = p.getUniqueId();
        startMs.remove(id);
        lastStep.remove(id);
        applied.remove(id);
        clearModifier(p);
    }

    private void tick() {
        if (startMs.isEmpty()) return;
        FileConfiguration cfg = plugin.getConfig();
        double max = Math.max(0.1, cfg.getDouble("sprint.max-multiplier", 1.5));
        double startMul = Math.max(0.1, Math.min(max, cfg.getDouble("sprint.start-multiplier", 0.75)));
        double time = Math.max(0.1, cfg.getDouble("sprint.time-to-max-seconds", 4.0));
        int maxStep = (int) Math.ceil(time);
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<UUID, Long>> it = startMs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> en = it.next();
            UUID id = en.getKey();
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline() || p.isDead() || !p.isSprinting() || !active(p)) {
                it.remove();
                lastStep.remove(id);
                applied.remove(id);
                if (p != null) clearModifier(p);
                continue;
            }
            double sec = (now - en.getValue()) / 1000.0;
            double amount = (startMul + (max - startMul) * Math.min(1.0, sec / time)) - 1.0;
            Double prev = applied.get(id);
            if (prev == null || Math.abs(prev - amount) > 0.003) {
                setModifier(p, amount);
                applied.put(id, amount);
            }
            int step = (int) Math.floor(sec);
            int ls = lastStep.getOrDefault(id, 0);
            if (step > ls && step <= maxStep) {
                lastStep.put(id, step);
                plugin.fx().particle(p.getLocation().add(0, 0.1, 0), Particle.CLOUD, 6, 0.25, 0.05, 0.25, 0.02);
                plugin.fx().sound(p.getLocation(), "entity.horse.gallop", 0.3f, 1.0f + step * 0.15f);
            }
        }
    }

    private void setModifier(Player p, double amount) {
        AttributeInstance a = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (a == null) return;
        removeFrom(a);
        if (Math.abs(amount) > 0.0001) {
            a.addTransientModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_SCALAR));
        }
    }

    public void clearModifier(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (a != null) removeFrom(a);
    }

    private void removeFrom(AttributeInstance a) {
        for (AttributeModifier m : new ArrayList<>(a.getModifiers())) {
            if (key.equals(m.getKey())) a.removeModifier(m);
        }
    }

    @EventHandler
    public void onSprint(PlayerToggleSprintEvent e) {
        Player p = e.getPlayer();
        if (e.isSprinting()) begin(p);
        else stop(p);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) { stop(e.getEntity()); }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) { stop(e.getPlayer()); }
}
