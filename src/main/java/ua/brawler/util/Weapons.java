package ua.brawler.util;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collection;

/** Характеристики зброї (шкода, швидкість атаки) та допоміжні функції. */
public final class Weapons {
    private static final double[] SWORD_DMG = {4, 4, 5, 6, 7, 8};
    private static final double[] AXE_DMG = {7, 7, 9, 9, 9, 10};
    private static final double[] AXE_SPD = {0.8, 1.0, 0.8, 0.9, 1.0, 1.0};

    private Weapons() {}

    public static boolean isEmpty(ItemStack i) { return i == null || i.getType().isAir(); }

    public static boolean isOffhandAllowed(Material m) {
        return m == Material.BOW || m == Material.SHIELD || m == Material.TOTEM_OF_UNDYING;
    }

    private static int tier(String n) {
        if (n.startsWith("WOODEN")) return 0;
        if (n.startsWith("GOLDEN")) return 1;
        if (n.startsWith("STONE")) return 2;
        if (n.startsWith("IRON")) return 3;
        if (n.startsWith("DIAMOND")) return 4;
        if (n.startsWith("NETHERITE")) return 5;
        return 2;
    }

    /** Повна шкода зброї (включно з базовою 1.0 гравця). */
    public static double attackDamage(ItemStack item) {
        WeaponType t = WeaponType.of(item.getType());
        if (t == null) return 1.0;
        String n = item.getType().name();
        double table = switch (t) {
            case MACE -> 6.0;
            case SWORD -> SWORD_DMG[tier(n)];
            case AXE -> AXE_DMG[tier(n)];
        };
        double meta = metaValue(item, Attribute.ATTACK_DAMAGE, 1.0);
        return Double.isNaN(meta) ? table : Math.max(0.0, meta);
    }

    /** Швидкість атаки зброї (атак/сек). */
    public static double attackSpeed(ItemStack item) {
        WeaponType t = WeaponType.of(item.getType());
        if (t == null) return 4.0;
        String n = item.getType().name();
        double table = switch (t) {
            case MACE -> 0.6;
            case SWORD -> 1.6;
            case AXE -> AXE_SPD[tier(n)];
        };
        double meta = metaValue(item, Attribute.ATTACK_SPEED, 4.0);
        return Double.isNaN(meta) ? table : Math.max(0.1, meta);
    }

    /** Якщо на предметі є власні модифікатори атрибута — повертає base+сума, інакше NaN. */
    private static double metaValue(ItemStack item, Attribute attr, double base) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return Double.NaN;
        Collection<AttributeModifier> mods = meta.getAttributeModifiers(attr);
        if (mods == null || mods.isEmpty()) return Double.NaN;
        double add = 0;
        for (AttributeModifier m : mods) {
            if (m.getOperation() == AttributeModifier.Operation.ADD_NUMBER) add += m.getAmount();
        }
        return base + add;
    }

    /** Початкова вертикальна швидкість (блоків/тік), щоб піднятися приблизно на height блоків. */
    public static double velocityForHeight(double height) {
        double lo = 0.2, hi = 8.0;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (peak(mid) >= height) hi = mid; else lo = mid;
        }
        return hi;
    }

    public static boolean handsEmpty(org.bukkit.entity.Player p) {
        return isEmpty(p.getInventory().getItemInMainHand()) && isEmpty(p.getInventory().getItemInOffHand());
    }

    /** Скільки тіків сутність у повітрі після вертикального імпульсу vy (повернення на початкову висоту). */
    public static int airTicks(double vy) {
        double y = 0, v = vy;
        int t = 0;
        do {
            y += v;
            v = (v - 0.08) * 0.98;
            t++;
        } while (y > 0 && t < 300);
        return t;
    }

    /** Горизонтальна швидкість (блоків/тік), щоб при вертикальному імпульсі vy пролетіти dist блоків. */
    public static double horizontalSpeedFor(double dist, double vy) {
        int t = airTicks(vy);
        double sum = (1.0 - Math.pow(0.91, t)) / 0.09;
        return dist / sum;
    }

    private static double peak(double v0) {
        double y = 0, max = 0, vel = v0;
        for (int i = 0; i < 600 && vel > 0; i++) {
            y += vel;
            if (y > max) max = y;
            vel = (vel - 0.08) * 0.98;
        }
        return max;
    }
}
