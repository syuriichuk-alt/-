package ua.brawler.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ua.brawler.BrawlerPlugin;
import ua.brawler.data.PlayerData;
import ua.brawler.util.Weapons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Пасивка булави: лазіння по стінах.
 * Присядьте біля стіни — персонаж чіпляється за неї (висить). Пробіл — підтягування
 * вгору приблизно на один блок, після чого персонаж знову чіпляється за верх наступного блока.
 * Працює, коли булава у спец. слоті, а обидві руки порожні.
 * Затримка/утримання реалізовані ефектом Slow Falling + обнуленням швидкості (без зміни гравітації).
 */
public final class WallClimbManager implements Listener {
    private static final int HOLD_DELAY_TICKS = 6;

    private final BrawlerPlugin plugin;
    private final Set<UUID> candidates = new HashSet<>();
    private final Set<UUID> clinging = new HashSet<>();
    private final Map<UUID, Integer> airSince = new HashMap<>();
    private final Map<UUID, Integer> lastClimb = new HashMap<>();
    private BukkitTask task;

    public WallClimbManager(BrawlerPlugin plugin) { this.plugin = plugin; }

    public void start() {
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        for (UUID id : new ArrayList<>(clinging)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) release(p);
        }
        candidates.clear();
        clinging.clear();
        airSince.clear();
        lastClimb.clear();
    }

    public void stop(Player p) {
        UUID id = p.getUniqueId();
        candidates.remove(id);
        airSince.remove(id);
        lastClimb.remove(id);
        release(p);
    }

    private boolean eligible(Player p) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("abilities.mace", true) || !cfg.getBoolean("mace.wall-climb-enabled", true)) return false;
        PlayerData d = plugin.data().get(p.getUniqueId());
        ItemStack main = d.getMainItem();
        return d.isBrawler() && main != null && main.getType() == Material.MACE && Weapons.handsEmpty(p);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (e.isSneaking()) {
            if (eligible(p)) candidates.add(p.getUniqueId());
        } else {
            stop(p);
        }
    }

    private void tick() {
        if (candidates.isEmpty()) return;
        int now = Bukkit.getCurrentTick();
        for (UUID id : new ArrayList<>(candidates)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline() || p.isDead() || !p.isSneaking() || !eligible(p)) {
                candidates.remove(id);
                airSince.remove(id);
                if (p != null) release(p);
                continue;
            }
            if (p.isOnGround() || p.isGliding() || p.isInWater()) {
                airSince.remove(id);
                release(p);
                continue;
            }
            airSince.putIfAbsent(id, now);
            if (!touchingWall(p, p.getFacing())) {
                release(p);
                continue;
            }
            if (now - airSince.get(id) < HOLD_DELAY_TICKS) continue; // даємо завершити стрибок/підтягування
            hold(p);
        }
    }

    private void hold(Player p) {
        p.setVelocity(new Vector(0, 0, 0));
        p.setFallDistance(0f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 6, 0, true, false, false));
        clinging.add(p.getUniqueId());
    }

    private void release(Player p) {
        if (!clinging.remove(p.getUniqueId())) return;
        PotionEffect pe = p.getPotionEffect(PotionEffectType.SLOW_FALLING);
        if (pe != null && pe.isAmbient() && pe.getDuration() <= 8) p.removePotionEffect(PotionEffectType.SLOW_FALLING);
    }

    /** Пробіл під час «висіння» — підтягування вгору. */
    @EventHandler
    public void onInput(PlayerInputEvent e) {
        if (!e.getInput().isJump()) return;
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (!candidates.contains(id) || p.isOnGround()) return;
        BlockFace face = p.getFacing();
        if (!touchingWall(p, face)) return;

        FileConfiguration cfg = plugin.getConfig();
        int now = Bukkit.getCurrentTick();
        Integer lc = lastClimb.get(id);
        if (lc != null && now - lc < cfg.getInt("mace.wall-climb-cooldown-ticks", 8)) return;
        lastClimb.put(id, now);
        airSince.put(id, now);
        release(p);

        double h = Math.max(0.5, cfg.getDouble("mace.wall-climb-height", 1.3));
        Vector v = new Vector(0, Weapons.velocityForHeight(h), 0);
        Location l = p.getLocation();
        Block above = p.getWorld().getBlockAt(l.getBlockX(), l.getBlockY() + 2, l.getBlockZ()).getRelative(face);
        if (above.isPassable()) { // верх стіни поруч — трохи штовхаємо вперед, щоб забратися нагору
            v.add(new Vector(face.getModX() * 0.12, 0, face.getModZ() * 0.12));
        }
        p.setVelocity(v);
        p.setFallDistance(0f);
        plugin.fx().sound(l, "block.stone.hit", 0.6f, 1.2f);
        plugin.fx().particle(l.clone().add(0, 1.0, 0), Particle.CRIT, 4, 0.2, 0.2, 0.2, 0.05);
    }

    private boolean touchingWall(Player p, BlockFace face) {
        Location l = p.getLocation();
        int bx = l.getBlockX(), by = l.getBlockY(), bz = l.getBlockZ();
        Block feet = p.getWorld().getBlockAt(bx, by, bz).getRelative(face);
        Block chest = p.getWorld().getBlockAt(bx, by + 1, bz).getRelative(face);
        if (feet.isPassable() && chest.isPassable()) return false;
        double dist;
        switch (face) {
            case EAST -> dist = bx + 1 - l.getX();
            case WEST -> dist = l.getX() - bx;
            case SOUTH -> dist = bz + 1 - l.getZ();
            case NORTH -> dist = l.getZ() - bz;
            default -> { return false; }
        }
        return dist <= 0.55;
    }
}
