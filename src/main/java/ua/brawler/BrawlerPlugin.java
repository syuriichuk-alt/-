package ua.brawler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ua.brawler.command.Commands;
import ua.brawler.data.DataManager;
import ua.brawler.data.PlayerData;
import ua.brawler.gui.BrawlerMenu;
import ua.brawler.gui.ClassMenu;
import ua.brawler.listener.AbilityListener;
import ua.brawler.listener.CombatListener;
import ua.brawler.listener.PlayerListener;
import ua.brawler.listener.TotemListener;
import ua.brawler.listener.WallClimbManager;
import ua.brawler.sprint.SprintManager;
import ua.brawler.util.Fx;
import ua.brawler.util.Items;
import ua.brawler.util.Msg;

import java.util.UUID;

public final class BrawlerPlugin extends JavaPlugin {
    private DataManager data;
    private CooldownManager cooldowns;
    private Msg msg;
    private Fx fx;
    private SprintManager sprint;
    private CombatListener combat;
    private AbilityListener abilities;
    private BrawlerMenu brawlerMenu;
    private ClassMenu classMenu;
    private PlayerListener playerListener;
    private WallClimbManager wall;
    private int internalDepth = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        data = new DataManager(this);
        cooldowns = new CooldownManager(this);
        msg = new Msg(this);
        fx = new Fx(this);
        sprint = new SprintManager(this);
        combat = new CombatListener(this);
        abilities = new AbilityListener(this);
        brawlerMenu = new BrawlerMenu(this);
        classMenu = new ClassMenu(this);
        playerListener = new PlayerListener(this);
        wall = new WallClimbManager(this);

        Bukkit.getPluginManager().registerEvents(sprint, this);
        Bukkit.getPluginManager().registerEvents(combat, this);
        Bukkit.getPluginManager().registerEvents(abilities, this);
        Bukkit.getPluginManager().registerEvents(new TotemListener(this), this);
        Bukkit.getPluginManager().registerEvents(brawlerMenu, this);
        Bukkit.getPluginManager().registerEvents(classMenu, this);
        Bukkit.getPluginManager().registerEvents(playerListener, this);
        Bukkit.getPluginManager().registerEvents(wall, this);

        Commands cmds = new Commands(this);
        getCommand("class").setExecutor(cmds);
        getCommand("brawler").setExecutor(cmds);
        getCommand("brawler").setTabCompleter(cmds);

        sprint.start();
        wall.start();
        for (Player p : Bukkit.getOnlinePlayers()) playerListener.handleJoin(p);
    }

    @Override
    public void onDisable() {
        if (brawlerMenu != null) brawlerMenu.syncAllOpen();
        if (sprint != null) sprint.shutdown();
        if (wall != null) wall.shutdown();
        if (data != null) data.saveAll();
    }

    public void reloadAll() {
        reloadConfig();
        abilities.reloadSettings();
        sprint.start();
    }

    /** Повне очищення тимчасових даних гравця (вихід із сервера). */
    public void cleanup(Player p) {
        UUID id = p.getUniqueId();
        sprint.stop(p);
        wall.stop(p);
        combat.cleanup(id);
        abilities.cleanup(id);
        data.unload(id);
    }

    /** Викликається після вибору/скидання класу. */
    public void onClassChanged(Player p) {
        UUID id = p.getUniqueId();
        sprint.stop(p);
        wall.stop(p);
        combat.cleanup(id);
        abilities.cleanup(id);
        PlayerData d = data.get(id);
        if (d.isBrawler() && p.isSprinting()) sprint.begin(p);
    }

    /** Повертає предмети зі спец. слотів гравцю (при скиданні класу). */
    public void returnItems(Player p) {
        PlayerData d = data.get(p.getUniqueId());
        ItemStack m = d.getMainItem();
        ItemStack o = d.getOffItem();
        d.setMainItem(null);
        d.setOffItem(null);
        data.save(p.getUniqueId());
        Items.give(p, m);
        Items.give(p, o);
    }

    /** Виконує шкоду, що не має повторно оброблятись нашими обробниками (захист від рекурсії). */
    public void runInternal(Runnable r) {
        internalDepth++;
        try {
            r.run();
        } finally {
            internalDepth--;
        }
    }

    public boolean isInternal() { return internalDepth > 0; }

    public DataManager data() { return data; }
    public CooldownManager cooldowns() { return cooldowns; }
    public Msg msg() { return msg; }
    public Fx fx() { return fx; }
    public SprintManager sprint() { return sprint; }
    public CombatListener combat() { return combat; }
    public AbilityListener abilities() { return abilities; }
    public BrawlerMenu brawlerMenu() { return brawlerMenu; }
    public ClassMenu classMenu() { return classMenu; }
}
