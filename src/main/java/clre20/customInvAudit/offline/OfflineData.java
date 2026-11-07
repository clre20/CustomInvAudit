package clre20.customInvAudit.offline;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 一位離線玩家的資料（記憶體快取）。只在主執行緒讀寫。
 * 背包索引：0~35 背包、36 頭盔、37 胸甲、38 護腿、39 靴子、40 副手。
 */
public final class OfflineData {

    public static final int INV_SIZE = 41;
    public static final int ENDER_SIZE = 27;
    private static final String[] EQUIP_KEYS = {"head", "chest", "legs", "feet", "offhand"};

    public final UUID uuid;
    public final String name;
    final Nbt.Compound root;
    final int dataVersion;

    final Nbt.Compound[] rawInv = new Nbt.Compound[INV_SIZE];
    final Nbt.Compound[] rawEnder = new Nbt.Compound[ENDER_SIZE];
    public final ItemStack[] inv = new ItemStack[INV_SIZE];
    public final ItemStack[] ender = new ItemStack[ENDER_SIZE];

    /** 可否修改（資料版本不同或有物品無法解析時為 false，避免寫壞資料）。 */
    public boolean editable;
    /** 不可修改的原因（messages 路徑）。 */
    public String readOnlyReason;
    boolean dirty;
    boolean discarded;
    final Set<UUID> viewers = new HashSet<>();

    OfflineData(UUID uuid, String name, Nbt.Compound root) {
        this.uuid = uuid;
        this.name = name;
        this.root = root;
        this.dataVersion = root.getInt("DataVersion", -1);
    }

    /** 在背景執行緒：只拆出原始物品標籤。 */
    void splitRaw() {
        Nbt.NbtList list = root.getList("Inventory");
        if (list != null) {
            for (Object o : list.items) {
                if (!(o instanceof Nbt.Compound c)) continue;
                int slot = c.get("Slot") instanceof Number n ? n.intValue() : Integer.MIN_VALUE;
                int idx = switch (slot) {
                    case 103 -> 36;
                    case 102 -> 37;
                    case 101 -> 38;
                    case 100 -> 39;
                    case -106 -> 40;
                    default -> slot >= 0 && slot < 36 ? slot : -1;
                };
                if (idx >= 0) rawInv[idx] = c;
            }
        }
        Nbt.Compound eq = root.getCompound("equipment");
        if (eq != null) {
            for (int i = 0; i < EQUIP_KEYS.length; i++) {
                Nbt.Compound c = eq.getCompound(EQUIP_KEYS[i]);
                if (c != null) rawInv[36 + i] = c;
            }
        }
        Nbt.NbtList ec = root.getList("EnderItems");
        if (ec != null) {
            for (Object o : ec.items) {
                if (!(o instanceof Nbt.Compound c)) continue;
                int slot = c.get("Slot") instanceof Number n ? n.intValue() : -1;
                if (slot >= 0 && slot < ENDER_SIZE) rawEnder[slot] = c;
            }
        }
    }

    /** 在主執行緒：把原始標籤轉成 ItemStack（會套用 DataFixer 升級舊版物品）。 */
    void decodeItems(int currentDataVersion) {
        editable = dataVersion == currentDataVersion;
        if (!editable) readOnlyReason = "offline.read-only-version";
        int dv = dataVersion > 0 ? dataVersion : currentDataVersion;
        for (int i = 0; i < INV_SIZE; i++) inv[i] = decode(rawInv[i], dv);
        for (int i = 0; i < ENDER_SIZE; i++) ender[i] = decode(rawEnder[i], dv);
    }

    private ItemStack decode(Nbt.Compound raw, int dv) {
        if (raw == null) return null;
        try {
            Nbt.Compound copy = new Nbt.Compound(raw);
            copy.remove("Slot");
            copy.put("DataVersion", dv);
            return ItemStack.deserializeBytes(Nbt.writeCompressed(copy));
        } catch (Throwable t) {
            editable = false;
            readOnlyReason = "offline.read-only-item";
            return null;
        }
    }

    /** 在主執行緒：把目前的物品與數值寫回 root 並序列化成檔案內容。 */
    byte[] encode() throws IOException {
        Nbt.NbtList oldInv = root.getList("Inventory");
        List<Object> invItems = new ArrayList<>();
        if (oldInv != null) {
            for (Object o : oldInv.items) {
                if (o instanceof Nbt.Compound c && c.get("Slot") instanceof Number n
                        && n.intValue() >= 0 && n.intValue() < 36) continue;
                invItems.add(o);
            }
        }
        for (int i = 0; i < 36; i++) {
            Nbt.Compound c = encodeItem(inv[i]);
            if (c == null) continue;
            c.put("Slot", (byte) i);
            invItems.add(c);
        }
        root.put("Inventory", new Nbt.NbtList(Nbt.COMPOUND, invItems));

        Nbt.Compound eq = root.getCompound("equipment");
        if (eq == null) eq = new Nbt.Compound();
        for (int i = 0; i < EQUIP_KEYS.length; i++) {
            Nbt.Compound c = encodeItem(inv[36 + i]);
            if (c == null) eq.remove(EQUIP_KEYS[i]);
            else eq.put(EQUIP_KEYS[i], c);
        }
        if (eq.isEmpty()) root.remove("equipment");
        else root.put("equipment", eq);

        List<Object> enderItems = new ArrayList<>();
        for (int i = 0; i < ENDER_SIZE; i++) {
            Nbt.Compound c = encodeItem(ender[i]);
            if (c == null) continue;
            c.put("Slot", (byte) i);
            enderItems.add(c);
        }
        root.put("EnderItems", new Nbt.NbtList(Nbt.COMPOUND, enderItems));
        return Nbt.writeCompressed(root);
    }

