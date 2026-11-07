package clre20.customInvAudit.gui;

import clre20.customInvAudit.CustomInvAudit;
import clre20.customInvAudit.config.Messages;
import clre20.customInvAudit.offline.OfflineData;
import clre20.customInvAudit.util.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 本插件所有箱子介面的基底。
 * 簽署：Holder 本身綁定插件實例（{@link #getPlugin()}、{@link #SIGNATURE}），介面物品另帶 PDC 標籤。
 * 功能按鈕只吃左鍵，其他點擊一律取消。
 */
public abstract class GuiHolder implements InventoryHolder {

    public static final String SIGNATURE = "CustomInvAudit";

    protected final CustomInvAudit plugin;
    protected final Messages msg;
    protected final UUID viewer;
    protected Inventory inventory;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();

    protected GuiHolder(CustomInvAudit plugin, UUID viewer) {
        this.plugin = plugin;
        this.msg = plugin.messages();
        this.viewer = viewer;
    }

    /** 其他插件可用來辨識介面來源。 */
    public Plugin getPlugin() {
        return plugin;
    }

    public String getSignature() {
        return SIGNATURE;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    protected void create(int rows, Component title) {
        inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    /** 建立/重建介面內容（開啟前與每次刷新時呼叫）。 */
    public abstract void render();

    /** 定時刷新（只有開著時才會被呼叫）。回傳 false 表示目標已失效，介面會被關閉。 */
    public boolean tick() {
        render();
        return true;
    }

    /** 目標失效時顯示的訊息路徑。 */
    public String invalidMessage() {
        return "gui.target-gone";
    }

    /** 這個介面使用的離線資料（沒有則為 null）。 */
    public OfflineData offlineData() {
        return null;
    }

    /** 這個介面查看的目標（玩家或生物 UUID），沒有則為 null。 */
    public UUID targetId() {
        return null;
    }

    public UUID viewer() {
        return viewer;
    }

    // ---------------- 按鈕 ----------------

    protected void button(int slot, ItemStack item, Consumer<Player> action) {
        inventory.setItem(slot, item);
        if (action == null) actions.remove(slot);
        else actions.put(slot, action);
    }

    protected void clearButtons() {
        actions.clear();
    }

    protected ItemStack item(Material m, String namePath, String lorePath, Object... ph) {
        List<Component> lore = lorePath == null ? List.of() : msg.lore(lorePath, ph);
        return Items.make(m, msg.text(namePath, ph), lore);
    }

    protected ItemStack filler() {
        return Items.filler();
    }

    protected void fillEmpty(int from, int to) {
        for (int i = from; i < to; i++) {
            ItemStack cur = inventory.getItem(i);
            if (cur == null || cur.isEmpty()) inventory.setItem(i, filler());
        }
    }

    // ---------------- 事件 ----------------

    public void onClick(InventoryClickEvent e, Player p) {
        e.setCancelled(true);
        if (e.getClickedInventory() != inventory) return;
        if (e.getClick() != ClickType.LEFT) return;
        Consumer<Player> a = actions.get(e.getRawSlot());
        if (a != null) a.accept(p);
    }

    public void onDrag(InventoryDragEvent e, Player p) {
        for (int raw : e.getRawSlots()) {
            if (raw < inventory.getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    /** 介面被關閉時（主執行緒）。 */
    public void onClose(Player p) {
    }

    // ---------------- 工具 ----------------

    /** 在下一個 tick 開啟另一個介面（不在點擊事件內直接開啟）。 */
    public void openLater(Player p, GuiHolder next) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) plugin.guis().open(p, next);
        });
    }

    protected void closeLater(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder(false) == this) p.closeInventory();
        });
    }

    protected Player viewerPlayer() {
        return Bukkit.getPlayer(viewer);
    }
}
