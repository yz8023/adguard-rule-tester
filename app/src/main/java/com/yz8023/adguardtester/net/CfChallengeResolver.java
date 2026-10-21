package com.yz8023.adguardtester.net;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.appcompat.app.AlertDialog;

import com.yz8023.adguardtester.R;

import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Android WebView Cloudflare 自动过盾解算器。
 *
 * <p>工作流程：
 * 1. 当 OkHttp 遇到 Cloudflare 5秒盾/Turnstile/安全验证（HTTP 403/503 或验证页特征）时触发；
 * 2. 在主线程创建 WebView，开启完整 JavaScript/DOM/Cookie 容器执行 Cloudflare 客户端验证挑战；
 * 3. 注入辅助脚本定时轮询挑战状态，自动触发 Turnstile 确认；
 * 4. 若验证需要手动交互或耗时超过 2 秒，自动浮出对话框供用户一键点选验证码；
 * 5. 验证通过后自动捕获已解算的页面 HTML 与 {@code cf_clearance} 等 Cookie，
 *    同步回 {@link SourceFetcher} 供后续请求复用，并自动关闭对话框。
 */
public final class CfChallengeResolver implements SourceFetcher.ChallengeResolver {

    private static final long TIMEOUT_SECONDS = 30;
    private static final long DIALOG_DELAY_MS = 2000;
    private static final long POLL_INTERVAL_MS = 400;

    private final SourceFetcher fetcher;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile WeakReference<Activity> activityRef;

    public CfChallengeResolver(SourceFetcher fetcher, Activity activity) {
        this.fetcher = fetcher;
        this.activityRef = new WeakReference<>(activity);
    }

    public void attachActivity(Activity activity) {
        this.activityRef = new WeakReference<>(activity);
    }

    public void detachActivity() {
        this.activityRef = null;
    }

    @Override
    public SourceFetcher.FetchedSource resolve(String name, String pageUrl, String[] scriptUrlFilters) throws IOException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> resolvedHtml = new AtomicReference<>();
        final AtomicReference<Exception> failure = new AtomicReference<>();
        final AtomicBoolean finished = new AtomicBoolean(false);

        mainHandler.post(() -> {
            try {
                startSolving(name, pageUrl, scriptUrlFilters, latch, resolvedHtml, failure, finished);
            } catch (Exception e) {
                failure.set(e);
                finished.set(true);
                latch.countDown();
            }
        });

        try {
            boolean completed = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                mainHandler.post(() -> finished.set(true));
                throw new IOException("Cloudflare 验证超时（" + TIMEOUT_SECONDS + " 秒）；请检查网络或重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Cloudflare 验证已中断");
        }

        if (failure.get() != null) {
            Exception err = failure.get();
            if (err instanceof IOException) {
                throw (IOException) err;
            }
            throw new IOException("Cloudflare 验证失败：" + err.getMessage(), err);
        }

        String html = resolvedHtml.get();
        if (html == null || html.isEmpty()) {
            throw new IOException("未能通过 Cloudflare 验证获取有效页面内容");
        }

