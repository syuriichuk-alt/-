package ua.brawler.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Items {
    private static final LegacyComponentSerializer LEG = LegacyComponentSerializer.legacyAmpersand();

    private Items() {}

    public static Component plain(String legacy) {
        return LEG.deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }

    public static ItemStack icon(Material m, String name, boolean glow, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(plain(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(plain(s));
        meta.lore(l);
        if (glow) meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    /** Віддає предмет гравцю; якщо немає місця — викидає під ноги. */
    public static void give(Player p, ItemStack item) {
        if (Weapons.isEmpty(item)) return;
        Map<Integer, ItemStack> left = p.getInventory().addItem(item.clone());
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }
}
