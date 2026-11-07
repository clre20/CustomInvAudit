package clre20.customInvAudit.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * 介面物品工具。介面物品都帶本插件的 PDC 標籤（簽署），也因此不會和一般物品疊在一起。
 */
public final class Items {

    private static NamespacedKey guiKey;

    private Items() {
    }

    private static ItemStack filler;

    public static void init(Plugin plugin) {
        guiKey = new NamespacedKey(plugin, "gui_item");
        filler = make(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
    }

    /** 填充用玻璃片（回傳複本）。 */
    public static ItemStack filler() {
        return filler.clone();
    }

    public static NamespacedKey guiKey() {
        return guiKey;
    }

    public static ItemStack make(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material.isItem() && !material.isAir() ? material : Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (lore != null && !lore.isEmpty()) meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
            meta.getPersistentDataContainer().set(guiKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack glow(ItemStack item, boolean glow) {
        if (!glow) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isEmpty(ItemStack i) {
        return i == null || i.isEmpty();
    }

    public static boolean same(ItemStack a, ItemStack b) {
        boolean ea = isEmpty(a), eb = isEmpty(b);
        if (ea || eb) return ea == eb;
        return a.equals(b);
    }

    public static ItemStack copy(ItemStack i) {
        return isEmpty(i) ? null : i.clone();
    }

    /** 紀錄檔用的物品摘要。 */
    public static String describe(ItemStack i) {
        if (isEmpty(i)) return "-";
        StringBuilder sb = new StringBuilder(i.getType().getKey().getKey()).append(" x").append(i.getAmount());
        ItemMeta meta = i.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            String n = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(meta.displayName());
            if (n.length() > 48) n = n.substring(0, 48) + "…";
            sb.append(" \"").append(n.replace('\n', ' ').replace('\r', ' ')).append('"');
        }
        return sb.toString();
    }
}
