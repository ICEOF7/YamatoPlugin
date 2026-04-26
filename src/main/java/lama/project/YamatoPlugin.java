package lama.project;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
public final class YamatoPlugin extends JavaPlugin {

    public NamespacedKey KEY_IS_YAMATO;
    public NamespacedKey KEY_KILLS;

    private YamatoItem yamatoItem;
    private final Cooldowns cooldowns = new Cooldowns();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        KEY_IS_YAMATO = new NamespacedKey(this, "is_yamato");
        KEY_KILLS = new NamespacedKey(this, "kills");

        yamatoItem = new YamatoItem(KEY_IS_YAMATO, KEY_KILLS);

        Bukkit.getPluginManager().registerEvents(new YamatoListener(this, yamatoItem, cooldowns), this);

        PluginCommand cmd = getCommand("yamato");
        if (cmd != null) {
            YamatoCommand exec = new YamatoCommand(this, yamatoItem);
            cmd.setExecutor(exec);
            cmd.setTabCompleter(exec);
        }

        getLogger().info("YamatoPlugin enabled.");
    }

    public YamatoItem yamatoItem() {
        return yamatoItem;
    }

    public Cooldowns cooldowns() {
        return cooldowns;
    }

    public void reloadAndRefreshAll() {
        reloadConfig();
        FileConfiguration cfg = getConfig();

        for (Player p : Bukkit.getOnlinePlayers()) {
            refreshPlayerInventoryYamatoLore(p, cfg);
        }
    }

    public void refreshPlayerInventoryYamatoLore(Player p, FileConfiguration cfg) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (yamatoItem.isYamato(it)) yamatoItem.refreshLore(it, cfg);
        }
        for (ItemStack it : p.getInventory().getArmorContents()) {
            if (yamatoItem.isYamato(it)) yamatoItem.refreshLore(it, cfg);
        }
        ItemStack off = p.getInventory().getItemInOffHand();
        if (yamatoItem.isYamato(off)) yamatoItem.refreshLore(off, cfg);

        p.updateInventory();
    }
}
