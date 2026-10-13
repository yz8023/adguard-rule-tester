package com.yz8023.adguardtester.net;

import com.yz8023.adguardtester.parse.SourceParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 抓取测试页面与其外链脚本（纯 JVM，OkHttp，无 android.* 依赖）。
 */
public final class SourceFetcher {

    /** 已抓取的一个来源。 */
    public static final class FetchedSource {
        public final String name;
        public final String pageUrl;
        public final String html;
        public final List<String> assetTexts;

        public FetchedSource(String name, String pageUrl, String html, List<String> assetTexts) {
            this.name = name;
            this.pageUrl = pageUrl;
            this.html = html;
            this.assetTexts = assetTexts;
        }
    }

    private static final int MAX_ASSETS = 12;
    private static final long MAX_PAGE_BYTES = 4 * 1024 * 1024;
    private static final long MAX_ASSET_BYTES = 2 * 1024 * 1024;

    private final OkHttpClient client;

    public SourceFetcher(OkHttpClient client) {
        this.client = client;
    }

    /** 抓取页面 HTML，并顺带抓取同页引用的 &lt;script src&gt; 文本（限量）。 */
    public FetchedSource fetch(String name, String pageUrl) throws IOException {
        return fetch(name, pageUrl, null);
    }

    /**
     * 抓取页面；scriptUrlFilters 为 null 时抓取全部脚本，空数组时不抓脚本，
     * 非空时仅抓取 URL 中包含任一筛选片段的脚本。
     */
    public FetchedSource fetch(String name, String pageUrl, String[] scriptUrlFilters) throws IOException {
        String canonicalPageUrl = canonicalizeKnownPageUrl(pageUrl);
        String html = get(canonicalPageUrl, MAX_PAGE_BYTES);
        if (isSecurityVerificationPage(html)) {
            throw new IOException("站点要求浏览器安全验证；请复制链接并在浏览器中手动测试");
        }
        List<String> scripts = SourceParser.findLinkedScriptUrls(canonicalPageUrl, html);
        List<String> texts = new ArrayList<>();
        int n = 0;
        for (String u : scripts) {
            if (scriptUrlFilters != null && !matchesAny(u, scriptUrlFilters)) {
                continue;
            }
            if (n >= MAX_ASSETS) {
                break;
            }
            try {
                texts.add(get(u, MAX_ASSET_BYTES));
                n++;
            } catch (IOException ignore) {
                // 单个脚本失败不影响整体解析
            }
        }
        return new FetchedSource(name, canonicalPageUrl, html, texts);
    }

    /** 检测反自动化安全页；不尝试绕过挑战，提示用户改在浏览器完成验证。 */
    static boolean isSecurityVerificationPage(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        String lower = html.toLowerCase(java.util.Locale.ROOT);
        return html.contains("正在进行安全验证")
                || html.contains("验证您不是自动程序")
                || lower.contains("security verification")
                || lower.contains("verify you are human")
                || lower.contains("checking your browser")
                || lower.contains("challenge-platform");
    }

    /** 修复已知移动端测试页的大小写/空格别名，避免把畸形路径发给站点。 */
    public static String canonicalizeKnownPageUrl(String pageUrl) {
        if (pageUrl == null) {
            return "";
        }
        String trimmed = pageUrl.trim();
        try {
            // URI 不接受裸空格；编码后取 getPath() 会还原为可比较的路径。
            java.net.URI uri = new java.net.URI(trimmed.replace(" ", "%20"));
            if ("mobileproxy.space".equalsIgnoreCase(uri.getHost())) {
                String path = uri.getPath();
                if (path != null) {
                    String normalized = path.toLowerCase(java.util.Locale.ROOT)
                            .replaceAll("[\\s_-]+", "");
                    if ("/ch/adblocktest.html".equals(normalized)) {
                        return "https://mobileproxy.space/ch/adblock-test.html";
                    }
                }
            }
        } catch (Exception ignore) {
            // 非标准 URL 仍交给 OkHttp 生成更具体的错误信息。
        }
        return trimmed;
    }

    private static boolean matchesAny(String url, String[] filters) {
        for (String filter : filters) {
            if (filter != null && !filter.isEmpty() && url.contains(filter)) {
                return true;
            }
        }
        return false;
    }

    private String get(String url, long maxBytes) throws IOException {
        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", "AdGuardRuleTester/1.0 (Android)")
                .header("Accept", "text/html,application/javascript,*/*")
                .get()
                .build();
        try (Response resp = client.newCall(req).execute()) {
            ResponseBody body = resp.body();
            if (!resp.isSuccessful() || body == null) {
                if (resp.code() == 403 || resp.code() == 429) {
                    throw new IOException("HTTP " + resp.code()
                            + "：安全验证/限流，请复制链接并在浏览器中手动测试");
                }
                throw new IOException("HTTP " + resp.code() + " for " + url);
            }
            long declared = body.contentLength();
            if (declared > maxBytes) {
                throw new IOException("response too large: " + url);
            }
            okio.Buffer buf = new okio.Buffer();
            okio.BufferedSource source = body.source();
            long read = 0;
            while (true) {
                long r = source.read(buf, 8192);
                if (r == -1) {
                    break;
                }
                read += r;
                if (read > maxBytes) {
                    throw new IOException("response too large: " + url);
                }
            }
            return buf.readString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
