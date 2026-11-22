package ua.brawler.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ua.brawler.BrawlerPlugin;
import ua.brawler.data.PlayerData;
import ua.brawler.util.Fx;
import ua.brawler.util.WeaponType;
import ua.brawler.util.Weapons;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Власна система кулачного бою.
 * HIGH: обчислення шкоди (зброя зі спец. слота, швидкість атаки, зачарування, smash булави).
 * MONITOR: побічні ефекти (вогонь, відкидання, лічильник 3-го удару, сплеш, підкидання...),
 *          лише якщо подію ніхто не скасував.
 */
public final class CombatListener implements Listener {
    private static final Set<EntityType> UNDEAD = EnumSet.of(
            EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.HUSK, EntityType.DROWNED,
            EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED, EntityType.WITHER_SKELETON,
            EntityType.WITHER, EntityType.ZOMBIFIED_PIGLIN, EntityType.ZOMBIE_HORSE,
            EntityType.SKELETON_HORSE, EntityType.PHANTOM, EntityType.ZOGLIN);
    private static final Set<EntityType> ARTHROPOD = EnumSet.of(
            EntityType.SPIDER, EntityType.CAVE_SPIDER, EntityType.SILVERFISH,
            EntityType.ENDERMITE, EntityType.BEE);

    private static final class Ctx {
        EntityDamageByEntityEvent event;
        Player player;
        LivingEntity target;
        ItemStack weapon;
        WeaponType type;
        boolean smash;
        double fall;
        double strength;
        double speed;
    }

    private final BrawlerPlugin plugin;
    private final Map<UUID, Integer> counters = new HashMap<>();
    private final Map<UUID, Integer> lastAttackTick = new HashMap<>();
    private final Map<UUID, Long> launchImmunity = new HashMap<>();
    private Ctx pending;
    private record Mark(UUID attacker, long until) {}
    private final Map<UUID, Mark> marks = new HashMap<>();
    private final Map<UUID, BukkitTask> bars = new HashMap<>();

    public CombatListener(BrawlerPlugin plugin) { this.plugin = plugin; }

    public void resetCounter(UUID id) { counters.remove(id); }

    public void cleanup(UUID id) {
        counters.remove(id);
        lastAttackTick.remove(id);
        launchImmunity.remove(id);
        marks.remove(id);
        marks.values().removeIf(m -> m.attacker().equals(id));
        BukkitTask bar = bars.remove(id);
        if (bar != null) bar.cancel();
        if (pending != null && pending.player.getUniqueId().equals(id)) pending = null;
    }

    // =====================================================================
    //  ЕТАП 1: розрахунок шкоди
    // =====================================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (plugin.isInternal()) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;
        if (p.getGameMode() == GameMode.SPECTATOR) return;
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (!d.isBrawler()) return;

        FileConfiguration cfg = plugin.getConfig();
        // бафи зброї діють, лише коли ОБИДВІ руки порожні
        if (cfg.getBoolean("fist.require-empty-hands", true) && !Weapons.handsEmpty(p)) return;

        ItemStack weapon = d.getMainItem();
        WeaponType type = weapon == null ? null : WeaponType.of(weapon.getType());
        if (type == null) weapon = null;

        // --- швидкість атаки (власний «кулдаун» удару) ---
        double speed = type == null ? 4.0 : Weapons.attackSpeed(weapon);
        int now = Bukkit.getCurrentTick();
        Integer last = lastAttackTick.put(p.getUniqueId(), now);
        double strength = last == null ? 1.0 : clamp((now - last + 0.5) / (20.0 / speed), 0.0, 1.0);

        // --- базова шкода ---
        double base = type == null
                ? cfg.getDouble("fist.base-damage", 1.0)
                : Weapons.attackDamage(weapon) * cfg.getDouble("fist.weapon-damage-multiplier", 1.0);

