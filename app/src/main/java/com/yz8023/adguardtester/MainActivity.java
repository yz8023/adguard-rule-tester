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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yz8023.adguardtester.model.ProbeResult;
import com.yz8023.adguardtester.model.ProbeStatus;
import com.yz8023.adguardtester.model.TestTarget;
import com.yz8023.adguardtester.net.CfChallengeResolver;
import com.yz8023.adguardtester.net.SourceFetcher;
import com.yz8023.adguardtester.net.Sources;
import com.yz8023.adguardtester.parse.SourceParser;
import com.yz8023.adguardtester.probe.ProbeEngine;
import com.yz8023.adguardtester.rules.RuleGenerator;
import com.yz8023.adguardtester.ui.ResultAdapter;
import com.yz8023.adguardtester.util.Texts;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * AdGuard 规则测试器主界面。
 *
 * <p>流程：解析测试站点列表（支持 Cloudflare 自动过盾）→ 真机探测哪些域名未被拦截 → 生成 AdGuard 补漏规则。
 * 共享状态（目标/结果/引擎/线程池）在 {@link AppRuntime}，旋转屏后恢复界面。
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_SAVE_RULES = 1001;

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

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ResultAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        applyWindowInsets();

        // 绑定 Cloudflare 自动过盾解算器
        SourceFetcher fetcher = AppRuntime.get().fetcher;
        if (fetcher.getChallengeResolver() instanceof CfChallengeResolver) {
            ((CfChallengeResolver) fetcher.getChallengeResolver()).attachActivity(this);
        } else {
            fetcher.setChallengeResolver(new CfChallengeResolver(fetcher, this));
        }

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

        if (etUrl1.getText().length() == 0) {
            etUrl1.setText(Sources.SRC1_URL);
        }
        if (etUrl2.getText().length() == 0) {
            etUrl2.setText(Sources.SRC2_URL);
        }

        findViewById(R.id.btn_copy_src1).setOnClickListener(v ->
                copyLink(R.string.source_link_copied, etUrl1.getText().toString().trim()));
        findViewById(R.id.btn_copy_src2).setOnClickListener(v ->
                copyLink(R.string.source_link_copied,
                        SourceFetcher.canonicalizeKnownPageUrl(etUrl2.getText().toString())));
        findViewById(R.id.btn_copy_src3).setOnClickListener(v ->
                copyLink(R.string.source_link_copied, Sources.SRC3_URL));
        findViewById(R.id.btn_copy_src4).setOnClickListener(v ->
                copyLink(R.string.source_link_copied, Sources.SRC4_URL));
        findViewById(R.id.btn_copy_src5).setOnClickListener(v ->
                copyLink(R.string.source_link_copied, Sources.SRC5_URL));

        adapter = new ResultAdapter(url -> copyLink(R.string.target_link_copied, url));
        rvResults.setLayoutManager(new LinearLayoutManager(this));
        rvResults.setAdapter(adapter);
        rvResults.setNestedScrollingEnabled(true);

        btnParse.setOnClickListener(v -> parseSources());
        btnStart.setOnClickListener(v -> startTest());
        btnStop.setOnClickListener(v -> {
            AppRuntime.get().engine.cancel();
            setStatus(getString(R.string.status_stopping));
        });
        btnGenerate.setOnClickListener(v -> generateRules());
        btnCopy.setOnClickListener(v -> copyRules());
        btnShare.setOnClickListener(v -> shareRules());
        btnSave.setOnClickListener(v -> saveRules());

        restoreAfterRecreate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SourceFetcher fetcher = AppRuntime.get().fetcher;
        if (fetcher != null && fetcher.getChallengeResolver() instanceof CfChallengeResolver) {
            ((CfChallengeResolver) fetcher.getChallengeResolver()).attachActivity(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SourceFetcher fetcher = AppRuntime.get().fetcher;
        if (fetcher != null && fetcher.getChallengeResolver() instanceof CfChallengeResolver) {
            ((CfChallengeResolver) fetcher.getChallengeResolver()).detachActivity();
        }
    }

    /** targetSdk 35+ 强制 edge-to-edge：把系统栏 insets 转成根布局 padding。 */
    private void applyWindowInsets() {
        View root = findViewById(R.id.root_scroll);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
                            | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    /** 旋转屏/进程内重建后恢复共享状态对应的界面。 */
    private void restoreAfterRecreate() {
        AppRuntime app = AppRuntime.get();
        tvParseInfo.setText(app.lastParseNote);

        if (app.engine.isRunning()) {
            adapter.clear();
            for (ProbeResult r : app.engine.snapshot()) {
                app.results.add(r);
                adapter.add(r);
            }
            lastProgressCount = Math.max(app.engine.progressDone(), app.results.size());
            progressBar.setProgress(lastProgressCount * 1000
                    / Math.max(1, app.engine.progressTotal()));
            tvProgress.setText(getString(R.string.progress_text,
                    lastProgressCount, app.engine.progressTotal()));
            updateSummary();
            setTestButtons(true);
            setStatus(getString(R.string.status_testing));
            app.engine.attach(new TestListener());
        } else {
            adapter.clear();
            adapter.addAll(new ArrayList<>(app.results));
            updateSummary();
            btnParse.setEnabled(!app.parsing);
            if (app.results.isEmpty()) {
                setStatus(getString(R.string.status_idle));
            } else {
                setStatus(getString(R.string.status_tested));
            }
        }
    }

    // ------------------------------------------------------------------
    // 1) 解析测试列表
    // ------------------------------------------------------------------

    private void parseSources() {
        final List<Sources.Choice> sources = new ArrayList<>();
        if (cbSrc1.isChecked()) {
            String url = etUrl1.getText().toString().trim();
            String[] scripts = SourceFetcher.canonicalizeKnownPageUrl(url)
                    .equalsIgnoreCase(Sources.SRC1_URL)
                    ? Sources.SRC1_SCRIPTS : null;
            sources.add(new Sources.Choice(Sources.SRC1_NAME, url, scripts));
        }
        if (cbSrc2.isChecked()) {
            String url = etUrl2.getText().toString().trim();
            String[] scripts = SourceFetcher.canonicalizeKnownPageUrl(url)
                    .equalsIgnoreCase(Sources.SRC2_URL)
                    ? Sources.NO_SCRIPTS : null;
            sources.add(new Sources.Choice(Sources.SRC2_NAME, url, scripts));
        }
        if (cbSrc3.isChecked()) {
            sources.add(new Sources.Choice(Sources.SRC3_NAME, Sources.SRC3_URL, Sources.SRC3_SCRIPTS));
        }
        if (cbSrc4.isChecked()) {
            sources.add(new Sources.Choice(Sources.SRC4_NAME, Sources.SRC4_URL, Sources.SRC4_SCRIPTS));
        }
        if (cbSrc5.isChecked()) {
            sources.add(new Sources.Choice(Sources.SRC5_NAME, Sources.SRC5_URL, Sources.NO_SCRIPTS));
        }
        if (cbSrcCustom.isChecked()) {
            String url = etUrlCustom.getText().toString().trim();
            if (!url.isEmpty()) {
                sources.add(new Sources.Choice("自定义", url, null));
            }
        }
        if (sources.isEmpty()) {
            toast(getString(R.string.err_no_source));
            return;
        }
        final AppRuntime app = AppRuntime.get();
        if (app.parsing) {
            toast(getString(R.string.status_parsing));
            return;
        }
        synchronized (app.targets) {
            app.targets.clear();
        }
        app.parsing = true;
        btnParse.setEnabled(false);
        setStatus(getString(R.string.status_parsing));
        tvParseInfo.setText("");

        final SourceFetcher fetcher = app.fetcher;
        app.fetchPool.execute(() -> {
            final List<Future<ParseOutcome>> pending = new ArrayList<>();
            for (Sources.Choice source : sources) {
                pending.add(app.fetchPool.submit(() -> {
                    try {
                        SourceFetcher.FetchedSource fetched =
                                fetcher.fetch(source.name, source.url, source.scriptUrlFilters);
                        SourceParser.ParsedList parsed = SourceParser.parseSource(
                                source.name, fetched.pageUrl, fetched.html, fetched.assetTexts);
                        return new ParseOutcome(parsed.targets, null);
                    } catch (IOException e) {
                        return new ParseOutcome(new ArrayList<>(),
                                source.name + "：抓取失败（" + Texts.shortMsg(e, 60) + "）");
                    } catch (RuntimeException e) {
                        return new ParseOutcome(new ArrayList<>(),
                                source.name + "：解析失败（" + Texts.shortMsg(e, 60) + "）");
                    }
                }));
            }

            final List<String> notes = new ArrayList<>();
            for (int i = 0; i < pending.size(); i++) {
                try {
                    ParseOutcome outcome = pending.get(i).get();
                    if (outcome.failure != null) {
                        notes.add(outcome.failure);
                        continue;
                    }
                    int added = 0;
                    synchronized (app.targets) {
                        for (TestTarget target : outcome.targets) {
                            boolean exists = false;
                            for (TestTarget t : app.targets) {
                                if (t.host.equals(target.host)) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                app.targets.add(target);
                                added++;
                            }
                        }
                    }
                    notes.add(sources.get(i).name + "：新增 " + added + " 个目标");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    notes.add("抓取已中断");
                    break;
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    notes.add(sources.get(i).name + "：抓取失败（" + Texts.shortMsg(cause, 60) + "）");
                }
            }
            final int total;
            synchronized (app.targets) {
                total = app.targets.size();
            }
            final String note = Texts.join(notes, "\n");
            app.lastParseNote = note;
            app.parsing = false;
            mainHandler.post(() -> {
                tvParseInfo.setText(getString(R.string.parse_result, total, note));
                btnParse.setEnabled(true);
                setStatus(getString(total == 0
                        ? R.string.status_no_targets : R.string.status_parsed));
            });
        });
    }

    // ------------------------------------------------------------------
    // 2) 拦截测试
    // ------------------------------------------------------------------

    private void startTest() {
        final AppRuntime app = AppRuntime.get();
        if (app.engine.isRunning()) {
            toast(getString(R.string.err_running));
            return;
        }
        final List<TestTarget> toTest = new ArrayList<>();
        boolean withOem = cbOem.isChecked();
        synchronized (app.targets) {
            for (TestTarget t : app.targets) {
                if (withOem || !t.isOem()) {
                    toTest.add(t);
                }
            }
        }
        if (toTest.isEmpty()) {
            toast(getString(R.string.err_no_targets));
            return;
        }
        app.results.clear();
        adapter.clear();
        updateSummary();
        lastProgressCount = 0;
        progressBar.setProgress(0);
        tvProgress.setText(getString(R.string.progress_text, 0, toTest.size()));
        setTestButtons(true);
        setStatus(getString(R.string.status_testing));
        ScrollView rootScroll = findViewById(R.id.root_scroll);
        rootScroll.post(() -> rootScroll.smoothScrollTo(0, tvSummary.getTop()));
        final boolean useDoh = cbDoh.isChecked();

        app.engine.start(toTest, useDoh, new TestListener());
    }

    private final class TestListener implements ProbeEngine.Listener {
        @Override
        public void onProgress(ProbeResult result, int done, int total) {
            mainHandler.post(() -> {
                AppRuntime.get().results.add(result);
                adapter.add(result);
                updateSummary();
                lastProgressCount = Math.max(lastProgressCount, done);
                progressBar.setProgress(lastProgressCount * 1000 / total);
                tvProgress.setText(getString(R.string.progress_text, lastProgressCount, total));
            });
        }

        @Override
        public void onFinished(List<ProbeResult> done) {
            mainHandler.post(() -> {
                setTestButtons(false);
                boolean cancelled = false;
                for (ProbeResult r : done) {
                    if (r.status == ProbeStatus.CANCELLED) {
                        cancelled = true;
                        break;
                    }
                }
                setStatus(getString(cancelled
                        ? R.string.status_cancelled : R.string.status_tested));
                updateSummary();
                generateRules();
            });
        }
    }

    private void updateSummary() {
        int unblocked = 0;
        int blocked = 0;
        int dead = 0;
        int unknown = 0;
        int cancelled = 0;
        List<ProbeResult> results = AppRuntime.get().results;
        for (ProbeResult r : results) {
            if (r.status == ProbeStatus.NOT_BLOCKED) {
                unblocked++;
            } else if (r.status == ProbeStatus.BLOCKED_DNS
                    || r.status == ProbeStatus.BLOCKED_NETWORK) {
                blocked++;
            } else if (r.status == ProbeStatus.DEAD) {
                dead++;
            } else if (r.status == ProbeStatus.CANCELLED) {
                cancelled++;
            } else {
                unknown++;
            }
        }
        tvSummary.setText(getString(R.string.summary_text,
                results.size(), unblocked, blocked, dead, unknown, cancelled));
    }

    // ------------------------------------------------------------------
    // 3) 生成与导出规则
    // ------------------------------------------------------------------

    private void generateRules() {
        RuleGenerator.Options o = new RuleGenerator.Options();
        o.important = cbImportant.isChecked();
        o.groupByCategory = cbGroup.isChecked();
        o.includeUnknown = cbUnknown.isChecked();
        etRules.setText(RuleGenerator.generate(AppRuntime.get().results, o));
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
    @Deprecated
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
            toast(getString(R.string.err_save_failed) + "：" + Texts.shortMsg(e, 60));
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

    private static final class ParseOutcome {
        final List<TestTarget> targets;
        final String failure;

        ParseOutcome(List<TestTarget> targets, String failure) {
            this.targets = targets;
            this.failure = failure;
        }
    }
}
