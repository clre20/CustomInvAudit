package clre20.customInvAudit.gui;

import clre20.customInvAudit.CustomInvAudit;
import clre20.customInvAudit.config.Messages;
import clre20.customInvAudit.util.Items;
import clre20.customInvAudit.util.Perms;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** 數值選單與位置選單（玩家與生物共用的部分）。 */
public final class PlayerMenus {

    private PlayerMenus() {
    }

    /** 把數值欄位清單做成選單項目。 */
    public static List<MenuGui.Entry> numberEntries(CustomInvAudit plugin, GuiHolder self, List<NumberField> fields,
                                                    Predicate<Player> canEdit, String targetLabel) {
        Messages msg = plugin.messages();
        List<MenuGui.Entry> list = new ArrayList<>();
        for (NumberField f : fields) {
            list.add(new MenuGui.Entry(() -> {
                Double v = f.getter().get();
                return Items.make(f.icon(), msg.text("stats.entry-name", "name", msg.text(f.nameKey()),
                        "value", v == null ? "-" : f.format(v)), msg.lore("stats.entry-lore"));
            }, p -> {
                GuiHolder editor = new NumberEditorGui(plugin, p.getUniqueId(), self, f, canEdit, targetLabel);
                self.openLater(p, editor);
            }));
        }
        return list;
    }

    public static MenuGui statsMenu(CustomInvAudit plugin, Player viewer, GuiHolder parent, PlayerTarget t) {
        String label = t.label(plugin);
        List<NumberField> fields = t.statFields();
        MenuGui[] holder = new MenuGui[1];
        holder[0] = new MenuGui(plugin, viewer.getUniqueId(), parent, t::valid,
                plugin.messages().text("stats.title", "player", t.name),
                () -> numberEntries(plugin, holder[0], fields, t.statsEditable(plugin), label));
        return holder[0];
    }

    public static ItemStack locationItem(CustomInvAudit plugin, Location loc, String fallbackWorld) {
        Messages msg = plugin.messages();
        if (loc == null) {
            return Items.make(Material.COMPASS, msg.text("location.info-name"),
                    msg.lore("location.info-unknown", "world", fallbackWorld));
        }
        return Items.make(Material.COMPASS, msg.text("location.info-name"), msg.lore("location.info-lore",
                "world", loc.getWorld().getName(),
                "x", fmt(loc.getX()), "y", fmt(loc.getY()), "z", fmt(loc.getZ()),
                "yaw", fmt(loc.getYaw()), "pitch", fmt(loc.getPitch())));
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }

    /** 玩家的位置選單：查看座標、傳送到他、把他拉過來。 */
    public static MenuGui locationMenu(CustomInvAudit plugin, Player viewer, GuiHolder parent, PlayerTarget t) {
        Messages msg = plugin.messages();
        BooleanSupplier valid = t::valid;
        String label = t.label(plugin);
        return new MenuGui(plugin, viewer.getUniqueId(), parent, valid,
                msg.text("location.title", "name", t.name), () -> {
            List<MenuGui.Entry> list = new ArrayList<>();
            list.add(new MenuGui.Entry(() -> locationItem(plugin, t.location(),
                    t.data == null ? "?" : t.data.getDimensionName()), null));
            list.add(new MenuGui.Entry(() -> Items.make(Material.ENDER_PEARL, msg.text("location.tp-to"),
                    msg.lore("location.tp-to-lore")), p -> {
                if (!p.hasPermission(Perms.TELEPORT)) {
                    msg.send(p, "gui.no-permission");
                    return;
                }
                Location loc = t.location();
                if (loc == null) {
                    msg.send(p, "location.unknown-world");
                    return;
                }
                p.closeInventory();
                p.teleportAsync(loc);
                plugin.audit().action(p, label, "teleport-to", "");
            }));
            list.add(new MenuGui.Entry(() -> Items.make(Material.LEAD, msg.text("location.tp-here"),
                    msg.lore(t.offline() ? "location.tp-here-lore-offline" : "location.tp-here-lore")), p -> {
                if (!p.hasPermission(Perms.TELEPORT) || t.isProtectedFrom(plugin, p)) {
                    msg.send(p, "gui.no-permission");
                    return;
                }
                if (!t.valid()) return;
                Player target = t.online();
                Location here = p.getLocation();
                if (target != null) {
                    target.teleportAsync(here);
                } else if (t.data != null) {
                    if (!t.writable()) {
                        msg.send(p, "offline.read-only");
                        return;
                    }
                    t.data.setLocation(here);
                }
                msg.send(p, "location.moved-here", "name", t.name);
                plugin.audit().action(p, label, "teleport-here", fmt(here.getX()) + "," + fmt(here.getY())
                        + "," + fmt(here.getZ()) + "@" + here.getWorld().getName());
            }));
            return list;
        });
    }
}
