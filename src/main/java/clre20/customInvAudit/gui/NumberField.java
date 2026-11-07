package clre20.customInvAudit.gui;

import org.bukkit.Material;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * 數值欄位定義。getter 回傳 null 代表目標已失效。
 *
 * @param nameKey messages 路徑（欄位名稱）
 * @param max     動態上限（例如血量上限 = 目前最大血量）
 */
public record NumberField(String nameKey, Material icon, boolean integer, double min, DoubleSupplier max,
                          double small, double big, Supplier<Double> getter, DoubleConsumer setter) {

    public String format(double v) {
        return integer ? String.valueOf((long) Math.round(v)) : String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    public double clamp(double v) {
        double hi = max.getAsDouble();
        if (hi < min) hi = min;
        double c = Math.max(min, Math.min(hi, v));
        return integer ? Math.round(c) : c;
    }
}
