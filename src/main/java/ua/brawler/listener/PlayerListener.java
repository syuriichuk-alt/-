package ua.brawler.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.player.PlayerQuitEvent;
import ua.brawler.BrawlerPlugin;
import ua.brawler.data.PlayerData;

public final class PlayerListener implements Listener {
    private final BrawlerPlugin plugin;

    public PlayerListener(BrawlerPlugin plugin) { this.plugin = plugin; }

    public void handleJoin(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        plugin.sprint().clearModifier(p); // на випадок краху сервера
        if (d.isBrawler() && p.isSprinting()) plugin.sprint().begin(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { handleJoin(e.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.brawlerMenu().syncIfOpen(p);
        plugin.cleanup(p);
    }

    /** Предмети зі спец. слотів випадають на місці смерті (якщо не ввімкнено keepInventory). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        PlayerData d = plugin.data().get(p.getUniqueId());
        if (!d.isBrawler()) return;
        p.closeInventory(); // синхронізує відкрите GUI з даними
        plugin.combat().resetCounter(p.getUniqueId());
        if (e.getKeepInventory()) return;
        ItemStack m = d.getMainItem();
        ItemStack o = d.getOffItem();
        if (m == null && o == null) return;
        if (m != null) e.getDrops().add(m.clone());
        if (o != null) e.getDrops().add(o.clone());
        d.setMainItem(null);
        d.setOffItem(null);
        plugin.data().save(p.getUniqueId());
    }
}
