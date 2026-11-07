package clre20.customInvAudit.entity;

import clre20.customInvAudit.CustomInvAudit;
import clre20.customInvAudit.config.Messages;
import clre20.customInvAudit.gui.ChoiceGui;
import clre20.customInvAudit.gui.GuiHolder;
import clre20.customInvAudit.gui.NumberEditorGui;
import clre20.customInvAudit.gui.NumberField;
import clre20.customInvAudit.util.Items;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Colorable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 依生物種類產生可查看/修改的屬性清單。所有 lambda 都接收「當下重新取得的」生物實例，不長期持有。
 */
public final class EntityProps {

    /** 屬性項目。 */
    public interface Prop {
        ItemStack icon(LivingEntity e);

        /** 左鍵點擊；gui 為屬性選單本身（用來開子選單）。 */
        void click(Player p, LivingEntity e, GuiHolder gui);
    }

    private final CustomInvAudit plugin;
    private final Messages msg;
    private final UUID entityId;
    private final Predicate<Player> canEdit;
    private final String targetLabel;

    public EntityProps(CustomInvAudit plugin, UUID entityId, Predicate<Player> canEdit, String targetLabel) {
        this.plugin = plugin;
        this.msg = plugin.messages();
        this.entityId = entityId;
        this.canEdit = canEdit;
        this.targetLabel = targetLabel;
    }

    private LivingEntity resolve() {
        Entity e = Bukkit.getEntity(entityId);
        return e instanceof LivingEntity le && le.isValid() ? le : null;
    }

    // ---------------- 屬性型別 ----------------

    private Prop toggle(String key, Material icon, Predicate<LivingEntity> get, BiConsumer<LivingEntity, Boolean> set) {
        return new Prop() {
            @Override
            public ItemStack icon(LivingEntity e) {
                boolean v = get.test(e);
                return Items.glow(Items.make(icon, msg.text("property.toggle-name", "name", msg.text("property." + key),
                        "value", msg.text(v ? "gui.yes" : "gui.no")), msg.lore("property.toggle-lore")), v);
            }

            @Override
            public void click(Player p, LivingEntity e, GuiHolder gui) {
                if (!canEdit.test(p)) {
                    msg.send(p, "gui.no-permission-modify");
                    return;
                }
                boolean before = get.test(e);
                set.accept(e, !before);
                plugin.audit().valueChange(p, targetLabel, Messages.toPlain(msg.text("property." + key)),
                        String.valueOf(before), String.valueOf(get.test(e)));
                gui.render();
            }
        };
    }

    private <T> Prop choice(String key, String category, Material icon, Supplier<List<T>> options,
                            Function<LivingEntity, T> get, BiConsumer<LivingEntity, T> set, Function<T, Material> optIcon) {
        Function<T, String> label = t -> msg.valueName(category, keyOf(t));
        return new Prop() {
            @Override
            public ItemStack icon(LivingEntity e) {
                T v = get.apply(e);
                return Items.make(icon, msg.text("property.choice-name", "name", msg.text("property." + key),
                        "value", v == null ? "-" : label.apply(v)), msg.lore("property.choice-lore"));
            }

            @Override
            public void click(Player p, LivingEntity e, GuiHolder gui) {
                gui.openLater(p, new ChoiceGui<>(plugin, p.getUniqueId(), gui, () -> resolve() != null,
                        "property." + key, options.get(), label, optIcon == null ? t -> icon : optIcon,
                        () -> {
                            LivingEntity le = resolve();
                            return le == null ? null : get.apply(le);
                        },
                        t -> {
                            LivingEntity le = resolve();
                            if (le != null) set.accept(le, t);
                        }, canEdit, targetLabel));
            }
        };
    }

    private Prop number(NumberField field) {
        return new Prop() {
            @Override
            public ItemStack icon(LivingEntity e) {
                Double v = field.getter().get();
                return Items.make(field.icon(), msg.text("stats.entry-name", "name", msg.text(field.nameKey()),
                        "value", v == null ? "-" : field.format(v)), msg.lore("stats.entry-lore"));
            }

            @Override
            public void click(Player p, LivingEntity e, GuiHolder gui) {
                gui.openLater(p, new NumberEditorGui(plugin, p.getUniqueId(), gui, field, canEdit, targetLabel));
            }
        };
    }

