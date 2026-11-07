package clre20.customInvAudit.update;

import clre20.customInvAudit.CustomInvAudit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub Releases 更新檢查。結果快取 10 分鐘、不定時輪詢；同時間只會有一個請求。
 */
public final class UpdateChecker {

    public static final String REPO = "clre20/CustomInvAudit";
    private static final long CACHE_MS = 10 * 60 * 1000L;
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]{1,64})\"");

    public enum Status { UPDATE_AVAILABLE, UP_TO_DATE, DEV_BUILD, NO_RELEASE, FAILED }

    public record Result(Status status, String current, String latest) {
    }

    private final CustomInvAudit plugin;
    private final HttpClient client;
    private volatile Result cached;
    private volatile long cachedAt;
    private CompletableFuture<Result> inflight;

    public UpdateChecker(CustomInvAudit plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(plugin.io())
                .build();
    }

    public String currentVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /** 有快取（10 分鐘內）就直接回傳，否則向 GitHub 查詢。 */
    public synchronized CompletableFuture<Result> check() {
        Result r = cached;
        if (r != null && System.currentTimeMillis() - cachedAt < CACHE_MS) {
            return CompletableFuture.completedFuture(r);
        }
        if (inflight != null && !inflight.isDone()) return inflight;
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + REPO + "/releases/latest"))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "CustomInvAudit-UpdateChecker")
                .GET().build();
        String current = currentVersion();
        inflight = client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> parse(resp.statusCode(), resp.body(), current))
                .exceptionally(t -> {
                    plugin.getLogger().warning("檢查更新失敗：" + t);
                    return new Result(Status.FAILED, current, null);
                })
                .thenApply(res -> {
                    if (res.status() != Status.FAILED) {
                        cached = res;
                        cachedAt = System.currentTimeMillis();
                    }
                    return res;
                });
        return inflight;
    }

    /** 只讀快取，不發請求（登入提醒用）。 */
    public Result cachedResult() {
        return cached;
    }

    private static Result parse(int code, String body, String current) {
        if (code == 404) return new Result(Status.NO_RELEASE, current, null);
        if (code != 200 || body == null) return new Result(Status.FAILED, current, null);
        Matcher m = TAG.matcher(body);
        if (!m.find()) return new Result(Status.FAILED, current, null);
        String latest = strip(m.group(1));
        int cmp = compare(strip(current), latest);
        Status s = cmp < 0 ? Status.UPDATE_AVAILABLE : cmp == 0 ? Status.UP_TO_DATE : Status.DEV_BUILD;
        return new Result(s, current, latest);
    }

    static String strip(String v) {
        v = v.trim();
        return v.startsWith("v") || v.startsWith("V") ? v.substring(1) : v;
    }

    /** 逐段比較數字版本（1.2.10 > 1.2.9）。 */
    static int compare(String a, String b) {
        String[] pa = a.split("[.\\-+]"), pb = b.split("[.\\-+]");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int x = i < pa.length ? num(pa[i]) : 0;
            int y = i < pb.length ? num(pb[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int num(String s) {
        int end = 0;
        while (end < s.length() && end < 9 && Character.isDigit(s.charAt(end))) end++;
        return end == 0 ? 0 : Integer.parseInt(s.substring(0, end));
    }
}
