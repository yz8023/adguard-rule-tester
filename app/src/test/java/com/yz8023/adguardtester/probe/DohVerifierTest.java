package com.yz8023.adguardtester.probe;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/** {@link DohVerifier} 判定与缓存语义测试。 */
public class DohVerifierTest {

    private MockWebServer doh;
    private DohVerifier verifier;

    @Before
    public void setUp() throws Exception {
        doh = new MockWebServer();
        doh.start();
        verifier = new DohVerifier(new OkHttpClient.Builder()
                        .connectTimeout(2, TimeUnit.SECONDS)
                        .readTimeout(2, TimeUnit.SECONDS)
                        .build(),
                new String[]{doh.url("/resolve?name=").toString()});
    }

    @After
    public void tearDown() throws Exception {
        doh.shutdown();
    }

    @Test
    public void aliveWhenStatusZeroWithAnswer() {
        doh.enqueue(new MockResponse().setBody(
                "{\"Status\":0,\"Answer\":[{\"name\":\"a\",\"type\":1,\"data\":\"1.1.1.1\"}]}"));
        assertEquals(DohVerifier.Verdict.ALIVE, verifier.verify("a.example.com"));
    }

    @Test
    public void noAnswerWhenStatusZeroWithoutAnswer() {
        doh.enqueue(new MockResponse().setBody("{\"Status\":0}"));
        assertEquals(DohVerifier.Verdict.NO_ANSWER, verifier.verify("b.example.com"));
    }

    @Test
    public void nxdomainWhenStatusThree() {
        doh.enqueue(new MockResponse().setBody("{\"Status\":3}"));
        assertEquals(DohVerifier.Verdict.NXDOMAIN, verifier.verify("c.example.com"));
    }

    @Test
    public void errorWhenServerFailsOrMalformed() {
        doh.enqueue(new MockResponse().setResponseCode(500));
        doh.enqueue(new MockResponse().setBody("<html>not json</html>"));
        doh.enqueue(new MockResponse().setBody("{\"Status\":2}"));
        assertEquals(DohVerifier.Verdict.ERROR, verifier.verify("d.example.com"));
        assertEquals(DohVerifier.Verdict.ERROR, verifier.verify("d.example.com"));
        assertEquals(DohVerifier.Verdict.ERROR, verifier.verify("d.example.com"));
        assertEquals(3, doh.getRequestCount());
    }

    @Test
    public void errorIsNotCachedAndRequeriesOnNextVerify() {
        doh.enqueue(new MockResponse().setResponseCode(503));
        doh.enqueue(new MockResponse().setBody(
                "{\"Status\":0,\"Answer\":[{\"name\":\"a\",\"type\":1,\"data\":\"1.1.1.1\"}]}"));
        assertEquals(DohVerifier.Verdict.ERROR, verifier.verify("flaky.example.com"));
        assertEquals("DoH 一次失败后应重新查询而非永久缓存 ERROR",
                DohVerifier.Verdict.ALIVE, verifier.verify("flaky.example.com"));
        assertEquals(2, doh.getRequestCount());
    }

    @Test
    public void definiteVerdictsAreCached() {
        doh.enqueue(new MockResponse().setBody("{\"Status\":3}"));
        assertEquals(DohVerifier.Verdict.NXDOMAIN, verifier.verify("dead.example.com"));
        assertEquals(DohVerifier.Verdict.NXDOMAIN, verifier.verify("dead.example.com"));
        assertEquals("确定结论应命中缓存", 1, doh.getRequestCount());
    }

    @Test
    public void emptyHostIsError() {
        assertEquals(DohVerifier.Verdict.ERROR, verifier.verify(""));
        assertEquals(DohVerifier.Verdict.ERROR, verifier.verify(null));
    }
}
