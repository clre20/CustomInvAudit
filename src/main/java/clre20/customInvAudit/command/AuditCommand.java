package clre20.customInvAudit.command;

import clre20.customInvAudit.CustomInvAudit;
import clre20.customInvAudit.config.Messages;
import clre20.customInvAudit.gui.EnderChestGui;
import clre20.customInvAudit.gui.EntityGui;
import clre20.customInvAudit.gui.GuiHolder;
import clre20.customInvAudit.gui.PlayerInventoryGui;
import clre20.customInvAudit.gui.PlayerTarget;
import clre20.customInvAudit.offline.OfflineData;
import clre20.customInvAudit.offline.OfflineDataManager;
import clre20.customInvAudit.update.UpdateChecker;
import clre20.customInvAudit.util.Perms;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * /custominvaudit（/ca）：inv、ec、entity、version、reload。
 */
public final class AuditCommand implements TabExecutor {

    /** 玩家名稱（含 Bedrock 前綴字元）或 UUID。 */
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_.*\\-]{1,32}$");
    private static final List<String> SUBS = List.of("inv", "ec", "entity", "version", "reload");

    private final CustomInvAudit plugin;
    private final Messages msg;
    /** 正在讀取離線資料的檢視者（防止連續送指令塞爆背景佇列）。 */
    private final java.util.Set<UUID> loading = new java.util.HashSet<>();

    public AuditCommand(CustomInvAudit plugin) {
        this.plugin = plugin;
        this.msg = plugin.messages();
    }

    private static String perm(String sub) {
        return switch (sub) {
            case "inv" -> Perms.INV_VIEW;
            case "ec" -> Perms.EC_SELF + "|" + Perms.EC_OTHER;
            case "entity" -> Perms.ENTITY_VIEW;
            case "version" -> Perms.VERSION;
            case "reload" -> Perms.RELOAD;
            default -> null;
        };
    }

    private static boolean allowed(CommandSender s, String sub) {
        String p = perm(sub);
        if (p == null) return false;
        for (String node : p.split("\\|")) if (s.hasPermission(node)) return true;
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!SUBS.contains(sub)) {
            msg.send(sender, "command.unknown", "label", label);
            return true;
        }
        if (!allowed(sender, sub)) {
            msg.send(sender, "command.no-permission");
            return true;
        }
        switch (sub) {
            case "inv" -> inv(sender, label, args);
            case "ec" -> ec(sender, label, args);
            case "entity" -> entity(sender, label, args);
            case "version" -> version(sender);
            case "reload" -> {
                plugin.reloadAll();
                msg.send(sender, "command.reloaded");
            }
            default -> {
            }
        }
        return true;
    }

    private void help(CommandSender s, String label) {
        msg.send(s, "command.help-header");
        for (String sub : SUBS) {
            if (allowed(s, sub)) msg.send(s, "command.help-" + sub, "label", label);
        }
    }

    // ---------------- inv / ec ----------------

    private void inv(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            msg.send(sender, "command.player-only");
            return;
        }
        if (args.length < 2) {
            msg.send(sender, "command.usage-inv", "label", label);
            return;
        }
        if (isSelf(p, args[1])) {
            msg.send(p, "inventory.self");
            return;
        }
        resolveAndOpen(p, args[1], t -> new PlayerInventoryGui(plugin, p.getUniqueId(), t), "inventory");
    }

    private void ec(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            msg.send(sender, "command.player-only");
            return;
        }
        if (args.length < 2 || isSelf(p, args[1])) {
            if (!p.hasPermission(Perms.EC_SELF)) {
                msg.send(p, "command.no-permission");
                return;
            }
            PlayerTarget self = new PlayerTarget(p.getUniqueId(), p.getName(), null);
            plugin.guis().open(p, new EnderChestGui(plugin, p.getUniqueId(), self, null));
            return;
        }
        if (!p.hasPermission(Perms.EC_OTHER)) {
            msg.send(p, "command.no-permission");
            return;
        }
        resolveAndOpen(p, args[1], t -> new EnderChestGui(plugin, p.getUniqueId(), t, null), "enderchest");
    }

    private static boolean isSelf(Player p, String arg) {
        return arg.equalsIgnoreCase(p.getName()) || arg.equalsIgnoreCase(p.getUniqueId().toString());
    }

    private void resolveAndOpen(Player p, String arg, Function<PlayerTarget, GuiHolder> factory, String what) {
        UUID uuid = parseUuid(arg);
        if (uuid == null && !NAME.matcher(arg).matches()) {
            msg.send(p, "command.invalid-name");
            return;
        }
        Player online = uuid != null ? Bukkit.getPlayer(uuid) : Bukkit.getPlayerExact(arg);
        if (online != null) {
            if (online.equals(p)) {
                msg.send(p, "inventory.self");
                return;
            }
            if (plugin.isProtected(p, online.getUniqueId(), online.getName())) {
                msg.send(p, "command.exempt", "player", online.getName());
                return;
            }
            PlayerTarget t = new PlayerTarget(online.getUniqueId(), online.getName(), null);
            plugin.guis().open(p, factory.apply(t));
            plugin.audit().view(p, t.label(plugin), what);
            return;
        }

        // 離線
        if (!plugin.config().offlineEnabled) {
            msg.send(p, "command.player-not-online", "player", arg);
            return;
        }
        if (!p.hasPermission(Perms.OFFLINE)) {
            msg.send(p, "command.no-permission-offline");
            return;
        }
        OfflinePlayer op = uuid != null ? Bukkit.getOfflinePlayer(uuid) : Bukkit.getOfflinePlayerIfCached(arg);
        if (op == null || !op.hasPlayedBefore()) {
            msg.send(p, "command.never-joined", "player", arg);
            return;
        }
        UUID id = op.getUniqueId();
        String name = op.getName() != null ? op.getName() : arg;
        if (id.equals(p.getUniqueId())) {
            msg.send(p, "inventory.self");
            return;
        }
        if (plugin.isProtected(p, id, name)) {
            msg.send(p, "command.exempt", "player", name);
            return;
        }
        UUID viewer = p.getUniqueId();
        if (!loading.add(viewer)) {
            msg.send(p, "offline.busy");
            return;
        }
        msg.send(p, "offline.loading", "player", name);
        plugin.offline().load(id, name).whenComplete((data, err) -> {
            loading.remove(viewer);
            Player v = Bukkit.getPlayer(viewer);
            if (v == null) return;
            if (err != null) {
                Throwable cause = err instanceof CompletionException ce && ce.getCause() != null ? ce.getCause() : err;
                if (cause instanceof OfflineDataManager.LoadException le) {
                    switch (le.error) {
                        case NO_FILE -> msg.send(v, "offline.no-file", "player", name);
                        case LOCKED -> msg.send(v, "offline.locked", "player", name);
                        default -> msg.send(v, "offline.load-failed", "player", name);
                    }
                } else {
                    msg.send(v, "offline.load-failed", "player", name);
                }
                return;
            }
            openOffline(v, data, factory, what);
        });
    }

    private void openOffline(Player v, OfflineData data, Function<PlayerTarget, GuiHolder> factory, String what) {
        // 權限可能在讀檔期間被收回，開啟前再檢查一次
        if (!v.hasPermission(Perms.OFFLINE)) return;
        PlayerTarget t = new PlayerTarget(data.uuid, data.name, data);
        plugin.guis().open(v, factory.apply(t));
        if (!data.editable && data.readOnlyReason != null) msg.send(v, data.readOnlyReason);
        plugin.audit().view(v, t.label(plugin), what + " (offline)");
    }

    private static UUID parseUuid(String s) {
        if (s.length() != 36) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------------- entity ----------------

    private void entity(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            msg.send(sender, "command.player-only");
            return;
        }
        Entity target;
        if (args.length >= 2) {
            UUID id = parseUuid(args[1]);
            if (id == null) {
                msg.send(p, "command.usage-entity", "label", label);
                return;
            }
            target = Bukkit.getEntity(id);
        } else {
            target = p.getTargetEntity(plugin.config().entityTargetDistance);
        }
        if (target instanceof Player) {
            msg.send(p, "entity.is-player", "label", label);
            return;
        }
        if (!(target instanceof LivingEntity le) || !le.isValid()) {
            msg.send(p, "entity.not-found");
            return;
        }
        EntityGui gui = new EntityGui(plugin, p.getUniqueId(), le);
        plugin.guis().open(p, gui);
        plugin.audit().view(p, le.getType().getKey().getKey() + " " + le.getUniqueId(), "entity");
    }

    // ---------------- version ----------------

    private void version(CommandSender sender) {
        msg.send(sender, "update.checking");
        UUID id = sender instanceof Player p ? p.getUniqueId() : null;
        plugin.updates().check().thenAccept(r -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                CommandSender to = id == null ? Bukkit.getConsoleSender() : Bukkit.getPlayer(id);
                if (to != null) plugin.sendUpdateResult(to, r);
            });
        });
    }

    // ---------------- 補全 ----------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String pre = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : SUBS) if (s.startsWith(pre) && allowed(sender, s)) out.add(s);
            return out;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (!allowed(sender, sub)) return List.of();
            String pre = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("inv") || sub.equals("ec")) {
                return names(sender, pre);
            }
            if (sub.equals("entity") && sender instanceof Player p) {
                Entity t = p.getTargetEntity(plugin.config().entityTargetDistance);
                if (t instanceof LivingEntity && !(t instanceof Player)) {
                    String s = t.getUniqueId().toString();
                    if (s.startsWith(pre)) return List.of(s);
                }
            }
        }
        return List.of();
    }

    private List<String> names(CommandSender sender, String pre) {
        int max = plugin.config().tabMaxResults;
        List<String> out = new ArrayList<>();
        Player self = sender instanceof Player p ? p : null;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (out.size() >= max) return out;
            if (self != null && !self.canSee(p)) continue;
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(pre)) out.add(p.getName());
        }
        if (plugin.config().tabOffline && sender.hasPermission(Perms.OFFLINE)) {
            for (String n : plugin.knownNames()) {
                if (out.size() >= max) break;
                if (n.toLowerCase(Locale.ROOT).startsWith(pre) && Bukkit.getPlayerExact(n) == null) out.add(n);
            }
        }
        return out;
    }
}
