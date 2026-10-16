package com.yz8023.adguardtester;

import com.yz8023.adguardtester.model.ProbeResult;
import com.yz8023.adguardtester.model.TestTarget;
import com.yz8023.adguardtester.net.SourceFetcher;
import com.yz8023.adguardtester.probe.ProbeEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * 进程级运行时：HTTP 客户端、探测引擎、解析线程池与共享数据。
 *
 * <p>放在进程级（而不是 Activity 字段）是为了：旋转屏等配置变化销毁重建 Activity 时，
 * 进行中的测试与已解析/已测得的数据得以保留，新 Activity 重新绑定即可恢复界面；
 * 同时后台任务只持有这里的数据，避免泄漏 Activity。
 */
public final class AppRuntime {

    private static final AppRuntime INSTANCE = new AppRuntime();

    public static AppRuntime get() {
        return INSTANCE;
    }

    public final OkHttpClient http;
    public final SourceFetcher fetcher;
    public final ProbeEngine engine;
    public final ExecutorService fetchPool;

    /** 已解析的测试目标（解析完成后填充）。 */
    public final List<TestTarget> targets = new ArrayList<>();
    /** 已得到的探测结果（测试过程中增量填充）。 */
    public final List<ProbeResult> results = new ArrayList<>();
    /** 最近一次解析的提示文本（旋转恢复用）。 */
    public volatile String lastParseNote = "";
    /** 解析是否进行中。 */
    public volatile boolean parsing = false;

    private AppRuntime() {
        http = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .callTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
        fetcher = new SourceFetcher(http);
        engine = new ProbeEngine();
        fetchPool = Executors.newFixedThreadPool(5);
    }
}
