package ua.brawler.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ua.brawler.BrawlerPlugin;

import java.util.List;

public final class Commands implements CommandExecutor, TabCompleter {
    private final BrawlerPlugin plugin;

    public Commands(BrawlerPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("brawler") && args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("brawler.admin")) {
                plugin.msg().send(sender, "no-permission");
                return true;
            }
            plugin.reloadAll();
            plugin.msg().send(sender, "reloaded");
            return true;
        }
        if (!(sender instanceof Player p)) {
            plugin.msg().send(sender, "player-only");
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("class")) {
            plugin.classMenu().open(p);
            return true;
        }
        if (!plugin.data().get(p.getUniqueId()).isBrawler()) {
            plugin.msg().send(p, "not-brawler");
            return true;
        }
        plugin.brawlerMenu().open(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("brawler.admin")) return List.of("reload");
        return List.of();
    }
}
