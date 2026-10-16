package com.yz8023.adguardtester.parse;

import com.yz8023.adguardtester.model.TestTarget;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析广告拦截测试页面里的测试目标列表。
 *
 * <p>支持多类真实来源结构：paileactivist/toolz 和 mobileproxy.space 的 JS 对象、
 * Turtlecute 的 {@code JSON.parse('...')} 数据、Super Adblock Test 的 {@code list(`...`,)}
 * 模板清单，以及 GetBlockify 的 SSR {@code itemProp="name"} 主机节点。
 *
 * <p>优先解析结构化 JS/语义化节点，再对无结构页面使用引号域名与根路径 URL 扫描；
 * 去噪规则排除站点自身主机、页面链接/静态资源、JSON-LD、内容平台根域和假 TLD。
 * 纯 JVM，无 android.* 依赖。
 */
public final class SourceParser {

    /** 一个解析出的目标集合。 */
    public static final class ParsedList {
        public final String sourceName;
        public final List<TestTarget> targets;

        public ParsedList(String sourceName, List<TestTarget> targets) {
            this.sourceName = sourceName;
            this.targets = Collections.unmodifiableList(targets);
        }
    }

    private SourceParser() {
    }

    // ------------------------------------------------------------------
    // 入口
    // ------------------------------------------------------------------

    /**
     * 解析一个测试页面（页面 HTML + 已抓取的外链脚本文本）。
     *
     * @param sourceName 来源名（展示用）
     * @param pageUrl    页面 URL，用于解析相对路径与排除站点自身域名
     * @param html       页面原始 HTML（含内联脚本）
     * @param assetTexts 已抓取的外链脚本文本，可为 null
     */
    public static ParsedList parseSource(String sourceName, String pageUrl, String html,
                                         List<String> assetTexts) {
        String pageHost = hostOf(pageUrl);
        Map<String, TestTarget> merged = new LinkedHashMap<>();
        List<String> texts = new ArrayList<>();
        if (html != null) {
            texts.add(html);
        }
        if (assetTexts != null) {
            for (String a : assetTexts) {
                if (a != null && !a.isEmpty()) {
                    texts.add(a);
                }
            }
        }
        for (String t : texts) {
            boolean structured = scanStructured(t, sourceName, pageHost, merged);
            structured |= scanJsonParsedObjects(t, sourceName, pageHost, merged);
            if (!structured) {
                scanGeneric(t, sourceName, pageHost, merged);
            }
        }
        List<TestTarget> out = new ArrayList<>(merged.values());
        Collections.sort(out, new Comparator<TestTarget>() {
            @Override
            public int compare(TestTarget a, TestTarget b) {
                int c = a.category.compareTo(b.category);
                if (c != 0) {
                    return c;
                }
                c = a.host.compareTo(b.host);
                if (c != 0) {
                    return c;
                }
                return a.sourceName.compareTo(b.sourceName);
            }
        });
        return new ParsedList(sourceName, out);
    }

