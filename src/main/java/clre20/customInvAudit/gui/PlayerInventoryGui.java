package clre20.customInvAudit.gui;

import clre20.customInvAudit.CustomInvAudit;
import clre20.customInvAudit.offline.OfflineData;
import clre20.customInvAudit.source.OfflineSource;
import clre20.customInvAudit.source.OnlinePlayerInvSource;
import clre20.customInvAudit.source.SlotSource;
import clre20.customInvAudit.util.Items;
import clre20.customInvAudit.util.Perms;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * 玩家背包（54 格）：
 * 0~26 背包、27~35 快捷欄、36 頭盔 37 胸甲 38 護腿 39 靴子 40 副手、41 說明；
 * 最下排為功能按鈕。
 */
public final class PlayerInventoryGui extends MirrorGui {

    private static final int[] LAYOUT = new int[41];

    static {
        for (int i = 0; i < 9; i++) LAYOUT[i] = 27 + i;
        for (int i = 9; i < 36; i++) LAYOUT[i] = i - 9;
        for (int i = 36; i < 41; i++) LAYOUT[i] = i;
    }

    private final PlayerTarget target;
    private boolean dropMode;

    public PlayerInventoryGui(CustomInvAudit plugin, UUID viewer, PlayerTarget target) {
        super(plugin, viewer, source(target), LAYOUT);
        this.target = target;
        create(6, plugin.messages().text(target.offline() ? "inventory.title-offline" : "inventory.title",
                "player", target.name));
    }

    private static SlotSource source(PlayerTarget t) {
        return t.data == null ? new OnlinePlayerInvSource(t.uuid) : new OfflineSource(t.data, false);
    }

    @Override
    public OfflineData offlineData() {
        return target.data;
    }

    @Override
    public UUID targetId() {
        return target.uuid;
    }

    @Override
    public boolean tick() {
        if (!target.valid()) return false;
        return super.tick();
    }

    @Override
    public String invalidMessage() {
        return target.offline() ? "offline.closed-player-login" : "gui.target-offline";
    }

    @Override
    protected boolean canModify(Player p) {
        return p.hasPermission(Perms.INV_MODIFY) && target.writable() && !target.isProtectedFrom(plugin, p)
                && !p.getUniqueId().equals(target.uuid);
    }

    @Override
    protected String targetLabel() {
        return target.label(plugin);
    }

    @Override
    protected void renderButtons() {
        button(41, item(Material.OAK_SIGN, "inventory.help-name", "inventory.help-lore"), null);
        for (int s = 42; s <= 44; s++) inventory.setItem(s, filler());

        button(45, item(Material.APPLE, "inventory.stats-name", "inventory.stats-lore"),
                p -> openLater(p, PlayerMenus.statsMenu(plugin, p, this, target)));
        button(46, item(Material.COMPASS, "inventory.location-name", "inventory.location-lore"),
                p -> openLater(p, PlayerMenus.locationMenu(plugin, p, this, target)));
        button(47, item(Material.ENDER_CHEST, "inventory.enderchest-name", "inventory.enderchest-lore"), p -> {
            if (!p.hasPermission(Perms.EC_OTHER)) {
                msg.send(p, "gui.no-permission");
                return;
            }
            openLater(p, new EnderChestGui(plugin, p.getUniqueId(), target, this));
        });
        if (target.offline()) {
            button(48, item(Material.GRAY_DYE, "inventory.drop-mode-offline", null), null);
        } else {
            button(48, Items.glow(item(dropMode ? Material.HOPPER : Material.DROPPER,
                    dropMode ? "inventory.drop-mode-on" : "inventory.drop-mode-off", "inventory.drop-mode-lore"),
                    dropMode), p -> {
                if (!canModify(p)) {
                    msg.send(p, "gui.no-permission-modify");
                    return;
                }
                dropMode = !dropMode;
                renderButtons();
            });
        }
        button(49, target.head(plugin), null);
        button(50, item(Material.LAVA_BUCKET, "inventory.clear-name", "inventory.clear-lore"), p -> {
            if (!canModify(p)) {
                msg.send(p, "gui.no-permission-modify");
                return;
            }
            openLater(p, new ConfirmGui(plugin, p.getUniqueId(), this, target::valid, "confirm.clear-inventory",
                    who -> clearAll(who), "player", target.name));
        });
        button(51, item(Material.SUNFLOWER, "gui.refresh", null), p -> render());
        inventory.setItem(52, filler());
        button(53, item(Material.BARRIER, "gui.close", null), this::closeLater);
    }

    private void clearAll(Player p) {
        if (!canModify(p) || !target.valid()) return;
        int n = 0;
        for (int i = 0; i < source.size(); i++) {
            if (!Items.isEmpty(source.get(i))) {
                source.set(i, null);
                n++;
            }
        }
        plugin.audit().action(p, targetLabel(), "clear-inventory", n + " slots");
        msg.send(p, "inventory.cleared", "player", target.name);
    }

    @Override
    protected boolean interceptItemClick(InventoryClickEvent e, Player p, int index) {
        if (!dropMode) return false;
        if (e.getClick() != ClickType.LEFT) return true;
        Player t = target.online();
        if (t == null || !canModify(p)) return true;
        ItemStack item = source.get(index);
        if (Items.isEmpty(item)) return true;
        ItemStack copy = item.clone();
        source.set(index, null);
        t.getWorld().dropItemNaturally(t.getLocation(), copy);
        plugin.audit().action(p, targetLabel(), "force-drop", source.slotName(index) + " " + Items.describe(copy));
        pull();
        return true;
    }
}
