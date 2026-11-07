package clre20.customInvAudit.source;

import org.bukkit.inventory.ItemStack;

/**
 * 介面鏡像的資料來源（玩家背包、終界箱、離線資料、生物）。只在主執行緒使用。
 */
public interface SlotSource {

    int size();

    /** 目標是否仍然存在（離線、死亡、卸載後為 false）。 */
    boolean valid();

    ItemStack get(int index);

    /**
     * 寫入物品。
     * @return 無法放入的部分（全部放入則回傳 null），呼叫端要把它還給操作者
     */
    ItemStack set(int index, ItemStack item);

    /** 紀錄檔用的格子名稱。 */
    String slotName(int index);
}
