package lama.project;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
public final class YamatoCommand implements CommandExecutor, TabCompleter {

    private final YamatoPlugin plugin;
    private final YamatoItem item;

    public YamatoCommand(YamatoPlugin plugin, YamatoItem item) {
        this.plugin = plugin;
        this.item = item;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration cfg = plugin.getConfig();

        if (!sender.hasPermission("yamato.admin")) {
            sender.sendMessage(Text.cfg(cfg, "messages.command.no-permission", "<red>У тебя нет прав на эту команду."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Text.cfg(cfg, "messages.command.usage", "<gray>Использование: <white>/yamato give [player] <gray>или <white>/yamato reload"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadAndRefreshAll();
            cfg = plugin.getConfig();
            sender.sendMessage(Text.cfg(cfg, "messages.command.reloaded", "<green>YamatoPlugin перезагружен. Конфиг применён."));
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            Player target;
            if (args.length >= 2) {
                target = Bukkit.getPlayerExact(args[1]);
            } else {
                target = (sender instanceof Player p) ? p : null;
            }

            if (target == null) {
                sender.sendMessage(Text.cfg(cfg, "messages.command.player-not-found", "<red>Игрок не найден."));
                return true;
            }

            ItemStack yamato = item.create(cfg);
            item.refreshLore(yamato, cfg);
            target.getInventory().addItem(yamato);
            sender.sendMessage(Text.cfg(cfg, "messages.command.give-sender", "<green>Yamato выдан игроку <yellow>{player}<green>.", "{player}", target.getName()));
            if (!target.equals(sender)) {
                target.sendMessage(Text.cfg(cfg, "messages.command.give-target", "<dark_purple>Ты получил Yamato."));
            }
            return true;
        }

        sender.sendMessage(Text.cfg(cfg, "messages.command.usage", "<gray>Использование: <white>/yamato give [player] <gray>или <white>/yamato reload"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("yamato.admin")) return out;

        if (args.length == 1) {
            out.add("give");
            out.add("reload");
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        return out;
    }
}
