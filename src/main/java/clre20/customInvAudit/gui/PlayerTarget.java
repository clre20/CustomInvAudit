package clre20.customInvAudit.gui;

import clre20.customInvAudit.CustomInvAudit;
import clre20.customInvAudit.offline.OfflineData;
import clre20.customInvAudit.util.Items;
import clre20.customInvAudit.util.Perms;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 被查看的玩家（線上或離線）。只存 UUID 與名稱；線上時每次都重新取得 Player。
 */
public final class PlayerTarget {

    public final UUID uuid;
    public final String name;
    /** 離線時的資料；線上為 null。 */
    public final OfflineData data;

    public PlayerTarget(UUID uuid, String name, OfflineData data) {
        this.uuid = uuid;
        this.name = name;
        this.data = data;
    }

    public boolean offline() {
        return data != null;
    }

    public Player online() {
        return data == null ? Bukkit.getPlayer(uuid) : null;
    }

    public boolean valid() {
        Player p = Bukkit.getPlayer(uuid);
        return data == null ? p != null : p == null && !data.isDiscarded();
    }

    /** 離線資料可否修改（線上一律可以）。 */
    public boolean writable() {
        return data == null || data.editable;
    }

    public String label(CustomInvAudit plugin) {
        return plugin.messages().plain("audit-log.target-player", "name", name, "uuid", uuid);
    }

    public boolean isProtectedFrom(CustomInvAudit plugin, Player viewer) {
        return plugin.isProtected(viewer, uuid, name);
    }

    public ItemStack head(CustomInvAudit plugin) {
        var msg = plugin.messages();
        var status = msg.text(data == null ? "gui.status-online" : "gui.status-offline");
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>(msg.lore("gui.head-lore",
                "status", status, "uuid", uuid));
        if (data != null && !data.editable && data.readOnlyReason != null) {
            lore.addAll(msg.lore(data.readOnlyReason));
        }
        ItemStack head = Items.make(Material.PLAYER_HEAD, msg.text("gui.head-name", "player", name), lore);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setPlayerProfile(Bukkit.createProfile(uuid, name));
            head.setItemMeta(meta);
        }
        return head;
    }

    // ---------------- 數值欄位 ----------------

    public List<NumberField> statFields() {
        List<NumberField> list = new ArrayList<>();
        list.add(new NumberField("field.health", Material.RED_DYE, false, 0, this::maxHealth, 1, 5,
                () -> {
                    Player p = online();
                    if (p != null) return p.getHealth();
                    return valid() && data != null ? data.getHealth() : null;
                },
                v -> {
                    Player p = online();
                    if (p != null) p.setHealth(Math.min(v, maxHealth()));
                    else if (data != null) data.setHealth(v);
                }));
        if (data == null) {
            list.add(new NumberField("field.max-health", Material.GOLDEN_APPLE, false, 1, () -> 1024, 1, 10,
                    () -> {
                        Player p = online();
                        AttributeInstance a = p == null ? null : p.getAttribute(Attribute.MAX_HEALTH);
                        return a == null ? null : a.getBaseValue();
                    },
                    v -> {
                        Player p = online();
                        AttributeInstance a = p == null ? null : p.getAttribute(Attribute.MAX_HEALTH);
                        if (a != null) a.setBaseValue(v);
                        if (p != null && p.getHealth() > maxHealth()) p.setHealth(maxHealth());
                    }));
        }
        list.add(new NumberField("field.food", Material.COOKED_BEEF, true, 0, () -> 20, 1, 5,
                () -> {
                    Player p = online();
                    if (p != null) return (double) p.getFoodLevel();
                    return valid() && data != null ? (double) data.getFoodLevel() : null;
                },
                v -> {
                    Player p = online();
                    if (p != null) p.setFoodLevel((int) v);
                    else if (data != null) data.setFoodLevel((int) v);
                }));
        list.add(new NumberField("field.saturation", Material.GOLDEN_CARROT, false, 0, () -> 20, 1, 5,
                () -> {
                    Player p = online();
                    if (p != null) return (double) p.getSaturation();
                    return valid() && data != null ? data.getSaturation() : null;
                },
                v -> {
                    Player p = online();
                    if (p != null) p.setSaturation((float) v);
                    else if (data != null) data.setSaturation(v);
                }));
        list.add(new NumberField("field.exhaustion", Material.ROTTEN_FLESH, false, 0, () -> 4, 0.1, 1,
                () -> {
                    Player p = online();
                    if (p != null) return (double) p.getExhaustion();
                    return valid() && data != null ? data.getExhaustion() : null;
                },
                v -> {
                    Player p = online();
                    if (p != null) p.setExhaustion((float) v);
                    else if (data != null) data.setExhaustion(v);
                }));
        list.add(new NumberField("field.xp-level", Material.EXPERIENCE_BOTTLE, true, 0, () -> 100000, 1, 10,
                () -> {
                    Player p = online();
                    if (p != null) return (double) p.getLevel();
                    return valid() && data != null ? (double) data.getXpLevel() : null;
                },
                v -> {
                    Player p = online();
                    if (p != null) p.setLevel((int) v);
                    else if (data != null) data.setXpLevel((int) v);
                }));
        list.add(new NumberField("field.xp-progress", Material.LIME_DYE, false, 0, () -> 0.99, 0.01, 0.1,
                () -> {
                    Player p = online();
                    if (p != null) return (double) p.getExp();
                    return valid() && data != null ? data.getXpProgress() : null;
                },
                v -> {
                    Player p = online();
                    if (p != null) p.setExp((float) v);
                    else if (data != null) data.setXpProgress(v);
                }));
        list.add(new NumberField("field.selected-slot", Material.CLOCK, true, 1, () -> 9, 1, 4,
                () -> {
                    Player p = online();
                    if (p != null) return (double) p.getInventory().getHeldItemSlot() + 1;
                    return valid() && data != null ? (double) data.getSelectedSlot() + 1 : null;
                },
                v -> {
                    Player p = online();
                    if (p != null) p.getInventory().setHeldItemSlot((int) v - 1);
                    else if (data != null) data.setSelectedSlot((int) v - 1);
                }));
        return list;
    }

    private double maxHealth() {
        Player p = online();
        if (p == null) return 1024;
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20 : a.getValue();
    }

    public Predicate<Player> statsEditable(CustomInvAudit plugin) {
        return p -> p.hasPermission(Perms.STATS_MODIFY) && writable() && valid() && !isProtectedFrom(plugin, p);
    }

    public Location location() {
        Player p = online();
        if (p != null) return p.getLocation();
        return data == null ? null : data.getLocation();
    }
}
