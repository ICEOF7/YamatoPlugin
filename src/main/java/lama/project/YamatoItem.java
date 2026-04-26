package lama.project;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
public final class YamatoItem {

    private final NamespacedKey keyIsYamato;
    private final NamespacedKey keyKills;

    public YamatoItem(NamespacedKey keyIsYamato, NamespacedKey keyKills) {
        this.keyIsYamato = keyIsYamato;
        this.keyKills = keyKills;
    }

    public boolean isYamato(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Byte b = pdc.get(keyIsYamato, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    public int getKills(ItemStack stack) {
        if (!isYamato(stack)) return 0;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return 0;
        Integer v = meta.getPersistentDataContainer().get(keyKills, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    public void setKills(ItemStack stack, int kills) {
        if (!isYamato(stack)) return;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(keyKills, PersistentDataType.INTEGER, Math.max(0, kills));
        stack.setItemMeta(meta);
    }

    public ItemStack create(FileConfiguration cfg) {
        Material material = Material.matchMaterial(cfg.getString("item.material", "DIAMOND_SWORD"));
        if (material == null || !material.isItem()) material = Material.DIAMOND_SWORD;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(Text.color(cfg.getString("item.name")));
        meta.getPersistentDataContainer().set(keyIsYamato, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(keyKills, PersistentDataType.INTEGER, 0);

        double baseDamage = cfg.getDouble("item.base-damage", 7.0);
        double attackSpeed = cfg.getDouble("item.attack-speed", 1.6);

        Attribute damageAttribute = findAttribute("ATTACK_DAMAGE", "GENERIC_ATTACK_DAMAGE");
        Attribute speedAttribute = findAttribute("ATTACK_SPEED", "GENERIC_ATTACK_SPEED");

        if (damageAttribute != null) {
            meta.removeAttributeModifier(damageAttribute);
            meta.addAttributeModifier(
                    damageAttribute,
                    new AttributeModifier(UUID.randomUUID(), "yamato_damage", baseDamage, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HAND)
            );
        }

        if (speedAttribute != null) {
            meta.removeAttributeModifier(speedAttribute);
            meta.addAttributeModifier(
                    speedAttribute,
                    new AttributeModifier(UUID.randomUUID(), "yamato_speed", attackSpeed - 4.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HAND)
            );
        }

        if (cfg.getBoolean("item.hide-attributes", true)) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }

        item.setItemMeta(meta);
        refreshLore(item, cfg);
        return item;
    }

    public void refreshLore(ItemStack item, FileConfiguration cfg) {
        if (!isYamato(item)) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        int kills = getKills(item);
        int need = cfg.getInt("charge.kills-needed", 10);
        String bar = buildChargeBar(cfg, kills, need);
        String status = kills >= need ? cfg.getString("charge.ready-text", "<green>(Готово)")
                : cfg.getString("charge.not-ready-text", "<red>(Не готово)");
        String chargeLine = Text.placeholders(
                cfg.getString("item.charge-line", "<gray>Заряд ульты: <white>[{bar}] <yellow>{kills}<gray>/<yellow>{need} {status}"),
                "{bar}", bar,
                "{kills}", String.valueOf(kills),
                "{need}", String.valueOf(need),
                "{status}", status
        );

        List<String> configuredLore = cfg.getStringList("item.description");
        if (configuredLore.isEmpty()) configuredLore = defaultDescription();

        List<String> lore = new ArrayList<>();
        for (String line : configuredLore) {
            lore.add(Text.color(Text.placeholders(
                    line,
                    "{bar}", bar,
                    "{kills}", String.valueOf(kills),
                    "{need}", String.valueOf(need),
                    "{status}", status,
                    "{charge_line}", chargeLine
            )));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    public String buildChargeLine(FileConfiguration cfg, int kills, int need) {
        String status = kills >= need ? cfg.getString("charge.ready-text", "<green>(Готово)")
                : cfg.getString("charge.not-ready-text", "<red>(Не готово)");
        String fmt = cfg.getString("item.charge-line", "<gray>Заряд ульты: <white>[{bar}] <yellow>{kills}<gray>/<yellow>{need} {status}");
        return Text.placeholders(
                fmt,
                "{bar}", buildChargeBar(cfg, kills, need),
                "{kills}", String.valueOf(kills),
                "{need}", String.valueOf(need),
                "{status}", status
        );
    }

    private Attribute findAttribute(String... names) {
        for (String name : names) {
            try {
                return Attribute.valueOf(name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private String buildChargeBar(FileConfiguration cfg, int kills, int need) {
        int len = cfg.getInt("charge.bar-length", 10);
        String filled = cfg.getString("charge.bar-filled", "█");
        String empty = cfg.getString("charge.bar-empty", "░");

        int clamped = Math.min(kills, need);
        int filledCount = (int) Math.round((need == 0 ? 1.0 : (clamped / (double) need)) * len);
        filledCount = Math.min(len, Math.max(0, filledCount));
        int emptyCount = Math.max(0, len - filledCount);

        return filled.repeat(filledCount) + empty.repeat(emptyCount);
    }

    private List<String> defaultDescription() {
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>Клинок, который режет пространство.");
        lore.add("");
        lore.add("<gray>ПКМ <dark_gray>• <white>Judgement Cut");
        lore.add("<gray>Shift + ПКМ <dark_gray>• <white>Dash Slash");
        lore.add("<gray>F <dark_gray>• <white>Stun Cut");
        lore.add("<gray>Shift + прыжок + ЛКМ <dark_gray>• <light_purple>Ульта");
        lore.add("");
        lore.add("{charge_line}");
        lore.add("<gold>Редкость: <light_purple>Легендарное");
        return lore;
    }
}
