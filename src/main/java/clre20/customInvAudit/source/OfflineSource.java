package clre20.customInvAudit.source;

import clre20.customInvAudit.offline.OfflineData;
import clre20.customInvAudit.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/** 離線玩家的背包或終界箱（共用同一份 OfflineData 快取）。 */
public final class OfflineSource implements SlotSource {

    private final OfflineData data;
    private final boolean ender;

    public OfflineSource(OfflineData data, boolean ender) {
        this.data = data;
        this.ender = ender;
    }

    private ItemStack[] arr() {
        return ender ? data.ender : data.inv;
    }

    @Override
    public int size() {
        return arr().length;
    }

    @Override
    public boolean valid() {
        return Bukkit.getPlayer(data.uuid) == null;
    }

    @Override
    public ItemStack get(int index) {
        return arr()[index];
    }

    @Override
    public ItemStack set(int index, ItemStack item) {
        if (!data.editable) return item;
        arr()[index] = Items.copy(item);
        data.markDirty();
        return null;
    }

    @Override
    public String slotName(int index) {
        return ender ? "ender#" + index : OnlinePlayerInvSource.slotNameOf(index);
    }
}
