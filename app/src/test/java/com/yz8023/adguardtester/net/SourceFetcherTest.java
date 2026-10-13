package com.yz8023.adguardtester.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SourceFetcherTest {

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
    public void detectsSecurityVerificationPageWithoutTryingToBypassIt() {
        String verification = "<html><h1>正在进行安全验证</h1>"
                + "本网站使用安全服务防护恶意自动程序。在验证您不是自动程序期间，将显示此页面。</html>";
        assertTrue(SourceFetcher.isSecurityVerificationPage(verification));
        assertFalse(SourceFetcher.isSecurityVerificationPage(
                "<html><title>AdBlock test</title><body>ready</body></html>"));
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
