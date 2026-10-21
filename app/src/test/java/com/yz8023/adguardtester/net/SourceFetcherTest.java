package com.yz8023.adguardtester.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class SourceFetcherTest {

    private MockWebServer server;
    private SourceFetcher fetcher;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build();
        fetcher = new SourceFetcher(client);
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    public void canonicalizesReportedMobileproxyPathTypo() {
        assertEquals(
                "https://mobileproxy.space/ch/adblock-test.html",
                SourceFetcher.canonicalizeKnownPageUrl(
                        "https://mobileproxy.space/Ch/adblock test .html"));
    }

    @Test
    public void canonicalizesMobileproxyCaseAndSeparatorVariants() {
        assertEquals(
                "https://mobileproxy.space/ch/adblock-test.html",
                SourceFetcher.canonicalizeKnownPageUrl(
                        "https://MOBILEPROXY.space/CH/adblocktest.html?source=app"));
    }

    @Test
    public void detectsSecurityVerificationPage() {
        String verification1 = "<html><h1>正在进行安全验证</h1>"
                + "本网站使用安全服务防护恶意自动程序。在验证您不是自动程序期间，将显示此页面。</html>";
        String verification2 = "<html><form id=\"challenge-form\"></form></html>";
        String verification3 = "<html><title>Just a moment...</title><body>Checking your browser before accessing cloudflare...</body></html>";

        assertTrue(SourceFetcher.isSecurityVerificationPage(verification1));
        assertTrue(SourceFetcher.isSecurityVerificationPage(verification2));
        assertTrue(SourceFetcher.isSecurityVerificationPage(verification3));

        // 真实测试页（即便包含某些 Turnstile 引用但含 AdBlockTest）不属于拦截验证页
        assertFalse(SourceFetcher.isSecurityVerificationPage(
                "<html><title>AdBlock test</title><body><script>const AdBlockTest = {};</script></body></html>"));
    }

    @Test
    public void invokesChallengeResolverWhenSecurityVerificationDetected() throws Exception {
        String pageUrl = server.url("/adblock-test.html").toString();
        server.enqueue(new MockResponse().setBody("<html><h1>正在进行安全验证</h1><div id='challenge-platform'></div></html>"));

        fetcher.setChallengeResolver((name, url, filters) ->
                new SourceFetcher.FetchedSource(name, url,
                        "<html><script>const AdBlockTest = { tests: { t: 'ad.example.com' } };</script></html>",
                        Collections.emptyList()));

        SourceFetcher.FetchedSource result = fetcher.fetch("mobileproxy", pageUrl);
        assertNotNull(result);
        assertTrue(result.html.contains("AdBlockTest"));
    }

    @Test
    public void invokesChallengeResolverWhenHttp403Encountered() throws Exception {
        String pageUrl = server.url("/adblock-test.html").toString();
        server.enqueue(new MockResponse().setResponseCode(403).setBody("Forbidden by Cloudflare"));

        fetcher.setChallengeResolver((name, url, filters) ->
                new SourceFetcher.FetchedSource(name, url,
                        "<html><script>const AdBlockTest = { analytics: { a: 'stat.example.com' } };</script></html>",
                        Collections.emptyList()));

        SourceFetcher.FetchedSource result = fetcher.fetch("mobileproxy", pageUrl);
        assertNotNull(result);
        assertTrue(result.html.contains("stat.example.com"));
    }

    @Test
    public void sendsRememberedCookieAndCustomUserAgent() throws Exception {
        String pageUrl = server.url("/test.html").toString();
        server.enqueue(new MockResponse().setBody("<html><body><script>const AdBlockTest={};</script></body></html>"));

        String host = server.getHostName();
        fetcher.setCookie(host, "cf_clearance=abc123token; __cf_bm=bm456");
        fetcher.setUserAgent("CustomBrowserUA/1.0");

        fetcher.fetch("custom", pageUrl);

        RecordedRequest req = server.takeRequest();
        assertEquals("CustomBrowserUA/1.0", req.getHeader("User-Agent"));
        assertEquals("cf_clearance=abc123token; __cf_bm=bm456", req.getHeader("Cookie"));
        assertTrue(req.getHeader("Accept").contains("text/html"));
    }

    @Test
    public void trimsInputAndLeavesOtherSitesUnchanged() {
        assertEquals(
                "https://example.org/custom-adblock-test.html",
                SourceFetcher.canonicalizeKnownPageUrl(
                        "  https://example.org/custom-adblock-test.html  "));
        assertEquals(
                "https://other.example/Ch/adblock test .html",
                SourceFetcher.canonicalizeKnownPageUrl(
                        "https://other.example/Ch/adblock test .html"));
        assertEquals("", SourceFetcher.canonicalizeKnownPageUrl(null));
    }
}
