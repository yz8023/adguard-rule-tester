package com.yz8023.adguardtester;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yz8023.adguardtester.model.ProbeResult;
import com.yz8023.adguardtester.model.ProbeStatus;
import com.yz8023.adguardtester.model.TestTarget;
import com.yz8023.adguardtester.net.SourceFetcher;
import com.yz8023.adguardtester.parse.SourceParser;
import com.yz8023.adguardtester.probe.ProbeEngine;
import com.yz8023.adguardtester.rules.RuleGenerator;
import com.yz8023.adguardtester.ui.ResultAdapter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import okhttp3.OkHttpClient;

/**
 * AdGuard 规则测试器主界面。
 *
 * <p>流程：解析测试站点列表 → 真机探测哪些域名未被拦截 → 生成 AdGuard 补漏规则。
 */
public class MainActivity extends AppCompatActivity {

    private static final String SRC1_NAME = "paileactivist/toolz";
    private static final String SRC1_URL = "https://paileactivist.github.io/toolz/adblock.html";
    private static final String SRC2_NAME = "mobileproxy.space";
    private static final String SRC2_URL = "https://mobileproxy.space/ch/adblock-test.html";
    private static final String SRC3_NAME = "Adblock Turtlecute";
    private static final String SRC3_URL = "https://adblock.turtlecute.org/";
    private static final String SRC4_NAME = "Super Adblock Test";
    private static final String SRC4_URL = "https://superadblocktest.com/";
    private static final String SRC5_NAME = "GetBlockify";
    private static final String SRC5_URL = "https://getblockify.com/adblock-test";

    private static final String[] SRC1_SCRIPTS = {"/resources/adblock/data.js"};
    private static final String[] NO_SCRIPTS = {};
    private static final String[] SRC3_SCRIPTS = {"/js/index.js"};
    private static final String[] SRC4_SCRIPTS = {"/adblock.js"};

    private static final int REQUEST_SAVE_RULES = 1001;

    private static final class SourceChoice {
        final String name;
        final String url;
        final String[] scriptUrlFilters;

        SourceChoice(String name, String url, String[] scriptUrlFilters) {
            this.name = name;
            this.url = url;
            this.scriptUrlFilters = scriptUrlFilters;
        }
    }

    private static final class SourceResult {
        final List<TestTarget> targets;
        final String failure;

        SourceResult(List<TestTarget> targets, String failure) {
            this.targets = targets;
            this.failure = failure;
        }
    }

    private CheckBox cbSrc1;
    private CheckBox cbSrc2;
    private CheckBox cbSrc3;
    private CheckBox cbSrc4;
    private CheckBox cbSrc5;
    private CheckBox cbSrcCustom;
    private EditText etUrl1;
    private EditText etUrl2;
    private EditText etUrlCustom;
    private Button btnParse;
    private TextView tvParseInfo;

    private CheckBox cbDoh;
    private CheckBox cbOem;
    private Button btnStart;
    private Button btnStop;
    private ProgressBar progressBar;
    private TextView tvProgress;
    private int lastProgressCount;

    private TextView tvSummary;
    private RecyclerView rvResults;

    private CheckBox cbImportant;
    private CheckBox cbUnknown;
    private CheckBox cbGroup;
    private EditText etRules;
    private Button btnGenerate;
    private Button btnCopy;
    private Button btnShare;
    private Button btnSave;

    private TextView tvStatus;

    private OkHttpClient http;
    private SourceFetcher fetcher;
    private ProbeEngine engine;
    private ExecutorService fetchPool;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<TestTarget> targets = new ArrayList<>();
    private final List<ProbeResult> results = new ArrayList<>();
    private ResultAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cbSrc1 = findViewById(R.id.cb_src1);
        cbSrc2 = findViewById(R.id.cb_src2);
        cbSrc3 = findViewById(R.id.cb_src3);
        cbSrc4 = findViewById(R.id.cb_src4);
        cbSrc5 = findViewById(R.id.cb_src5);
        cbSrcCustom = findViewById(R.id.cb_src_custom);
        TextView tvSourceUpdateNote = findViewById(R.id.tv_source_update_note);
        tvSourceUpdateNote.setText(getString(R.string.source_update_note, BuildConfig.VERSION_NAME));
        etUrl1 = findViewById(R.id.et_url1);
        etUrl2 = findViewById(R.id.et_url2);
        etUrlCustom = findViewById(R.id.et_url_custom);
        btnParse = findViewById(R.id.btn_parse);
        tvParseInfo = findViewById(R.id.tv_parse_info);

