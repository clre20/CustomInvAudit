package clre20.customInvAudit.listener;

import clre20.customInvAudit.CustomInvAudit;
import clre20.customInvAudit.gui.GuiHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** 介面事件：最外層只做一次 Holder 判斷，不是本插件的介面立即返回。 */
public final class GuiListener implements Listener {

    private final CustomInvAudit plugin;

    public GuiListener(CustomInvAudit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof GuiHolder gui)) return;
        if (!(e.getWhoClicked() instanceof Player p) || !p.getUniqueId().equals(gui.viewer())) {
            e.setCancelled(true);
            return;
        }
        gui.onClick(e, p);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof GuiHolder gui)) return;
        if (!(e.getWhoClicked() instanceof Player p) || !p.getUniqueId().equals(gui.viewer())) {
            e.setCancelled(true);
            return;
        }
        gui.onDrag(e, p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof GuiHolder gui)) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        plugin.guis().closed(gui, p);
    }
}
