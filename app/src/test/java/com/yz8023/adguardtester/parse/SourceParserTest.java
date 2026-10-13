package com.yz8023.adguardtester.parse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.yz8023.adguardtester.model.TestTarget;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** {@link SourceParser} 解析测试：既有来源、候选测试站点和噪声过滤回归。 */
public class SourceParserTest {

    private static String resource(String name) throws IOException {
        InputStream in = SourceParserTest.class.getResourceAsStream("/" + name);
        assertNotNull("缺少测试资源 " + name, in);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    private static Map<String, TestTarget> byHost(List<TestTarget> targets) {
        Map<String, TestTarget> m = new HashMap<>();
        for (TestTarget t : targets) {
            m.put(t.host, t);
        }
        return m;
    }

    // ------------------------------------------------------------------
    // paileactivist/toolz（真实 data.js）
    // ------------------------------------------------------------------

    @Test
    public void parsesPaileactivistDataJs() throws IOException {
        String pageHtml = resource("paileactivist-page.html");
        String dataJs = resource("paileactivist-data.js");

        List<String> scripts = SourceParser.findLinkedScriptUrls(
                "https://paileactivist.github.io/toolz/adblock.html", pageHtml);
        assertEquals(5, scripts.size());
        assertTrue(scripts.contains("https://paileactivist.github.io/toolz/resources/adblock/data.js"));
        assertTrue(scripts.contains("https://paileactivist.github.io/toolz/src/js/script.js"));

        SourceParser.ParsedList parsed = SourceParser.parseSource(
                "paileactivist",
                "https://paileactivist.github.io/toolz/adblock.html",
                pageHtml,
                java.util.Collections.singletonList(dataJs));
        List<TestTarget> targets = parsed.targets;
        assertEquals(127, targets.size());

        Map<String, TestTarget> byHost = byHost(targets);
        TestTarget t = byHost.get("pagead2.googlesyndication.com");
        assertNotNull(t);
        assertEquals("广告", t.category);
        assertEquals("Google Ads", t.vendor);
        assertEquals("https://pagead2.googlesyndication.com/", t.url);

        assertEquals("分析", byHost.get("hotjar.com").category);
        assertEquals("错误监控", byHost.get("notify.bugsnag.com").category);
        assertEquals("社交跟踪器", byHost.get("pixel.facebook.com").category);
        assertEquals("综合", byHost.get("ads.yahoo.com").category);

        // dataOEM：分类 OEM跟踪器，厂商 Xiaomi；带尾随空格的条目被归一化。
        TestTarget oem = byHost.get("data.mistat.xiaomi.com");
        assertNotNull(oem);
        assertEquals("OEM跟踪器", oem.category);
        assertEquals("Xiaomi", oem.vendor);
        assertNotNull(byHost.get("metrics5.data.hicloud.com"));
        assertEquals("Huawei", byHost.get("metrics5.data.hicloud.com").vendor);
        // 奇形主机名保持有效
        assertNotNull(byHost.get("samsung-com.112.2o7.net"));

        // tracking.miui.com 在 Xiaomi 列表里重复出现，只保留一份
        int dup = 0;
        for (TestTarget target : targets) {
            if (target.host.equals("tracking.miui.com")) {
                dup++;
            }
        }
        assertEquals(1, dup);
    }

    // ------------------------------------------------------------------
    // mobileproxy.space 风格（AdBlockTest.tests + i18n）
    // ------------------------------------------------------------------

    @Test
    public void parsesMobileproxyStyleInlineScript() throws IOException {
        String html = resource("mobileproxy-like.html");
        SourceParser.ParsedList parsed = SourceParser.parseSource(
                "mobileproxy",
                "https://mobileproxy.space/ch/adblock-test.html",
                html,
                null);
        Map<String, TestTarget> byHost = byHost(parsed.targets);

        // 结构化提取：分类来自 i18n（cat_ads -> 广告）
        assertEquals("广告", byHost.get("pagead2.googlesyndication.com").category);
        assertEquals("广告", byHost.get("ib.adnxs.com").category);
        assertEquals("分析", byHost.get("hotjar.com").category);
        assertEquals("跟踪", byHost.get("bat.bing.com").category);
        assertEquals("错误监控", byHost.get("sentry.io").category);
        assertEquals(11, parsed.targets.size());

        // 噪声全部过滤：站点自身域名、JSON-LD、静态资源、假 TLD、带路径链接
        Set<String> hosts = new HashSet<>();
        for (TestTarget t : parsed.targets) {
            hosts.add(t.host);
        }
        assertFalse(hosts.contains("mobileproxy.space"));
        assertFalse(hosts.contains("cdn-delivery.mobileproxy.space"));
        assertFalse(hosts.contains("schema.org"));
        assertFalse(hosts.contains("t.me"));
        assertFalse(hosts.contains("cdn.jsdelivr.net"));
        assertFalse(hosts.contains("jquery.min.js"));
        assertFalse(hosts.contains("fa-ad"));
    }

    // ------------------------------------------------------------------
    // 新增真实站点夹具（抓取时间：2026-10）
    // ------------------------------------------------------------------

    @Test
    public void parsesMobileproxyCurrentPageStructure() throws IOException {
        String html = resource("mobileproxy-page-2026-10.html");
        SourceParser.ParsedList parsed = SourceParser.parseSource(
                "mobileproxy", "https://mobileproxy.space/ch/adblock-test.html", html, null);
        Map<String, TestTarget> hosts = byHost(parsed.targets);

        assertTrue("应提取四百余个实际测试主机", parsed.targets.size() >= 450);
        assertEquals("广告", hosts.get("pagead2.googlesyndication.com").category);
        assertEquals("分析", hosts.get("analytics.google.com").category);
        assertEquals("OEM跟踪器", hosts.get("api.ad.xiaomi.com").category);
        assertFalse(hosts.containsKey("mobileproxy.space"));
    }

    @Test
    public void parsesTurtlecuteJsonParsedHostList() throws IOException {
        String page = resource("turtlecute-page-2026-10.html");
        String hostList = resource("turtlecute-host-list-2026-10.js");
        List<String> scripts = SourceParser.findLinkedScriptUrls(
                "https://adblock.turtlecute.org/", page);
        assertTrue(scripts.contains("https://adblock.turtlecute.org/js/index.js"));

        SourceParser.ParsedList parsed = SourceParser.parseSource(
                "Turtlecute", "https://adblock.turtlecute.org/", page,
                java.util.Collections.singletonList(hostList));
        Map<String, TestTarget> hosts = byHost(parsed.targets);
        assertTrue("Turtlecute host 清单应覆盖 120 余项", parsed.targets.size() >= 120);
        assertEquals("广告", hosts.get("pagead2.googlesyndication.com").category);
        assertEquals("Google Ads", hosts.get("pagead2.googlesyndication.com").vendor);
        assertEquals("OEM跟踪器", hosts.get("api.ad.xiaomi.com").category);
        assertEquals("社交跟踪器", hosts.get("pixel.facebook.com").category);
        assertFalse(hosts.containsKey("adblock.turtlecute.org"));
    }

    @Test
    public void parsesSuperAdblockTestTemplateLists() throws IOException {
        String page = resource("superadblocktest-page-2026-10.html");
        String hostTests = resource("superadblocktest-host-tests-2026-10.js");
        List<String> scripts = SourceParser.findLinkedScriptUrls(
                "https://superadblocktest.com/", page);
        assertTrue(scripts.stream().anyMatch(url -> url.contains("/adblock.js")));

        SourceParser.ParsedList parsed = SourceParser.parseSource(
                "Super Adblock Test", "https://superadblocktest.com/adblock.js", hostTests, null);
        Map<String, TestTarget> hosts = byHost(parsed.targets);
        assertTrue("Super Adblock Test 应解析其大型模板域名清单，实际=" + parsed.targets.size(),
                parsed.targets.size() >= 300);
        assertEquals("广告", hosts.get("pagead2.googlesyndication.com").category);
        assertNotNull(hosts.get("analytics.google.com"));
        assertFalse(hosts.containsKey("googlevideo.com"));
        assertFalse(hosts.containsKey("superadblocktest.com"));
        assertFalse(hosts.containsKey("row.dataset.label"));
    }

    @Test
    public void parsesGetBlockifyServerRenderedHostNodes() throws IOException {
        String html = resource("getblockify-page-2026-10.html");
        SourceParser.ParsedList parsed = SourceParser.parseSource(
                "GetBlockify", "https://getblockify.com/adblock-test", html, null);
        Map<String, TestTarget> hosts = byHost(parsed.targets);
        assertTrue("应从语义化列表提取 80 余个主机", parsed.targets.size() >= 80);
        assertEquals("广告", hosts.get("adservice.google.com").category);
        assertEquals("错误监控", hosts.get("notify.bugsnag.com").category);
        assertFalse(hosts.containsKey("getblockify.com"));
        assertFalse(hosts.containsKey("youtube.com"));
        assertFalse(hosts.containsKey("googlevideo.com"));
        assertFalse(hosts.containsKey("spotify.com"));
        assertFalse(hosts.containsKey("nextjs.org"));
    }

    @Test
    public void genericScanFindsBareDomainsWithoutStructure() {
        String html = "<html><body><script>var list = ['ads.example-tracker.com', " +
                "'pixel.demo-analytics.net'];</script></body></html>";
        SourceParser.ParsedList parsed = SourceParser.parseSource(
                "custom", "https://some.test/page.html", html, null);
        Map<String, TestTarget> byHost = byHost(parsed.targets);
        assertNotNull(byHost.get("ads.example-tracker.com"));
        assertNotNull(byHost.get("pixel.demo-analytics.net"));
    }

    // ------------------------------------------------------------------
    // 过滤器单测
    // ------------------------------------------------------------------

    @Test
    public void hostPlausibilityFilters() {
        assertTrue(SourceParser.isPlausibleHost("pagead2.googlesyndication.com"));
        assertTrue(SourceParser.isPlausibleHost("samsung-com.112.2o7.net"));
        assertFalse(SourceParser.isPlausibleHost("jquery.min.js"));
        assertFalse(SourceParser.isPlausibleHost("app.min.css"));
        assertFalse(SourceParser.isPlausibleHost("localhost"));
        assertFalse(SourceParser.isPlausibleHost("single"));
        assertFalse(SourceParser.isPlausibleHost("bad_host.example.com"));
        assertFalse(SourceParser.isPlausibleHost("174.138.71.128"));
        assertFalse(SourceParser.isPlausibleHost(".cc_banner-wrapper"));
        assertFalse(SourceParser.isPlausibleHost("has..empty.com"));
    }

    @Test
    public void canonicalCategoryMapsBothLanguages() {
        assertEquals("广告", SourceParser.canonicalCategory("Ads", null));
        assertEquals("广告", SourceParser.canonicalCategory("ads", null));
        assertEquals("分析", SourceParser.canonicalCategory("Analytics", null));
        assertEquals("错误监控", SourceParser.canonicalCategory("Error Trackers", null));
        assertEquals("OEM跟踪器", SourceParser.canonicalCategory("oem", null));
        assertEquals("OEM跟踪器", SourceParser.canonicalCategory("OEM Vendors", null));
        assertEquals("A/B测试", SourceParser.canonicalCategory("abtest", null));
        assertEquals("联盟网络", SourceParser.canonicalCategory("Affiliate Networks", null));
        assertEquals("Cookie横幅", SourceParser.canonicalCategory("Consent Management", null));
        assertEquals("邮件跟踪", SourceParser.canonicalCategory("Email Tracking & Live Chat", null));
        assertEquals("视频广告", SourceParser.canonicalCategory("Video Ads", null));
        assertEquals("未分类", SourceParser.canonicalCategory("whatever", "未分类"));
    }

    @Test
    public void jsParserHandlesCommentsAndTrailingCommas() {
        String js = "const x = { /* c */ \"a\": [\"https://one.example.com/\", // line\n" +
                " \"https://two.example.com\" ], };\n";
        SourceParser.ParsedList parsed = SourceParser.parseSource(
                "custom", "https://some.test/p.html", js, null);
        Map<String, TestTarget> byHost = byHost(parsed.targets);
        assertNotNull(byHost.get("one.example.com"));
        assertNotNull(byHost.get("two.example.com"));
    }

    @Test
    public void linkedScriptsResolveRelativeAndSkipNonHttp() {
        String html = "<script src=\"../lib/data.js\"></script>" +
                "<script src=\"https://cdn.example.com/x.js\"></script>" +
                "<script src=\"data:text/javascript,foo\"></script>" +
                "<script>var a=1;</script>";
        List<String> urls = SourceParser.findLinkedScriptUrls(
                "https://host.example.com/tools/adblock.html", html);
        assertEquals(2, urls.size());
        assertEquals("https://host.example.com/lib/data.js", urls.get(0));
        assertEquals("https://cdn.example.com/x.js", urls.get(1));
    }

    @Test
    public void selfHostExcluded() {
        String html = "var x = ['https://sub.mobileproxy.space/', 'mobileproxy.space', " +
                "'tracker-widget.example.org'];";
        SourceParser.ParsedList parsed = SourceParser.parseSource(
                "mobileproxy", "https://mobileproxy.space/ch/adblock-test.html", html, null);
        List<String> hosts = new ArrayList<>();
        for (TestTarget t : parsed.targets) {
            hosts.add(t.host);
        }
        assertTrue(hosts.contains("tracker-widget.example.org"));
        assertFalse(hosts.contains("mobileproxy.space"));
        assertFalse(hosts.contains("sub.mobileproxy.space"));
    }
}
