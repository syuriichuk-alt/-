package ua.brawler.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import ua.brawler.BrawlerPlugin;
import ua.brawler.data.PlayerData;
import ua.brawler.util.Items;

/** GUI /class: вибір класу. Усі кліки скасовуються, предмети взяти неможливо. */
public final class ClassMenu implements Listener {
    private static final int SLOT_BRAWLER = 11;
    private static final int SLOT_RESET = 15;

    public static final class Holder implements InventoryHolder {
        private Inventory inv;
        @Override
        public Inventory getInventory() { return inv; }
    }

    private final BrawlerPlugin plugin;

    public ClassMenu(BrawlerPlugin plugin) { this.plugin = plugin; }

    public void open(Player p) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 27, Component.text("Вибір класу"));
        h.inv = inv;
        render(inv, p);
        p.openInventory(inv);
    }

    private void render(Inventory inv, Player p) {
        boolean sel = plugin.data().get(p.getUniqueId()).isBrawler();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, Items.icon(Material.GRAY_STAINED_GLASS_PANE, "&7 ", false));
        }
        inv.setItem(SLOT_BRAWLER, Items.icon(Material.NETHERITE_INGOT, "&6&lКулачник", sel,
                "&7Б'є кулаками, але з характеристиками",
                "&7зброї зі спеціального слота (/brawler).",
                "&7",
                "&eПасивки:&7 розгін під час бігу, особливі",
                "&7удари булави/меча/сокири, щит, лук, тотем.",
                "&7",
                sel ? "&aВи вже обрали цей клас" : "&aНатисніть, щоб обрати"));
        inv.setItem(SLOT_RESET, Items.icon(Material.BARRIER, "&cСкинути клас", false,
                "&7Прибирає клас і повертає предмети",
                "&7зі спец. слотів у ваш інвентар."));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != h.inv) return;
        int slot = e.getRawSlot();
        if (slot == SLOT_BRAWLER) select(p);
        else if (slot == SLOT_RESET) reset(p);
        else return;
        render(h.inv, p);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    private void select(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (d.isBrawler()) {
            plugin.msg().send(p, "class-already");
            return;
        }
        d.setBrawler(true);
        plugin.data().save(p.getUniqueId());
        plugin.msg().send(p, "class-selected");
        plugin.fx().sound(p.getLocation(), "entity.player.levelup", 0.7f, 1.2f);
        plugin.onClassChanged(p);
    }

    private void reset(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (!d.isBrawler()) {
            plugin.msg().send(p, "no-class");
            return;
        }
        plugin.returnItems(p);
        d.setBrawler(false);
        plugin.data().save(p.getUniqueId());
        plugin.msg().send(p, "class-reset");
        plugin.onClassChanged(p);
    }
}
