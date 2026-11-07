package clre20.customInvAudit.gui;

import clre20.customInvAudit.CustomInvAudit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 二次確認（清空等不可復原的操作）。 */
public final class ConfirmGui extends SubGui {

    private final String descKey;
    private final Object[] placeholders;
    private final Consumer<Player> onConfirm;

    public ConfirmGui(CustomInvAudit plugin, UUID viewer, GuiHolder parent, BooleanSupplier valid,
                      String descKey, Consumer<Player> onConfirm, Object... placeholders) {
        super(plugin, viewer, parent, valid);
        this.descKey = descKey;
        this.placeholders = placeholders;
        this.onConfirm = onConfirm;
        create(3, plugin.messages().text("confirm.title"));
    }

    @Override
    public void render() {
        button(11, item(Material.LIME_CONCRETE, "confirm.yes", descKey, placeholders), p -> {
            if (valid.getAsBoolean()) onConfirm.accept(p);
            back(p);
        });
        button(15, item(Material.RED_CONCRETE, "confirm.no", null), this::back);
        fillEmpty(0, inventory.getSize());
    }
}
