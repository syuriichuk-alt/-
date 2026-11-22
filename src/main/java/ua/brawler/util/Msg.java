package ua.brawler.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ua.brawler.BrawlerPlugin;

public final class Msg {
    private static final LegacyComponentSerializer LEG = LegacyComponentSerializer.legacyAmpersand();
    private final BrawlerPlugin plugin;

    public Msg(BrawlerPlugin plugin) { this.plugin = plugin; }

    public String raw(String key, String... pairs) {
        String s = plugin.getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < pairs.length; i += 2) s = s.replace("{" + pairs[i] + "}", pairs[i + 1]);
        return s;
    }

    public void send(CommandSender to, String key, String... pairs) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        to.sendMessage(LEG.deserialize(prefix + raw(key, pairs)));
    }

    public void bar(Player p, String key, String... pairs) {
        Component c = LEG.deserialize(raw(key, pairs));
        p.sendActionBar(c);
    }
}