    /** 从页面 HTML 里找出所有 &lt;script src&gt;，解析为绝对 URL。 */
    public static List<String> findLinkedScriptUrls(String pageUrl, String html) {
        List<String> out = new ArrayList<>();
        if (html == null || pageUrl == null) {
            return out;
        }
        Matcher m = Pattern
                .compile("<script\\b[^>]*\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                .matcher(html);
        while (m.find()) {
            String raw = m.group(1).trim();
            try {
                URI base = new URI(pageUrl);
                URI abs = base.resolve(raw);
                String scheme = abs.getScheme();
                if (abs.getHost() != null && scheme != null
                        && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                        && !out.contains(abs.toString())) {
                    out.add(abs.toString());
                }
            } catch (Exception ignore) {
                // 非法 URL 直接跳过
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 层 A：结构化 JS 对象扫描
    // ------------------------------------------------------------------

    /** {@code (const|let|var) name = { 或 [；也允许 window.xxx = {...}。} */
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "(?:^|[^\\w$.])(?:window\\.|self\\.|globalThis\\.)?"
                    + "((?:const|let|var)\\s+([A-Za-z_$][\\w$]*)|[A-Za-z_$][\\w$]*)\\s*=\\s*(?=[\\[{])");

    private static boolean scanStructured(String text, String sourceName, String pageHost,
                                          Map<String, TestTarget> merged) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        boolean foundStructuredValue = false;
        Matcher m = ASSIGNMENT.matcher(text);
        while (m.find()) {
            String rootName = m.group(2) != null ? m.group(2) : m.group(1);
            int start = m.end();
            C cursor = new C(text, start);
            Object tree;
            try {
                tree = cursor.value();
            } catch (RuntimeException e) {
                continue;
            }
            if (tree instanceof Map || tree instanceof List) {
                foundStructuredValue = true;
                Map<String, String> i18n = collectI18n(tree);
                walk(tree, hintCategory(rootName), "", i18n, sourceName, pageHost, merged);
            }
        }
        return foundStructuredValue;
    }

    /** 解析 JSON.parse('...') 中内嵌的字面量对象（Turtlecute 的实时测试清单采用此格式）。 */
    private static final Pattern JSON_PARSE_CALL = Pattern.compile("\\bJSON\\s*\\.\\s*parse\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    private static boolean scanJsonParsedObjects(String text, String sourceName, String pageHost,
                                                 Map<String, TestTarget> merged) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        boolean foundJsonObject = false;
        Matcher call = JSON_PARSE_CALL.matcher(text);
        while (call.find()) {
            try {
                Object argument = new C(text, call.end()).value();
                if (!(argument instanceof String)) {
                    continue;
                }
                Object tree = new C((String) argument, 0).value();
                if (tree instanceof Map || tree instanceof List) {
                    foundJsonObject = true;
                    Map<String, String> i18n = collectI18n(tree);
                    walk(tree, "", "", i18n, sourceName, pageHost, merged);
                }
            } catch (RuntimeException ignore) {
                // 动态 JSON.parse 参数或无效 JSON 均跳过
            }
        }
        return foundJsonObject;
    }

    private static String hintCategory(String rootName) {
        if (rootName == null) {
            return "";
        }
        String lower = rootName.toLowerCase(Locale.ROOT);
        if (lower.contains("oem")) {
            return "OEM跟踪器";
        }
        String canon = canonicalCategory(rootName, null);
        return canon == null ? "" : canon;
    }

    /** 收集 i18n 映射（如 cat_ads -> 广告）。 */
    private static Map<String, String> collectI18n(Object tree) {
        Map<String, String> out = new LinkedHashMap<>();
        if (!(tree instanceof Map)) {
            return out;
        }
        Object i18n = ((Map<?, ?>) tree).get("i18n");
        if (i18n instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) i18n).entrySet()) {
                if (e.getKey() instanceof String && e.getValue() instanceof String) {
                    out.put((String) e.getKey(), (String) e.getValue());
                }
            }
        }
        return out;
    }

    private static void walk(Object node, String category, String vendor, Map<String, String> i18n,
                             String sourceName, String pageHost, Map<String, TestTarget> merged) {
        if (node instanceof Map) {
            Map<?, ?> map = ((Map<?, ?>) node);
            String resolvedCategory = category;
            Object nameVal = map.get("name");
            if (nameVal instanceof String && i18n.containsKey(nameVal)) {
                String i18nVal = i18n.get(nameVal);
                resolvedCategory = canonicalCategory(i18nVal, i18nVal);
            }
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!(e.getKey() instanceof String)) {
                    continue;
                }
                String key = (String) e.getKey();
                Object val = e.getValue();
                if (val instanceof String) {
                    if (isStructuralKey(key) || canonicalCategory(key, null) != null) {
                        emit((String) val, resolvedCategory, "", sourceName, pageHost, merged);
                    } else {
                        emit((String) val, resolvedCategory, key, sourceName, pageHost, merged);
                    }
                } else if (val instanceof Map || val instanceof List) {
                    String childCat;
                    String childVendor;
                    if (isStructuralKey(key)) {
                        childCat = resolvedCategory;
                        childVendor = vendor;
                    } else {
                        String canon = canonicalCategory(key, null);
                        if (canon != null) {
                            childCat = canon;
                            childVendor = "";
                        } else {
                            childCat = resolvedCategory;
                            childVendor = key;
                        }
                    }
                    walk(val, childCat, childVendor, i18n, sourceName, pageHost, merged);
                }
            }
        } else if (node instanceof List) {
            for (Object v : (List<?>) node) {
                if (v instanceof String) {
                    emit((String) v, category, vendor, sourceName, pageHost, merged);
                } else {
                    walk(v, category, vendor, i18n, sourceName, pageHost, merged);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 层 B：通用扫描（引号裸域名 + 根路径 URL）
    // ------------------------------------------------------------------

    private static final Pattern URL_TOKEN = Pattern.compile(
            "https?://[^\\s\"'<>\\(\\)\\[\\]{},;\\\\]+", Pattern.CASE_INSENSITIVE);

    private static final Pattern QUOTED = Pattern.compile("'([^'\\n]*)'|\"([^\"\\n]*)\"");

    /** 页面样板噪声：JSON-LD 块与只带属性的标签（a/link/meta）。 */
    private static final Pattern JSON_LD_BLOCK = Pattern.compile(
            "<script\\b[^>]*type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>.*?</script>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATTR_TAGS = Pattern.compile(
            "<(?:a|link|meta)\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    /** 页面以语义化 itemProp=name 标记的测试主机（如 GetBlockify 的 SSR 域名列表）。 */
    private static final Pattern NAMED_HOST_NODE = Pattern.compile(
            "<[a-z][^>]*\\bitemprop\\s*=\\s*[\"']name[\"'][^>]*>\\s*([a-z0-9.-]{3,253})\\s*</[a-z][^>]*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HEADING = Pattern.compile(
            "<h[1-6]\\b[^>]*>(.*?)</h[1-6]>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    /** 中文分类词 -> 规范名。 */
    private static final String[][] CN_CATEGORIES = {
            {"广告", "广告"},
            {"分析", "分析"},
            {"跟踪", "跟踪"},
            {"错误监控", "错误监控"},
            {"加密货币挖矿", "加密货币挖矿"},
            {"社交跟踪器", "社交跟踪器"},
            {"OEM跟踪器", "OEM跟踪器"},
            {"Cookie横幅", "Cookie横幅"},
            {"联盟网络", "联盟网络"},
            {"A/B测试", "A/B测试"},
            {"邮件跟踪", "邮件跟踪"},
            {"视频广告", "视频广告"},
    };

    private static final Pattern EN_CATEGORY_WORD = Pattern.compile(
            "(?i)(?<![\\w-])(ads|advertising|analytics|trackers?|errors?|crypto|mining|social|oem|consent|affiliate|email|video|mix)(?![\\w-])");

    private static void scanGeneric(String text, String sourceName, String pageHost,
                                    Map<String, TestTarget> merged) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String cleaned = JSON_LD_BLOCK.matcher(text).replaceAll(" ");
        cleaned = ATTR_TAGS.matcher(cleaned).replaceAll(" ");
        CategoryIndex catIndex = new CategoryIndex(cleaned, cleaned.length());
        // GetBlockify 在服务端渲染的测试列表用 itemProp=name 标记每个主机；只读取该语义区，
        // 不扫描它的 Next.js/第三方脚本，避免把框架依赖当作测试域名。
        Matcher namedHost = NAMED_HOST_NODE.matcher(cleaned);
        boolean foundSemanticHostList = false;
        while (namedHost.find()) {
            foundSemanticHostList = true;
            String host = namedHost.group(1).trim();
            String category = categoryHeadingBefore(cleaned, namedHost.start());
            if (category == null) {
                category = catIndex.before(namedHost.start());
            }
            emit("https://" + host.toLowerCase(Locale.ROOT) + "/",
                    category, "", sourceName, pageHost, merged);
        }
        if (foundSemanticHostList) {
            return;
        }
        // 1) 带 scheme 的 URL：仅接受根路径（无路径或 "/"），避免抓到页面链接与静态资源。
        Matcher u = URL_TOKEN.matcher(cleaned);
        while (u.find()) {
            String raw = trimTrailingPunct(u.group());
            if (!isRootUrl(raw)) {
                continue;
            }
            String category = catIndex.before(u.start());
            emit(raw, category, "", sourceName, pageHost, merged);
        }
        // 2) 引号内裸域名：整串必须是一个域名。
        Matcher q = QUOTED.matcher(cleaned);
        while (q.find()) {
            String s = q.group(1) != null ? q.group(1) : q.group(2);
            if (s == null) {
                continue;
            }
            String cand = s.trim();
            while (cand.endsWith("/") || cand.endsWith(".")) {
                cand = cand.substring(0, cand.length() - 1);
            }
            if (!isPlausibleHost(cand)) {
                continue;
            }
            String category = catIndex.before(q.start());
            emit("https://" + cand.toLowerCase(Locale.ROOT) + "/", category, "", sourceName, pageHost, merged);
        }
    }

    /** 读取最近一个 HTML 标题作为列表分类（供服务端渲染的语义化主机清单使用）。 */
    private static String categoryHeadingBefore(String text, int offset) {
        Matcher heading = HEADING.matcher(text);
        String last = null;
        while (heading.find() && heading.start() < offset) {
            last = heading.group(1);
        }
        if (last == null) {
            return null;
        }
        String label = HTML_TAG.matcher(last).replaceAll("")
                .replace("&amp;", "&")
                .replace("&nbsp;", " ")
                .trim();
        return canonicalCategory(label, null);
    }

    /**
     * 在 text 里建立“位置 → 分类”的有序索引：中文分类词逐个 indexOf 扫一遍，
     * 英文分类词正则扫一遍；此后每个目标主机用二分查找取最近分类词。
     * 大页面（数百主机 × 数百 KB 文本）从 O(hosts×textLen) 降为 O(textLen)。
     */
    private static final class CategoryIndex {
        private final long[] positions;
        private final String[] categories;

        CategoryIndex(String text, int limit) {
            List<long[]> pos = new ArrayList<>();
            List<String> cats = new ArrayList<>();
            for (String[] pair : CN_CATEGORIES) {
                int idx = text.indexOf(pair[0]);
                while (idx >= 0 && idx < limit) {
                    pos.add(new long[]{idx});
                    cats.add(pair[1]);
                    idx = text.indexOf(pair[0], idx + 1);
                }
            }
            Matcher m = EN_CATEGORY_WORD.matcher(text);
            while (m.find() && m.start() < limit) {
                String canon = canonicalCategory(m.group(1), "未分类");
                pos.add(new long[]{m.start()});
                cats.add(canon);
            }
            // 按位置排序（插入序不稳定，需统一排序）。
            Integer[] order = new Integer[pos.size()];
            for (int i = 0; i < order.length; i++) {
                order[i] = i;
            }
            final List<long[]> posRef = pos;
            java.util.Arrays.sort(order, (a, b) -> Long.compare(posRef.get(a)[0], posRef.get(b)[0]));
            positions = new long[order.length];
            categories = new String[order.length];
            for (int i = 0; i < order.length; i++) {
                positions[i] = pos.get(order[i])[0];
                categories[i] = cats.get(order[i]);
            }
        }

        /** offset 之前最近的分类词；无命中返回 "未分类"。 */
        String before(int offset) {
            int lo = 0;
            int hi = positions.length - 1;
            int best = -1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (positions[mid] < offset) {
                    best = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            return best >= 0 ? categories[best] : "未分类";
        }
    }

    /** 读取最近一个 HTML 标题作为列表分类（供服务端渲染的语义化主机清单使用）。 */

    // ------------------------------------------------------------------
    // 目标校验与归一化
    // ------------------------------------------------------------------

    /** 结构化层接受的字符串目标：http(s) URL（含路径）或裸域名。 */
    private static void emit(String raw, String category, String vendor, String sourceName,
                             String pageHost, Map<String, TestTarget> merged) {
        if (raw == null) {
            return;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return;
        }
        String scheme = "https";
        String host;
        if (s.regionMatches(true, 0, "http://", 0, 7) || s.regionMatches(true, 0, "https://", 0, 8)) {
            boolean isHttp = s.regionMatches(true, 0, "http://", 0, 7);
            scheme = isHttp ? "http" : "https";
            String rest = s.substring(isHttp ? 7 : 8);
            int slash = rest.indexOf('/');
            String authority = slash >= 0 ? rest.substring(0, slash) : rest;
            int at = authority.lastIndexOf('@');
            if (at >= 0) {
                authority = authority.substring(at + 1);
            }
            int colon = authority.indexOf(':');
            host = colon >= 0 ? authority.substring(0, colon) : authority;
        } else {
            host = s;
        }
        host = host.trim().toLowerCase(Locale.ROOT);
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (!isPlausibleHost(host)) {
            return;
        }
        if (isSelfHost(host, pageHost)) {
            return;
        }
        if (DENY_HOSTS.contains(host)) {
            return;
        }
        String url = scheme + "://" + host + "/";
        if (!merged.containsKey(host)) {
            merged.put(host, new TestTarget(sourceName, category, vendor, host, url));
        }
    }

    static boolean isSelfHost(String host, String pageHost) {
        if (pageHost == null || pageHost.isEmpty()) {
            return false;
        }
        return host.equals(pageHost) || host.endsWith("." + pageHost);
    }

    private static boolean isRootUrl(String raw) {
        try {
            URI uri = new URI(raw);
            if (uri.getHost() == null) {
                return false;
            }
            String path = uri.getPath();
            return path == null || path.isEmpty() || path.equals("/");
        } catch (Exception e) {
            return false;
        }
    }

    /** 末尾可能粘着句读符号。 */
    private static String trimTrailingPunct(String s) {
        int end = s.length();
        while (end > 0) {
            char c = s.charAt(end - 1);
            if (c == '.' || c == ',' || c == ')' || c == ']' || c == '}' || c == ';' || c == '"' || c == '\'') {
                end--;
            } else {
                break;
            }
        }
        return s.substring(0, end);
    }

    /** 容易混淆为文件名的“假 TLD”。 */
    private static final String[] BLOCKED_TLDS =
            ("js css png jpg jpeg gif svg ico html htm php asp aspx jsp json xml txt md map " +
                    "woff woff2 ttf eot otf webp avif bmp mp4 mp3 webm m4a wav flac ogg avi mov mkv " +
                    "zip gz tgz tar rar 7z bz2 xz iso apk ipa deb rpm dmg exe dll so bin dat log " +
                    "conf ini cfg yml yaml toml lock sum mod info less scss sass vue jsx tsx ts mjs cjs")
                    .split(" ");

    /**
     * 文档/元数据与大型公共平台的根域名——绝不生成 {@code ||host^} 这类整域规则，
     * 测试列表里的目标几乎都用子域名，这里按精确主机名排除“平台根域”噪声。
     */
    private static final Set<String> DENY_HOSTS = new HashSet<>();
    static {
        String[] hosts = {
                "schema.org", "www.schema.org", "w3.org", "www.w3.org",
                "example.com", "example.org", "example.net", "localhost", "row.dataset.label",
                "google.com", "www.google.com", "youtube.com", "www.youtube.com",
                "googlevideo.com", "www.googlevideo.com", "spotify.com", "www.spotify.com",
                "facebook.com", "www.facebook.com", "instagram.com", "twitter.com", "x.com",
                "github.com", "www.github.com", "wikipedia.org", "www.wikipedia.org",
                "cloudflare.com", "amazon.com", "apple.com", "microsoft.com",
                "linkedin.com", "reddit.com", "tiktok.com", "vk.com", "t.me", "telegram.org",
        };
        Collections.addAll(DENY_HOSTS, hosts);
    }

    /** 主机名是否“像一个真实广告/跟踪域名”。 */
    public static boolean isPlausibleHost(String host) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        if (h.isEmpty() || h.length() > 253 || h.startsWith(".") || h.endsWith(".") || h.contains("..")) {
            return false;
        }
        String[] labels = h.split("\\.", -1);
        if (labels.length < 2) {
            return false;
        }
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63) {
                return false;
            }
            if (!label.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) {
                return false;
            }
        }
        String tld = labels[labels.length - 1];
        if (tld.length() < 2 || tld.length() > 24 || !tld.matches(".*[a-z].*")) {
            return false;
        }
        for (String bad : BLOCKED_TLDS) {
            if (tld.equals(bad)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 分类规范名
    // ------------------------------------------------------------------

    /**
     * 把各种写法（ads / Ads / 广告 / errortrackers / Error Trackers …）映射为中文规范分类名。
     * 无法识别返回 fallback（可为 null）。
     */
    public static String canonicalCategory(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        for (String[] pair : CN_CATEGORIES) {
            if (pair[0].equals(raw)) {
                return pair[1];
            }
        }
        String n = raw.toLowerCase(Locale.ROOT).replaceAll("[\\s/_\\-]", "");
        if (n.isEmpty()) {
            return fallback;
        }
        switch (n) {
            case "ads":
            case "ad":
            case "advertising":
            case "adblock":
            case "display&bannerads":
            case "contextual&nativeads":
            case "programmaticads":
            case "audioads":
            case "mobileads":
            case "popups&overlays":
            case "scriptloading&adinjection":
                return "广告";
            case "analytics":
            case "analysis":
            case "statistics":
            case "stats":
            case "analytics&invisibletrackers":
                return "分析";
            case "tracking":
            case "tracker":
            case "trackers":
            case "tracking&fingerprinting":
            case "crosssitetracking":
            case "fingerprinting&advancedtracking":
            case "ecommerce&retargeting":
                return "跟踪";
            case "errors":
            case "error":
            case "errortracker":
            case "errortrackers":
            case "errormonitoring":
            case "error&crashreporting":
            case "errortracking&monitoring":
                return "错误监控";
            case "crypto":
            case "cryptomining":
            case "mining":
            case "cryptominers":
            case "cryptocurrencymining":
                return "加密货币挖矿";
            case "social":
            case "socialtracker":
            case "socialtrackers":
            case "socialmediatrackers":
                return "社交跟踪器";
            case "oem":
            case "oems":
            case "oemtracker":
            case "oemtrackers":
            case "oemvendors":
            case "device&oemtracking":
                return "OEM跟踪器";
            case "consent":
            case "cookie":
            case "cookies":
            case "cookiebanner":
            case "cookiebanners":
            case "consentmanagement":
            case "consent&cookiemanagement":
            case "cookie&consent":
                return "Cookie横幅";
            case "affiliate":
            case "affiliates":
            case "affiliatenetwork":
            case "affiliatenetworks":
            case "affiliateredirectblocking":
            case "affiliate&attributiontracking":
                return "联盟网络";
            case "abtest":
            case "abtesting":
                return "A/B测试";
            case "email":
            case "mail":
            case "emailtracking":
            case "emailtrackers":
            case "emailtracking&livechat":
                return "邮件跟踪";
            case "video":
            case "videoads":
            case "videoadvertising":
                return "视频广告";
            case "mix":
            case "misc":
            case "other":
            case "advancedriskyhostchecks":
                return "综合";
            default:
                return fallback;
        }
    }

    /** 结构性键：不作为分类/厂商名。 */
    static boolean isStructuralKey(String key) {
        if (key == null) {
            return true;
        }
        String n = key.toLowerCase(Locale.ROOT).replaceAll("[\\s/_\\-]", "");
        switch (n) {
            case "name":
            case "icon":
            case "version":
            case "i18n":
            case "tests":
            case "test":
            case "categories":
            case "category":
            case "list":
            case "lists":
            case "items":
            case "domains":
            case "urls":
            case "hosts":
            case "targets":
            case "data":
            case "url":
            case "host":
            case "title":
            case "label":
            case "id":
            case "key":
            case "type":
            case "count":
            case "total":
            case "enabled":
            case "description":
            case "desc":
            case "vendor":
            case "source":
                return true;
            default:
                return false;
        }
    }

    // ------------------------------------------------------------------
    // 迷你 JS 字面量解析（对象/数组/字符串/字面量 + 注释）
    // ------------------------------------------------------------------

    static String hostOf(String url) {
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

    /** 简易 JS 值解析游标。 */
    static final class C {
        /** 嵌套深度上限：远程页面可能出现超深嵌套字面量，递归解析必须拦截以防 StackOverflowError。 */
        private static final int MAX_DEPTH = 64;

        final String s;
        int i;
        int depth;

        C(String s, int i) {
            this.s = s;
            this.i = i;
        }

        void ws() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '\f') {
                    i++;
                } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                    while (i < s.length() && s.charAt(i) != '\n') {
                        i++;
                    }
                } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                    int end = s.indexOf("*/", i + 2);
                    i = end < 0 ? s.length() : end + 2;
                } else {
                    break;
                }
            }
        }

        char peek() {
            return i < s.length() ? s.charAt(i) : '\0';
        }

        Object value() {
            ws();
            if (i >= s.length()) {
                throw new IllegalStateException("eof");
            }
            if (depth >= MAX_DEPTH) {
                throw new IllegalStateException("嵌套过深");
            }
            char c = s.charAt(i);
            if (c == 'l' && s.regionMatches(i, "list", 0, 4)) {
                int save = i;
                i += 4;
                ws();
                if (peek() == '(') {
                    i++;
                    ws();
                    if (peek() == '`') {
                        String raw = template();
                        ws();
                        // 允许 list(`...`) 与 list(`...`,) 两种常见格式。
                        if (peek() == ',') {
                            i++;
                            ws();
                        }
                        if (peek() == ')') {
                            i++;
                        }
                        List<Object> values = new ArrayList<>();
                        for (String host : raw.trim().split("\\s+")) {
                            if (!host.isEmpty()) {
                                values.add(host);
                            }
                        }
                        return values;
                    }
                }
                i = save;
            }
            if (c == '{') {
                depth++;
                try {
                    return object();
                } finally {
                    depth--;
                }
            }
            if (c == '[') {
                depth++;
                try {
                    return array();
                } finally {
                    depth--;
                }
            }
            if (c == '"' || c == '\'') {
                return string();
            }
            if (c == '`') {
                return template();
            }
            return literal();
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // {
            ws();
            if (peek() == '}') {
                i++;
                return m;
            }
            while (true) {
                ws();
                String k;
                char c = peek();
                if (c == '"' || c == '\'') {
                    k = string();
                } else if (c == '}') {
                    i++;
                    break;
                } else if (i >= s.length()) {
                    break;
                } else {
                    k = identKey();
                }
                ws();
                if (peek() == ':') {
                    i++;
                    Object v = value();
                    m.put(k, v);
                }
                ws();
                if (peek() == ',') {
                    i++;
                    continue;
                }
                if (peek() == '}') {
                    i++;
                    break;
                }
                break; // 结构异常，带着已有内容返回
            }
            return m;
        }

        List<Object> array() {
            List<Object> out = new ArrayList<>();
            i++; // [
            ws();
            if (peek() == ']') {
                i++;
                return out;
            }
            while (true) {
                ws();
                if (peek() == ']' || i >= s.length()) {
                    if (peek() == ']') {
                        i++;
                    }
                    break;
                }
                if (peek() == ',') {
                    i++;
                    continue;
                }
                out.add(value());
                ws();
                if (peek() == ',') {
                    i++;
                    continue;
                }
                if (peek() == ']') {
                    i++;
                    break;
                }
                break;
            }
            return out;
        }

        String string() {
            char quote = s.charAt(i);
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '\\' && i + 1 < s.length()) {
                    char n = s.charAt(i + 1);
                    switch (n) {
                        case 'n':
                            sb.append('\n');
                            i += 2;
                            break;
                        case 't':
                            sb.append('\t');
                            i += 2;
                            break;
                        case 'r':
                            sb.append('\r');
                            i += 2;
                            break;
                        case 'u':
                            if (i + 5 < s.length()) {
                                try {
                                    sb.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                                    i += 6;
                                } catch (NumberFormatException e) {
                                    i++;
                                }
                            } else {
                                i++;
                            }
                            break;
                        default:
                            sb.append(n);
                            i += 2;
                            break;
                    }
                } else if (c == quote) {
                    i++;
                    return sb.toString();
                } else {
                    sb.append(c);
                    i++;
                }
            }
            return sb.toString();
        }

        String template() {
            i++; // `
            int start = i;
            while (i < s.length() && s.charAt(i) != '`') {
                if (s.charAt(i) == '\\') {
                    i++;
                }
                i++;
            }
            String out = s.substring(start, Math.min(i, s.length()));
            if (i < s.length()) {
                i++;
            }
            return out;
        }

        String identKey() {
            int start = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ':' || c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                    break;
                }
                i++;
            }
            return s.substring(start, i).trim();
        }

        String literal() {
            int start = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ',' || c == '}' || c == ']' || c == ')' || Character.isWhitespace(c)) {
                    break;
                }
                i++;
            }
            return s.substring(start, i);
        }
    }
}
