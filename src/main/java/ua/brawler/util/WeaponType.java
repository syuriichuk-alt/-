package ua.brawler.util;

import org.bukkit.Material;

public enum WeaponType {
    SWORD, AXE, MACE;

    public String configKey() { return name().toLowerCase(); }

    /** Повертає тип зброї для матеріалу або null, якщо це не меч/сокира/булава. */
    public static WeaponType of(Material m) {
        if (m == Material.MACE) return MACE;
        String n = m.name();
        if (n.endsWith("_SWORD")) return SWORD;
        if (n.endsWith("_AXE")) return AXE; // PICKAXE не підходить: закінчується на "KAXE"
        return null;
    }
}
