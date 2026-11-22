package ua.brawler.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import ua.brawler.BrawlerPlugin;
import ua.brawler.data.PlayerData;
import ua.brawler.util.Weapons;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Здібності допоміжної руки: щит і лук.
 * Активація — клавіша F (обмін предметів між руками), коли ОБИДВІ руки порожні,
 * бо ванільний щит/лук неможливо використати, якщо вони фізично не в руці.
 */
public final class AbilityListener implements Listener {
    private static final String CD_SHIELD = "shield";
    private static final String CD_BOW = "bow";

    private final BrawlerPlugin plugin;
    private final NamespacedKey arrowKey;
    private final Map<UUID, Long> stance = new HashMap<>();
    private Set<DamageCause> reflectCauses = EnumSet.noneOf(DamageCause.class);

    public AbilityListener(BrawlerPlugin plugin) {
        this.plugin = plugin;
        this.arrowKey = new NamespacedKey(plugin, "brawler_tp_arrow");
        reloadSettings();
    }

    public void reloadSettings() {
        Set<DamageCause> set = EnumSet.noneOf(DamageCause.class);
        for (String s : plugin.getConfig().getStringList("shield.reflect-causes")) {
            try {
                set.add(DamageCause.valueOf(s.trim().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("shield.reflect-causes: невідомий тип шкоди " + s);
            }
        }
        reflectCauses = set;
    }

    public void cleanup(UUID id) { stance.remove(id); }

    // =====================================================================
    //  Активація клавішею F
    // =====================================================================
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (!d.isBrawler()) return;
        ItemStack off = d.getOffItem();
        if (Weapons.isEmpty(off)) return;
        Material m = off.getType();
        if (m != Material.SHIELD && m != Material.BOW) return;
        if (!Weapons.isEmpty(p.getInventory().getItemInMainHand())
                || !Weapons.isEmpty(p.getInventory().getItemInOffHand())) return;
        FileConfiguration cfg = plugin.getConfig();
        if (m == Material.SHIELD && !cfg.getBoolean("abilities.shield", true)) return;
        if (m == Material.BOW && !cfg.getBoolean("abilities.bow", true)) return;

        e.setCancelled(true);
        if (m == Material.SHIELD) activateShield(p);
        else fireTeleportArrow(p, off);
    }

    private boolean checkCooldown(Player p, String key, String name) {
        long rem = plugin.cooldowns().remaining(p, key);
        if (rem > 0) {
            plugin.msg().bar(p, "cooldown", "ability", name, "time", plugin.cooldowns().format(rem));
            return false;
        }
        return true;
    }

    // =====================================================================
    //  ЩИТ
    // =====================================================================
    private void activateShield(Player p) {
        if (!checkCooldown(p, CD_SHIELD, "Щит")) return;
        FileConfiguration cfg = plugin.getConfig();
        double dur = Math.max(0.1, cfg.getDouble("shield.active-seconds", 3.0));
        plugin.cooldowns().start(p, CD_SHIELD, cfg.getDouble("shield.cooldown-seconds", 30.0));
        stance.put(p.getUniqueId(), System.currentTimeMillis() + (long) (dur * 1000));
        plugin.msg().bar(p, "shield-on", "seconds", String.valueOf(dur));
        plugin.fx().sound(p.getLocation(), "item.armor.equip_iron", 1.0f, 1.0f);
        plugin.fx().particle(p.getLocation().add(0, 1.0, 0), Particle.ENCHANTED_HIT, 15, 0.4, 0.6, 0.4, 0.1);
    }

    private boolean shieldReady(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        ItemStack off = d.getOffItem();
        return d.isBrawler() && off != null && off.getType() == Material.SHIELD
                && plugin.getConfig().getBoolean("abilities.shield", true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (plugin.isInternal()) return; // не блокуємо/не відбиваємо власну внутрішню шкоду
        if (!(e.getEntity() instanceof Player p)) return;
        UUID id = p.getUniqueId();
        Long until = stance.get(id);
        if (until == null) return;
        if (System.currentTimeMillis() > until || !shieldReady(p)) {
            stance.remove(id);
            return;
        }
        if (!reflectCauses.contains(e.getCause())) return;

        LivingEntity attacker = null;
        if (e instanceof EntityDamageByEntityEvent ee) {
            Entity dmg = ee.getDamager();
            if (dmg instanceof Projectile pr) {
                if (pr.getShooter() instanceof LivingEntity shooter) attacker = shooter;
            } else if (dmg instanceof LivingEntity melee) {
                attacker = melee;
            }
        }
        if (attacker != null && attacker.getUniqueId().equals(id)) return; // власна шкода

        final double amount = e.getDamage();
        e.setCancelled(true);
        stance.remove(id);

        if (attacker != null && !attacker.isDead() && amount > 0.0) {
            final LivingEntity victim = attacker;
            plugin.runInternal(() -> victim.damage(amount, p));
        }
        plugin.msg().bar(p, "shield-reflect", "damage", String.format(java.util.Locale.US, "%.1f", amount));
        plugin.fx().sound(p.getLocation(), "item.shield.block", 1.0f, 1.0f);
        plugin.fx().particle(p.getLocation().add(0, 1.0, 0), Particle.ENCHANTED_HIT, 20, 0.4, 0.6, 0.4, 0.2);
    }

    // =====================================================================
    //  ЛУК: телепортаційна стріла
    // =====================================================================
    private void fireTeleportArrow(Player p, ItemStack bow) {
        if (!checkCooldown(p, CD_BOW, "Лук")) return;
        FileConfiguration cfg = plugin.getConfig();
        plugin.cooldowns().start(p, CD_BOW, cfg.getDouble("bow.cooldown-seconds", 30.0));

        double speed = Math.max(0.5, cfg.getDouble("bow.arrow-speed", 3.0));
        Vector v = p.getEyeLocation().getDirection().multiply(speed);
        Arrow arrow = p.launchProjectile(Arrow.class, v);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setCritical(true);
        arrow.getPersistentDataContainer().set(arrowKey, PersistentDataType.BYTE, (byte) 1);

        int power = bow.getEnchantmentLevel(Enchantment.POWER);
        int punch = bow.getEnchantmentLevel(Enchantment.PUNCH);
        int flame = bow.getEnchantmentLevel(Enchantment.FLAME);
        arrow.setDamage(2.0 + (power > 0 ? 0.5 * power + 0.5 : 0.0));
        if (punch > 0) arrow.setKnockbackStrength(punch);
        if (flame > 0) arrow.setFireTicks(1200);

        plugin.msg().bar(p, "bow-fired");
        plugin.fx().sound(p.getLocation(), "entity.arrow.shoot", 1.0f, 1.2f);
    }

    /** Підтверджене попадання: подію шкоди не скасовано і шкода > 0. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArrowDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Arrow arrow)) return;
        if (!arrow.getPersistentDataContainer().has(arrowKey, PersistentDataType.BYTE)) return;
        if (!(arrow.getShooter() instanceof Player shooter)) return;
        if (!(e.getEntity() instanceof LivingEntity target) || target.equals(shooter)) return;
        if (e.getFinalDamage() <= 0.0) return; // заблоковано/поглинуто

        FileConfiguration cfg = plugin.getConfig();
        boolean isPlayer = target instanceof Player;
        if (isPlayer && !cfg.getBoolean("bow.hit-players", true)) return;
        if (!isPlayer && !cfg.getBoolean("bow.hit-mobs", true)) return;

        Bukkit.getScheduler().runTask(plugin, () -> leapToTarget(shooter, target));
    }

    /** Стріла, що влучила в блок, прибирається (не засмічує світ). Влучання в сутність не чіпаємо. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArrowHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow arrow)) return;
        if (!arrow.getPersistentDataContainer().has(arrowKey, PersistentDataType.BYTE)) return;
        if (e.getHitEntity() == null && e.getHitBlock() != null) arrow.remove();
    }

    /** Підкидає стрільця в бік цілі (за замовчуванням на 5 блоків). */
    private void leapToTarget(Player shooter, LivingEntity target) {
        if (!shooter.isOnline() || shooter.isDead() || !target.isValid()) return;
        if (!shooter.getWorld().equals(target.getWorld())) return;
        PlayerData d = plugin.data().get(shooter.getUniqueId());
        ItemStack off = d.getOffItem();
        // здібність вимикається, якщо лук прибрано зі слота або клас скинуто
        if (!d.isBrawler() || off == null || off.getType() != Material.BOW) return;

        FileConfiguration cfg = plugin.getConfig();
        Vector dir = target.getLocation().toVector().subtract(shooter.getLocation().toVector());
        dir.setY(0);
        double dist = dir.length();
        if (dist < 1.0e-3) dir = shooter.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1.0e-6) return;
        dir.normalize();

        double want = Math.max(0.5, Math.min(30.0, cfg.getDouble("bow.leap-distance", 5.0)));
        double use = cfg.getBoolean("bow.cap-to-target", true) ? Math.min(want, Math.max(1.0, dist - 1.0)) : want;
        double vy = cfg.getDouble("bow.leap-height-velocity", 0.5);
        double hv = Weapons.horizontalSpeedFor(use, vy);
        Vector v = dir.multiply(hv);
        v.setY(vy);

        if (shooter.isInsideVehicle()) shooter.leaveVehicle();
        shooter.setVelocity(v);
        shooter.setFallDistance(0f);
        plugin.msg().bar(shooter, "bow-leap");
        plugin.fx().sound(shooter.getLocation(), "entity.breeze.jump", 1.0f, 1.0f);
        plugin.fx().particle(shooter.getLocation().add(0, 0.1, 0), Particle.CLOUD, 12, 0.3, 0.05, 0.3, 0.05);
    }
}
