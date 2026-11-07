package clre20.customInvAudit.gui;

import clre20.customInvAudit.CustomInvAudit;
import clre20.customInvAudit.config.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * 數值修改子選單（只吃左鍵）：-大、-小、目前值、+小、+大、設為最小、設為最大。
 */
public final class NumberEditorGui extends SubGui {

    private final NumberField field;
    private final Predicate<Player> canEdit;
    private final String targetLabel;

    public NumberEditorGui(CustomInvAudit plugin, UUID viewer, GuiHolder parent, NumberField field,
                           Predicate<Player> canEdit, String targetLabel) {
        super(plugin, viewer, parent, () -> field.getter().get() != null);
        this.field = field;
        this.canEdit = canEdit;
        this.targetLabel = targetLabel;
        create(3, plugin.messages().text("number.title", "name", plugin.messages().text(field.nameKey())));
    }

    @Override
    public void render() {
        Double cur = field.getter().get();
        if (cur == null) return;
        String name = Messages.toPlain(msg.text(field.nameKey()));
        String min = field.format(field.min());
        String max = field.format(field.max().getAsDouble());
        button(13, item(field.icon(), "number.display", "number.display-lore",
                "name", name, "value", field.format(cur), "min", min, "max", max), null);
        stepButton(10, -field.big());
        stepButton(11, -field.small());
        stepButton(15, field.small());
        stepButton(16, field.big());
        button(21, item(Material.REDSTONE, "number.set-min", null, "value", min), p -> apply(p, field.min()));
        button(23, item(Material.GLOWSTONE_DUST, "number.set-max", null, "value", max),
                p -> apply(p, field.max().getAsDouble()));
        backButton(18);
        fillEmpty(0, inventory.getSize());
    }

    private void stepButton(int slot, double step) {
        boolean plus = step > 0;
        String text = field.format(Math.abs(step));
        button(slot, item(plus ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                plus ? "number.increase" : "number.decrease", null, "step", text), p -> {
            Double cur = field.getter().get();
            if (cur != null) apply(p, cur + step);
        });
    }

    private void apply(Player p, double value) {
        if (!canEdit.test(p)) {
            msg.send(p, "gui.no-permission-modify");
            return;
        }
        Double before = field.getter().get();
        if (before == null) return;
        double v = field.clamp(value);
        if (v == before) return;
        field.setter().accept(v);
        Double after = field.getter().get();
        plugin.audit().valueChange(p, targetLabel, Messages.toPlain(msg.text(field.nameKey())),
                field.format(before), field.format(after == null ? v : after));
        render();
    }
}
