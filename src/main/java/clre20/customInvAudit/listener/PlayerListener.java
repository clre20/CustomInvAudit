package clre20.customInvAudit.listener;

import clre20.customInvAudit.CustomInvAudit;
import clre20.customInvAudit.update.UpdateChecker;
import clre20.customInvAudit.util.Perms;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/** 登入保護、離線清理、更新提醒。 */
public final class PlayerListener implements Listener {

    private final CustomInvAudit plugin;

    public PlayerListener(CustomInvAudit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        if (!plugin.offline().beforeLogin(e.getUniqueId())) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    plugin.messages().text("offline.login-busy"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLoginResult(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            plugin.offline().unlockOnly(e.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.offline().onPlayerJoinCleanup(p);
        plugin.rememberName(p.getName());
        if (!plugin.config().notifyOnJoin || !p.hasPermission(Perms.VERSION)) return;
        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player online = Bukkit.getPlayer(id);
            if (online == null) return;
            plugin.updates().check().thenAccept(r -> {
                if (r.status() != UpdateChecker.Status.UPDATE_AVAILABLE || !plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player pl = Bukkit.getPlayer(id);
                    if (pl != null) plugin.messages().send(pl, "update.join-notify",
                            "current", r.current(), "latest", r.latest(), "url", "https://github.com/" + UpdateChecker.REPO + "/releases");
                });
            });
        }, 60L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        plugin.guis().closeAllForTarget(id, "gui.target-offline");
        plugin.guis().viewerQuit(id);
    }

}