        cbDoh = findViewById(R.id.cb_doh);
        cbOem = findViewById(R.id.cb_oem);
        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);
        progressBar = findViewById(R.id.progress);
        tvProgress = findViewById(R.id.tv_progress);

        tvSummary = findViewById(R.id.tv_summary);
        rvResults = findViewById(R.id.rv_results);

        cbImportant = findViewById(R.id.cb_important);
        cbUnknown = findViewById(R.id.cb_unknown);
        cbGroup = findViewById(R.id.cb_group);
        etRules = findViewById(R.id.et_rules);
        btnGenerate = findViewById(R.id.btn_generate);
        btnCopy = findViewById(R.id.btn_copy);
        btnShare = findViewById(R.id.btn_share);
        btnSave = findViewById(R.id.btn_save);
        tvStatus = findViewById(R.id.tv_status);

        etUrl1.setText(SRC1_URL);
        etUrl2.setText(SRC2_URL);

        findViewById(R.id.btn_copy_src1).setOnClickListener(v ->
                copyLink(R.string.source_link_copied, etUrl1.getText().toString().trim()));
        findViewById(R.id.btn_copy_src2).setOnClickListener(v ->
                copyLink(R.string.source_link_copied,
                        SourceFetcher.canonicalizeKnownPageUrl(etUrl2.getText().toString())));
        findViewById(R.id.btn_copy_src3).setOnClickListener(v ->
                copyLink(R.string.source_link_copied, SRC3_URL));
        findViewById(R.id.btn_copy_src4).setOnClickListener(v ->
                copyLink(R.string.source_link_copied, SRC4_URL));
        findViewById(R.id.btn_copy_src5).setOnClickListener(v ->
                copyLink(R.string.source_link_copied, SRC5_URL));

        adapter = new ResultAdapter(new ResultAdapter.LinkCopyListener() {
            @Override
            public void onCopyLink(String url) {
                copyLink(R.string.target_link_copied, url);
            }
        });
        rvResults.setLayoutManager(new LinearLayoutManager(this));
        rvResults.setAdapter(adapter);
        rvResults.setNestedScrollingEnabled(true);

        http = new OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
        fetcher = new SourceFetcher(http);
        engine = new ProbeEngine();
        fetchPool = Executors.newFixedThreadPool(5);

        btnParse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                parseSources();
            }
        });
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTest();
            }
        });
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                engine.cancel();
                setStatus(getString(R.string.status_stopping));
            }
        });
        btnGenerate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generateRules();
            }
        });
        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyRules();
            }
        });
        btnShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareRules();
            }
        });
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveRules();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        engine.shutdown();
        fetchPool.shutdownNow();
    }

    // ------------------------------------------------------------------
    // 1) 解析测试列表
    // ------------------------------------------------------------------

    private void parseSources() {
        final List<SourceChoice> sources = new ArrayList<>();
        if (cbSrc1.isChecked()) {
            String url = etUrl1.getText().toString().trim();
            String[] scripts = SourceFetcher.canonicalizeKnownPageUrl(url).equalsIgnoreCase(SRC1_URL)
                    ? SRC1_SCRIPTS : null;
            sources.add(new SourceChoice(SRC1_NAME, url, scripts));
        }
        if (cbSrc2.isChecked()) {
            String url = etUrl2.getText().toString().trim();
            String[] scripts = SourceFetcher.canonicalizeKnownPageUrl(url).equalsIgnoreCase(SRC2_URL)
                    ? NO_SCRIPTS : null;
            sources.add(new SourceChoice(SRC2_NAME, url, scripts));
        }
        if (cbSrc3.isChecked()) {
            sources.add(new SourceChoice(SRC3_NAME, SRC3_URL, SRC3_SCRIPTS));
        }
        if (cbSrc4.isChecked()) {
            sources.add(new SourceChoice(SRC4_NAME, SRC4_URL, SRC4_SCRIPTS));
        }
        if (cbSrc5.isChecked()) {
            sources.add(new SourceChoice(SRC5_NAME, SRC5_URL, NO_SCRIPTS));
        }
        if (cbSrcCustom.isChecked()) {
            String url = etUrlCustom.getText().toString().trim();
            if (!url.isEmpty()) {
                sources.add(new SourceChoice("自定义", url, null));
            }
        }
        if (sources.isEmpty()) {
            toast(getString(R.string.err_no_source));
            return;
        }
        btnParse.setEnabled(false);
        setStatus(getString(R.string.status_parsing));
        tvParseInfo.setText("");

        fetchPool.execute(new Runnable() {
            @Override
            public void run() {
                final List<Future<SourceResult>> pending = new ArrayList<>();
                for (final SourceChoice source : sources) {
                    pending.add(fetchPool.submit(new Callable<SourceResult>() {
                        @Override
                        public SourceResult call() {
                            try {
                                SourceFetcher.FetchedSource fetched = fetcher.fetch(
                                        source.name, source.url, source.scriptUrlFilters);
                                SourceParser.ParsedList parsed = SourceParser.parseSource(
                                        source.name, fetched.pageUrl, fetched.html, fetched.assetTexts);
                                return new SourceResult(parsed.targets, null);
                            } catch (IOException e) {
                                return new SourceResult(new ArrayList<TestTarget>(),
                                        source.name + "：抓取失败（" + shortMsg(e) + "）");
                            } catch (RuntimeException e) {
                                return new SourceResult(new ArrayList<TestTarget>(),
                                        source.name + "：解析失败（" + shortMsg(e) + "）");
                            }
                        }
                    }));
                }

                final Map<String, TestTarget> merged = new LinkedHashMap<>();
                final List<String> notes = new ArrayList<>();
                for (int i = 0; i < pending.size(); i++) {
                    try {
                        SourceResult fetched = pending.get(i).get();
                        if (fetched.failure != null) {
                            notes.add(fetched.failure);
                            continue;
                        }
                        int added = 0;
                        for (TestTarget target : fetched.targets) {
                            if (!merged.containsKey(target.host)) {
                                merged.put(target.host, target);
                                added++;
                            }
                        }
                        notes.add(sources.get(i).name + "：新增 " + added + " 个目标");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        notes.add("抓取已中断");
                        break;
                    } catch (ExecutionException e) {
                        Throwable cause = e.getCause() == null ? e : e.getCause();
                        notes.add(sources.get(i).name + "：抓取失败（" + shortMsg(cause) + "）");
                    }
                }
                final List<TestTarget> list = new ArrayList<>(merged.values());
                final String note = join(notes, "\n");
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        targets.clear();
                        targets.addAll(list);
                        btnParse.setEnabled(true);
                        tvParseInfo.setText(getString(R.string.parse_result, list.size(), note));
                        setStatus(getString(list.isEmpty()
                                ? R.string.status_no_targets : R.string.status_parsed));
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------------
    // 2) 拦截测试
    // ------------------------------------------------------------------

    private void startTest() {
        if (engine.isRunning()) {
            toast(getString(R.string.err_running));
            return;
        }
        final List<TestTarget> toTest = new ArrayList<>();
        boolean withOem = cbOem.isChecked();
        for (TestTarget t : targets) {
            if (withOem || !t.isOem()) {
                toTest.add(t);
            }
        }
        if (toTest.isEmpty()) {
            toast(getString(R.string.err_no_targets));
            return;
        }
        results.clear();
        adapter.clear();
        updateSummary();
        lastProgressCount = 0;
        progressBar.setProgress(0);
        tvProgress.setText(getString(R.string.progress_text, 0, toTest.size()));
        setTestButtons(true);
        setStatus(getString(R.string.status_testing));
        ScrollView rootScroll = findViewById(R.id.root_scroll);
        rootScroll.post(new Runnable() {
            @Override
            public void run() {
                rootScroll.smoothScrollTo(0, tvSummary.getTop());
            }
        });
        final boolean useDoh = cbDoh.isChecked();

        engine.start(toTest, useDoh, new ProbeEngine.Listener() {
            @Override
            public void onProgress(final ProbeResult result, final int done, final int total) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        results.add(result);
                        adapter.add(result);
                        updateSummary();
                        lastProgressCount = Math.max(lastProgressCount, done);
                        progressBar.setProgress(lastProgressCount * 1000 / total);
                        tvProgress.setText(getString(R.string.progress_text, lastProgressCount, total));
                    }
                });
            }

            @Override
            public void onFinished(final List<ProbeResult> done) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        setTestButtons(false);
                        setStatus(getString(R.string.status_tested));
                        updateSummary();
                        generateRules();
                    }
                });
            }
        });
    }

    private void updateSummary() {
        int unblocked = 0;
        int blocked = 0;
        int dead = 0;
        int unknown = 0;
        for (ProbeResult r : results) {
            if (r.status == ProbeStatus.NOT_BLOCKED) {
                unblocked++;
            } else if (r.status == ProbeStatus.BLOCKED_DNS
                    || r.status == ProbeStatus.BLOCKED_NETWORK) {
                blocked++;
            } else if (r.status == ProbeStatus.DEAD) {
                dead++;
            } else {
                unknown++;
            }
        }
        tvSummary.setText(getString(R.string.summary_text,
                results.size(), unblocked, blocked, dead, unknown));
    }

    // ------------------------------------------------------------------
    // 3) 生成与导出规则
    // ------------------------------------------------------------------

    private void generateRules() {
        RuleGenerator.Options o = new RuleGenerator.Options();
        o.important = cbImportant.isChecked();
        o.groupByCategory = cbGroup.isChecked();
        o.includeUnknown = cbUnknown.isChecked();
        etRules.setText(RuleGenerator.generate(results, o));
        setStatus(getString(R.string.status_rules_ready));
    }

    private void copyLink(int messageResource, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("adguard-test-link", text.trim()));
            toast(getString(messageResource));
        }
    }

    private void copyRules() {
        String text = etRules.getText().toString();
        if (text.isEmpty()) {
            toast(getString(R.string.err_no_rules));
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("adguard-rules", text));
            toast(getString(R.string.copied));
        }
    }

    private void shareRules() {
        String text = etRules.getText().toString();
        if (text.isEmpty()) {
            toast(getString(R.string.err_no_rules));
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, getString(R.string.share_title)));
    }

    private void saveRules() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "adguard-extra-rules.txt");
        try {
            startActivityForResult(intent, REQUEST_SAVE_RULES);
        } catch (Exception e) {
            toast(getString(R.string.err_no_storage));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SAVE_RULES || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        String text = etRules.getText().toString();
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os == null) {
                throw new IOException("null output stream");
            }
            os.write(text.getBytes(StandardCharsets.UTF_8));
            toast(getString(R.string.saved));
        } catch (IOException e) {
            toast(getString(R.string.err_save_failed) + "：" + shortMsg(e));
        }
    }

    // ------------------------------------------------------------------
    // 杂项
    // ------------------------------------------------------------------

    private void setTestButtons(boolean testing) {
        btnStart.setEnabled(!testing);
        btnStop.setEnabled(testing);
        btnParse.setEnabled(!testing);
    }

    private void setStatus(String s) {
        tvStatus.setText(s);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private static String shortMsg(Throwable e) {
        String m = e.getMessage();
        if (m == null || m.isEmpty()) {
            return e.getClass().getSimpleName();
        }
        m = m.replace('\n', ' ');
        return m.length() > 60 ? m.substring(0, 60) : m;
    }

    private static String join(List<String> parts, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) {
                sb.append(sep);
            }
            sb.append(p);
        }
        return sb.toString();
    }
}
