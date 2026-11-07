package clre20.customInvAudit.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * config.yml 的快取值（只在啟動/重載時讀一次，執行期不碰檔案）。
 */
public final class PluginConfig {

    public boolean checkOnStartup = true;
    public boolean notifyOnJoin = true;
    public int refreshIntervalTicks = 10;
    public int entityTargetDistance = 8;
    public boolean tabOffline = false;
    public int tabMaxResults = 50;
    public boolean offlineEnabled = true;
    public boolean writeBackup = true;
    public int loginWaitSeconds = 10;
    public boolean auditEnabled = true;
    public boolean auditViews = false;
    public int auditKeepDays = 30;
    public int auditFlushSeconds = 10;
    private Set<String> exemptNames = Set.of();
    private Set<UUID> exemptUuids = Set.of();

    public void load(JavaPlugin plugin) {
        YamlConfiguration c = YamlFiles.loadWithDefaults(plugin, "config.yml")[0];
        checkOnStartup = c.getBoolean("update-checker.check-on-startup", true);
        notifyOnJoin = c.getBoolean("update-checker.notify-on-join", true);
        refreshIntervalTicks = clamp(c.getInt("gui.refresh-interval-ticks", 10), 5, 200);
        entityTargetDistance = clamp(c.getInt("gui.entity-target-distance", 8), 1, 32);
        tabOffline = c.getBoolean("tab-complete.offline-players", false);
        tabMaxResults = clamp(c.getInt("tab-complete.max-results", 50), 1, 500);
        offlineEnabled = c.getBoolean("offline.enabled", true);
        writeBackup = c.getBoolean("offline.write-backup", true);
        loginWaitSeconds = clamp(c.getInt("offline.login-wait-timeout-seconds", 10), 1, 30);
        auditEnabled = c.getBoolean("audit-log.enabled", true);
        auditViews = c.getBoolean("audit-log.log-views", false);
        auditKeepDays = clamp(c.getInt("audit-log.keep-days", 30), 1, 3650);
        auditFlushSeconds = clamp(c.getInt("audit-log.flush-interval-seconds", 10), 2, 600);

        Set<String> names = new HashSet<>();
        Set<UUID> uuids = new HashSet<>();
        for (String s : c.getStringList("exempt-offline-players")) {
            if (s == null || s.isBlank()) continue;
            String t = s.trim();
            try {
                uuids.add(UUID.fromString(t));
            } catch (IllegalArgumentException e) {
                names.add(t.toLowerCase(Locale.ROOT));
            }
        }
        exemptNames = Set.copyOf(names);
        exemptUuids = Set.copyOf(uuids);
    }

    public boolean isOfflineExempt(UUID uuid, String name) {
        return exemptUuids.contains(uuid) || (name != null && exemptNames.contains(name.toLowerCase(Locale.ROOT)));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
