package clre20.customInvAudit.gui;

import clre20.customInvAudit.CustomInvAudit;
import clre20.customInvAudit.offline.OfflineData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 追蹤目前開著的介面，並只在有介面開著時跑一個共用的刷新排程。
 * 只保存 Holder（Holder 內只存 UUID），不保存玩家或生物實例。
 */
public final class GuiManager {

    private final CustomInvAudit plugin;
    private final Set<GuiHolder> open = new LinkedHashSet<>();
    private BukkitTask task;

    public GuiManager(CustomInvAudit plugin) {
        this.plugin = plugin;
    }

    public void open(Player p, GuiHolder gui) {
        gui.render();
        OfflineData data = gui.offlineData();
        if (data != null) plugin.offline().attach(data, p.getUniqueId());
        p.openInventory(gui.getInventory());
        if (p.getOpenInventory().getTopInventory().getHolder(false) != gui) {
            // 被其他插件取消開啟
            if (data != null) releaseLater(p.getUniqueId(), data);
            return;
        }
        open.add(gui);
        ensureTask();
    }

    /** InventoryCloseEvent 呼叫。 */
    public void closed(GuiHolder gui, Player p) {
        gui.onClose(p);
        open.remove(gui);
        OfflineData data = gui.offlineData();
        if (data != null) releaseLater(p.getUniqueId(), data);
        if (open.isEmpty()) stopTask();
    }

    /** 下一 tick 檢查：檢視者若沒有切換到同一份離線資料的其他介面，就釋放它。 */
    private void releaseLater(UUID viewer, OfflineData data) {
        if (!plugin.isEnabled()) {
            plugin.offline().detach(data, viewer);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(viewer);
            if (p != null) {
                InventoryHolder h = p.getOpenInventory().getTopInventory().getHolder(false);
                if (h instanceof GuiHolder g && g.offlineData() == data) return;
            }
            plugin.offline().detach(data, viewer);
        });
    }

    private void ensureTask() {
        if (task != null) return;
        int interval = plugin.config().refreshIntervalTicks;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, interval, interval);
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tickAll() {
        if (open.isEmpty()) {
            stopTask();
            return;
        }
        for (GuiHolder gui : new ArrayList<>(open)) {
            Player p = Bukkit.getPlayer(gui.viewer());
            if (p == null || p.getOpenInventory().getTopInventory().getHolder(false) != gui) {
                open.remove(gui);
                continue;
            }
            boolean ok;
            try {
                ok = gui.tick();
            } catch (Throwable t) {
                plugin.getLogger().warning("介面刷新失敗：" + t);
                ok = false;
            }
            if (!ok) {
                p.closeInventory();
                plugin.messages().send(p, gui.invalidMessage());
            }
        }
    }

    /** 是否有其他檢視者對同一目標的鏡像介面正等著寫回。 */
    public boolean otherPendingPush(MirrorGui self) {
        UUID target = self.targetId();
        if (target == null) return false;
        for (GuiHolder g : open) {
            if (g != self && g instanceof MirrorGui m && target.equals(m.targetId()) && m.isPushPending()) return true;
        }
        return false;
    }

    /** 關閉所有使用這份離線資料的介面。 */
    public void closeAllFor(OfflineData data, String messagePath) {
        for (GuiHolder gui : new ArrayList<>(open)) {
            if (gui.offlineData() != data) continue;
            Player p = Bukkit.getPlayer(gui.viewer());
            if (p != null) {
                p.closeInventory();
                plugin.messages().send(p, messagePath, "player", data.name);
            }
        }
    }

    /** 關閉所有查看此目標的介面（玩家離線、生物死亡等）。 */
    public void closeAllForTarget(UUID target, String messagePath) {
        for (GuiHolder gui : new ArrayList<>(open)) {
            if (!target.equals(gui.targetId())) continue;
            Player p = Bukkit.getPlayer(gui.viewer());
            if (p != null) {
                p.closeInventory();
                plugin.messages().send(p, messagePath);
            }
        }
    }

    /** 檢視者離線：直接移除其介面。 */
    public void viewerQuit(UUID viewer) {
        open.removeIf(g -> g.viewer().equals(viewer));
        if (open.isEmpty()) stopTask();
    }

    public void shutdown() {
        for (GuiHolder gui : new ArrayList<>(open)) {
            Player p = Bukkit.getPlayer(gui.viewer());
            if (p != null) p.closeInventory();
        }
        open.clear();
        stopTask();
    }

    public void restartTask() {
        stopTask();
        if (!open.isEmpty()) ensureTask();
    }
}
