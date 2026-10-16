package com.yz8023.adguardtester.probe;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 公共 DoH（DNS-over-HTTPS）参照解析，用于把“连接失败”区分成
 * 「域名被拦截」与「域名本身已失效」。纯 JVM（OkHttp），无 android.* 依赖。
 */
public final class DohVerifier {

    /** DoH 查询结论。 */
    public enum Verdict {
        /** 公共 DNS 能解析出记录——域名存活。 */
        ALIVE,
        /** 公共 DNS 返回 NXDOMAIN。 */
        NXDOMAIN,
        /** 公共 DNS 应答但没有记录。 */
        NO_ANSWER,
        /** DoH 服务不可达/无法验证。 */
        ERROR
    }

    static final String[] DEFAULT_RESOLVERS = {
            "https://dns.google/resolve?name=",
            "https://cloudflare-dns.com/dns-query?name=",
    };

    private static final Pattern STATUS = Pattern.compile("\"Status\"\\s*:\\s*(\\d+)");
    private static final Pattern ANSWER = Pattern.compile("\"Answer\"\\s*:\\s*\\[");

    private final OkHttpClient client;
    private final String[] resolvers;
    private final ConcurrentMap<String, Verdict> cache = new ConcurrentHashMap<>();

    public DohVerifier(OkHttpClient client) {
        this(client, DEFAULT_RESOLVERS);
    }

    /** 注入自定义 resolver 前缀（单测用；生产代码走默认公共 DoH）。 */
    DohVerifier(OkHttpClient client, String[] resolvers) {
        this.client = client;
        this.resolvers = resolvers;
    }

    public Verdict verify(String host) {
        if (host == null || host.isEmpty()) {
            return Verdict.ERROR;
        }
        Verdict cached = cache.get(host);
        if (cached != null) {
            return cached;
        }
        Verdict result = Verdict.ERROR;
        for (String resolver : resolvers) {
            result = query(resolver + host + "&type=A", host);
            if (result != Verdict.ERROR) {
                break;
            }
        }
        // ERROR 不缓存：DoH 一次抖动不应让该域名整个会话都停留“无法验证”。
        if (result != Verdict.ERROR) {
            cache.put(host, result);
        }
        return result;
    }

    private Verdict query(String url, String host) {
        Request req = new Request.Builder()
                .url(url)
                .header("Accept", "application/dns-json")
                .header("User-Agent", "AdGuardRuleTester/1.0")
                .get()
                .build();
        try (Response resp = client.newCall(req).execute()) {
            ResponseBody body = resp.body();
            if (!resp.isSuccessful() || body == null) {
                return Verdict.ERROR;
            }
            String json = body.string();
            Matcher sm = STATUS.matcher(json);
            if (!sm.find()) {
                return Verdict.ERROR;
            }
            int status = Integer.parseInt(sm.group(1));
            if (status == 3) {
                return Verdict.NXDOMAIN;
            }
            if (status != 0) {
                return Verdict.ERROR;
            }
            Matcher am = ANSWER.matcher(json);
            return am.find() ? Verdict.ALIVE : Verdict.NO_ANSWER;
        } catch (IOException | RuntimeException e) {
            return Verdict.ERROR;
        }
    }
}
