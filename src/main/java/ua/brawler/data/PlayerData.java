package ua.brawler.data;

import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Збережені дані гравця: клас, предмети спец. слотів, перезарядки. */
public final class PlayerData {
    private final UUID id;
    private boolean brawler;
    private ItemStack mainItem;
    private ItemStack offItem;
    private final Map<String, Long> cooldowns = new HashMap<>();

    public PlayerData(UUID id) { this.id = id; }

    public UUID getId() { return id; }
    public boolean isBrawler() { return brawler; }
    public void setBrawler(boolean brawler) { this.brawler = brawler; }
    public ItemStack getMainItem() { return mainItem; }
    public void setMainItem(ItemStack mainItem) { this.mainItem = mainItem; }
    public ItemStack getOffItem() { return offItem; }
    public void setOffItem(ItemStack offItem) { this.offItem = offItem; }
    public Map<String, Long> getCooldowns() { return cooldowns; }
}