        // --- зачарування, що змінюють шкоду ---
        double bonus = 0.0;
        if (type != null) {
            if (type != WeaponType.MACE) {
                int sharp = weapon.getEnchantmentLevel(Enchantment.SHARPNESS);
                if (sharp > 0) bonus += 0.5 * sharp + 0.5;
            }
            int smite = weapon.getEnchantmentLevel(Enchantment.SMITE);
            if (smite > 0 && UNDEAD.contains(target.getType())) bonus += 2.5 * smite;
            int bane = weapon.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS);
            if (bane > 0 && ARTHROPOD.contains(target.getType())) bonus += 2.5 * bane;
        }

        double dmg = base * (0.2 + strength * strength * 0.8) + bonus * strength;

        // --- smash-удар булави ---
        double fall = p.getFallDistance();
        boolean smash = type == WeaponType.MACE
                && cfg.getBoolean("mace.smash-enabled", true)
                && fall > 1.5 && !p.isGliding();

        // --- критичний удар (як у ваніллі; не під час smash) ---
        boolean crit = !smash && strength > 0.9 && fall > 0.0 && !p.isOnGround()
                && !p.isInWater() && !p.isSprinting() && !p.isInsideVehicle()
                && !p.hasPotionEffect(PotionEffectType.BLINDNESS);
        if (crit) dmg *= 1.5;

        if (smash) {
            double f = fall;
            double sb = f <= 3.0 ? 4.0 * f : (f <= 8.0 ? 12.0 + 2.0 * (f - 3.0) : 22.0 + (f - 8.0));
            int density = weapon.getEnchantmentLevel(Enchantment.DENSITY);
            sb += 0.5 * density * f;
            dmg += sb;
        }

        // --- Breach: зменшує ефективність броні цілі на 15% за рівень ---
        if (type == WeaponType.MACE) {
            int breach = weapon.getEnchantmentLevel(Enchantment.BREACH);
            if (breach > 0) dmg = applyBreach(target, dmg, breach);
        }

        e.setDamage(dmg);

        if (crit) {
            plugin.fx().particle(target.getLocation().add(0, target.getHeight() * 0.7, 0),
                    Particle.CRIT, 10, 0.3, 0.3, 0.3, 0.3);
            plugin.fx().sound(p.getLocation(), "entity.player.attack.crit", 0.8f, 1.0f);
        }

        Ctx c = new Ctx();
        c.event = e;
        c.player = p;
        c.target = target;
        c.weapon = weapon;
        c.type = type;
        c.smash = smash;
        c.fall = fall;
        c.strength = strength;
        c.speed = speed;
        pending = c;
    }

    private static double armorFactor(double armor, double toughness, double damage) {
        return clamp(armor - damage / (2.0 + toughness / 4.0), armor * 0.2, 20.0);
    }

    /** Підбирає шкоду так, щоб після ванільного зменшення бронею результат відповідав зменшеній броні. */
    private static double applyBreach(LivingEntity t, double dmg, int level) {
        AttributeInstance a = t.getAttribute(Attribute.ARMOR);
        AttributeInstance tough = t.getAttribute(Attribute.ARMOR_TOUGHNESS);
        double armor = a == null ? 0 : a.getValue();
        double tg = tough == null ? 0 : tough.getValue();
        if (armor <= 0) return dmg;
        double reduced = armor * (1.0 - clamp(0.15 * level, 0.0, 1.0));
        double normal = 1.0 - armorFactor(armor, tg, dmg) / 25.0;
        double breached = 1.0 - armorFactor(reduced, tg, dmg) / 25.0;
        if (normal <= 0.0) return dmg;
        return dmg * breached / normal;
    }

    // =====================================================================
    //  ЕТАП 2: побічні ефекти (лише якщо удар не скасовано)
    // =====================================================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttackApplied(EntityDamageByEntityEvent e) {
        Ctx c = pending;
        if (c == null || c.event != e) return;
        pending = null;

        FileConfiguration cfg = plugin.getConfig();
        Player p = c.player;
        LivingEntity t = c.target;

        if (cfg.getBoolean("effects.hit-particles", true)) {
            plugin.fx().particle(t.getLocation().add(0, t.getHeight() * 0.6, 0), Particle.CRIT, 3, 0.2, 0.2, 0.2, 0.05);
        }
        if (cfg.getBoolean("effects.attack-bar", true)) startChargeBar(p, c.speed);
        if (c.type == null) return;

        applyEnchantEffects(c);
        if (c.smash) smashEffects(c);
        if (c.type == WeaponType.MACE) return; // пасивка булави — лазіння по стінах (WallClimbManager)

        boolean knocked = false;
        if (c.type == WeaponType.AXE && cfg.getBoolean("abilities.axe", true)) knocked = consumeMark(c);

        String key = c.type.configKey();
        if (!cfg.getBoolean("abilities." + key, true)) return;
        if (c.strength < cfg.getDouble("passive.min-charge", 0.5)) return;
        int need = Math.max(1, cfg.getInt("passive.hits-required", 3));
        int n = counters.merge(p.getUniqueId(), 1, Integer::sum);
        if (n < need) return;
        counters.put(p.getUniqueId(), 0);

        switch (c.type) {
            case SWORD -> swordSpecial(c, e);
            case AXE -> { if (!knocked) axeSpecial(c); }
            default -> { }
        }
    }

    private void applyEnchantEffects(Ctx c) {
        ItemStack w = c.weapon;
        LivingEntity t = c.target;
        Player p = c.player;

        // Fire Aspect: меч і булава
        if (c.type != WeaponType.AXE) {
            int fire = w.getEnchantmentLevel(Enchantment.FIRE_ASPECT);
            if (fire > 0) t.setFireTicks(Math.max(t.getFireTicks(), 80 * fire));
        }
        // Bane of Arthropods: уповільнення
        int bane = w.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS);
        if (bane > 0 && ARTHROPOD.contains(t.getType())) {
            int dur = 20 + ThreadLocalRandom.current().nextInt(10 * bane);
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur, 3));
        }
        // Knockback: лише меч
        if (c.type == WeaponType.SWORD) {
            int kb = w.getEnchantmentLevel(Enchantment.KNOCKBACK);
            if (kb > 0) {
                Vector dir = p.getLocation().getDirection().setY(0);
                if (dir.lengthSquared() > 1.0e-6) {
                    Vector push = dir.normalize().multiply(0.5 * kb);
                    // наступного тіку, щоб ванільне відкидання не перезаписало нашу швидкість
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (t.isValid() && !t.isDead()) t.setVelocity(t.getVelocity().add(push));
                    });
                }
            }
        }
    }

    private void smashEffects(Ctx c) {
        Player p = c.player;
        Fx fx = plugin.fx();
        Location l = c.target.getLocation();
        fx.sound(l, c.fall > 5.0 ? "item.mace.smash_ground_heavy" : "item.mace.smash_ground", 1.0f, 1.0f);
        fx.particle(l.clone().add(0, 0.5, 0), Particle.EXPLOSION, 1, 0, 0, 0, 0);
        fx.ring(l, Particle.CLOUD, 12, 0.25);
        p.setFallDistance(0f); // як у ваніллі: після smash немає шкоди від падіння

        int wb = c.weapon.getEnchantmentLevel(Enchantment.WIND_BURST);
        if (wb > 0) {
            FileConfiguration cfg = plugin.getConfig();
            double h = cfg.getDouble("mace.wind-burst-base-height", 7.0)
                    + cfg.getDouble("mace.wind-burst-height-step", 4.0) * (wb - 1);
            double vy = Weapons.velocityForHeight(h);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline() || p.isDead()) return;
                Vector v = p.getVelocity();
                p.setVelocity(new Vector(v.getX(), vy, v.getZ()));
                p.setFallDistance(0f);
                fx.sound(p.getLocation(), "entity.wind_charge.wind_burst", 1.0f, 1.0f);
                fx.particle(p.getLocation(), Particle.CLOUD, 12, 0.3, 0.1, 0.3, 0.05);
            });
        }
    }

    // ------------------------------------------------------------------ меч
    private void swordSpecial(Ctx c, EntityDamageByEntityEvent e) {
        FileConfiguration cfg = plugin.getConfig();
        Player p = c.player;
        LivingEntity main = c.target;
        double r = clamp(cfg.getDouble("sword.splash-radius", 3.0), 0.5, 16.0);
        double mult = clamp(cfg.getDouble("sword.splash-damage-multiplier", 0.6), 0.0, 3.0);
        int sweep = c.weapon.getEnchantmentLevel(Enchantment.SWEEPING_EDGE);
        double ratio = sweep / (sweep + 1.0);
        if (mult < 1.0) mult = mult + (1.0 - mult) * ratio;
        final double amount = e.getDamage() * mult;
        int max = Math.max(1, cfg.getInt("sword.max-targets", 10));
        boolean hitPlayers = cfg.getBoolean("sword.hit-players", true);

        List<LivingEntity> victims = new ArrayList<>();
        Location center = main.getLocation();
        for (Entity en : main.getNearbyEntities(r, r, r)) {
            if (victims.size() >= max) break;
            if (!(en instanceof LivingEntity le) || !validSplash(p, le, hitPlayers)) continue;
            if (le.getLocation().distanceSquared(center) > r * r) continue;
            victims.add(le);
        }
        for (LivingEntity v : victims) {
            plugin.runInternal(() -> v.damage(amount, p));
        }
        Fx fx = plugin.fx();
        fx.ring(center, Particle.CLOUD, 16, 0.35);
        fx.particle(center.clone().add(0, 1.0, 0), Particle.SWEEP_ATTACK, 1, 0, 0, 0, 0);
        fx.sound(center, "entity.player.attack.sweep", 1.0f, 0.8f);
        fx.sound(center, "entity.generic.explode", 0.4f, 1.5f);
    }

    private boolean validSplash(Player p, LivingEntity le, boolean hitPlayers) {
        if (le.equals(p) || le.isDead() || !le.isValid() || le instanceof ArmorStand) return false;
        if (le.hasMetadata("NPC")) return false;
        if (le instanceof Tameable tm && p.getUniqueId().equals(tm.getOwnerUniqueId())) return false;
        if (le instanceof Player other) {
            if (!hitPlayers) return false;
            GameMode gm = other.getGameMode();
            if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return false;
        }
        return true;
    }

    // --------------------------------------------------------------- сокира
    private void axeSpecial(Ctx c) {
        FileConfiguration cfg = plugin.getConfig();
        LivingEntity t = c.target;
        if (!canLaunch(c.player, t)) return;

        double h = Math.min(cfg.getDouble("axe.launch-height", 5.0),
                Math.min(cfg.getDouble("axe.max-launch-height", 10.0), 40.0));
        if (h <= 0) return;
        final double vy = Weapons.velocityForHeight(h);

        long now = System.currentTimeMillis();
        launchImmunity.values().removeIf(v -> v < now);
        launchImmunity.put(t.getUniqueId(), now + (long) (cfg.getDouble("axe.target-immunity-seconds", 5.0) * 1000));
        // мітка: наступний удар цього гравця по цій цілі відкине її далеко від нього
        marks.values().removeIf(m -> m.until() < now);
        marks.put(t.getUniqueId(), new Mark(c.player.getUniqueId(),
                now + (long) (cfg.getDouble("axe.knockback-mark-seconds", 10.0) * 1000)));

        Fx fx = plugin.fx();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!t.isValid() || t.isDead()) return;
            t.setVelocity(new Vector(0, vy, 0));
            Location l = t.getLocation();
            fx.sound(l, "item.mace.smash_ground_heavy", 1.0f, 1.0f);
            fx.sound(l, "entity.iron_golem.attack", 1.0f, 0.7f);
            fx.particle(l.clone().add(0, 0.5, 0), Particle.EXPLOSION, 1, 0, 0, 0, 0);
            fx.ring(l, Particle.CLOUD, 14, 0.3);
        });
    }

    private boolean canLaunch(Player attacker, LivingEntity t) {
        FileConfiguration cfg = plugin.getConfig();
        if (t.hasMetadata("NPC") || t instanceof ArmorStand || t.isInsideVehicle()) return false;
        for (String s : cfg.getStringList("axe.excluded-entities")) {
            if (t.getType().name().equalsIgnoreCase(s)) return false;
        }
        if (t instanceof Player tp) {
            if (!cfg.getBoolean("axe.launch-players", true)) return false;
            GameMode gm = tp.getGameMode();
            if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return false;
            if (tp.isFlying() || tp.isGliding()) return false;
        }
        Long until = launchImmunity.get(t.getUniqueId());
        return until == null || System.currentTimeMillis() >= until;
    }

    /** Наступний удар сокирою по підкинутій цілі відкидає її від атакуючого. */
    private boolean consumeMark(Ctx c) {
        LivingEntity t = c.target;
        Player p = c.player;
        Mark m = marks.get(t.getUniqueId());
        if (m == null) return false;
        if (System.currentTimeMillis() > m.until()) {
            marks.remove(t.getUniqueId());
            return false;
        }
        if (!m.attacker().equals(p.getUniqueId())) return false;
        marks.remove(t.getUniqueId());

        Vector dir = t.getLocation().toVector().subtract(p.getLocation().toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 1.0e-6) dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1.0e-6) return false;
        double dist = clamp(plugin.getConfig().getDouble("axe.knockback-distance", 5.0), 0.5, 20.0);
        double vy = 0.5;
        double hv = Weapons.horizontalSpeedFor(dist, vy);
        final Vector vel = dir.normalize().multiply(hv);
        vel.setY(vy);
        Fx fx = plugin.fx();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!t.isValid() || t.isDead()) return;
            t.setVelocity(vel);
            Location l = t.getLocation();
            fx.sound(l, "entity.player.attack.knockback", 1.0f, 0.8f);
            fx.particle(l.clone().add(0, 1.0, 0), Particle.EXPLOSION, 1, 0, 0, 0, 0);
        });
        return true;
    }

    /** Невелика плашка над хотбаром: заряд удару / «Атака готова». */
    private void startChargeBar(Player p, double speed) {
        UUID id = p.getUniqueId();
        BukkitTask old = bars.remove(id);
        if (old != null) old.cancel();
        final double period = 20.0 / Math.max(0.1, speed);
        final int startTick = Bukkit.getCurrentTick();
        final BukkitTask[] self = new BukkitTask[1];
        self[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline()) {
                self[0].cancel();
                bars.remove(id);
                return;
            }
            double s = clamp((Bukkit.getCurrentTick() - startTick + 0.5) / period, 0.0, 1.0);
            if (s >= 1.0) {
                p.sendActionBar(Component.text("⚔ Атака готова", NamedTextColor.GREEN));
                self[0].cancel();
                bars.remove(id);
                return;
            }
            int filled = (int) Math.round(s * 10);
            String bar = "■".repeat(filled) + "□".repeat(10 - filled);
            p.sendActionBar(Component.text("Атака ", NamedTextColor.GRAY)
                    .append(Component.text(bar, NamedTextColor.YELLOW)));
        }, 0L, 2L);
        bars.put(id, self[0]);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
