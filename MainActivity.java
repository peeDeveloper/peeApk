package com.lipee.peeapk;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.webkit.WebViewAssetLoader;

import java.util.ArrayList;
import java.util.List;

/* Gerado pelo peeApk (por Lipee). */
public class MainActivity extends AppCompatActivity {

    // ---- Configuracao escolhida no peeApk ----
    private static final String APP_NAME = "peeApk";
    private static final String START_URL = "https://appassets.androidplatform.net/assets/www/index.html";
    private static final String STATUS_MODE = "transparent";
    private static final String NAV_MODE = "hidden";
    private static final int BAR_COLOR = Color.parseColor("#000000");
    private static final boolean LIGHT_ICONS = true;
    private static final boolean PULL_REFRESH = false;
    private static final int AUTO_REFRESH_SECONDS = 0;
    private static final boolean REFRESH_ON_RESUME = true;
    private static final boolean KEEP_SCREEN_ON = true;
    private static final boolean BLOCK_SCREENSHOTS = false;
    private static final boolean BACK_NAVIGATES = true;
    private static final boolean ALLOW_ZOOM = false;
    private static final boolean OPEN_EXTERNAL = true;
    private static final boolean ALLOW_DOWNLOADS = true;
    private static final boolean BIOMETRIC_LOCK = false;
    private static final boolean BIOMETRIC_RELOCK = false;
    private static final boolean PERM_CAMERA = true;
    private static final boolean PERM_MIC = true;
    private static final boolean PERM_LOCATION = true;
    private static final boolean PERM_STORAGE = true;

    private WebView webView;
    private SwipeRefreshLayout swipe;
    private View lockView;
    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<Intent> fileLauncher;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean needAuth = false;
    private boolean authenticating = false;
    private boolean loaded = false;
    private boolean externalFlow = false;
    private boolean wasBackgrounded = false;

