package com.yz8023.adguardtester.net;

import com.yz8023.adguardtester.parse.SourceParser;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 抓取测试页面与其外链脚本（纯 JVM，OkHttp，无 android.* 依赖）。
 * 支持浏览器拟真请求头、Cookie 记忆、Cloudflare 安全验证识别与自动过盾解算器回调。
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

    /** 挑战/安全验证解算器接口（在 Android 环境下由 WebView 解算器实现）。 */
    public interface ChallengeResolver {
        /**
         * 尝试解算受 Cloudflare/安全验证保护的页面，并返回解算后的 HTML 与脚本资源。
         */
        FetchedSource resolve(String name, String pageUrl, String[] scriptUrlFilters) throws IOException;
    }

    public static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36";

    private static final int MAX_ASSETS = 12;
    private static final long MAX_PAGE_BYTES = 4 * 1024 * 1024;
    private static final long MAX_ASSET_BYTES = 2 * 1024 * 1024;

    private final OkHttpClient client;
    private final ConcurrentMap<String, String> hostCookies = new ConcurrentHashMap<>();
    private volatile String userAgent = DEFAULT_USER_AGENT;
    private volatile ChallengeResolver challengeResolver;

    public SourceFetcher(OkHttpClient client) {
        this.client = client;
    }

    public void setChallengeResolver(ChallengeResolver resolver) {
        this.challengeResolver = resolver;
    }

    public ChallengeResolver getChallengeResolver() {
        return this.challengeResolver;
    }

    public void setUserAgent(String ua) {
        if (ua != null && !ua.trim().isEmpty()) {
            this.userAgent = ua.trim();
        }
    }

    public String getUserAgent() {
        return this.userAgent;
    }

    public void setCookie(String host, String cookie) {
        if (host != null && cookie != null) {
            hostCookies.put(host.toLowerCase(Locale.ROOT), cookie);
        }
    }

    public String getCookie(String host) {
        if (host == null) {
            return null;
        }
        return hostCookies.get(host.toLowerCase(Locale.ROOT));
    }

    public void clearCookies() {
        hostCookies.clear();
    }

    /** 抓取页面 HTML，并顺带抓取同页引用的 &lt;script src&gt; 文本（限量）。 */
    public FetchedSource fetch(String name, String pageUrl) throws IOException {
        return fetch(name, pageUrl, null);
    }

    /**
     * 抓取页面；scriptUrlFilters 为 null 时抓取全部脚本，空数组时不抓脚本，
     * 非空时仅抓取 URL 中包含任一筛选片段的脚本。
     * 当遇到 Cloudflare / 安全验证挑战时，自动通过绑定的 ChallengeResolver 进行解算。
     */
    public FetchedSource fetch(String name, String pageUrl, String[] scriptUrlFilters) throws IOException {
        String canonicalPageUrl = canonicalizeKnownPageUrl(pageUrl);
        String html = null;
        boolean needChallengeResolve = false;
        try {
            html = get(canonicalPageUrl, MAX_PAGE_BYTES);
            if (isSecurityVerificationPage(canonicalPageUrl, html)) {
                needChallengeResolve = true;
            }
        } catch (IOException e) {
            needChallengeResolve = true;
        }

        if (needChallengeResolve) {
            if (challengeResolver != null) {
                return challengeResolver.resolve(name, canonicalPageUrl, scriptUrlFilters);
            }
            if (html == null) {
                throw new IOException("无法连接至测试站点，且未配置过盾解算器");
            }
            throw new IOException("站点要求浏览器安全验证（Cloudflare）；请使用内置过盾引擎或在浏览器中打开");
        }

        return fetchWithHtml(name, canonicalPageUrl, html, scriptUrlFilters);
    }

    /** 给定已解算/获取的页面 HTML，提取并抓取关联脚本。 */
    public FetchedSource fetchWithHtml(String name, String canonicalPageUrl, String html,
                                       String[] scriptUrlFilters) {
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

    /** 检测特定 URL 及其返回 HTML 是否处于 Cloudflare / 安全验证拦截状态。 */
    public static boolean isSecurityVerificationPage(String url, String html) {
        if (html == null || html.trim().isEmpty()) {
            return true;
        }
        // 如果包含核心测试对象，说明是真实测试页
        if (html.contains("const AdBlockTest") || html.contains("AdBlockTest =") || html.contains("HOST_TESTS")) {
            return false;
        }
        // mobileproxy.space 特征：必须包含 AdBlockTest 才是有效页，否则就是 CF 挑战/拦截页
        if (url != null && url.contains("mobileproxy.space") && !html.contains("AdBlockTest")) {
            return true;
        }
        return isSecurityVerificationPage(html);
    }

    /** 检测反自动化/Cloudflare 安全验证页特征。 */
    public static boolean isSecurityVerificationPage(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        // 核心测试数据优先排除
        if (html.contains("const AdBlockTest") || html.contains("AdBlockTest =") || html.contains("HOST_TESTS")) {
            return false;
        }
        String lower = html.toLowerCase(Locale.ROOT);
        return html.contains("正在进行安全验证")
                || html.contains("验证您不是自动程序")
                || lower.contains("challenge-form")
                || lower.contains("challenge-platform")
                || lower.contains("cf-browser-verification")
                || lower.contains("cf-chl-widget")
                || lower.contains("id=\"challenge-stage\"")
                || lower.contains("id=\"challenge-running\"")
                || (lower.contains("just a moment") && lower.contains("cloudflare"))
                || (lower.contains("attention required") && lower.contains("cloudflare"))
                || (lower.contains("security verification") && !lower.contains("adblock"))
                || (lower.contains("verify you are human") && !lower.contains("adblock"))
                || (lower.contains("checking your browser") && lower.contains("cloudflare"));
    }

    /** 修复已知移动端测试页的大小写/空格别名，避免把畸形路径发给站点。 */
    public static String canonicalizeKnownPageUrl(String pageUrl) {
        if (pageUrl == null) {
            return "";
        }
        String trimmed = pageUrl.trim();
        try {
            // URI 不接受裸空格；编码后取 getPath() 会还原为可比较的路径。
            URI uri = new URI(trimmed.replace(" ", "%20"));
            if ("mobileproxy.space".equalsIgnoreCase(uri.getHost())) {
                String path = uri.getPath();
                if (path != null) {
                    String normalized = path.toLowerCase(Locale.ROOT)
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

    private static String hostOf(String url) {
        if (url == null) {
            return "";
        }
        try {
            URI u = new URI(url);
            if (u.getHost() != null) {
                return u.getHost().toLowerCase(Locale.ROOT);
            }
        } catch (Exception ignore) {
            // fall through
        }
        return "";
    }

    public String get(String url, long maxBytes) throws IOException {
        Request.Builder reqBuilder = new Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,application/javascript,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Sec-CH-UA", "\"Chromium\";v=\"128\", \"Not;A=Brand\";v=\"24\", \"Google Chrome\";v=\"128\"")
                .header("Sec-CH-UA-Mobile", "?1")
                .header("Sec-CH-UA-Platform", "\"Android\"")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .header("Upgrade-Insecure-Requests", "1")
                .get();

        String host = hostOf(url);
        if (!host.isEmpty()) {
            String cookie = hostCookies.get(host);
            if (cookie != null && !cookie.isEmpty()) {
                reqBuilder.header("Cookie", cookie);
            }
        }

        Request req = reqBuilder.build();
        try (Response resp = client.newCall(req).execute()) {
            ResponseBody body = resp.body();
            if (!resp.isSuccessful() || body == null) {
                if (resp.code() == 403 || resp.code() == 429 || resp.code() == 503) {
                    throw new IOException("HTTP " + resp.code()
                            + "：Cloudflare 安全验证或限流，需要过盾处理");
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
            return buf.readString(StandardCharsets.UTF_8);
        }
    }
}
