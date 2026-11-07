package clre20.customInvAudit.gui;

import clre20.customInvAudit.CustomInvAudit;
import clre20.customInvAudit.offline.OfflineData;
import clre20.customInvAudit.source.OfflineSource;
import clre20.customInvAudit.source.OnlineEnderSource;
import clre20.customInvAudit.util.Items;
import clre20.customInvAudit.util.Perms;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 終界箱（36 格）：0~26 終界箱內容，最下排為功能按鈕。
 */
public final class EnderChestGui extends MirrorGui {

    private static final int[] LAYOUT = new int[27];

    static {
        for (int i = 0; i < 27; i++) LAYOUT[i] = i;
    }

    private final PlayerTarget target;
    private final GuiHolder parent;
    private final boolean self;

    public EnderChestGui(CustomInvAudit plugin, UUID viewer, PlayerTarget target, GuiHolder parent) {
        super(plugin, viewer, target.data == null ? new OnlineEnderSource(target.uuid)
                : new OfflineSource(target.data, true), LAYOUT);
        this.target = target;
        this.parent = parent;
        this.self = viewer.equals(target.uuid);
        create(4, plugin.messages().text(target.offline() ? "enderchest.title-offline" : "enderchest.title",
                "player", target.name));
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
        if (!target.writable()) return false;
        if (self) return p.hasPermission(Perms.EC_SELF);
        return p.hasPermission(Perms.EC_MODIFY) && !target.isProtectedFrom(plugin, p);
    }

    @Override
    protected String targetLabel() {
        return target.label(plugin) + " [ender]";
    }

    @Override
    protected void renderButtons() {
        for (int s = 27; s < 36; s++) inventory.setItem(s, filler());
        if (!self) {
            button(27, item(Material.CHEST, "enderchest.inventory-name", "enderchest.inventory-lore"), p -> {
                if (!p.hasPermission(Perms.INV_VIEW)) {
                    msg.send(p, "gui.no-permission");
                    return;
                }
                openLater(p, parent instanceof PlayerInventoryGui ? parent
                        : new PlayerInventoryGui(plugin, p.getUniqueId(), target));
            });
        }
        button(31, target.head(plugin), null);
        button(33, item(Material.LAVA_BUCKET, "enderchest.clear-name", "enderchest.clear-lore"), p -> {
            if (!canModify(p)) {
                msg.send(p, "gui.no-permission-modify");
                return;
            }
            openLater(p, new ConfirmGui(plugin, p.getUniqueId(), this, target::valid, "confirm.clear-enderchest",
                    this::clearAll, "player", target.name));
        });
        button(34, item(Material.SUNFLOWER, "gui.refresh", null), p -> render());
        button(35, item(Material.BARRIER, "gui.close", null), this::closeLater);
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
        plugin.audit().action(p, targetLabel(), "clear-enderchest", n + " slots");
        msg.send(p, "enderchest.cleared", "player", target.name);
    }
}
