package ua.brawler.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import ua.brawler.BrawlerPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Завантаження/збереження даних гравців у plugins/BrawlerClass/data/UUID.yml. */
public final class DataManager {
    private final BrawlerPlugin plugin;
    private final File dir;
    private final Map<UUID, PlayerData> cache = new HashMap<>();

    public DataManager(BrawlerPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "data");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Не вдалося створити папку data");
        }
    }

    public PlayerData get(UUID id) {
        PlayerData d = cache.get(id);
        if (d == null) {
            d = load(id);
            cache.put(id, d);
        }
        return d;
    }

    private PlayerData load(UUID id) {
        PlayerData d = new PlayerData(id);
        File f = new File(dir, id + ".yml");
        if (!f.exists()) return d;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        d.setBrawler(y.getBoolean("brawler", false));
        d.setMainItem(decode(y.getString("main")));
        d.setOffItem(decode(y.getString("off")));
        ConfigurationSection cs = y.getConfigurationSection("cooldowns");
        if (cs != null) {
            for (String k : cs.getKeys(false)) d.getCooldowns().put(k, cs.getLong(k));
        }
        return d;
    }

    public void save(UUID id) {
        PlayerData d = cache.get(id);
        if (d == null) return;
        YamlConfiguration y = new YamlConfiguration();
        y.set("brawler", d.isBrawler());
        y.set("main", encode(d.getMainItem()));
        y.set("off", encode(d.getOffItem()));
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> en : d.getCooldowns().entrySet()) {
            if (en.getValue() > now) y.set("cooldowns." + en.getKey(), en.getValue());
        }
        try {
            y.save(new File(dir, id + ".yml"));
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Не вдалося зберегти дані " + id, ex);
        }
    }

    public void unload(UUID id) {
        save(id);
        cache.remove(id);
    }

    public void saveAll() {
        for (UUID id : new ArrayList<>(cache.keySet())) save(id);
    }

    private static String encode(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private ItemStack decode(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(s));
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Не вдалося прочитати збережений предмет", ex);
            return null;
        }
    }
}
