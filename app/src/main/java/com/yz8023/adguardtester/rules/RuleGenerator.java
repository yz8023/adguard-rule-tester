package com.yz8023.adguardtester.rules;

import com.yz8023.adguardtester.model.ProbeResult;
import com.yz8023.adguardtester.model.ProbeStatus;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * 把“未被拦截”的探测结果生成 AdGuard 网络规则文本。纯 JVM，无 android.* 依赖。
 *
 * <p>输出形如：
 * <pre>
 * ! Title: AdGuard 补漏规则
 * ! Version: 2026-10-13 12:00:00 (UTC)
 * ! —— 广告 ——
 * ||pagead2.googlesyndication.com^
 * </pre>
 */
public final class RuleGenerator {

    /** 生成选项。 */
    public static final class Options {
        /** 附加 $important（优先于一般规则）。 */
        public boolean important = false;
        /** 按分类输出注释分组。 */
        public boolean groupByCategory = true;
        /** 把“无法验证”的目标也写进规则。 */
        public boolean includeUnknown = false;
        /** 标题。 */
        public String title = "AdGuard 补漏规则";
    }

    private RuleGenerator() {
    }

    public static String generate(List<ProbeResult> results, Options options) {
        Options o = options != null ? options : new Options();
        List<ProbeResult> need = new ArrayList<>();
        int nBlocked = 0;
        int nDead = 0;
        int nUnknown = 0;
        int nTotal = results == null ? 0 : results.size();
        Set<String> sources = new LinkedHashSet<>();
        if (results != null) {
            for (ProbeResult r : results) {
                if (r == null || r.target == null) {
                    continue;
                }
                sources.add(r.target.sourceName);
                if (r.status == ProbeStatus.NOT_BLOCKED
                        || (o.includeUnknown && r.status == ProbeStatus.UNKNOWN)) {
                    need.add(r);
                } else if (r.status == ProbeStatus.BLOCKED_DNS
                        || r.status == ProbeStatus.BLOCKED_NETWORK) {
                    nBlocked++;
                } else if (r.status == ProbeStatus.DEAD) {
                    nDead++;
                } else {
                    nUnknown++;
                }
            }
        }

        // 排序：分类 -> 主机名；同类主机去重。
        Collections.sort(need, new Comparator<ProbeResult>() {
            @Override
            public int compare(ProbeResult a, ProbeResult b) {
                int c = a.target.category.compareTo(b.target.category);
                if (c != 0) {
                    return c;
                }
                return a.target.host.compareTo(b.target.host);
            }
        });

        StringBuilder sb = new StringBuilder();
        String now = utcNow();
        sb.append("! Title: ").append(o.title).append('\n');
        sb.append("! Description: 由 AdGuard 规则测试器自动生成：下列域名在拦截测试中未被拦截。\n");
        sb.append("! Version: ").append(now).append('\n');
        sb.append("! Last modified: ").append(now).append('\n');
        sb.append("! Expires: 7 days\n");
        if (!sources.isEmpty()) {
            sb.append("! Source: ").append(join(sources)).append('\n');
        }
        sb.append("! Statistics: tested ").append(nTotal)
                .append(" | not-blocked ").append(need.size())
                .append(" | blocked ").append(nBlocked)
                .append(" | dead ").append(nDead)
                .append(" | unknown ").append(nUnknown).append('\n');
        sb.append("!\n");

        String suffix = o.important ? "$important" : "";
        Set<String> written = new LinkedHashSet<>();
        String lastCategory = null;
        int nRules = 0;
        for (ProbeResult r : need) {
            String host = r.target.host;
            if (host == null || host.isEmpty() || !written.add(host)) {
                continue;
            }
            if (o.groupByCategory && !host.isEmpty()) {
                String cat = r.target.category == null ? "未分类" : r.target.category;
                if (!cat.equals(lastCategory)) {
                    sb.append("! ---- ").append(cat).append(" ----\n");
                    lastCategory = cat;
                }
            }
            sb.append("||").append(host).append('^');
            if (!suffix.isEmpty()) {
                sb.append(suffix);
            }
            sb.append('\n');
            nRules++;
        }
        sb.append("!\n");
        sb.append("! total ").append(nRules).append(" rules\n");
        return sb.toString();
    }

    /** 仅导出未拦截主机名列表（分享/复核用）。 */
    public static List<String> unblockedHosts(List<ProbeResult> results) {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        List<String> out = new ArrayList<>();
        if (results == null) {
            return out;
        }
        for (ProbeResult r : results) {
            if (r != null && r.target != null && r.status == ProbeStatus.NOT_BLOCKED
                    && !seen.containsKey(r.target.host)) {
                seen.put(r.target.host, Boolean.TRUE);
                out.add(r.target.host);
            }
        }
        return out;
    }

    private static String join(Set<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(p);
        }
        return sb.toString();
    }

    private static String utcNow() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date()) + " (UTC)";
    }
}
