package com.yz8023.adguardtester.probe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yz8023.adguardtester.model.ProbeResult;
import com.yz8023.adguardtester.model.ProbeStatus;
import com.yz8023.adguardtester.model.TestTarget;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;

/** {@link ProbeEngine} 探测分类测试（MockWebServer，不发真实外网请求）。 */
public class ProbeEngineTest {

    private MockWebServer target;
    private MockWebServer doh;
    private ExecutorService pool;
    private ProbeEngine engine;

    @Before
    public void setUp() throws Exception {
        target = new MockWebServer();
        target.start();
        doh = new MockWebServer();
        doh.start();
        pool = Executors.newFixedThreadPool(4);
    }

    @After
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
        pool.shutdownNow();
    }

    private ProbeEngine newEngine(long callTimeoutMs) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .retryOnConnectionFailure(false)
                .build();
        OkHttpClient dohClient = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .callTimeout(3, TimeUnit.SECONDS)
                .build();
        DohVerifier verifier = new DohVerifier(dohClient, new String[]{
                doh.url("/resolve?name=").toString()});
        return new ProbeEngine(client, verifier, pool);
    }

    private static TestTarget localTarget(MockWebServer server) {
        String url = server.url("/").toString();
        String host = url.replaceFirst("^https?://", "").replaceFirst("/.*$", "");
        return new TestTarget("test", "广告", "", host, url);
    }

    private ProbeResult runOne(TestTarget t, boolean useDoh) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<ProbeResult> got = new AtomicReference<>();
        engine.start(java.util.Collections.singletonList(t), useDoh,
                new ProbeEngine.Listener() {
                    @Override
                    public void onProgress(ProbeResult result, int done, int total) {
                        got.set(result);
                    }

                    @Override
                    public void onFinished(List<ProbeResult> results) {
                        latch.countDown();
                    }
                });
        assertTrue("探测应在超时内完成", latch.await(20, TimeUnit.SECONDS));
        return got.get();
    }

    @Test
    public void successfulHeadRequestMeansNotBlocked() throws Exception {
        target.enqueue(new MockResponse().setResponseCode(200));
        engine = newEngine(6000);
        ProbeResult r = runOne(localTarget(target), false);
        assertEquals(ProbeStatus.NOT_BLOCKED, r.status);
        assertEquals("HEAD", target.takeRequest().getMethod());
    }

    @Test
    public void anyHttpResponseStillCountsAsNotBlocked() throws Exception {
        target.enqueue(new MockResponse().setResponseCode(500));
        engine = newEngine(6000);
        ProbeResult r = runOne(localTarget(target), false);
        assertEquals(ProbeStatus.NOT_BLOCKED, r.status);
    }

    @Test
    public void refusedConnectionWithDohAliveMeansBlockedNetwork() throws Exception {
        // 目标端口无监听：先启动再关闭，URL 指向已关闭端口。
        target.shutdown();
        doh.enqueue(new MockResponse().setBody(
                "{\"Status\":0,\"Answer\":[{\"name\":\"x\",\"type\":1,\"data\":\"1.2.3.4\"}]}"));
        engine = newEngine(6000);
        TestTarget t = new TestTarget("test", "广告", "", "127.0.0.1",
                "http://127.0.0.1:1/");
        ProbeResult r = runOne(t, true);
        assertEquals(ProbeStatus.BLOCKED_NETWORK, r.status);
        assertTrue(r.detail.contains("DoH 确认域名存活"));
    }

    @Test
    public void refusedConnectionWithDohNxdomainMeansDead() throws Exception {
        target.shutdown();
        doh.enqueue(new MockResponse().setBody("{\"Status\":3}"));
        engine = newEngine(6000);
        TestTarget t = new TestTarget("test", "广告", "", "127.0.0.1",
                "http://127.0.0.1:1/");
        ProbeResult r = runOne(t, true);
        assertEquals(ProbeStatus.DEAD, r.status);
    }

    @Test
    public void refusedConnectionWithDohDownMeansUnknown() throws Exception {
        target.shutdown();
        doh.enqueue(new MockResponse().setResponseCode(500));
        engine = newEngine(6000);
        TestTarget t = new TestTarget("test", "广告", "", "127.0.0.1",
                "http://127.0.0.1:1/");
        ProbeResult r = runOne(t, true);
        assertEquals(ProbeStatus.UNKNOWN, r.status);
        assertTrue(r.detail.contains("DoH 不可达"));
    }

    @Test
    public void timeoutIsReportedAsUnknownNotBlocked() throws Exception {
        target.enqueue(new MockResponse()
                .setSocketPolicy(SocketPolicy.NO_RESPONSE));
        engine = newEngine(700);
        ProbeResult r = runOne(localTarget(target), true);
        assertEquals(ProbeStatus.UNKNOWN, r.status);
        assertTrue("超时必须有明确语义：" + r.detail, r.detail.contains("超时"));
    }

    @Test
    public void cancelMarksUntestedTargetsAsCancelled() throws Exception {
        // 2 个在途（NO_RESPONSE 挂起，30s 超时内）+ 2 个排队；取消后排队的必须是 CANCELLED。
        target.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        target.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        ExecutorService smallPool = Executors.newFixedThreadPool(2);
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build();
        OkHttpClient dohClient = new OkHttpClient.Builder().build();
        DohVerifier verifier = new DohVerifier(dohClient, new String[]{
                doh.url("/resolve?name=").toString()});
        engine = new ProbeEngine(client, verifier, smallPool);
        final CountDownLatch finished = new CountDownLatch(1);
        List<TestTarget> targets = java.util.Arrays.asList(
                localTarget(target), localTarget(target),
                new TestTarget("test", "广告", "", "queued1.example.test", "https://queued1.example.test/"),
                new TestTarget("test", "广告", "", "queued2.example.test", "https://queued2.example.test/"));
        engine.start(targets, false, new ProbeEngine.Listener() {
            @Override
            public void onProgress(ProbeResult result, int done, int total) {
            }

            @Override
            public void onFinished(List<ProbeResult> results) {
                finished.countDown();
            }
        });
        Thread.sleep(300);
        engine.cancel();
        assertTrue(finished.await(20, TimeUnit.SECONDS));
        List<ProbeResult> all = engine.snapshot();
        assertEquals(4, all.size());
        int cancelled = 0;
        int aborted = 0;
        for (ProbeResult r : all) {
            if (r.status == ProbeStatus.CANCELLED) {
                cancelled++;
            } else if (r.status == ProbeStatus.UNKNOWN) {
                aborted++;
            }
        }
        assertEquals("排队目标应标记 CANCELLED", 2, cancelled);
        assertEquals("在途目标被取消中断 → UNKNOWN", 2, aborted);
        smallPool.shutdownNow();
    }

    @Test
    public void snapshotReflectsFinishedRun() throws Exception {
        target.enqueue(new MockResponse().setResponseCode(200));
        engine = newEngine(6000);
        runOne(localTarget(target), false);
        assertEquals(1, engine.snapshot().size());
        assertEquals(1, engine.progressDone());
        assertEquals(1, engine.progressTotal());
    }
}