    private final Runnable autoRefreshTask = new Runnable() {
        @Override public void run() {
            if (!needAuth && webView != null) webView.reload();
            handler.postDelayed(this, AUTO_REFRESH_SECONDS * 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (BLOCK_SCREENSHOTS) getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        if (KEEP_SCREEN_ON) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        fileLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            externalFlow = false;
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.getResultCode(), result.getData()));
                filePathCallback = null;
            }
        });

        buildUi();
        setupSystemBars();
        setupBackHandler();

        needAuth = BIOMETRIC_LOCK;
        if (!BIOMETRIC_LOCK) startPage();
    }

    // ---- Interface ----
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BAR_COLOR);

        webView = new WebView(this);
        configureWebView();

        if (PULL_REFRESH) {
            swipe = new SwipeRefreshLayout(this);
            swipe.addView(webView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            swipe.setOnRefreshListener(() -> webView.reload());
            root.addView(swipe, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            root.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        lockView = buildLockView();
        lockView.setVisibility(View.GONE);
        root.addView(lockView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int top = "normal".equals(STATUS_MODE) ? bars.top : 0;
            int bottom = "normal".equals(NAV_MODE) ? bars.bottom : 0;
            bottom = Math.max(bottom, ime.bottom);
            v.setPadding(bars.left, top, bars.right, bottom);
            return insets;
        });
    }

    private View buildLockView() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.parseColor("#111111"));
        box.setClickable(true);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText(APP_NAME);
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);

        TextView sub = new TextView(this);
        sub.setText("Aplicativo bloqueado");
        sub.setTextColor(Color.parseColor("#AAAAAA"));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(24));

        Button unlock = new Button(this);
        unlock.setText("Desbloquear");
        unlock.setOnClickListener(v -> authenticate());

        box.addView(title);
        box.addView(sub);
        box.addView(unlock);
        return box;
    }

    private void setupSystemBars() {
        Window w = getWindow();
        WindowCompat.setDecorFitsSystemWindows(w, false);
        w.setStatusBarColor("normal".equals(STATUS_MODE) ? BAR_COLOR : Color.TRANSPARENT);
        w.setNavigationBarColor("normal".equals(NAV_MODE) ? BAR_COLOR : Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) w.setNavigationBarContrastEnforced(false);
        if (Build.VERSION.SDK_INT >= 28 && !"normal".equals(STATUS_MODE)) {
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.setAttributes(lp);
        }
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(w, w.getDecorView());
        c.setAppearanceLightStatusBars(!LIGHT_ICONS);
        c.setAppearanceLightNavigationBars(!LIGHT_ICONS);
        c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        hideBars();
    }

    private void hideBars() {
        Window w = getWindow();
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(w, w.getDecorView());
        if ("hidden".equals(STATUS_MODE)) c.hide(WindowInsetsCompat.Type.statusBars());
        if ("hidden".equals(NAV_MODE)) c.hide(WindowInsetsCompat.Type.navigationBars());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    // ---- WebView ----
    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(ALLOW_ZOOM);
        s.setBuiltInZoomControls(ALLOW_ZOOM);
        s.setDisplayZoomControls(false);
        s.setGeolocationEnabled(PERM_LOCATION);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (swipe != null) swipe.setRefreshing(false);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    List<String> ok = new ArrayList<>();
                    for (String r : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) && PERM_CAMERA) ok.add(r);
                        else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) && PERM_MIC) ok.add(r);
                    }
                    if (ok.isEmpty()) request.deny();
                    else request.grant(ok.toArray(new String[0]));
                });
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, PERM_LOCATION, false);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    externalFlow = true;
                    fileLauncher.launch(params.createIntent());
                } catch (Exception e) {
                    externalFlow = false;
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (!ALLOW_DOWNLOADS || !URLUtil.isNetworkUrl(url)) {
                Toast.makeText(this, "Download não suportado neste app.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                req.setMimeType(mimeType);
                req.addRequestHeader("User-Agent", userAgent);
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null) req.addRequestHeader("Cookie", cookie);
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType));
                ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
                Toast.makeText(this, "Baixando arquivo...", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Não foi possível baixar o arquivo.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean handleUrl(Uri uri) {
        String scheme = uri.getScheme();
        if (scheme == null || "blob".equals(scheme) || "data".equals(scheme) || "about".equals(scheme) || "javascript".equals(scheme)) return false;
        if ("appassets.androidplatform.net".equals(uri.getHost())) return false;
        boolean web = "http".equals(scheme) || "https".equals(scheme);
        if (web && !OPEN_EXTERNAL) return false;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            Toast.makeText(this, "Nenhum app para abrir este link.", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    // ---- Inicio, permissoes e biometria ----
    private void startPage() {
        requestRuntimePermissions();
        if (!loaded) {
            loaded = true;
            webView.loadUrl(START_URL);
        }
    }

    private void requestRuntimePermissions() {
        List<String> list = new ArrayList<>();
        if (PERM_CAMERA) list.add(Manifest.permission.CAMERA);
        if (PERM_MIC) list.add(Manifest.permission.RECORD_AUDIO);
        if (PERM_LOCATION) {
            list.add(Manifest.permission.ACCESS_FINE_LOCATION);
            list.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (PERM_STORAGE) {
            if (Build.VERSION.SDK_INT >= 33) {
                list.add("android.permission.READ_MEDIA_IMAGES");
                list.add("android.permission.READ_MEDIA_VIDEO");
                list.add("android.permission.READ_MEDIA_AUDIO");
            } else {
                list.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        if ((PERM_STORAGE || ALLOW_DOWNLOADS) && Build.VERSION.SDK_INT <= 28) {
            list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        List<String> missing = new ArrayList<>();
        for (String p : list) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        }
        if (!missing.isEmpty()) ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), 100);
    }

    private void authenticate() {
        if (authenticating) return;
        int auth = BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
        int can = BiometricManager.from(this).canAuthenticate(auth);
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            // Aparelho sem biometria nem bloqueio de tela: nao ha como autenticar, entao libera.
            Toast.makeText(this, "Este aparelho não tem biometria ou bloqueio de tela configurado.", Toast.LENGTH_LONG).show();
            unlock();
            return;
        }
        authenticating = true;
        BiometricPrompt prompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        authenticating = false;
                        unlock();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        authenticating = false;
                        lockView.setVisibility(View.VISIBLE);
                    }
                });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(APP_NAME)
                .setSubtitle("Confirme sua identidade para abrir")
                .setAllowedAuthenticators(auth)
                .build();
        prompt.authenticate(info);
    }

    private void unlock() {
        needAuth = false;
        lockView.setVisibility(View.GONE);
        startPage();
    }

    // ---- Ciclo de vida ----
    @Override
    protected void onStart() {
        super.onStart();
        if (AUTO_REFRESH_SECONDS > 0) {
            handler.removeCallbacks(autoRefreshTask);
            handler.postDelayed(autoRefreshTask, AUTO_REFRESH_SECONDS * 1000L);
        }
        if (wasBackgrounded) {
            wasBackgrounded = false;
            if (REFRESH_ON_RESUME && loaded) webView.reload();
        }
        if (needAuth) {
            lockView.setVisibility(View.VISIBLE);
            authenticate();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        lockView.setVisibility(needAuth ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onPause() {
        if (BIOMETRIC_LOCK && BIOMETRIC_RELOCK) lockView.setVisibility(View.VISIBLE);
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(autoRefreshTask);
        if (externalFlow) {
            externalFlow = false;
            return;
        }
        wasBackgrounded = true;
        if (BIOMETRIC_LOCK && BIOMETRIC_RELOCK) needAuth = true;
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(autoRefreshTask);
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (lockView.getVisibility() == View.VISIBLE) { finish(); return; }
                if (BACK_NAVIGATES && webView.canGoBack()) webView.goBack();
                else finish();
            }
        });
    }
}
