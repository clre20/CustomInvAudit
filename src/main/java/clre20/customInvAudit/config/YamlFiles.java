package clre20.customInvAudit.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * 讀取 YAML 檔並自動補齊缺少的項目（保留使用者原有的值），有補齊才寫回檔案。
 */
public final class YamlFiles {

    private YamlFiles() {
    }

    /** 回傳 [使用者設定, jar 內預設值]。 */
    public static YamlConfiguration[] loadWithDefaults(JavaPlugin plugin, String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }

        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream in = plugin.getResource(name)) {
            if (in != null) {
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    defaults = YamlConfiguration.loadConfiguration(reader);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "無法讀取預設 " + name, e);
        }

        YamlConfiguration user = new YamlConfiguration();
        try {
            user.load(file);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, name + " 格式錯誤，本次改用預設值（原檔不會被覆蓋）", e);
            return new YamlConfiguration[]{defaults, defaults};
        }

        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) {
                if (!user.isConfigurationSection(key) && !user.contains(key)) {
                    user.createSection(key);
                    user.setComments(key, defaults.getComments(key));
                    changed = true;
                }
                continue;
            }
            if (!user.contains(key)) {
                user.set(key, defaults.get(key));
                user.setComments(key, defaults.getComments(key));
                changed = true;
            }
        }
        if (changed) {
            try {
                user.save(file);
                plugin.getLogger().info(name + " 已自動補齊缺少的項目");
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "無法寫回 " + name, e);
            }
        }
        return new YamlConfiguration[]{user, defaults};
    }
}
