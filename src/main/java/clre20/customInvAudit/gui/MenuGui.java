package clre20.customInvAudit.gui;

import clre20.customInvAudit.CustomInvAudit;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 一般清單選單（數值、位置、生物屬性）。每次刷新重新產生圖示以顯示最新值。 */
public final class MenuGui extends SubGui {

    public record Entry(Supplier<ItemStack> icon, Consumer<Player> click) {
    }

    private static final int MAX = 45;
    private final Supplier<List<Entry>> entries;

    public MenuGui(CustomInvAudit plugin, UUID viewer, GuiHolder parent, BooleanSupplier valid,
                   Component title, Supplier<List<Entry>> entries) {
        super(plugin, viewer, parent, valid);
        this.entries = entries;
        int n = Math.min(MAX, entries.get().size());
        create(Math.min(6, (n + 8) / 9 + 1), title);
    }

    @Override
    public void render() {
        if (!valid.getAsBoolean()) return;
        clearButtons();
        List<Entry> list = entries.get();
        int size = inventory.getSize();
        int n = Math.min(Math.min(MAX, list.size()), size - 9);
        for (int i = 0; i < size - 9; i++) inventory.setItem(i, null);
        for (int i = 0; i < n; i++) {
            Entry e = list.get(i);
            button(i, e.icon().get(), e.click());
        }
        backButton(size - 9);
        fillEmpty(0, size);
    }
}
