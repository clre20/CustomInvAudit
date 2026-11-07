package clre20.customInvAudit.gui;

import clre20.customInvAudit.CustomInvAudit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * 子選單基底：有「返回」按鈕回到上一層，並在目標失效時自動關閉。
 */
public abstract class SubGui extends GuiHolder {

    protected final GuiHolder parent;
    protected final BooleanSupplier valid;

    protected SubGui(CustomInvAudit plugin, UUID viewer, GuiHolder parent, BooleanSupplier valid) {
        super(plugin, viewer);
        this.parent = parent;
        this.valid = valid == null ? () -> true : valid;
    }

    @Override
    public boolean tick() {
        if (!valid.getAsBoolean()) return false;
        render();
        return true;
    }

    @Override
    public UUID targetId() {
        return parent == null ? null : parent.targetId();
    }

    @Override
    public clre20.customInvAudit.offline.OfflineData offlineData() {
        return parent == null ? null : parent.offlineData();
    }

    protected void backButton(int slot) {
        if (parent == null) {
            button(slot, item(Material.BARRIER, "gui.close", null), this::closeLater);
        } else {
            button(slot, item(Material.ARROW, "gui.back", null), p -> openLater(p, parent));
        }
    }

    protected void back(Player p) {
        if (parent != null) openLater(p, parent);
        else closeLater(p);
    }
}
