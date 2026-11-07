package clre20.customInvAudit.gui;

import clre20.customInvAudit.CustomInvAudit;
import clre20.customInvAudit.source.SlotSource;
import clre20.customInvAudit.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 物品格會鏡像到目標（背包/終界箱/生物）的介面。
 * - 物品格：照原版拿放（含拖拉、Shift），下一 tick 只把「有變動的格子」寫回目標
 * - 每次點擊前先從目標拉一次最新內容，降低與目標同時操作造成的衝突
 * - 功能按鈕：只吃左鍵
 */
public abstract class MirrorGui extends GuiHolder {

    protected final SlotSource source;
    /** 來源索引 → 介面格子（-1 = 不顯示）。 */
    protected final int[] slotOf;
    private final Map<Integer, Integer> indexOf = new HashMap<>();
    private final ItemStack[] snapshot;
    private boolean pushScheduled;

    protected MirrorGui(CustomInvAudit plugin, UUID viewer, SlotSource source, int[] slotOf) {
        super(plugin, viewer);
        this.source = source;
        this.slotOf = slotOf;
        this.snapshot = new ItemStack[source.size()];
        for (int i = 0; i < slotOf.length; i++) {
            if (slotOf[i] >= 0) indexOf.put(slotOf[i], i);
        }
    }

    /** 操作者此刻能否修改物品格（每次點擊都重新檢查權限）。 */
    protected abstract boolean canModify(Player p);

    /** 紀錄檔用的目標描述。 */
    protected abstract String targetLabel();

    /** 重畫按鈕（不含物品格）。 */
    protected abstract void renderButtons();

    @Override
    public final void render() {
        renderButtons();
        if (!pushScheduled) pull();
    }

    @Override
    public boolean tick() {
        if (!source.valid()) return false;
        render();
        return true;
    }

    /** 目標 → 介面。只更新有變動的格子。 */
    protected void pull() {
        for (int i = 0; i < slotOf.length; i++) {
            int slot = slotOf[i];
            if (slot < 0) continue;
            ItemStack cur = source.get(i);
            if (!Items.same(cur, snapshot[i]) || !Items.same(inventory.getItem(slot), snapshot[i])) {
                ItemStack c = Items.copy(cur);
                inventory.setItem(slot, c);
                snapshot[i] = Items.copy(c);
            }
        }
    }

    /** 介面 → 目標。只寫回操作者改過的格子。 */
    protected void push(Player actor) {
        pushScheduled = false;
        if (!source.valid()) return;
        if (actor == null || !canModify(actor)) {
            pull();
            return;
        }
        for (int i = 0; i < slotOf.length; i++) {
            int slot = slotOf[i];
            if (slot < 0) continue;
            ItemStack now = inventory.getItem(slot);
            if (Items.same(now, snapshot[i])) continue;
            ItemStack before = snapshot[i];
            ItemStack leftover = source.set(i, Items.copy(now));
            ItemStack actual = Items.copy(source.get(i));
            inventory.setItem(slot, Items.copy(actual));
            snapshot[i] = actual;
            if (leftover != null && !leftover.isEmpty()) giveBack(actor, leftover);
            plugin.audit().itemChange(actor, targetLabel(), source.slotName(i), before, actual);
        }
    }

    private void giveBack(Player p, ItemStack item) {
        for (ItemStack rest : p.getInventory().addItem(item).values()) {
            p.getWorld().dropItem(p.getLocation(), rest);
        }
        msg.send(p, "gui.slot-rejected");
    }

    private void schedulePush(Player p) {
        if (pushScheduled) return;
        pushScheduled = true;
        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> push(Bukkit.getPlayer(id)));
    }

    public boolean isPushPending() {
        return pushScheduled;
    }

    /** 另一位管理員對同一目標的修改還沒寫回時，先擋下這次點擊，避免兩人同時拿到同一個物品。 */
    private boolean blockedByOther() {
        return plugin.guis().otherPendingPush(this);
    }

    protected boolean isMirrorSlot(int raw) {
        return indexOf.containsKey(raw);
    }

    /** 物品格被左鍵點擊時的額外處理（例如丟出模式）；回傳 true 表示已處理並取消事件。 */
    protected boolean interceptItemClick(InventoryClickEvent e, Player p, int index) {
        return false;
    }

    @Override
    public void onClick(InventoryClickEvent e, Player p) {
        InventoryAction action = e.getAction();
        if (action == InventoryAction.COLLECT_TO_CURSOR || action == InventoryAction.UNKNOWN) {
            e.setCancelled(true);
            return;
        }
        boolean top = e.getClickedInventory() == inventory;
        if (top && isMirrorSlot(e.getRawSlot())) {
            if (interceptItemClick(e, p, indexOf.get(e.getRawSlot()))) {
                e.setCancelled(true);
                return;
            }
            if (!canModify(p) || !source.valid() || blockedByOther()) {
                e.setCancelled(true);
                return;
            }
            if (!pushScheduled) pull();
            schedulePush(p);
            return;
        }
        if (top) {
            super.onClick(e, p);
            return;
        }
        // 操作者自己的背包
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (!canModify(p) || !source.valid() || blockedByOther()) {
                e.setCancelled(true);
                return;
            }
            if (!pushScheduled) pull();
            schedulePush(p);
        }
    }

    @Override
    public void onDrag(InventoryDragEvent e, Player p) {
        boolean touchesTop = false;
        for (int raw : e.getRawSlots()) {
            if (raw >= inventory.getSize()) continue;
            touchesTop = true;
            if (!isMirrorSlot(raw)) {
                e.setCancelled(true);
                return;
            }
        }
        if (!touchesTop) return;
        if (!canModify(p) || !source.valid() || blockedByOther()) {
            e.setCancelled(true);
            return;
        }
        schedulePush(p);
    }

    @Override
    public void onClose(Player p) {
        if (pushScheduled) push(p);
    }
}
