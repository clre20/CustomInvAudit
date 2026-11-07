package clre20.customInvAudit.gui;

import clre20.customInvAudit.CustomInvAudit;
import clre20.customInvAudit.config.Messages;
import clre20.customInvAudit.entity.EntityProps;
import clre20.customInvAudit.source.EntitySource;
import clre20.customInvAudit.util.Items;
import clre20.customInvAudit.util.Perms;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 生物介面（45 格）：
 * 第 1 列裝備（頭、胸、腿、腳、主手、副手、身體、鞍、手持方塊），第 2~4 列生物背包，第 5 列功能按鈕。
 */
public final class EntityGui extends MirrorGui {

    private final UUID entityId;
    private final String label;
    private final String typeName;
    private final Material icon;

    public EntityGui(CustomInvAudit plugin, UUID viewer, LivingEntity e) {
        super(plugin, viewer, new EntitySource(e), layout(new EntitySource(e)));
        this.entityId = e.getUniqueId();
        Messages msg = plugin.messages();
        this.typeName = msg.valueName("entity-type", e.getType().getKey().getKey());
        String name = e.customName() != null ? Messages.toPlain(e.customName()) : typeName;
        this.label = msg.plain("audit-log.target-entity", "type", e.getType().getKey().getKey(),
                "name", name, "uuid", entityId);
        Material egg = Material.matchMaterial(e.getType().getKey().getKey().toUpperCase(Locale.ROOT) + "_SPAWN_EGG");
        this.icon = egg == null ? Material.NAME_TAG : egg;
        create(5, msg.text("entity.title", "name", name));
    }

    private static int[] layout(EntitySource s) {
        int[] map = new int[s.size()];
        for (int i = 0; i < map.length; i++) map[i] = s.isAvailable(i) ? i : -1;
        return map;
    }

    private EntitySource src() {
        return (EntitySource) source;
    }

    @Override
    public UUID targetId() {
        return entityId;
    }

    @Override
    public String invalidMessage() {
        return "entity.gone";
    }

    @Override
    protected boolean canModify(Player p) {
        return p.hasPermission(Perms.ENTITY_MODIFY);
    }

    @Override
    protected String targetLabel() {
        return label;
    }

    @Override
    protected void renderButtons() {
        for (int i = 0; i < 36; i++) {
            if (slotOf.length <= i || slotOf[i] < 0) inventory.setItem(i, filler());
        }
        LivingEntity e = src().entity();
        if (e == null) return;
        Predicate<Player> canEdit = this::canModify;

        NumberField health = healthField(false);
        NumberField maxHealth = healthField(true);
        button(36, item(Material.RED_DYE, "stats.entry-name", "stats.entry-lore",
                "name", msg.text(health.nameKey()), "value", fmt(health)), p ->
                openLater(p, new NumberEditorGui(plugin, p.getUniqueId(), this, health, canEdit, label)));
        button(37, item(Material.GOLDEN_APPLE, "stats.entry-name", "stats.entry-lore",
                "name", msg.text(maxHealth.nameKey()), "value", fmt(maxHealth)), p ->
                openLater(p, new NumberEditorGui(plugin, p.getUniqueId(), this, maxHealth, canEdit, label)));
        button(38, PlayerMenus.locationItem(plugin, e.getLocation(), "?"), p -> openLater(p, locationMenu(p)));
        button(39, item(Material.WRITABLE_BOOK, "entity.properties-name", "entity.properties-lore"),
                p -> openLater(p, propertiesMenu(p)));
        button(40, Items.make(icon, msg.text("entity.info-name", "type", typeName),
                msg.lore("entity.info-lore", "uuid", entityId, "type", typeName)), null);
        button(41, item(Material.OAK_SIGN, "entity.help-name", "entity.help-lore"), null);
        button(42, item(Material.LAVA_BUCKET, "entity.clear-name", "entity.clear-lore"), p -> {
            if (!canModify(p)) {
                msg.send(p, "gui.no-permission-modify");
                return;
            }
            openLater(p, new ConfirmGui(plugin, p.getUniqueId(), this, source::valid, "confirm.clear-entity",
                    this::clearAll, "name", typeName));
        });
        button(43, item(Material.SUNFLOWER, "gui.refresh", null), p -> render());
        button(44, item(Material.BARRIER, "gui.close", null), this::closeLater);
    }

    private String fmt(NumberField f) {
        Double v = f.getter().get();
        return v == null ? "-" : f.format(v);
    }

