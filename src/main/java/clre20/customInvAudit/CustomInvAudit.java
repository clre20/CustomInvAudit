package clre20.customInvAudit;

import clre20.customInvAudit.audit.AuditLogger;
import clre20.customInvAudit.command.AuditCommand;
import clre20.customInvAudit.config.Messages;
import clre20.customInvAudit.config.PluginConfig;
import clre20.customInvAudit.gui.GuiManager;
import clre20.customInvAudit.listener.GuiListener;
import clre20.customInvAudit.listener.PlayerListener;
import clre20.customInvAudit.offline.OfflineDataManager;
import clre20.customInvAudit.update.UpdateChecker;
import clre20.customInvAudit.util.Items;
import clre20.customInvAudit.util.Perms;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class CustomInvAudit extends JavaPlugin {

    private final PluginConfig config = new PluginConfig();
    private final Messages messages = new Messages();
    private final Set<String> knownNames = ConcurrentHashMap.newKeySet();
    private ExecutorService io;
    private GuiManager guis;
    private OfflineDataManager offline;
    private AuditLogger audit;
    private UpdateChecker updates;
    private volatile boolean namesLoaded;

    @Override
    public void onEnable() {
        // 單一背景執行緒：離線檔案讀寫、紀錄檔、更新檢查（佇列有上限；滿了就拒絕，絕不改在主執行緒執行）
        io = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(10_000), r -> {
            Thread t = new Thread(r, "CustomInvAudit-IO");
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.AbortPolicy());

        Items.init(this);
        config.load(this);
        messages.load(this);

        guis = new GuiManager(this);
        offline = new OfflineDataManager(this);
        audit = new AuditLogger(this);
        updates = new UpdateChecker(this);

        AuditCommand cmd = new AuditCommand(this);
        PluginCommand pc = getCommand("custominvaudit");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        audit.start();
        loadNamesIfNeeded();
        if (config.checkOnStartup) {
            updates.check().thenAccept(r -> {
                if (isEnabled()) Bukkit.getScheduler().runTask(this, () -> sendUpdateResult(Bukkit.getConsoleSender(), r));
            });
        }
        getLogger().info("CustomInvAudit " + getPluginMeta().getVersion() + " 已啟用");
    }

    @Override
    public void onDisable() {
        if (guis != null) guis.shutdown();
        if (offline != null) offline.shutdown();
        if (audit != null) audit.shutdown();
        if (io != null) {
            io.shutdown();
            try {
                if (!io.awaitTermination(5, TimeUnit.SECONDS)) io.shutdownNow();
            } catch (InterruptedException e) {
                io.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        knownNames.clear();
    }

    public void reloadAll() {
        config.load(this);
        messages.load(this);
        guis.restartTask();
        audit.start();
        loadNamesIfNeeded();
    }

    /** 補全用的離線玩家名稱：只在開啟此功能時於背景讀一次，之後由登入事件補上。 */
    private void loadNamesIfNeeded() {
        if (!config.tabOffline || namesLoaded) return;
        namesLoaded = true;
        runIo(() -> {
            try {
                for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                    String n = op.getName();
                    if (n != null) knownNames.add(n);
                }
            } catch (Throwable t) {
                getLogger().warning("讀取離線玩家清單失敗：" + t);
            }
        });
    }

    /** 丟到背景執行緒；佇列已滿或已關閉時直接放棄並記錄。 */
    public void runIo(Runnable r) {
        try {
            io.execute(r);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            getLogger().warning("背景工作佇列已滿，略過一項工作");
        }
    }

    public void rememberName(String name) {
        if (namesLoaded) knownNames.add(name);
    }

    public Set<String> knownNames() {
        return knownNames;
    }

    /** 目標是否受 exempt 保護（線上看權限，離線看設定檔名單）。 */
    public boolean isProtected(CommandSender viewer, UUID target, String name) {
        if (viewer.hasPermission(Perms.BYPASS_EXEMPT)) return false;
        Player online = Bukkit.getPlayer(target);
        if (online != null) return online.hasPermission(Perms.EXEMPT);
        return config.isOfflineExempt(target, name);
    }

    public void sendUpdateResult(CommandSender to, UpdateChecker.Result r) {
        String url = "https://github.com/" + UpdateChecker.REPO + "/releases";
        String path = switch (r.status()) {
            case UPDATE_AVAILABLE -> "update.available";
            case UP_TO_DATE -> "update.latest";
            case DEV_BUILD -> "update.dev";
            case NO_RELEASE -> "update.no-release";
            case FAILED -> "update.failed";
        };
        messages.send(to, path, "current", r.current(), "latest", r.latest() == null ? "-" : r.latest(), "url", url);
    }

    public PluginConfig config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public ExecutorService io() {
        return io;
    }

    public GuiManager guis() {
        return guis;
    }

    public OfflineDataManager offline() {
        return offline;
    }

    public AuditLogger audit() {
        return audit;
    }

    public UpdateChecker updates() {
        return updates;
    }
}
