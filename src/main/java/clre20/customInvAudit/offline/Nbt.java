package clre20.customInvAudit.offline;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 輕量 NBT 讀寫（只用於玩家資料檔與物品序列化轉換，不依賴伺服器內部類別）。
 * 數值型別對應：Byte/Short/Integer/Long/Float/Double/byte[]/String/NbtList/Compound/int[]/long[]。
 */
public final class Nbt {

    public static final byte END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6,
            BYTE_ARRAY = 7, STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;

    private static final int MAX_DEPTH = 512;
    private static final int MAX_ARRAY = 16 * 1024 * 1024;
    /** 解壓後最多 64 MB，防止壓縮炸彈。 */
    private static final long MAX_UNCOMPRESSED = 64L * 1024 * 1024;

    private static final class LimitedInputStream extends java.io.FilterInputStream {
        private long remaining;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) throw new IOException("NBT 資料過大");
            int b = super.read();
            if (b >= 0) remaining--;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) throw new IOException("NBT 資料過大");
            int n = super.read(b, off, (int) Math.min(len, remaining));
            if (n > 0) remaining -= n;
            return n;
        }
    }

    private Nbt() {
    }

    /** 有序的 Compound。 */
    public static final class Compound extends LinkedHashMap<String, Object> {
        public Compound() {
        }

        public Compound(Map<String, Object> other) {
            super(other);
        }

        public Compound getCompound(String key) {
            return get(key) instanceof Compound c ? c : null;
        }

        public NbtList getList(String key) {
            return get(key) instanceof NbtList l ? l : null;
        }

        public int getInt(String key, int def) {
            return get(key) instanceof Number n ? n.intValue() : def;
        }

        public float getFloat(String key, float def) {
            return get(key) instanceof Number n ? n.floatValue() : def;
        }

        public String getString(String key) {
            return get(key) instanceof String s ? s : null;
        }
    }

    /** 列表（記住元素型別以便原樣寫回）。 */
    public static final class NbtList {
        public byte type;
        public final List<Object> items;

        public NbtList(byte type, List<Object> items) {
            this.type = type;
            this.items = items;
        }
    }

    // ---------------- 讀 ----------------

    public static Compound readCompressed(byte[] data) throws IOException {
        return readCompressed(new ByteArrayInputStream(data));
    }

    public static Compound readCompressed(InputStream in) throws IOException {
        try (DataInputStream din = new DataInputStream(new BufferedInputStream(
                new LimitedInputStream(new GZIPInputStream(in), MAX_UNCOMPRESSED)))) {
            byte type = din.readByte();
            if (type != COMPOUND) throw new IOException("根標籤不是 Compound");
            din.readUTF();
            return (Compound) readPayload(din, COMPOUND, 0);
        }
    }

    private static Object readPayload(DataInputStream in, byte type, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT 巢狀過深");
        switch (type) {
            case BYTE:
                return in.readByte();
            case SHORT:
                return in.readShort();
            case INT:
                return in.readInt();
            case LONG:
                return in.readLong();
            case FLOAT:
                return in.readFloat();
            case DOUBLE:
                return in.readDouble();
            case BYTE_ARRAY: {
                byte[] a = new byte[checkLen(in.readInt())];
                in.readFully(a);
                return a;
            }
            case STRING:
                return in.readUTF();
            case LIST: {
                byte et = in.readByte();
                int len = checkLen(in.readInt());
                List<Object> list = new ArrayList<>(Math.min(len, 1024));
                for (int i = 0; i < len; i++) list.add(readPayload(in, et, depth + 1));
                return new NbtList(et, list);
            }
            case COMPOUND: {
                Compound c = new Compound();
                while (true) {
                    byte t = in.readByte();
                    if (t == END) break;
                    String name = in.readUTF();
                    c.put(name, readPayload(in, t, depth + 1));
                }
                return c;
            }
            case INT_ARRAY: {
                int len = checkLen(in.readInt());
                int[] a = new int[len];
                for (int i = 0; i < len; i++) a[i] = in.readInt();
                return a;
            }
            case LONG_ARRAY: {
                int len = checkLen(in.readInt());
                long[] a = new long[len];
                for (int i = 0; i < len; i++) a[i] = in.readLong();
                return a;
            }
            case END:
                return null;
            default:
                throw new IOException("未知的 NBT 型別 " + type);
        }
    }

    private static int checkLen(int len) throws IOException {
        if (len < 0 || len > MAX_ARRAY) throw new IOException("NBT 長度異常 " + len);
        return len;
    }

    // ---------------- 寫 ----------------

    public static byte[] writeCompressed(Compound root) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(4096);
        writeCompressed(root, bos);
        return bos.toByteArray();
    }

    public static void writeCompressed(Compound root, OutputStream out) throws IOException {
        try (DataOutputStream dout = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(out)))) {
            dout.writeByte(COMPOUND);
            dout.writeUTF("");
            writePayload(dout, root);
        }
    }

    public static byte typeOf(Object v) {
        if (v instanceof Byte) return BYTE;
        if (v instanceof Short) return SHORT;
        if (v instanceof Integer) return INT;
        if (v instanceof Long) return LONG;
        if (v instanceof Float) return FLOAT;
        if (v instanceof Double) return DOUBLE;
        if (v instanceof byte[]) return BYTE_ARRAY;
        if (v instanceof String) return STRING;
        if (v instanceof NbtList) return LIST;
        if (v instanceof Compound) return COMPOUND;
        if (v instanceof int[]) return INT_ARRAY;
        if (v instanceof long[]) return LONG_ARRAY;
        throw new IllegalArgumentException("不支援的 NBT 值 " + (v == null ? "null" : v.getClass()));
    }

    private static void writePayload(DataOutputStream out, Object v) throws IOException {
        switch (typeOf(v)) {
            case BYTE -> out.writeByte((Byte) v);
            case SHORT -> out.writeShort((Short) v);
            case INT -> out.writeInt((Integer) v);
            case LONG -> out.writeLong((Long) v);
            case FLOAT -> out.writeFloat((Float) v);
            case DOUBLE -> out.writeDouble((Double) v);
            case BYTE_ARRAY -> {
                byte[] a = (byte[]) v;
                out.writeInt(a.length);
                out.write(a);
            }
            case STRING -> out.writeUTF((String) v);
            case LIST -> {
                NbtList l = (NbtList) v;
                byte et = l.items.isEmpty() ? (l.type == 0 ? END : l.type) : typeOf(l.items.get(0));
                out.writeByte(et);
                out.writeInt(l.items.size());
                for (Object o : l.items) writePayload(out, o);
            }
            case COMPOUND -> {
                for (Map.Entry<String, Object> e : ((Compound) v).entrySet()) {
                    if (e.getValue() == null) continue;
                    out.writeByte(typeOf(e.getValue()));
                    out.writeUTF(e.getKey());
                    writePayload(out, e.getValue());
                }
                out.writeByte(END);
            }
            case INT_ARRAY -> {
                int[] a = (int[]) v;
                out.writeInt(a.length);
                for (int i : a) out.writeInt(i);
            }
            case LONG_ARRAY -> {
                long[] a = (long[]) v;
                out.writeInt(a.length);
                for (long l : a) out.writeLong(l);
            }
            default -> throw new IOException("unreachable");
        }
    }
}