    private Prop info(String key, Material icon, Function<LivingEntity, Object[]> placeholders) {
        return new Prop() {
            @Override
            public ItemStack icon(LivingEntity e) {
                Object[] ph = placeholders.apply(e);
                return Items.make(icon, msg.text("property." + key, ph), msg.lore("property." + key + "-lore", ph));
            }

            @Override
            public void click(Player p, LivingEntity e, GuiHolder gui) {
            }
        };
    }

    private Prop action(String key, Material icon, BiConsumer<Player, LivingEntity> run) {
        return new Prop() {
            @Override
            public ItemStack icon(LivingEntity e) {
                return Items.make(icon, msg.text("property." + key), msg.lore("property." + key + "-lore"));
            }

            @Override
            public void click(Player p, LivingEntity e, GuiHolder gui) {
                if (!canEdit.test(p)) {
                    msg.send(p, "gui.no-permission-modify");
                    return;
                }
                run.accept(p, e);
                plugin.audit().action(p, targetLabel, key, "");
                gui.render();
            }
        };
    }

    /** 生物用的數值欄位。 */
    public NumberField intField(String key, Material icon, int min, int max, int small, int big,
                                Function<LivingEntity, Integer> get, BiConsumer<LivingEntity, Integer> set) {
        return new NumberField("property." + key, icon, true, min, () -> max, small, big,
                () -> {
                    LivingEntity e = resolve();
                    return e == null ? null : (double) get.apply(e);
                },
                v -> {
                    LivingEntity e = resolve();
                    if (e != null) set.accept(e, (int) v);
                });
    }

    // ---------------- 工具 ----------------

    static String keyOf(Object o) {
        if (o instanceof Enum<?> en) return en.name().toLowerCase(Locale.ROOT);
        if (o instanceof Keyed k) return k.getKey().getKey();
        return String.valueOf(o).toLowerCase(Locale.ROOT);
    }

    private static <T extends Keyed> Supplier<List<T>> registry(RegistryKey<T> key) {
        return () -> {
            List<T> list = new ArrayList<>();
            for (T t : RegistryAccess.registryAccess().getRegistry(key)) list.add(t);
            list.sort((a, b) -> a.getKey().getKey().compareTo(b.getKey().getKey()));
            return list;
        };
    }

    private static <T> Supplier<List<T>> values(T[] arr) {
        List<T> list = List.copyOf(Arrays.asList(arr));
        return () -> list;
    }

    private static Material wool(DyeColor c) {
        Material m = c == null ? null : Material.matchMaterial(c.name() + "_WOOL");
        return m == null ? Material.WHITE_WOOL : m;
    }

    private String ownerName(UUID id) {
        if (id == null) return msg.raw("property.no-owner");
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() == null ? id.toString() : op.getName();
    }

    // ---------------- 清單 ----------------