    private NumberField healthField(boolean max) {
        if (max) {
            return new NumberField("field.max-health", Material.GOLDEN_APPLE, false, 1, () -> 1024, 1, 10,
                    () -> {
                        LivingEntity e = src().entity();
                        AttributeInstance a = e == null ? null : e.getAttribute(Attribute.MAX_HEALTH);
                        return a == null ? null : a.getBaseValue();
                    },
                    v -> {
                        LivingEntity e = src().entity();
                        AttributeInstance a = e == null ? null : e.getAttribute(Attribute.MAX_HEALTH);
                        if (a == null) return;
                        a.setBaseValue(v);
                        if (e.getHealth() > a.getValue()) e.setHealth(a.getValue());
                    });
        }
        return new NumberField("field.health", Material.RED_DYE, false, 0.5, () -> {
            LivingEntity e = src().entity();
            AttributeInstance a = e == null ? null : e.getAttribute(Attribute.MAX_HEALTH);
            return a == null ? 20 : a.getValue();
        }, 1, 5,
                () -> {
                    LivingEntity e = src().entity();
                    return e == null ? null : e.getHealth();
                },
                v -> {
                    LivingEntity e = src().entity();
                    if (e == null) return;
                    AttributeInstance a = e.getAttribute(Attribute.MAX_HEALTH);
                    e.setHealth(Math.min(v, a == null ? v : a.getValue()));
                });
    }

    private MenuGui locationMenu(Player viewer) {
        return new MenuGui(plugin, viewer.getUniqueId(), this, source::valid,
                msg.text("location.title", "name", typeName), () -> {
            List<MenuGui.Entry> list = new ArrayList<>();
            LivingEntity e = src().entity();
            list.add(new MenuGui.Entry(() -> PlayerMenus.locationItem(plugin, e == null ? null : e.getLocation(), "?"), null));
            list.add(new MenuGui.Entry(() -> Items.make(Material.ENDER_PEARL, msg.text("location.tp-to"),
                    msg.lore("location.tp-to-lore")), p -> {
                LivingEntity le = src().entity();
                if (le == null) return;
                if (!p.hasPermission(Perms.TELEPORT)) {
                    msg.send(p, "gui.no-permission");
                    return;
                }
                p.closeInventory();
                p.teleportAsync(le.getLocation());
                plugin.audit().action(p, label, "teleport-to", "");
            }));
            list.add(new MenuGui.Entry(() -> Items.make(Material.LEAD, msg.text("location.tp-here"),
                    msg.lore("location.tp-here-lore")), p -> {
                LivingEntity le = src().entity();
                if (le == null) return;
                if (!p.hasPermission(Perms.TELEPORT) || !canModify(p)) {
                    msg.send(p, "gui.no-permission");
                    return;
                }
                Location here = p.getLocation();
                le.teleportAsync(here);
                msg.send(p, "location.moved-here", "name", typeName);
                plugin.audit().action(p, label, "teleport-here", String.format(Locale.ROOT, "%.1f,%.1f,%.1f@%s",
                        here.getX(), here.getY(), here.getZ(), here.getWorld().getName()));
            }));
            return list;
        });
    }

    private MenuGui propertiesMenu(Player viewer) {
        EntityProps props = new EntityProps(plugin, entityId, this::canModify, label);
        LivingEntity first = src().entity();
        List<EntityProps.Prop> list = first == null ? List.of() : props.build(first);
        MenuGui[] self = new MenuGui[1];
        self[0] = new MenuGui(plugin, viewer.getUniqueId(), this, source::valid,
                msg.text("entity.properties-title", "name", typeName), () -> {
            List<MenuGui.Entry> entries = new ArrayList<>(list.size());
            for (EntityProps.Prop prop : list) {
                entries.add(new MenuGui.Entry(() -> {
                    LivingEntity le = src().entity();
                    return le == null ? filler() : prop.icon(le);
                }, p -> {
                    LivingEntity le = src().entity();
                    if (le != null) prop.click(p, le, self[0]);
                }));
            }
            return entries;
        });
        return self[0];
    }

    private void clearAll(Player p) {
        if (!canModify(p) || !source.valid()) return;
        int n = 0;
        for (int i = 0; i < source.size(); i++) {
            if (!src().isAvailable(i)) continue;
            ItemStack it = source.get(i);
            if (!Items.isEmpty(it)) {
                source.set(i, null);
                n++;
            }
        }
        plugin.audit().action(p, label, "clear-entity", n + " slots");
        msg.send(p, "entity.cleared", "name", typeName);
    }
}
