package ua.brawler.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import ua.brawler.BrawlerPlugin;
import ua.brawler.data.PlayerData;
import ua.brawler.util.Items;
import ua.brawler.util.WeaponType;
import ua.brawler.util.Weapons;

import java.util.Map;
import java.util.UUID;

/**
 * GUI /brawler з двома спеціальними слотами.
 * ВСІ кліки скасовуються, а переміщення предметів виконуються вручну коду нижче —
 * так неможливі дюпи через shift-клік, drag, подвійний клік, number-key тощо.
 * Дані гравця синхронізуються після кожної зміни.
 */
public final class BrawlerMenu implements Listener {
    public static final int MAIN = 11;
    public static final int OFF = 15;

    public static final class Holder implements InventoryHolder {
        private Inventory inv;
        @Override
        public Inventory getInventory() { return inv; }
    }

    private final BrawlerPlugin plugin;

    public BrawlerMenu(BrawlerPlugin plugin) { this.plugin = plugin; }

    // ------------------------------------------------------------ відкриття

    public void open(Player p) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof Holder) p.closeInventory();
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 27, Component.text("Спеціальні слоти Кулачника"));
        h.inv = inv;
        for (int i = 0; i < inv.getSize(); i++) {
            if (i == MAIN || i == OFF) continue;
            inv.setItem(i, Items.icon(Material.GRAY_STAINED_GLASS_PANE, "&7 ", false));
        }
        inv.setItem(MAIN - 9, Items.icon(Material.IRON_SWORD, "&6Основна рука", false,
                "&7Дозволено: &fмеч, сокира, булава",
                "&7Кулаки отримають її характеристики."));
        inv.setItem(OFF - 9, Items.icon(Material.SHIELD, "&6Допоміжна рука", false,
                "&7Дозволено: &fлук, щит, тотем безсмертя",
                "&7Щит/лук активуються клавішею &eF&7",
                "&7(обидві руки порожні)."));
        render(inv, plugin.data().get(p.getUniqueId()));
        p.openInventory(inv);
        plugin.fx().sound(p.getLocation(), "block.ender_chest.open", 0.6f, 1.2f);
    }

    private void render(Inventory inv, PlayerData d) {
        inv.setItem(MAIN, d.getMainItem() == null ? null : d.getMainItem().clone());
        inv.setItem(OFF, d.getOffItem() == null ? null : d.getOffItem().clone());
    }

    /** Оновити відкрите GUI з даних (наприклад, після використання тотема). */
    public void refresh(Player p) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof Holder h) {
            render(h.inv, plugin.data().get(p.getUniqueId()));
        }
    }

    // ------------------------------------------------------------ валідація

    private static boolean valid(int slot, ItemStack it) {
        if (Weapons.isEmpty(it) || it.getAmount() != 1) return false;
        if (slot == MAIN) return WeaponType.of(it.getType()) != null;
        if (slot == OFF) return Weapons.isOffhandAllowed(it.getType());
        return false;
    }

    // ------------------------------------------------------------ кліки

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int raw = e.getRawSlot();
        if (raw < 0) return;
        Inventory top = h.inv;
        ClickType ct = e.getClick();

        if (raw < top.getSize()) {
            if (raw != MAIN && raw != OFF) return;
            handleTop(p, top, raw, e, ct);
        } else if (ct == ClickType.SHIFT_LEFT || ct == ClickType.SHIFT_RIGHT) {
            handleShiftFromBottom(p, top, e);
        } else {
            return;
        }
        sync(p, top);
        p.updateInventory();
    }

    private void handleTop(Player p, Inventory top, int slot, InventoryClickEvent e, ClickType ct) {
        ItemStack cur = top.getItem(slot);
        switch (ct) {
            case LEFT, RIGHT -> {
                ItemStack cursor = p.getItemOnCursor();
                if (Weapons.isEmpty(cursor)) {
                    if (!Weapons.isEmpty(cur)) {
                        p.setItemOnCursor(cur.clone());
                        top.setItem(slot, null);
                    }
                } else {
                    if (!valid(slot, cursor)) return;
                    ItemStack placed = cursor.clone();
                    p.setItemOnCursor(Weapons.isEmpty(cur) ? null : cur.clone());
                    top.setItem(slot, placed);
                }
            }
            case SHIFT_LEFT, SHIFT_RIGHT -> {
                if (Weapons.isEmpty(cur)) return;
                Map<Integer, ItemStack> left = p.getInventory().addItem(cur.clone());
                if (left.isEmpty()) top.setItem(slot, null);
            }
            case NUMBER_KEY -> {
                int btn = e.getHotbarButton();
                if (btn < 0 || btn > 8) return;
                ItemStack hot = p.getInventory().getItem(btn);
                swapWithPlayerSlot(p, top, slot, cur, hot, btn);
            }
            case SWAP_OFFHAND -> {
                ItemStack hot = p.getInventory().getItemInOffHand();
                if (Weapons.isEmpty(hot) && Weapons.isEmpty(cur)) return;
                if (!Weapons.isEmpty(hot) && !valid(slot, hot)) return;
                top.setItem(slot, Weapons.isEmpty(hot) ? null : hot.clone());
                p.getInventory().setItemInOffHand(Weapons.isEmpty(cur) ? null : cur.clone());
            }
            default -> { /* drop, double-click, middle, creative — ігноруємо */ }
        }
    }

    private void swapWithPlayerSlot(Player p, Inventory top, int slot, ItemStack cur, ItemStack hot, int hotbar) {
        if (Weapons.isEmpty(hot)) {
            if (Weapons.isEmpty(cur)) return;
            p.getInventory().setItem(hotbar, cur.clone());
            top.setItem(slot, null);
        } else {
            if (!valid(slot, hot)) return;
            ItemStack incoming = hot.clone();
            p.getInventory().setItem(hotbar, Weapons.isEmpty(cur) ? null : cur.clone());
            top.setItem(slot, incoming);
        }
    }

    private void handleShiftFromBottom(Player p, Inventory top, InventoryClickEvent e) {
        ItemStack it = e.getCurrentItem();
        if (Weapons.isEmpty(it) || e.getClickedInventory() != p.getInventory()) return;
        int target;
        if (valid(MAIN, it) && Weapons.isEmpty(top.getItem(MAIN))) target = MAIN;
        else if (valid(OFF, it) && Weapons.isEmpty(top.getItem(OFF))) target = OFF;
        else return;
        top.setItem(target, it.clone());
        p.getInventory().setItem(e.getSlot(), null);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        if (e.getPlayer() instanceof Player p) sync(p, h.inv);
    }

    // ------------------------------------------------------------ синхронізація

    public void syncIfOpen(Player p) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof Holder h) sync(p, h.inv);
    }

    public void syncAllOpen() {
        for (Player p : Bukkit.getOnlinePlayers()) syncIfOpen(p);
    }

    private void sync(Player p, Inventory inv) {
        UUID id = p.getUniqueId();
        PlayerData d = plugin.data().get(id);
        ItemStack m = clean(p, inv, MAIN);
        ItemStack o = clean(p, inv, OFF);
        boolean mainChanged = !same(d.getMainItem(), m);
        d.setMainItem(m);
        d.setOffItem(o);
        if (mainChanged) plugin.combat().resetCounter(id);
        plugin.data().save(id);
    }

    /** Захисна перевірка: некоректний предмет у слоті повертається гравцю. */
    private ItemStack clean(Player p, Inventory inv, int slot) {
        ItemStack it = inv.getItem(slot);
        if (Weapons.isEmpty(it)) return null;
        if (valid(slot, it)) return it.clone();
        inv.setItem(slot, null);
        Items.give(p, it);
        return null;
    }

    private static boolean same(ItemStack a, ItemStack b) {
        if (a == null || b == null) return a == b;
        return a.equals(b);
    }
}
