package com.yz8023.adguardtester.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yz8023.adguardtester.model.ProbeResult;
import com.yz8023.adguardtester.model.ProbeStatus;
import com.yz8023.adguardtester.model.TestTarget;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** {@link RuleGenerator} 输出格式测试。 */
public class RuleGeneratorTest {

    private static ProbeResult result(String host, String category, ProbeStatus status) {
        TestTarget t = new TestTarget("test-src", category, "", host, "https://" + host + "/");
        return new ProbeResult(t, status, "detail", 1);
    }

    @Test
    public void generatesRulesForUnblockedOnly() {
        List<ProbeResult> results = Arrays.asList(
                result("ads.leaky.example.com", "广告", ProbeStatus.NOT_BLOCKED),
                result("track.ok.example.net", "跟踪", ProbeStatus.NOT_BLOCKED),
                result("blocked.example.org", "广告", ProbeStatus.BLOCKED_DNS),
                result("dead.example.io", "分析", ProbeStatus.DEAD),
                result("unknown.example.co", "分析", ProbeStatus.UNKNOWN));

        String rules = RuleGenerator.generate(results, null);
        assertTrue(rules.contains("||ads.leaky.example.com^\n"));
        assertTrue(rules.contains("||track.ok.example.net^\n"));
        assertFalse(rules.contains("||blocked.example.org^"));
        assertFalse(rules.contains("||dead.example.io^"));
        assertFalse(rules.contains("||unknown.example.co^"));

        assertTrue(rules.contains("! Title: AdGuard 补漏规则"));
        assertTrue(rules.contains("! Version: "));
        assertTrue(rules.contains("! Expires: 7 days"));
        assertTrue(rules.contains("| not-blocked 2"));
        assertTrue(rules.contains("| blocked 1"));
        assertTrue(rules.contains("| dead 1"));
        assertTrue(rules.contains("! ---- 广告 ----"));
        assertTrue(rules.contains("! ---- 跟踪 ----"));
        assertTrue(rules.contains("! total 2 rules"));
    }

    @Test
    public void importantOptionAppendsSuffix() {
        List<ProbeResult> results = Arrays.asList(
                result("ads.leaky.example.com", "广告", ProbeStatus.NOT_BLOCKED));
        RuleGenerator.Options o = new RuleGenerator.Options();
        o.important = true;
        String rules = RuleGenerator.generate(results, o);
        assertTrue(rules.contains("||ads.leaky.example.com^$important\n"));
    }

    @Test
    public void includeUnknownOptionAddsUnknown() {
        List<ProbeResult> results = Arrays.asList(
                result("unknown.example.co", "分析", ProbeStatus.UNKNOWN));
        RuleGenerator.Options o = new RuleGenerator.Options();
        o.includeUnknown = true;
        String rules = RuleGenerator.generate(results, o);
        assertTrue(rules.contains("||unknown.example.co^\n"));
    }

    @Test
    public void dedupesHostsAndSortsByCategoryThenHost() {
        List<ProbeResult> results = new ArrayList<>(Arrays.asList(
                result("zzz.example.com", "广告", ProbeStatus.NOT_BLOCKED),
                result("aaa.example.com", "广告", ProbeStatus.NOT_BLOCKED),
                result("zzz.example.com", "广告", ProbeStatus.NOT_BLOCKED),
                result("mmm.example.com", "分析", ProbeStatus.NOT_BLOCKED)));
        RuleGenerator.Options o = new RuleGenerator.Options();
        o.groupByCategory = true;
        String rules = RuleGenerator.generate(results, o);
        int iAaa = rules.indexOf("aaa.example.com");
        int iZzz = rules.indexOf("zzz.example.com");
        int iMmm = rules.indexOf("mmm.example.com");
        assertTrue(iAaa >= 0 && iMmm >= 0 && iZzz >= 0);
        // 中文码点序：「分析」(U+5206) 在「广告」(U+5E7F) 之前
        assertTrue("分析分组应在广告分组之前", iMmm < iAaa);
        assertTrue(iAaa < iZzz);
        assertTrue(rules.contains("! total 3 rules"));
    }

    @Test
    public void unblockedHostsHelper() {
        List<ProbeResult> results = Arrays.asList(
                result("a.example.com", "广告", ProbeStatus.NOT_BLOCKED),
                result("b.example.com", "广告", ProbeStatus.BLOCKED_NETWORK),
                result("a.example.com", "广告", ProbeStatus.NOT_BLOCKED));
        List<String> hosts = RuleGenerator.unblockedHosts(results);
        assertEquals(1, hosts.size());
        assertEquals("a.example.com", hosts.get(0));
    }

    @Test
    public void emptyResultsStillValid() {
        String rules = RuleGenerator.generate(new ArrayList<ProbeResult>(), null);
        assertTrue(rules.contains("! Title: "));
        assertTrue(rules.contains("! total 0 rules"));
    }
}