        return fetcher.fetchWithHtml(name, pageUrl, html, scriptUrlFilters);
    }

    private void startSolving(final String name, final String pageUrl, final String[] scriptUrlFilters,
                              final CountDownLatch latch, final AtomicReference<String> resolvedHtml,
                              final AtomicReference<Exception> failure, final AtomicBoolean finished) {
        Activity activity = activityRef != null ? activityRef.get() : null;
        Context context = activity != null ? activity : null;
        if (context == null) {
            failure.set(new IOException("无可用 Context 执行 WebView 验证"));
            finished.set(true);
            latch.countDown();
            return;
        }

        final WebView webView;
        try {
            webView = new WebView(context);
        } catch (Exception e) {
            failure.set(new IOException("初始化 WebView 失败：" + e.getMessage(), e));
            finished.set(true);
            latch.countDown();
            return;
        }

        final AtomicReference<AlertDialog> dialogRef = new AtomicReference<>();
        final AtomicReference<Runnable> pollRunnableRef = new AtomicReference<>();
        final AtomicReference<Runnable> showDialogRunnableRef = new AtomicReference<>();

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        try {
            String defaultUa = WebSettings.getDefaultUserAgent(context);
            if (defaultUa != null && !defaultUa.isEmpty()) {
                ws.setUserAgentString(defaultUa);
                fetcher.setUserAgent(defaultUa);
            }
        } catch (Exception ignore) {
        }

        final CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        final Runnable cleanup = () -> {
            Runnable showTask = showDialogRunnableRef.get();
            if (showTask != null) {
                mainHandler.removeCallbacks(showTask);
            }
            Runnable pollTask = pollRunnableRef.get();
            if (pollTask != null) {
                mainHandler.removeCallbacks(pollTask);
            }
            AlertDialog dialog = dialogRef.get();
            if (dialog != null && dialog.isShowing()) {
                try {
                    dialog.dismiss();
                } catch (Exception ignore) {
                }
            }
            try {
                if (webView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) webView.getParent()).removeView(webView);
                }
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.clearHistory();
                webView.destroy();
            } catch (Exception ignore) {
            }
        };

        // 2 秒后若仍未解决且 Activity 正常，显示带有 WebView 的过盾对话框
        Runnable showDialogTask = () -> {
            if (finished.get()) {
                return;
            }
            Activity act = activityRef != null ? activityRef.get() : null;
            if (act != null && !act.isFinishing() && !act.isDestroyed()) {
                try {
                    View dialogView = LayoutInflater.from(act).inflate(R.layout.dialog_cf_challenge, null);
                    FrameLayout container = dialogView.findViewById(R.id.fl_webview_container);
                    if (webView.getParent() instanceof ViewGroup) {
                        ((ViewGroup) webView.getParent()).removeView(webView);
                    }
                    container.addView(webView, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                    AlertDialog dialog = new AlertDialog.Builder(act)
                            .setView(dialogView)
                            .setCancelable(false)
                            .create();

                    dialogView.findViewById(R.id.btn_cf_cancel).setOnClickListener(v -> {
                        if (finished.compareAndSet(false, true)) {
                            failure.set(new IOException("用户取消了 Cloudflare 验证"));
                            cleanup.run();
                            latch.countDown();
                        }
                    });

                    dialog.show();
                    dialogRef.set(dialog);
                } catch (Exception ignore) {
                }
            }
        };
        showDialogRunnableRef.set(showDialogTask);
        mainHandler.postDelayed(showDialogTask, DIALOG_DELAY_MS);

        final ValueCallback<String> evalCallback = resultJson -> {
            if (finished.get()) {
                return;
            }
            if (resultJson == null || "null".equals(resultJson)) {
                return;
            }
            try {
                String raw = resultJson;
                if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
                    raw = new JSONObject("{\"d\":" + raw + "}").getString("d");
                }
                JSONObject obj = new JSONObject(raw);
                boolean isChallenge = obj.optBoolean("isChallenge", true);
                boolean hasData = obj.optBoolean("hasData", false);
                String html = obj.optString("html", "");

                if (hasData || (!isChallenge && html.length() > 5000)) {
                    if (finished.compareAndSet(false, true)) {
                        resolvedHtml.set(html);
                        // 提取并保存 Cookie
                        try {
                            String cookies = cm.getCookie(pageUrl);
                            if (cookies != null && !cookies.isEmpty()) {
                                String host = Uri.parse(pageUrl).getHost();
                                if (host != null) {
                                    fetcher.setCookie(host.toLowerCase(Locale.ROOT), cookies);
                                }
                            }
                        } catch (Exception ignore) {
                        }
                        cleanup.run();
                        latch.countDown();
                    }
                }
            } catch (Exception ignore) {
            }
        };

        final String checkJs = "(function() {" +
                "  try {" +
                "    var html = document.documentElement ? document.documentElement.outerHTML : '';" +
                "    var text = document.body ? (document.body.innerText || '') : '';" +
                "    var title = document.title || '';" +
                "    var isChallenge = html.indexOf('challenge-platform') !== -1" +
                "      || html.indexOf('正在进行安全验证') !== -1" +
                "      || html.indexOf('验证您不是自动程序') !== -1" +
                "      || text.indexOf('Just a moment') !== -1" +
                "      || text.indexOf('Checking your browser') !== -1" +
                "      || title.indexOf('Just a moment') !== -1" +
                "      || title.indexOf('Cloudflare') !== -1;" +
                "    var hasData = html.indexOf('AdBlockTest') !== -1" +
                "      || html.indexOf('HOST_TESTS') !== -1" +
                "      || html.indexOf('itemprop=\"name\"') !== -1" +
                "      || (html.length > 5000 && !isChallenge);" +
                "    return JSON.stringify({" +
                "      isChallenge: isChallenge," +
                "      hasData: hasData," +
                "      title: title," +
                "      length: html.length," +
                "      html: html" +
                "    });" +
                "  } catch(e) {" +
                "    return JSON.stringify({ error: e.toString() });" +
                "  }" +
                "})()";

        final Runnable pollTask = new Runnable() {
            @Override
            public void run() {
                if (finished.get()) {
                    return;
                }
                try {
                    webView.evaluateJavascript(checkJs, evalCallback);
                } catch (Exception ignore) {
                }
                if (!finished.get()) {
                    mainHandler.postDelayed(this, POLL_INTERVAL_MS);
                }
            }
        };
        pollRunnableRef.set(pollTask);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!finished.get()) {
                    view.evaluateJavascript(checkJs, evalCallback);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress >= 80 && !finished.get()) {
                    view.evaluateJavascript(checkJs, evalCallback);
                }
            }
        });

        mainHandler.postDelayed(pollTask, POLL_INTERVAL_MS);
        webView.loadUrl(pageUrl);
    }
}
