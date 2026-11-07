package clre20.customInvAudit.offline;

import clre20.customInvAudit.CustomInvAudit;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * 離線玩家資料：非同步讀檔 → 記憶體快取 → 所有檢視者關閉後非同步寫回（原子替換）。
 * 玩家登入前若有未寫入的修改會先寫完，避免伺服器讀到舊檔或被覆蓋。
 */
public final class OfflineDataManager {

    private static final long MAX_FILE_SIZE = 32L * 1024 * 1024;

    private final CustomInvAudit plugin;
    private final Map<UUID, OfflineData> loaded = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> pendingWrites = new ConcurrentHashMap<>();
    private final Set<UUID> locked = ConcurrentHashMap.newKeySet();
    private volatile Path dataDir;

    public OfflineDataManager(CustomInvAudit plugin) {
        this.plugin = plugin;
    }

    public enum LoadError { NO_FILE, LOCKED, IO }

    public static final class LoadException extends RuntimeException {
        public final LoadError error;

        LoadException(LoadError error, Throwable cause) {
            super(error.name(), cause);
            this.error = error;
        }
    }

    public boolean isLocked(UUID uuid) {
        return locked.contains(uuid);
    }

    /** 主執行緒呼叫；回傳的 future 在主執行緒完成。 */
    public CompletableFuture<OfflineData> load(UUID uuid, String name) {
        if (locked.contains(uuid)) {
            return CompletableFuture.failedFuture(new LoadException(LoadError.LOCKED, null));
        }
        OfflineData cached = loaded.get(uuid);
        if (cached != null && !cached.discarded) return CompletableFuture.completedFuture(cached);

        Path dir = resolveDataDir();
        CompletableFuture<Void> before = pendingWrites.getOrDefault(uuid, CompletableFuture.completedFuture(null));
        CompletableFuture<OfflineData> result = new CompletableFuture<>();
        before.exceptionally(t -> null).thenRunAsync(() -> {
            try {
                Path file = dir == null ? null : dir.resolve(uuid + ".dat");
                if (file == null || !Files.isRegularFile(file)) throw new LoadException(LoadError.NO_FILE, null);
                if (Files.size(file) > MAX_FILE_SIZE) throw new IOException("檔案過大");
                Nbt.Compound root;
                try (InputStream in = Files.newInputStream(file)) {
                    root = Nbt.readCompressed(in);
                }
                OfflineData data = new OfflineData(uuid, name, root);
                data.splitRaw();
                Bukkit.getScheduler().runTask(plugin, () -> finishLoad(data, result));
            } catch (LoadException e) {
                completeOnMain(result, e);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "讀取離線玩家資料失敗 " + uuid, t);
                completeOnMain(result, new LoadException(LoadError.IO, t));
            }
        }, plugin.io()).exceptionally(t -> {
            // 背景佇列拒絕（已滿/關閉）時也要讓呼叫端收到結果
            if (!result.isDone()) completeOnMain(result, new LoadException(LoadError.IO, t));
            return null;
        });
        return result;
    }

    private void finishLoad(OfflineData data, CompletableFuture<OfflineData> result) {
        if (locked.contains(data.uuid) || Bukkit.getPlayer(data.uuid) != null) {
            result.completeExceptionally(new LoadException(LoadError.LOCKED, null));
            return;
        }
        OfflineData existing = loaded.get(data.uuid);
        if (existing != null && !existing.discarded) {
            result.complete(existing);
            return;
        }
        data.decodeItems(Bukkit.getUnsafe().getDataVersion());
        loaded.put(data.uuid, data);
        result.complete(data);
    }

    private void completeOnMain(CompletableFuture<OfflineData> f, Throwable t) {
        if (!plugin.isEnabled()) {
            f.completeExceptionally(t);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> f.completeExceptionally(t));
    }

    /** 檢視者開始使用這份資料（主執行緒）。 */
    public void attach(OfflineData data, UUID viewer) {
        data.viewers.add(viewer);
    }

    /** 檢視者不再使用這份資料（主執行緒）；沒有人用了就寫回並卸載。 */
    public void detach(OfflineData data, UUID viewer) {
        data.viewers.remove(viewer);
        if (!data.viewers.isEmpty() || data.discarded) return;
        if (!plugin.isEnabled()) return; // 關服流程由 shutdown() 同步寫入
        if (data.dirty) {
            saveAsync(data).whenComplete((v, t) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (data.viewers.isEmpty()) loaded.remove(data.uuid, data);
            }));
        } else {
            loaded.remove(data.uuid, data);
        }
    }

    /** 主執行緒：編碼 → 背景寫檔。 */
    private CompletableFuture<Void> saveAsync(OfflineData data) {
        byte[] bytes = encodeForWrite(data);
        if (bytes == null) return CompletableFuture.completedFuture(null);
        Path dir = resolveDataDir();
        boolean backup = plugin.config().writeBackup;
        CompletableFuture<Void> prev = pendingWrites.getOrDefault(data.uuid, CompletableFuture.completedFuture(null));
        CompletableFuture<Void> f = prev.exceptionally(t -> null)
                .thenRunAsync(() -> writeFile(dir, data.uuid, bytes, backup), plugin.io());
        pendingWrites.put(data.uuid, f);
        f.whenComplete((v, t) -> {
            pendingWrites.remove(data.uuid, f);
            if (t != null) plugin.getLogger().log(Level.SEVERE, "寫入離線玩家資料失敗 " + data.uuid, t);
        });
        return f;
    }

    private byte[] encodeForWrite(OfflineData data) {
        if (!data.dirty || !data.editable || data.discarded) return null;
        if (Bukkit.getPlayer(data.uuid) != null) {
            plugin.getLogger().warning("玩家 " + data.name + " 已在線，放棄寫入離線修改以免覆蓋即時資料");
            data.discarded = true;
            return null;
        }
        try {
            byte[] b = data.encode();
            data.dirty = false;
            return b;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "編碼離線玩家資料失敗 " + data.uuid, t);
            return null;
        }
    }

    private void writeFile(Path dir, UUID uuid, byte[] bytes, boolean backup) {
        if (dir == null) throw new IllegalStateException("找不到玩家資料資料夾");
        try {
            Path target = dir.resolve(uuid + ".dat");
            Path tmp = dir.resolve(uuid + ".dat.cia-tmp");
            Files.write(tmp, bytes);
            if (backup && Files.isRegularFile(target)) {
                Files.copy(target, dir.resolve(uuid + ".dat.cia-bak"), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ---------------- 登入保護 ----------------

    /**
     * 在 AsyncPlayerPreLoginEvent 執行緒呼叫。有未寫入的修改時：鎖定 → 主執行緒關閉介面並寫檔 → 等待完成。
     * @return false 表示逾時，應拒絕登入
     */
    public boolean beforeLogin(UUID uuid) {
        if (!loaded.containsKey(uuid) && !pendingWrites.containsKey(uuid)) return true;
        locked.add(uuid);
        if (!plugin.isEnabled()) return true;
        try {
            CompletableFuture<Void> write = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                OfflineData data = loaded.get(uuid);
                CompletableFuture<Void> f = null;
                if (data != null) {
                    plugin.guis().closeAllFor(data, "offline.closed-player-login");
                    f = saveAsync(data);
                    data.discarded = true;
                    loaded.remove(uuid, data);
                }
                CompletableFuture<Void> pending = pendingWrites.get(uuid);
                if (pending != null) return pending;
                return f == null ? CompletableFuture.<Void>completedFuture(null) : f;
            }).get(plugin.config().loginWaitSeconds, TimeUnit.SECONDS);
            write.get(plugin.config().loginWaitSeconds, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "等待離線資料寫入逾時，暫時拒絕 " + uuid + " 登入", e);
            return false;
        }
    }

    /** 登入被拒時只解除鎖定（非主執行緒呼叫）。 */
    public void unlockOnly(UUID uuid) {
        locked.remove(uuid);
    }

    /** 登入流程結束（成功加入或被拒）後解除鎖定，並丟棄任何殘留的離線快取。 */
    public void unlock(UUID uuid) {
        locked.remove(uuid);
        OfflineData data = loaded.get(uuid);
        if (data != null && Bukkit.getPlayer(uuid) != null) {
            plugin.guis().closeAllFor(data, "offline.closed-player-login");
            data.discarded = true;
            loaded.remove(uuid, data);
        }
    }

    /** 關服：同步寫完所有修改。 */
    public void shutdown() {
        List<OfflineData> all = new ArrayList<>(loaded.values());
        Path dir = resolveDataDir();
        for (OfflineData d : all) {
            byte[] b = encodeForWrite(d);
            if (b == null) continue;
            try {
                writeFile(dir, d.uuid, b, plugin.config().writeBackup);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "關服時寫入離線玩家資料失敗 " + d.uuid, t);
            }
        }
        for (CompletableFuture<Void> f : new ArrayList<>(pendingWrites.values())) {
            try {
                f.get(10, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }
        loaded.clear();
    }

    // ---------------- 資料夾定位 ----------------

    /** 找出玩家資料資料夾（26.1+：<世界>/players/data；舊版：<世界>/playerdata）。 */
    public Path resolveDataDir() {
        Path d = dataDir;
        if (d != null) return d;
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) return null;
        World main = worlds.get(0);
        List<File> bases = new ArrayList<>();
        bases.add(new File(Bukkit.getWorldContainer(), main.getName()));
        File wf = main.getWorldFolder();
        for (int i = 0; i < 5 && wf != null; i++, wf = wf.getParentFile()) bases.add(wf);
        for (File base : bases) {
            File p = new File(new File(base, "players"), "data");
            if (p.isDirectory()) return dataDir = p.toPath();
        }
        for (File base : bases) {
            File p = new File(base, "playerdata");
            if (p.isDirectory()) return dataDir = p.toPath();
        }
        return null;
    }

    public void onPlayerJoinCleanup(Player p) {
        unlock(p.getUniqueId());
    }
}
