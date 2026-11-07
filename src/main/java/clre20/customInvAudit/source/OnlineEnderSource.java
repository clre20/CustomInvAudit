package clre20.customInvAudit.source;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** 線上玩家終界箱。 */
public final class OnlineEnderSource implements SlotSource {

    private final UUID target;

    public OnlineEnderSource(UUID target) {
        this.target = target;
    }

    @Override
    public int size() {
        return 27;
    }

    @Override
    public boolean valid() {
        return Bukkit.getPlayer(target) != null;
    }

    @Override
    public ItemStack get(int index) {
        Player p = Bukkit.getPlayer(target);
        return p == null ? null : p.getEnderChest().getItem(index);
    }

    @Override
    public ItemStack set(int index, ItemStack item) {
        Player p = Bukkit.getPlayer(target);
        if (p == null) return item;
        p.getEnderChest().setItem(index, item);
        return null;
    }

    @Override
    public String slotName(int index) {
        return "ender#" + index;
    }
}
