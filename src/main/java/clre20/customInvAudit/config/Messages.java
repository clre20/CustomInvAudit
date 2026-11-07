package clre20.customInvAudit.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * messages.yml。所有顯示文字都從這裡取得。
 * 變數格式 {name}；變數值一律以純文字插入（不會解析顏色碼，避免玩家名稱/生物名稱注入格式）。
 */
public final class Messages {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z0-9_-]+)}");
    private static final Pattern COLOR_CODE =Pattern.compile("(?i)&(#[0-9a-f]{6}|[0-9a-fk-or])");

    private YamlConfiguration user = new YamlConfiguration();
    private YamlConfiguration defaults = new YamlConfiguration();
    private Component prefix = Component.empty();
    private java.util.logging.Logger logger;

    public void load(JavaPlugin plugin) {
        logger = plugin.getLogger();
        YamlConfiguration[] pair = YamlFiles.loadWithDefaults(plugin, "messages.yml");
        user = pair[0];
        defaults = pair[1];
        prefix = LEGACY.deserialize(raw("prefix"));
    }

    /** 取原始字串；缺少時用 jar 預設值，再缺就回傳路徑本身。 */
    public String raw(String path) {
        String s = user.getString(path);
        if (s == null) s = defaults.getString(path);
        return s != null ? s : path;
    }

    public boolean has(String path) {
        return user.contains(path) || defaults.contains(path);
    }

    public List<String> rawList(String path) {
        if (user.isList(path)) return user.getStringList(path);
        if (user.isString(path)) return List.of(user.getString(path, ""));
        if (defaults.isList(path)) return defaults.getStringList(path);
        if (defaults.isString(path)) return List.of(defaults.getString(path, ""));
        return List.of();
    }

    /** 文字元件（物品名稱/標題用，取消斜體）。placeholders: key, value, key, value... */
    public Component text(String path, Object... placeholders) {
        return build(raw(path), placeholders);
    }

    public List<Component> lore(String path, Object... placeholders) {
        List<String> lines = rawList(path);
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(build(line, placeholders));
        }
        return out;
    }

    public void send(CommandSender to, String path, Object... placeholders) {
        String r = raw(path);
        if (r.isEmpty()) return;
        if (to instanceof ConsoleCommandSender && logger != null) {
            // 主控台走插件 logger，前綴統一是 [CustomInvAudit]
            logger.info(plain(path, placeholders));
            return;
        }
        to.sendMessage(prefix.append(build(r, placeholders)));
    }

    /** 純文字（紀錄檔、主控台用）。 */
    public String plain(String path, Object... placeholders) {
        String s = COLOR_CODE.matcher(raw(path)).replaceAll("");
        if (placeholders.length < 2) return s;
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            map.put(String.valueOf(placeholders[i]), placeholders[i + 1]);
        }
        return PLACEHOLDER.matcher(s).replaceAll(m -> {
            Object v = map.get(m.group(1));
            String val = v == null ? m.group() : (v instanceof Component comp ? toPlain(comp) : String.valueOf(v));
            return java.util.regex.Matcher.quoteReplacement(val);
        });
    }

    public static String toPlain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** 列舉/註冊表值的顯示名稱：values.<category>.<key>，沒有就把 key 美化後顯示。 */
    public String valueName(String category, String key) {
        String k = key.toLowerCase(Locale.ROOT);
        String path = "values." + category + "." + k;
        String s = user.getString(path);
        if (s == null) s = defaults.getString(path);
        if (s != null) return s;
        StringBuilder sb = new StringBuilder();
        for (String part : k.split("_")) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private Component build(String template, Object... placeholders) {
        Component c = LEGACY.deserialize(template).decoration(TextDecoration.ITALIC, false);
        if (placeholders.length < 2 || template.indexOf('{') < 0) return c;
        // 單次掃描替換，替換進去的值不會再被當成變數處理
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            map.put(String.valueOf(placeholders[i]), placeholders[i + 1]);
        }
        return c.replaceText(TextReplacementConfig.builder().match(PLACEHOLDER).replacement((match, builder) -> {
            Object v = map.get(match.group(1));
            if (v == null) return builder.content(match.group());
            return v instanceof Component comp ? comp : Component.text(String.valueOf(v));
        }).build());
    }
}
