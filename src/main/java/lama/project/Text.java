package lama.project;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public final class Text {
    private static final Pattern HEX_TAG = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final Map<String, String> TAGS = new LinkedHashMap<>();

    static {
        TAGS.put("<black>", "§0");
        TAGS.put("<dark_blue>", "§1");
        TAGS.put("<dark_green>", "§2");
        TAGS.put("<dark_aqua>", "§3");
        TAGS.put("<dark_red>", "§4");
        TAGS.put("<dark_purple>", "§5");
        TAGS.put("<gold>", "§6");
        TAGS.put("<gray>", "§7");
        TAGS.put("<dark_gray>", "§8");
        TAGS.put("<blue>", "§9");
        TAGS.put("<green>", "§a");
        TAGS.put("<aqua>", "§b");
        TAGS.put("<red>", "§c");
        TAGS.put("<light_purple>", "§d");
        TAGS.put("<yellow>", "§e");
        TAGS.put("<white>", "§f");
        TAGS.put("<bold>", "§l");
        TAGS.put("<italic>", "§o");
        TAGS.put("<underlined>", "§n");
        TAGS.put("<underline>", "§n");
        TAGS.put("<strikethrough>", "§m");
        TAGS.put("<obfuscated>", "§k");
        TAGS.put("<reset>", "§r");
    }

    private Text() {}

    public static String color(String input) {
        if (input == null) return "";
        String out = input;
        Matcher matcher = HEX_TAG.matcher(out);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(hexColor(matcher.group(1))));
        }
        matcher.appendTail(buffer);
        out = buffer.toString();
        for (Map.Entry<String, String> entry : TAGS.entrySet()) {
            out = out.replace(entry.getKey(), entry.getValue());
        }
        out = out.replaceAll("(?i)</?gradient(:[^>]*)?>", "");
        out = out.replaceAll("(?i)</?rainbow(:[^>]*)?>", "");
        out = out.replaceAll("(?i)</?[a-z_]+(:[^>]*)?>", "");
        return ChatColor.translateAlternateColorCodes('&', out);
    }

    public static String cfg(FileConfiguration cfg, String path, String fallback, String... replacements) {
        return color(placeholders(cfg.getString(path, fallback), replacements));
    }

    public static void actionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(color(message)));
    }

    public static void actionBarCfg(Player player, FileConfiguration cfg, String path, String fallback, String... replacements) {
        actionBar(player, cfg(cfg, path, fallback, replacements));
    }

    public static String placeholders(String input, String... replacements) {
        if (input == null) return "";
        String out = input;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            out = out.replace(replacements[i], replacements[i + 1]);
        }
        return out;
    }

    public static String seconds(long millis) {
        double seconds = millis / 1000.0;
        if (seconds >= 10 || seconds == Math.floor(seconds)) {
            return String.valueOf((long) Math.ceil(seconds));
        }
        return String.format(Locale.US, "%.1f", seconds);
    }

    private static String hexColor(String hex) {
        StringBuilder out = new StringBuilder("§x");
        for (char c : hex.toCharArray()) {
            out.append('§').append(c);
        }
        return out.toString();
    }
}
