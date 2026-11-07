package clre20.customInvAudit.audit;

import clre20.customInvAudit.CustomInvAudit;
import clre20.customInvAudit.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * 修改紀錄：記憶體佇列 + 定時非同步批次寫入 logs/yyyy-MM-dd.log；超過保留天數的檔案自動刪除。
 */
public final class AuditLogger {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_QUEUE = 50_000;

    private final CustomInvAudit plugin;
    private final Path dir;
    private final ConcurrentLinkedQueue<Entry> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();
    private BukkitTask task;
    private volatile LocalDate lastCleanup;

    private record Entry(LocalDate date, String line) {
    }

    public AuditLogger(CustomInvAudit plugin) {
        this.plugin = plugin;
        this.dir = plugin.getDataFolder().toPath().resolve("logs");
    }

    public void start() {
        stopTask();
        if (!plugin.config().auditEnabled) return;
        long period = plugin.config().auditFlushSeconds * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (size.get() > 0 || !LocalDate.now().equals(lastCleanup)) plugin.runIo(this::flushAndCleanup);
        }, period, period);
        plugin.runIo(this::flushAndCleanup);
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** 關服：同步寫完。 */
    public void shutdown() {
        stopTask();
        flush();
    }

    // ---------------- 記錄 ----------------

    public void view(CommandSender actor, String target, String what) {
        if (!plugin.config().auditViews) return;
        add("audit-log.line-view", "actor", actor.getName(), "target", target, "what", what);
    }

    public void itemChange(CommandSender actor, String target, String slot, ItemStack before, ItemStack after) {
        add("audit-log.line-item", "actor", actor.getName(), "target", target, "slot", slot,
                "before", Items.describe(before), "after", Items.describe(after));
    }

    public void valueChange(CommandSender actor, String target, String field, String before, String after) {
        add("audit-log.line-value", "actor", actor.getName(), "target", target, "field", field,
                "before", before, "after", after);
    }

    public void action(CommandSender actor, String target, String action, String detail) {
        add("audit-log.line-action", "actor", actor.getName(), "target", target, "action", action, "detail", detail);
    }

    private void add(String path, Object... ph) {
        if (!plugin.config().auditEnabled) return;
        if (size.get() >= MAX_QUEUE) return; // 保護記憶體（理論上不會發生）
        LocalDateTime now = LocalDateTime.now();
        String text = plugin.messages().plain(path, ph).replace('\n', ' ').replace('\r', ' ');
        queue.add(new Entry(now.toLocalDate(), "[" + TIME.format(now) + "] " + text));
        size.incrementAndGet();
    }

    // ---------------- 寫檔 ----------------

    private void flushAndCleanup() {
        flush();
        LocalDate today = LocalDate.now();
        if (!today.equals(lastCleanup)) {
            lastCleanup = today;
            cleanup(today);
        }
    }

    private synchronized void flush() {
        if (queue.isEmpty()) return;
        List<Entry> batch = new ArrayList<>();
        Entry e;
        while ((e = queue.poll()) != null) {
            batch.add(e);
            size.decrementAndGet();
        }
        try {
            Files.createDirectories(dir);
            LocalDate current = null;
            BufferedWriter w = null;
            try {
                for (Entry en : batch) {
                    if (!en.date().equals(current)) {
                        if (w != null) w.close();
                        current = en.date();
                        w = Files.newBufferedWriter(dir.resolve(current + ".log"), StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    }
                    w.write(en.line());
                    w.newLine();
                }
            } finally {
                if (w != null) w.close();
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "寫入修改紀錄失敗", ex);
        }
    }

    private void cleanup(LocalDate today) {
        if (!Files.isDirectory(dir)) return;
        LocalDate limit = today.minusDays(plugin.config().auditKeepDays);
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.log")) {
            for (Path p : ds) {
                String n = p.getFileName().toString();
                try {
                    LocalDate d = LocalDate.parse(n.substring(0, n.length() - 4));
                    if (d.isBefore(limit)) Files.deleteIfExists(p);
                } catch (DateTimeParseException | StringIndexOutOfBoundsException ignored) {
                    // 不是本插件產生的檔案，不動
                }
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "清理舊紀錄失敗", ex);
        }
    }
}
