package ua.brawler.listener;

import org.bukkit.EntityEffect;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ua.brawler.BrawlerPlugin;
import ua.brawler.data.PlayerData;

/**
 * Тотем безсмертя зі спец. слота. Працює за ванільною механікою: при смертельній шкоді
 * здоров'я = 1, прибираються ефекти, видаються Regeneration II (45с), Absorption II (5с),
 * Fire Resistance (40с), програється анімація тотема. Тотем витрачається зі слота.
 */
public final class TotemListener implements Listener {
    private final BrawlerPlugin plugin;

    public TotemListener(BrawlerPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFatalDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        DamageCause cause = e.getCause();
        // як у ваніллі, тотем не рятує від /kill та порожнечі
        if (cause == DamageCause.VOID || cause == DamageCause.KILL || cause == DamageCause.SUICIDE) return;
        if (p.getHealth() - e.getFinalDamage() > 0.0) return;

        PlayerData d = plugin.data().get(p.getUniqueId());
        if (!d.isBrawler() || !plugin.getConfig().getBoolean("abilities.totem", true)) return;
        ItemStack off = d.getOffItem();
        if (off == null || off.getType() != Material.TOTEM_OF_UNDYING) return;
        // якщо справжній тотем у руці — спрацює ванільний
        if (p.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
                || p.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING) return;

        EntityResurrectEvent res = new EntityResurrectEvent(p, EquipmentSlot.OFF_HAND);
        if (!res.callEvent()) return;

        // тотем витрачається
        d.setOffItem(null);
        plugin.data().save(p.getUniqueId());
        plugin.brawlerMenu().refresh(p);

        e.setCancelled(true);
        p.setHealth(1.0);
        p.clearActivePotionEffects();
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 900, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 800, 0));
        playTotemEffect(p);
        plugin.fx().particle(p.getLocation().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 30, 0.4, 0.6, 0.4, 0.4);
        plugin.msg().bar(p, "totem-used");
    }

    /** Ванільна анімація тотема. Назва константи різниться між збірками API — пробуємо обидві. */
    private void playTotemEffect(Player p) {
        for (String name : new String[]{"TOTEM_RESURRECT", "PROTECTED_FROM_DEATH"}) {
            try {
                p.playEffect(EntityEffect.valueOf(name));
                return;
            } catch (IllegalArgumentException ignored) {
                // пробуємо наступну назву
            }
        }
    }
}
