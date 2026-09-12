package ir.echopaint.game;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import ir.tapsell.plus.AdRequestCallback;
import ir.tapsell.plus.AdShowListener;
import ir.tapsell.plus.TapsellPlus;
import ir.tapsell.plus.TapsellPlusInitListener;
import ir.tapsell.plus.model.AdNetworkError;
import ir.tapsell.plus.model.AdNetworks;
import ir.tapsell.plus.model.TapsellPlusAdModel;
import ir.tapsell.plus.model.TapsellPlusErrorModel;

public class MainActivity extends Activity {

    // ==== Tapsell credentials (provided by the developer) ====
    private static final String TAPSELL_APP_KEY =
            "nlhiaormqkitsjmfcoakhskplhrghsgaknmokdggljqmpbjroelthbkhkblqsekntkthen";
    private static final String INTERSTITIAL_ZONE_ID =
            "6aa439fccd33cd4ed6e43122";

    private WebView webView;
    private volatile String pendingInterstitialResponseId = null;
    private volatile boolean interstitialRequestInFlight = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);
        applyImmersiveMode();

        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new AdsBridge(), "AndroidAds");
        webView.loadUrl("file:///android_asset/index.html");

        initTapsell();
    }

    private void initTapsell() {
        TapsellPlus.initialize(this, TAPSELL_APP_KEY, new TapsellPlusInitListener() {
            @Override
            public void onInitializeSuccess(AdNetworks adNetworks) {
                preloadInterstitial();
            }

            @Override
            public void onInitializeFailed(AdNetworks adNetworks, AdNetworkError adNetworkError) {
                // مقداردهی اولیه شکست خورد؛ بار بعد که JS درخواستِ نمایشِ
                // تبلیغ بدهد، preloadInterstitial() دوباره تلاش می‌کند.
            }
        });
    }

    private void preloadInterstitial() {
        if (interstitialRequestInFlight || pendingInterstitialResponseId != null) return;
        interstitialRequestInFlight = true;
        TapsellPlus.requestInterstitialAd(this, INTERSTITIAL_ZONE_ID, new AdRequestCallback() {
            @Override
            public void response(TapsellPlusAdModel tapsellPlusAdModel) {
                super.response(tapsellPlusAdModel);
                interstitialRequestInFlight = false;
                pendingInterstitialResponseId = tapsellPlusAdModel.getResponseId();
            }

            @Override
            public void error(String s) {
                super.error(s);
                interstitialRequestInFlight = false;
            }
        });
    }

    /** پلی که به جاوااسکریپتِ بازی به اسمِ window.AndroidAds داده می‌شود.
     *  تصمیمِ «الان تبلیغ نشان بده یا نه» (همان ۳۵٪ احتمال، سرِ بردن یا
     *  باختنِ مرحله) کاملاً سمتِ index.html گرفته می‌شود؛ این کلاس فقط
     *  وقتی صدا زده شد، واقعاً تبلیغ را نمایش می‌دهد. */
    private class AdsBridge {
        @JavascriptInterface
        public void showInterstitial() {
            runOnUiThread(MainActivity.this::tryShowInterstitial);
        }
    }

    private void tryShowInterstitial() {
        final String responseId = pendingInterstitialResponseId;
        if (responseId == null) {
            preloadInterstitial();
            return;
        }
        pendingInterstitialResponseId = null;

        TapsellPlus.showInterstitialAd(this, responseId, new AdShowListener() {
            @Override
            public void onClosed(TapsellPlusAdModel tapsellPlusAdModel) {
                super.onClosed(tapsellPlusAdModel);
                preloadInterstitial();
            }

            @Override
            public void onError(TapsellPlusErrorModel tapsellPlusErrorModel) {
                super.onError(tapsellPlusErrorModel);
                preloadInterstitial();
            }
        });
    }

    private void applyImmersiveMode() {
        View decorView = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            if (getWindow().getInsetsController() != null) {
                getWindow().getInsetsController().hide(
                        android.view.WindowInsets.Type.statusBars()
                                | android.view.WindowInsets.Type.navigationBars());
                getWindow().getInsetsController().setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveMode();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
