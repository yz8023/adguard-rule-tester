package com.yz8023.adguardtester.probe;

import com.yz8023.adguardtester.model.ProbeResult;
import com.yz8023.adguardtester.model.ProbeStatus;
import com.yz8023.adguardtester.model.TestTarget;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 拦截探测引擎：对每个测试目标发起一次真实请求，
 * “连接成功（任意 HTTP 状态码）= 未被拦截”；失败时用 DoH 参照区分拦截/失效。
 * 纯 JVM（OkHttp），无 android.* 依赖。
 */
public final class ProbeEngine {

    /** 进度回调（工作线程调用）。 */
    public interface Listener {
        void onProgress(ProbeResult result, int done, int total);

        void onFinished(List<ProbeResult> results);
    }

    private static final int POOL_SIZE = 10;
    private static final String USER_AGENT = "AdGuardRuleTester/1.0 (Android)";

    private final OkHttpClient client;
    private final DohVerifier doh;
    private final ExecutorService pool;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean cancelled = false;

    public ProbeEngine() {
        client = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .writeTimeout(3, TimeUnit.SECONDS)
                .callTimeout(6, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .build();
        OkHttpClient dohClient = client.newBuilder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .callTimeout(3, TimeUnit.SECONDS)
                .build();
        doh = new DohVerifier(dohClient);
        pool = Executors.newFixedThreadPool(POOL_SIZE);
    }

    /** 是否正在测试。 */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 开始探测。立即返回，进度从 listener 回调（工作线程）。
     *
     * @param targets 目标列表
     * @param useDoh  是否用公共 DoH 做参照验证（区分拦截/失效）
     */
    public void start(final List<TestTarget> targets, final boolean useDoh, final Listener listener) {
        if (targets == null || listener == null) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        cancelled = false;
        final int total = targets.size();
        final AtomicInteger done = new AtomicInteger(0);
        final Object callbackLock = new Object();
        final List<ProbeResult> results = Collections.synchronizedList(new ArrayList<ProbeResult>());
        if (total == 0) {
            running.set(false);
            listener.onFinished(results);
            return;
        }
        for (final TestTarget t : targets) {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    ProbeResult r;
                    if (cancelled) {
                        r = new ProbeResult(t, ProbeStatus.UNKNOWN, "已取消", 0);
                    } else {
                        r = probeOne(t, useDoh);
                    }
                    synchronized (callbackLock) {
                        results.add(r);
                        int n = done.incrementAndGet();
                        listener.onProgress(r, n, total);
                        if (n >= total) {
                            running.set(false);
                            listener.onFinished(results);
                        }
                    }
                }
            });
        }
    }

    /** 取消进行中的测试。 */
    public void cancel() {
        cancelled = true;
        client.dispatcher().cancelAll();
    }

    /** 释放线程池（Activity 销毁时调用）。 */
    public void shutdown() {
        cancel();
        pool.shutdownNow();
    }

    private ProbeResult probeOne(TestTarget t, boolean useDoh) {
        long start = System.currentTimeMillis();
        Request req = new Request.Builder()
                .url(t.url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .get()
                .build();
        try (Response resp = client.newCall(req).execute()) {
            ResponseBody body = resp.body();
            long elapsed = System.currentTimeMillis() - start;
            String detail = "HTTP " + resp.code();
            if (body != null) {
                body.close();
            }
            return new ProbeResult(t, ProbeStatus.NOT_BLOCKED, detail, elapsed);
        } catch (UnknownHostException e) {
            return classify(t, useDoh, System.currentTimeMillis() - start,
                    "系统 DNS 无法解析");
        } catch (SSLException e) {
            return classify(t, useDoh, System.currentTimeMillis() - start,
                    "TLS 失败：" + shortMsg(e));
        } catch (IOException e) {
            return classify(t, useDoh, System.currentTimeMillis() - start,
                    "连接失败：" + shortMsg(e));
        } catch (RuntimeException e) {
            return classify(t, useDoh, System.currentTimeMillis() - start,
                    "请求异常：" + shortMsg(e));
        }
    }

    private ProbeResult classify(TestTarget t, boolean useDoh, long elapsed, String reason) {
        if (!useDoh) {
            return new ProbeResult(t, ProbeStatus.UNKNOWN, reason + "（未启用 DoH 验证）", elapsed);
        }
        DohVerifier.Verdict v = doh.verify(t.host);
        switch (v) {
            case ALIVE:
                boolean dnsLevel = reason.contains("DNS");
                return new ProbeResult(t,
                        dnsLevel ? ProbeStatus.BLOCKED_DNS : ProbeStatus.BLOCKED_NETWORK,
                        reason + "；DoH 确认域名存活 → 疑似被拦截", elapsed);
            case NXDOMAIN:
                return new ProbeResult(t, ProbeStatus.DEAD,
                        reason + "；DoH 亦为 NXDOMAIN → 域名失效", elapsed);
            case NO_ANSWER:
                return new ProbeResult(t, ProbeStatus.DEAD,
                        reason + "；DoH 无解析记录 → 域名失效", elapsed);
            case ERROR:
            default:
                return new ProbeResult(t, ProbeStatus.UNKNOWN,
                        reason + "；DoH 不可达，无法验证", elapsed);
        }
    }

    private static String shortMsg(Throwable e) {
        String m = e.getMessage();
        if (m == null || m.isEmpty()) {
            return e.getClass().getSimpleName();
        }
        m = m.replace('\n', ' ');
        return m.length() > 80 ? m.substring(0, 80) : m;
    }
}
