package clre20.customInvAudit.source;

import clre20.customInvAudit.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 生物的裝備、背包與終界使者手上的方塊。
 * 索引 0~8 對應介面第一列（頭、胸、腿、腳、主手、副手、身體、鞍、手持方塊），-1 代表不可用；9 起為生物背包。
 */
public final class EntitySource implements SlotSource {

    public static final EquipmentSlot[] EQUIP_ORDER = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.BODY, EquipmentSlot.SADDLE
    };
    public static final int CARRIED = 8;
    public static final int INV_START = 9;
    public static final int MAX_INV = 27;

    private final UUID target;
    private final boolean[] available = new boolean[9];
    private final int invSize;

    public EntitySource(LivingEntity e) {
        this.target = e.getUniqueId();
        boolean horse = e instanceof AbstractHorse;
        for (int i = 0; i < EQUIP_ORDER.length; i++) {
            EquipmentSlot s = EQUIP_ORDER[i];
            // 馬類的鞍與馬鎧放在馬的背包裡，避免兩邊同時寫入造成複製
            if (horse && (s == EquipmentSlot.BODY || s == EquipmentSlot.SADDLE)) continue;
            available[i] = e.getEquipment() != null && canUse(e, s);
        }
        available[CARRIED] = e instanceof Enderman;
        int size = 0;
        if (e instanceof InventoryHolder h) {
            size = Math.min(MAX_INV, h.getInventory().getSize());
        }
        invSize = size;
    }

    private static boolean canUse(LivingEntity e, EquipmentSlot s) {
        try {
            return e.canUseEquipmentSlot(s);
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isAvailable(int index) {
        return index >= INV_START ? index - INV_START < invSize : available[index];
    }

    public int invSize() {
        return invSize;
    }

    public LivingEntity entity() {
        Entity e = Bukkit.getEntity(target);
        return e instanceof LivingEntity le && le.isValid() ? le : null;
    }

    @Override
    public int size() {
        return INV_START + invSize;
    }

    @Override
    public boolean valid() {
        return entity() != null;
    }

    @Override
    public ItemStack get(int index) {
        LivingEntity e = entity();
        if (e == null || !isAvailable(index)) return null;
        if (index < EQUIP_ORDER.length) {
            EntityEquipment eq = e.getEquipment();
            return eq == null ? null : eq.getItem(EQUIP_ORDER[index]);
        }
        if (index == CARRIED) {
            BlockData bd = ((Enderman) e).getCarriedBlock();
            if (bd == null) return null;
            Material m = bd.getPlacementMaterial();
            return m.isItem() && !m.isAir() ? new ItemStack(m) : null;
        }
        Inventory inv = ((InventoryHolder) e).getInventory();
        return inv.getItem(index - INV_START);
    }

    @Override
    public ItemStack set(int index, ItemStack item) {
        LivingEntity e = entity();
        if (e == null || !isAvailable(index)) return item;
        if (index < EQUIP_ORDER.length) {
            EntityEquipment eq = e.getEquipment();
            if (eq == null) return item;
            eq.setItem(EQUIP_ORDER[index], item);
            return leftover(item, get(index));
        }
        if (index == CARRIED) {
            Enderman en = (Enderman) e;
            if (Items.isEmpty(item)) {
                en.setCarriedBlock(null);
                return null;
            }
            if (!item.getType().isBlock()) return item;
            en.setCarriedBlock(item.getType().createBlockData());
            if (item.getAmount() <= 1) return null;
            ItemStack rest = item.clone();
            rest.setAmount(item.getAmount() - 1);
            return rest;
        }
        Inventory inv = ((InventoryHolder) e).getInventory();
        inv.setItem(index - INV_START, item);
        return leftover(item, inv.getItem(index - INV_START));
    }

    /** 比對實際寫入的結果，算出沒放進去的部分。 */
    private static ItemStack leftover(ItemStack wanted, ItemStack actual) {
        if (Items.isEmpty(wanted)) return null;
        if (Items.isEmpty(actual) || !actual.isSimilar(wanted)) return wanted;
        int rest = wanted.getAmount() - actual.getAmount();
        if (rest <= 0) return null;
        ItemStack r = wanted.clone();
        r.setAmount(rest);
        return r;
    }

    @Override
    public String slotName(int index) {
        if (index < EQUIP_ORDER.length) return EQUIP_ORDER[index].name().toLowerCase();
        if (index == CARRIED) return "carried";
        return "inv#" + (index - INV_START);
    }

    public static List<Integer> indices(EntitySource s) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < s.size(); i++) if (s.isAvailable(i)) out.add(i);
        return out;
    }
}
