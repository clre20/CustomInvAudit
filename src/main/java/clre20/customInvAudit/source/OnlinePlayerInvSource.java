package clre20.customInvAudit.source;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.UUID;

/** 線上玩家背包。索引 0~35 背包、36 頭盔、37 胸甲、38 護腿、39 靴子、40 副手。 */
public final class OnlinePlayerInvSource implements SlotSource {

    /** 我們的索引 → PlayerInventory 的格子編號。 */
    private static final int[] MAP = new int[41];
    private static final String[] EXTRA = {"head", "chest", "legs", "feet", "offhand"};

    static {
        for (int i = 0; i < 36; i++) MAP[i] = i;
        MAP[36] = 39;
        MAP[37] = 38;
        MAP[38] = 37;
        MAP[39] = 36;
        MAP[40] = 40;
    }

    private final UUID target;

    public OnlinePlayerInvSource(UUID target) {
        this.target = target;
    }

    private PlayerInventory inv() {
        Player p = Bukkit.getPlayer(target);
        return p == null ? null : p.getInventory();
    }

    @Override
    public int size() {
        return 41;
    }

    @Override
    public boolean valid() {
        return Bukkit.getPlayer(target) != null;
    }

    @Override
    public ItemStack get(int index) {
        PlayerInventory inv = inv();
        return inv == null ? null : inv.getItem(MAP[index]);
    }

    @Override
    public ItemStack set(int index, ItemStack item) {
        PlayerInventory inv = inv();
        if (inv == null) return item;
        inv.setItem(MAP[index], item);
        return null;
    }

    @Override
    public String slotName(int index) {
        return slotNameOf(index);
    }

    public static String slotNameOf(int index) {
        return index < 36 ? "inv#" + index : EXTRA[index - 36];
    }
}