    public List<Prop> build(LivingEntity e) {
        List<Prop> list = new ArrayList<>();

        // 共通
        list.add(toggle("custom-name-visible", Material.NAME_TAG, LivingEntity::isCustomNameVisible,
                Entity::setCustomNameVisible));
        list.add(toggle("glowing", Material.GLOWSTONE_DUST, Entity::isGlowing, Entity::setGlowing));
        list.add(toggle("ai", Material.REDSTONE_TORCH, LivingEntity::hasAI, LivingEntity::setAI));
        list.add(toggle("invulnerable", Material.SHIELD, Entity::isInvulnerable, Entity::setInvulnerable));
        list.add(toggle("silent", Material.NOTE_BLOCK, Entity::isSilent, Entity::setSilent));
        list.add(toggle("gravity", Material.ANVIL, Entity::hasGravity, Entity::setGravity));
        list.add(toggle("persistent", Material.BEDROCK, le -> !le.getRemoveWhenFarAway(),
                (le, v) -> le.setRemoveWhenFarAway(!v)));

        if (e instanceof Ageable) {
            list.add(toggle("baby", Material.EGG, le -> !((Ageable) le).isAdult(), (le, v) -> {
                if (v) ((Ageable) le).setBaby();
                else ((Ageable) le).setAdult();
            }));
        }
        if (e instanceof Tameable) {
            list.add(info("owner", Material.LEAD, le -> {
                Tameable t = (Tameable) le;
                return new Object[]{"owner", t.isTamed() ? ownerName(t.getOwnerUniqueId()) : msg.raw("property.no-owner")};
            }));
            list.add(action("tame-to-me", Material.BONE, (p, le) -> {
                Tameable t = (Tameable) le;
                t.setTamed(true);
                t.setOwner(p);
            }));
            list.add(action("untame", Material.SHEARS, (p, le) -> {
                Tameable t = (Tameable) le;
                t.setOwner(null);
                t.setTamed(false);
            }));
        }
        if (e instanceof Sittable) {
            list.add(toggle("sitting", Material.OAK_STAIRS, le -> ((Sittable) le).isSitting(),
                    (le, v) -> ((Sittable) le).setSitting(v)));
        }
        if (e instanceof Colorable && e instanceof LivingEntity) {
            list.add(choice("color", "dye-color", Material.WHITE_WOOL, values(DyeColor.values()),
                    le -> ((Colorable) le).getColor(), (le, v) -> ((Colorable) le).setColor(v), EntityProps::wool));
        }

        // 各別生物
        if (e instanceof Sheep) {
            list.add(toggle("sheared", Material.SHEARS, le -> ((Sheep) le).isSheared(), (le, v) -> ((Sheep) le).setSheared(v)));
        }
        if (e instanceof Villager) {
            list.add(choice("villager-profession", "villager-profession", Material.EMERALD,
                    registry(RegistryKey.VILLAGER_PROFESSION),
                    le -> ((Villager) le).getProfession(), (le, v) -> ((Villager) le).setProfession(v), null));
            list.add(choice("villager-type", "villager-type", Material.GRASS_BLOCK, registry(RegistryKey.VILLAGER_TYPE),
                    le -> ((Villager) le).getVillagerType(), (le, v) -> ((Villager) le).setVillagerType(v), null));
            list.add(number(intField("villager-level", Material.EXPERIENCE_BOTTLE, 1, 5, 1, 1,
                    le -> ((Villager) le).getVillagerLevel(), (le, v) -> ((Villager) le).setVillagerLevel(v))));
            list.add(number(intField("villager-experience", Material.EMERALD, 0, 250, 1, 10,
                    le -> ((Villager) le).getVillagerExperience(), (le, v) -> ((Villager) le).setVillagerExperience(v))));
            list.add(action("villager-restock", Material.BARREL, (p, le) -> ((Villager) le).restock()));
        }
        if (e instanceof ZombieVillager) {
            list.add(choice("villager-profession", "villager-profession", Material.EMERALD,
                    registry(RegistryKey.VILLAGER_PROFESSION),
                    le -> ((ZombieVillager) le).getVillagerProfession(),
                    (le, v) -> ((ZombieVillager) le).setVillagerProfession(v), null));
            list.add(choice("villager-type", "villager-type", Material.GRASS_BLOCK, registry(RegistryKey.VILLAGER_TYPE),
                    le -> ((ZombieVillager) le).getVillagerType(), (le, v) -> ((ZombieVillager) le).setVillagerType(v), null));
        }
        if (e instanceof Cat) {
            list.add(choice("cat-variant", "cat-variant", Material.STRING, registry(RegistryKey.CAT_VARIANT),
                    le -> ((Cat) le).getCatType(), (le, v) -> ((Cat) le).setCatType(v), null));
            list.add(choice("collar-color", "dye-color", Material.RED_DYE, values(DyeColor.values()),
                    le -> ((Cat) le).getCollarColor(), (le, v) -> ((Cat) le).setCollarColor(v), EntityProps::wool));
            list.add(toggle("lying-down", Material.RED_BED, le -> ((Cat) le).isLyingDown(), (le, v) -> ((Cat) le).setLyingDown(v)));
        }
        if (e instanceof Wolf) {
            list.add(choice("wolf-variant", "wolf-variant", Material.BONE, registry(RegistryKey.WOLF_VARIANT),
                    le -> ((Wolf) le).getVariant(), (le, v) -> ((Wolf) le).setVariant(v), null));
            list.add(choice("collar-color", "dye-color", Material.RED_DYE, values(DyeColor.values()),
                    le -> ((Wolf) le).getCollarColor(), (le, v) -> ((Wolf) le).setCollarColor(v), EntityProps::wool));
            list.add(toggle("angry", Material.IRON_SWORD, le -> ((Wolf) le).isAngry(), (le, v) -> ((Wolf) le).setAngry(v)));
        }
        if (e instanceof Fox) {
            list.add(choice("fox-type", "fox-type", Material.SWEET_BERRIES, values(Fox.Type.values()),
                    le -> ((Fox) le).getFoxType(), (le, v) -> ((Fox) le).setFoxType(v), null));
            list.add(toggle("sleeping", Material.WHITE_BED, LivingEntity::isSleeping, (le, v) -> ((Fox) le).setSleeping(v)));
            list.add(toggle("crouching", Material.LEATHER_BOOTS, le -> ((Fox) le).isCrouching(), (le, v) -> ((Fox) le).setCrouching(v)));
        }
        if (e instanceof Rabbit) {
            list.add(choice("rabbit-type", "rabbit-type", Material.RABBIT_HIDE, values(Rabbit.Type.values()),
                    le -> ((Rabbit) le).getRabbitType(), (le, v) -> ((Rabbit) le).setRabbitType(v), null));
        }
        if (e instanceof Salmon) {
            list.add(choice("salmon-variant", "salmon-variant", Material.SALMON, values(Salmon.Variant.values()),
                    le -> ((Salmon) le).getVariant(), (le, v) -> ((Salmon) le).setVariant(v), null));
        }
        if (e instanceof Axolotl) {
            list.add(choice("axolotl-variant", "axolotl-variant", Material.AXOLOTL_BUCKET, values(Axolotl.Variant.values()),
                    le -> ((Axolotl) le).getVariant(), (le, v) -> ((Axolotl) le).setVariant(v), null));
            list.add(toggle("playing-dead", Material.SKELETON_SKULL, le -> ((Axolotl) le).isPlayingDead(),
                    (le, v) -> ((Axolotl) le).setPlayingDead(v)));
        }
        if (e instanceof Panda) {
            list.add(choice("panda-main-gene", "panda-gene", Material.BAMBOO, values(Panda.Gene.values()),
                    le -> ((Panda) le).getMainGene(), (le, v) -> ((Panda) le).setMainGene(v), null));
            list.add(choice("panda-hidden-gene", "panda-gene", Material.BAMBOO, values(Panda.Gene.values()),
                    le -> ((Panda) le).getHiddenGene(), (le, v) -> ((Panda) le).setHiddenGene(v), null));
        }
        if (e instanceof Parrot) {
            list.add(choice("parrot-variant", "parrot-variant", Material.FEATHER, values(Parrot.Variant.values()),
                    le -> ((Parrot) le).getVariant(), (le, v) -> ((Parrot) le).setVariant(v), null));
        }
        if (e instanceof Llama) {
            list.add(choice("llama-color", "llama-color", Material.HAY_BLOCK, values(Llama.Color.values()),
                    le -> ((Llama) le).getColor(), (le, v) -> ((Llama) le).setColor(v), null));
            list.add(number(intField("llama-strength", Material.CHEST, 1, 5, 1, 1,
                    le -> ((Llama) le).getStrength(), (le, v) -> ((Llama) le).setStrength(v))));
        }
        if (e instanceof Horse) {
            list.add(choice("horse-color", "horse-color", Material.LEATHER, values(Horse.Color.values()),
                    le -> ((Horse) le).getColor(), (le, v) -> ((Horse) le).setColor(v), null));
            list.add(choice("horse-style", "horse-style", Material.WHITE_DYE, values(Horse.Style.values()),
                    le -> ((Horse) le).getStyle(), (le, v) -> ((Horse) le).setStyle(v), null));
        }
        if (e instanceof MushroomCow) {
            list.add(choice("mooshroom-variant", "mooshroom-variant", Material.RED_MUSHROOM, values(MushroomCow.Variant.values()),
                    le -> ((MushroomCow) le).getVariant(), (le, v) -> ((MushroomCow) le).setVariant(v), null));
        } else if (e instanceof Cow) {
            list.add(choice("cow-variant", "cow-variant", Material.LEATHER, registry(RegistryKey.COW_VARIANT),
                    le -> ((Cow) le).getVariant(), (le, v) -> ((Cow) le).setVariant(v), null));
        }
        if (e instanceof Pig) {
            list.add(choice("pig-variant", "pig-variant", Material.PORKCHOP, registry(RegistryKey.PIG_VARIANT),
                    le -> ((Pig) le).getVariant(), (le, v) -> ((Pig) le).setVariant(v), null));
        }
        if (e instanceof Chicken) {
            list.add(choice("chicken-variant", "chicken-variant", Material.FEATHER, registry(RegistryKey.CHICKEN_VARIANT),
                    le -> ((Chicken) le).getVariant(), (le, v) -> ((Chicken) le).setVariant(v), null));
        }
        if (e instanceof Frog) {
            list.add(choice("frog-variant", "frog-variant", Material.SLIME_BALL, registry(RegistryKey.FROG_VARIANT),
                    le -> ((Frog) le).getVariant(), (le, v) -> ((Frog) le).setVariant(v), null));
        }
        if (e instanceof Creeper) {
            list.add(toggle("creeper-powered", Material.CREEPER_HEAD, le -> ((Creeper) le).isPowered(),
                    (le, v) -> ((Creeper) le).setPowered(v)));
            list.add(number(intField("creeper-radius", Material.TNT, 0, 10, 1, 3,
                    le -> ((Creeper) le).getExplosionRadius(), (le, v) -> ((Creeper) le).setExplosionRadius(v))));
            list.add(number(intField("creeper-fuse", Material.FLINT_AND_STEEL, 1, 200, 5, 20,
                    le -> ((Creeper) le).getMaxFuseTicks(), (le, v) -> ((Creeper) le).setMaxFuseTicks(v))));
        }
        if (e instanceof Goat) {
            list.add(toggle("goat-screaming", Material.GOAT_HORN, le -> ((Goat) le).isScreaming(), (le, v) -> ((Goat) le).setScreaming(v)));
            list.add(toggle("goat-left-horn", Material.GOAT_HORN, le -> ((Goat) le).hasLeftHorn(), (le, v) -> ((Goat) le).setLeftHorn(v)));
            list.add(toggle("goat-right-horn", Material.GOAT_HORN, le -> ((Goat) le).hasRightHorn(), (le, v) -> ((Goat) le).setRightHorn(v)));
        }
        if (e instanceof IronGolem) {
            list.add(toggle("player-created", Material.IRON_BLOCK, le -> ((IronGolem) le).isPlayerCreated(),
                    (le, v) -> ((IronGolem) le).setPlayerCreated(v)));
        }
        if (e instanceof Snowman) {
            list.add(toggle("snowman-pumpkin", Material.CARVED_PUMPKIN, le -> !((Snowman) le).isDerp(),
                    (le, v) -> ((Snowman) le).setDerp(!v)));
        }
        if (e instanceof Bat) {
            list.add(toggle("bat-awake", Material.BLACK_DYE, le -> ((Bat) le).isAwake(), (le, v) -> ((Bat) le).setAwake(v)));
        }
        if (e instanceof Bee) {
            list.add(toggle("bee-nectar", Material.HONEYCOMB, le -> ((Bee) le).hasNectar(), (le, v) -> ((Bee) le).setHasNectar(v)));
            list.add(toggle("bee-stung", Material.IRON_SWORD, le -> ((Bee) le).hasStung(), (le, v) -> ((Bee) le).setHasStung(v)));
            list.add(number(intField("bee-anger", Material.BLAZE_POWDER, 0, 2400, 20, 200,
                    le -> ((Bee) le).getAnger(), (le, v) -> ((Bee) le).setAnger(v))));
            list.add(info("bee-hive", Material.BEEHIVE, le -> {
                Location h = ((Bee) le).getHive();
                return new Object[]{"location", h == null ? msg.raw("property.no-hive")
                        : h.getBlockX() + " " + h.getBlockY() + " " + h.getBlockZ()};
            }));
        }
        if (e instanceof Ocelot) {
            list.add(toggle("ocelot-trusting", Material.COD, le -> ((Ocelot) le).isTrusting(), (le, v) -> ((Ocelot) le).setTrusting(v)));
        }
        if (e instanceof Slime) {
            list.add(number(intField("slime-size", Material.SLIME_BALL, 1, 8, 1, 2,
                    le -> ((Slime) le).getSize(), (le, v) -> ((Slime) le).setSize(v))));
        }
        return list;
    }
}
