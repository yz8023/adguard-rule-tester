package com.yz8023.adguardtester.probe;

import com.yz8023.adguardtester.model.ProbeResult;
import com.yz8023.adguardtester.model.ProbeStatus;
import com.yz8023.adguardtester.model.TestTarget;

import java.io.IOException;
import java.io.InterruptedIOException;
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

/**
 * 拦截探测引擎：对每个测试目标发起一次真实 HEAD 请求，
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
    private volatile Listener listener;
    private final AtomicInteger doneCount = new AtomicInteger(0);
    private volatile int totalCount = 0;
    /** 当前（最近一轮）运行的结果列表；snapshot 与 onFinished 都基于它。 */
    private volatile List<ProbeResult> currentRun = Collections.emptyList();

    /** 默认构造：公共 DoH、内置超时与 10 路并发。 */
    public ProbeEngine() {
        this(defaultClient(), defaultDoh());
    }

    ProbeEngine(OkHttpClient client, DohVerifier doh) {
        this(client, doh, Executors.newFixedThreadPool(POOL_SIZE));
    }

    ProbeEngine(OkHttpClient client, DohVerifier doh, ExecutorService pool) {
        this.client = client;
        this.doh = doh;
        this.pool = pool;
    }

    private static OkHttpClient defaultClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .writeTimeout(3, TimeUnit.SECONDS)
                .callTimeout(6, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .build();
    }

    private static DohVerifier defaultDoh() {
        OkHttpClient dohClient = defaultClient().newBuilder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .callTimeout(3, TimeUnit.SECONDS)
                .build();
        return new DohVerifier(dohClient);
    }

    /** 是否正在测试。 */
    public boolean isRunning() {
        return running.get();
    }

    /** 当前进度（已完成数 / 总数），供界面重建后恢复显示。 */
    public int progressDone() {
        return doneCount.get();
    }

    public int progressTotal() {
        return totalCount;
    }

    /** 本轮已产生的全部结果快照（界面重建后恢复列表用）。 */
    public List<ProbeResult> snapshot() {
        List<ProbeResult> run = currentRun;
        synchronized (run) {
            return new ArrayList<>(run);
        }
    }

    /**
     * 绑定进度回调（界面旋转后重新绑定用）。回调在工作线程触发。
     */
    public void attach(Listener newListener) {
        listener = newListener;
    }

    /** 解除回调绑定，避免持有已销毁的界面。 */
    public void detach() {
        listener = null;
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
        attach(listener);
        final int total = targets.size();
        totalCount = total;
        doneCount.set(0);
        final List<ProbeResult> runList = Collections.synchronizedList(new ArrayList<ProbeResult>());
        currentRun = runList;
        if (total == 0) {
            running.set(false);
            listener.onFinished(runList);
            return;
        }
        for (final TestTarget t : targets) {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    ProbeResult r;
                    if (cancelled) {
                        r = new ProbeResult(t, ProbeStatus.CANCELLED, "已取消", 0);
                    } else {
                        r = probeOne(t, useDoh);
                    }
                    synchronized (runList) {
                        runList.add(r);
                        int n = doneCount.incrementAndGet();
                        Listener cb = ProbeEngine.this.listener;
                        if (cb != null) {
                            cb.onProgress(r, n, total);
                        }
                        if (n >= total) {
                            running.set(false);
                            Listener fin = ProbeEngine.this.listener;
                            if (fin != null) {
                                fin.onFinished(new ArrayList<>(runList));
                            }
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

    /** 释放线程池（进程退出前无需手动调用）。 */
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
                .head()
                .build();
        try (Response resp = client.newCall(req).execute()) {
            long elapsed = System.currentTimeMillis() - start;
            return new ProbeResult(t, ProbeStatus.NOT_BLOCKED, "HTTP " + resp.code(), elapsed);
        } catch (UnknownHostException e) {
            return classify(t, useDoh, System.currentTimeMillis() - start,
                    "系统 DNS 无法解析");
        } catch (SSLException e) {
            return classify(t, useDoh, System.currentTimeMillis() - start,
                    "TLS 失败：" + shortMsg(e));
        } catch (InterruptedIOException e) {
            // OkHttp：callTimeout 抛 "timeout"，连接/读超时抛 SocketTimeoutException，
            // 线程被中断抛 "interrupted"。三者都与“被拦截的连接重置”不同，单独归类。
            String m = e.getMessage() == null ? "" : e.getMessage();
            String reason = m.contains("interrupted")
                    ? "请求已中断"
                    : "连接超时（网络慢或被静默丢弃，无法判定）";
            return new ProbeResult(t, ProbeStatus.UNKNOWN, reason,
                    System.currentTimeMillis() - start);
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