    private static Nbt.Compound encodeItem(ItemStack item) throws IOException {
        if (item == null || item.isEmpty()) return null;
        Nbt.Compound c = Nbt.readCompressed(item.serializeAsBytes());
        c.remove("DataVersion");
        return c;
    }

    public boolean isDiscarded() {
        return discarded;
    }

    public void markDirty() {
        dirty = true;
    }

    // ---------------- 數值 ----------------

    public double getHealth() {
        return root.getFloat("Health", 20f);
    }

    public void setHealth(double v) {
        root.put("Health", (float) v);
        dirty = true;
    }

    public int getFoodLevel() {
        return root.getInt("foodLevel", 20);
    }

    public void setFoodLevel(int v) {
        root.put("foodLevel", v);
        dirty = true;
    }

    public double getSaturation() {
        return root.getFloat("foodSaturationLevel", 5f);
    }

    public void setSaturation(double v) {
        root.put("foodSaturationLevel", (float) v);
        dirty = true;
    }

    public double getExhaustion() {
        return root.getFloat("foodExhaustionLevel", 0f);
    }

    public void setExhaustion(double v) {
        root.put("foodExhaustionLevel", (float) v);
        dirty = true;
    }

    public int getXpLevel() {
        return root.getInt("XpLevel", 0);
    }

    public void setXpLevel(int v) {
        root.put("XpLevel", v);
        dirty = true;
    }

    public double getXpProgress() {
        return root.getFloat("XpP", 0f);
    }

    public void setXpProgress(double v) {
        root.put("XpP", (float) v);
        dirty = true;
    }

    public int getSelectedSlot() {
        return root.getInt("SelectedItemSlot", 0);
    }

    public void setSelectedSlot(int v) {
        root.put("SelectedItemSlot", v);
        dirty = true;
    }

    /** 讀取儲存的位置；世界不存在時回傳 null。 */
    public Location getLocation() {
        Nbt.NbtList pos = root.getList("Pos");
        if (pos == null || pos.items.size() < 3) return null;
        World world = null;
        if (root.get("WorldUUIDMost") instanceof Long m && root.get("WorldUUIDLeast") instanceof Long l) {
            world = Bukkit.getWorld(new UUID(m, l));
        }
        if (world == null) {
            String dim = root.getString("Dimension");
            NamespacedKey key = dim == null ? null : NamespacedKey.fromString(dim);
            if (key != null) world = Bukkit.getWorld(key);
        }
        if (world == null) return null;
        float yaw = 0, pitch = 0;
        Nbt.NbtList rot = root.getList("Rotation");
        if (rot != null && rot.items.size() >= 2 && rot.items.get(0) instanceof Number y && rot.items.get(1) instanceof Number p) {
            yaw = y.floatValue();
            pitch = p.floatValue();
        }
        return new Location(world, num(pos, 0), num(pos, 1), num(pos, 2), yaw, pitch);
    }

    public String getDimensionName() {
        String d = root.getString("Dimension");
        return d == null ? "?" : d;
    }

    public void setLocation(Location loc) {
        List<Object> pos = new ArrayList<>(3);
        pos.add(loc.getX());
        pos.add(loc.getY());
        pos.add(loc.getZ());
        root.put("Pos", new Nbt.NbtList(Nbt.DOUBLE, pos));
        List<Object> rot = new ArrayList<>(2);
        rot.add(loc.getYaw());
        rot.add(loc.getPitch());
        root.put("Rotation", new Nbt.NbtList(Nbt.FLOAT, rot));
        World w = loc.getWorld();
        root.put("Dimension", w.getKey().toString());
        if (root.containsKey("WorldUUIDMost") || root.containsKey("WorldUUIDLeast")) {
            root.put("WorldUUIDMost", w.getUID().getMostSignificantBits());
            root.put("WorldUUIDLeast", w.getUID().getLeastSignificantBits());
        }
        dirty = true;
    }

    private static double num(Nbt.NbtList l, int i) {
        return l.items.get(i) instanceof Number n ? n.doubleValue() : 0;
    }
}
