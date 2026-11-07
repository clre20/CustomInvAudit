package clre20.customInvAudit.gui;

import clre20.customInvAudit.CustomInvAudit;
import clre20.customInvAudit.config.Messages;
import clre20.customInvAudit.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 從固定選項中挑一個（羊毛顏色、村民職業、品種…）。左鍵選擇。
 */
public final class ChoiceGui<T> extends SubGui {

    private static final int MAX = 45;

    private final String nameKey;
    private final List<T> options;
    private final Function<T, String> label;
    private final Function<T, Material> icon;
    private final Supplier<T> current;
    private final Consumer<T> setter;
    private final Predicate<Player> canEdit;
    private final String targetLabel;

    public ChoiceGui(CustomInvAudit plugin, UUID viewer, GuiHolder parent, BooleanSupplier valid, String nameKey,
                     List<T> options, Function<T, String> label, Function<T, Material> icon,
                     Supplier<T> current, Consumer<T> setter, Predicate<Player> canEdit, String targetLabel) {
        super(plugin, viewer, parent, valid);
        this.nameKey = nameKey;
        this.options = options.size() > MAX ? options.subList(0, MAX) : options;
        this.label = label;
        this.icon = icon;
        this.current = current;
        this.setter = setter;
        this.canEdit = canEdit;
        this.targetLabel = targetLabel;
        int rows = Math.min(6, (this.options.size() + 8) / 9 + 1);
        create(rows, plugin.messages().text("choice.title", "name", plugin.messages().text(nameKey)));
    }

    @Override
    public void render() {
        if (!valid.getAsBoolean()) return;
        T cur = current.get();
        for (int i = 0; i < options.size(); i++) {
            T opt = options.get(i);
            boolean selected = Objects.equals(opt, cur);
            button(i, Items.glow(item(icon.apply(opt), selected ? "choice.selected-name" : "choice.option-name",
                    selected ? "choice.selected-lore" : "choice.option-lore", "value", label.apply(opt)), selected),
                    selected ? null : p -> choose(p, opt));
        }
        int size = inventory.getSize();
        backButton(size - 9);
        fillEmpty(0, size);
    }

    private void choose(Player p, T opt) {
        if (!valid.getAsBoolean()) return;
        if (!canEdit.test(p)) {
            msg.send(p, "gui.no-permission-modify");
            return;
        }
        T before = current.get();
        setter.accept(opt);
        plugin.audit().valueChange(p, targetLabel, Messages.toPlain(msg.text(nameKey)),
                before == null ? "-" : label.apply(before), label.apply(opt));
        render();
    }
}
