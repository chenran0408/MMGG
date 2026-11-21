package com.leaf.hyperdragshare.gestures.xposed;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import com.leaf.hyperdragshare.gestures.config.ConfigContract;
import com.leaf.hyperdragshare.gestures.config.ShortcutIconLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Runtime launcher surface for targets that do not fit in the configured fan. */
final class MoreAppsOverlayController {
    private static final int MODE_ALL = MoreAppsCatalog.MODE_APPS;
    private static final int MODE_SHORTCUTS = MoreAppsCatalog.MODE_SHORTCUTS;
    private static final int INDEX_WIDTH_DP = 24;
    private static final int ICON_BATCH_SIZE = 16;
    // Keep space for the search field and at least one app row on a short display.
    private static final int FULL_CONTROLS_MIN_HEIGHT_DP = 280;
    private static final int[] COLOR_IDS = {MoreAppsCatalog.COLOR_ALL,
            MoreAppsCatalog.COLOR_GRAY, MoreAppsCatalog.COLOR_RED,
            MoreAppsCatalog.COLOR_ORANGE, MoreAppsCatalog.COLOR_YELLOW,
            MoreAppsCatalog.COLOR_GREEN, MoreAppsCatalog.COLOR_BLUE,
            MoreAppsCatalog.COLOR_PURPLE, MoreAppsCatalog.COLOR_PINK};
    private static final int[] COLOR_SWATCHES = {0xffeeeeee, 0xff888888, 0xffe53935,
            0xfffb8c00, 0xffffd600, 0xff43a047, 0xff1e88e5, 0xff8e24aa, 0xffec407a};

    private final Context context;
    private final Handler handler;
    private final WindowManager windowManager;
    private final Runnable visibilityChanged;
    private final ExecutorService loader = serialWorker("MoreAppsCatalog");
    private final ExecutorService iconLoader = serialWorker("MoreAppsIcons");
    private volatile List<Entry> cachedEntries;
    private final Set<String> changedPackages = new LinkedHashSet<>();
    private final Set<Entry> visibleIconRequests = new LinkedHashSet<>();
    private final Runnable loadIcons = this::loadRequestedIcons;
    private volatile boolean activityCatalogFailed;
    private volatile boolean shortcutCatalogFailed;
    private boolean shortcutCatalogChanged;
    private boolean loading;
    private boolean iconLoadScheduled;
    private boolean iconLoadRunning;
    private volatile View attached;
    private WindowManager.LayoutParams attachedParams;
    private LaunchAction launchAction;
    private MoreAdapter adapter;
    private Button[] tabButtons;
    private LinearLayout tabs;
    private GridView grid;
    private ListView basicList;
    private TextView empty;
    private LinearLayout indexBar;
    private List<String> indexLetters = Collections.emptyList();
    private EditText search;
    private Button clearFilters;
    private LinearLayout colorBar;
    private HorizontalScrollView colorScroll;
    private boolean compactControls;
    private boolean loadingShortcuts;
    private boolean enhanced;
    private int mode = MODE_ALL;
    private int selectedColor = MoreAppsCatalog.COLOR_ALL;
    private String selectedInitial = "";
    private String query = "";
    private long panelOpenedAt;
    private Palette palette;

    private final BroadcastReceiver packageChanges = new BroadcastReceiver() {
        @Override public void onReceive(Context ignored, Intent intent) {
            String pkg = intent.getData() == null ? null : intent.getData().getSchemeSpecificPart();
            if (pkg == null) return;
            handler.post(() -> {
                changedPackages.add(pkg);
                if (attached != null) loadEntriesAsync();
            });
        }
    };

    private final ContentObserver catalogChanges;

    private static ExecutorService serialWorker(String name) {
        return new ThreadPoolExecutor(0, 1, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), task -> {
            Thread thread = new Thread(task, name);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });
    }

    MoreAppsOverlayController(Context context, Handler handler, Runnable visibilityChanged) {
        this.context = context;
        this.handler = handler;
        this.visibilityChanged = visibilityChanged;
        this.windowManager = context.getSystemService(WindowManager.class);
        catalogChanges = new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange) {
                shortcutCatalogChanged = true;
                if (attached != null) loadEntriesAsync();
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        try { context.registerReceiver(packageChanges, filter, Context.RECEIVER_NOT_EXPORTED); }
        catch (Throwable error) { Log.e("Cannot observe app catalog changes", error); }
        context.getContentResolver().registerContentObserver(ConfigContract.CATALOG_URI,
                false, catalogChanges);
    }

    void prepare() {
        if (cachedEntries == null) loadEntriesAsync();
    }

    boolean isVisible() { return attached != null; }

    void show(LaunchAction action, boolean enhanced) {
        if (!handler.getLooper().isCurrentThread()) {
            handler.post(() -> show(action, enhanced));
            return;
        }
        removeNow();
        launchAction = action;
        this.enhanced = enhanced;
        mode = MODE_ALL;
        selectedColor = MoreAppsCatalog.COLOR_ALL;
        selectedInitial = "";
        query = "";
        compactControls = false;
        palette = Palette.forContext(context);
        if (windowManager == null) return;
        long start = android.os.SystemClock.elapsedRealtime();
        panelOpenedAt = start;

        PanelRoot root = new PanelRoot(context, this::removeNow);
        root.setBackgroundColor(palette.scrim);
        root.setOnClickListener(v -> removeNow());
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackground(roundRect(palette.panel, dp(20)));
        panel.setOnClickListener(v -> { });
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                -1, -1, Gravity.CENTER);
        root.addView(panel, panelParams);

        LinearLayout searchHost = new LinearLayout(context);
        root.searchRow = searchHost;
        searchHost.setGravity(Gravity.CENTER_VERTICAL);
        searchHost.setPadding(0, 0, 0, dp(6));
        search = new EditText(context);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        search.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            InputMethodManager imm = context.getSystemService(InputMethodManager.class);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            return true;
        });
        search.setHint("搜索应用或快捷方式");
        search.setHintTextColor(palette.secondaryText);
        search.setTextColor(palette.primaryText);
        search.setTextSize(15f);
        search.setPadding(dp(12), 0, dp(12), 0);
        search.setBackground(roundRect(palette.search, dp(12)));
        search.setMinimumHeight(dp(48));
        searchHost.addView(search, new LinearLayout.LayoutParams(0, -2, 1f));
        Button clear = tab("清除");
        clear.setContentDescription("清除搜索与筛选");
        clear.setMinimumHeight(dp(48));
        clear.setMinimumWidth(dp(48));
        clear.setVisibility(View.GONE);
        searchHost.addView(clear, new LinearLayout.LayoutParams(-2, -2));
        Button close = tab("×");
        close.setContentDescription("关闭更多应用");
        close.setTextSize(22f);
        close.setMinimumHeight(dp(48));
        close.setMinimumWidth(dp(48));
        close.setOnClickListener(v -> removeNow());
        searchHost.addView(close, new LinearLayout.LayoutParams(-2, -2));
        panel.addView(searchHost);

        tabs = new LinearLayout(context);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        Button all = tab("全部应用");
        Button shortcuts = tab("快捷方式");
        tabButtons = new Button[]{all, shortcuts};
        all.setMinimumHeight(dp(48));
        shortcuts.setMinimumHeight(dp(48));
        tabs.addView(all, new LinearLayout.LayoutParams(0, -2, 1));
        tabs.addView(shortcuts, new LinearLayout.LayoutParams(0, -2, 1));
        panel.addView(tabs);
        all.setOnClickListener(v -> setMode(MODE_ALL));
        shortcuts.setOnClickListener(v -> setMode(MODE_SHORTCUTS));
        updateTabs();

        FrameLayout content = new FrameLayout(context);
        root.results = content;
        panel.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        grid = new GridView(context);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(76));
        grid.setHorizontalSpacing(dp(2));
        grid.setVerticalSpacing(dp(4));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(android.R.color.transparent);
        grid.setVerticalScrollBarEnabled(false);
        grid.setPadding(0, dp(4), 0, dp(6));
        content.addView(grid, new FrameLayout.LayoutParams(-1, -1));

        basicList = new ListView(context);
        basicList.setDivider(null);
        basicList.setVerticalScrollBarEnabled(false);
        basicList.setVisibility(enhanced ? View.GONE : View.VISIBLE);
        content.addView(basicList, new FrameLayout.LayoutParams(-1, -1));

        empty = new TextView(context);
        empty.setText("正在加载应用…");
        empty.setTextColor(palette.secondaryText);
        empty.setTextSize(14f);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.VISIBLE);
        content.addView(empty, new FrameLayout.LayoutParams(-1, -1));

        indexBar = new LinearLayout(context);
        indexBar.setOrientation(LinearLayout.VERTICAL);
        indexBar.setGravity(Gravity.CENTER);
        indexBar.setPadding(dp(2), 0, 0, 0);
        indexBar.setVisibility(View.GONE);
        content.addView(indexBar, new FrameLayout.LayoutParams(dp(INDEX_WIDTH_DP), -1,
                Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        indexBar.setOnTouchListener((v, event) -> onIndexTouch(event));

        colorScroll = new HorizontalScrollView(context);
        colorScroll.setHorizontalScrollBarEnabled(false);
        colorScroll.setVisibility(enhanced ? View.VISIBLE : View.GONE);
        colorBar = new LinearLayout(context);
        colorBar.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < COLOR_IDS.length; i++) {
            final int color = COLOR_IDS[i];
            FrameLayout hit = new FrameLayout(context);
            TextView chip = new TextView(context);
            chip.setText(color == MoreAppsCatalog.COLOR_ALL ? "全" : "");
            chip.setTextColor(color == MoreAppsCatalog.COLOR_ALL
                    ? palette.primaryText : Color.WHITE);
            chip.setTextSize(10f);
            chip.setGravity(Gravity.CENTER);
            chip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            hit.addView(chip, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
            hit.setContentDescription(colorName(color));
            hit.setFocusable(true);
            colorBar.addView(hit, new LinearLayout.LayoutParams(dp(48), dp(48)));
            hit.setOnClickListener(v -> {
                selectedColor = selectedColor == color && color != MoreAppsCatalog.COLOR_ALL
                        ? MoreAppsCatalog.COLOR_ALL : color;
                applyFilters();
            });
        }
        colorScroll.addView(colorBar);
        panel.addView(colorScroll, new LinearLayout.LayoutParams(-1, dp(48)));
        clear.setOnClickListener(v -> {
            selectedColor = MoreAppsCatalog.COLOR_ALL;
            selectedInitial = "";
            if (search.length() == 0) applyFilters();
            else search.setText("");
        });
        clearFilters = clear;

        adapter = new MoreAdapter();
        grid.setAdapter(adapter);
        basicList.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> launchPosition(position));
        basicList.setOnItemClickListener((parent, view, position, id) -> {
            Entry entry = (Entry) view.getTag();
            int currentPosition = adapter == null ? -1 : adapter.visible.indexOf(entry);
            if (currentPosition >= 0) launchPosition(currentPosition);
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s == null ? "" : s.toString();
                applyFilters();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                -1, -1, 2038,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                -3);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setTitle("HyperGestureMoreApps");
        params.setFitInsetsTypes(0);
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        try {
            windowManager.addView(root, params);
            root.setFocusableInTouchMode(true);
            root.requestFocus();
            attached = root;
            attachedParams = params;
            visibilityChanged.run();
            root.setAlpha(1f);
            if (animationsEnabled()) {
                panel.setAlpha(0.94f);
                panel.animate().alpha(1f).setDuration(100L)
                        .setInterpolator(new DecelerateInterpolator()).start();
            } else panel.setAlpha(1f);
            List<Entry> snapshot = cachedEntries;
            if (snapshot != null) {
                adapter.setEntries(snapshot);
                applyFilters();
                Log.i("More-apps panel opened cache=hit elapsed="
                        + (android.os.SystemClock.elapsedRealtime() - start) + "ms");
                if (activityCatalogFailed || shortcutCatalogFailed
                        || !changedPackages.isEmpty() || shortcutCatalogChanged) loadEntriesAsync();
            } else {
                applyFilters();
                loadEntriesAsync();
                Log.i("More-apps panel opened cache=miss elapsed="
                        + (android.os.SystemClock.elapsedRealtime() - start) + "ms");
            }
            if (!enhanced) handler.postDelayed(() -> {
                if (attached != root || search == null) return;
                search.requestFocus();
                InputMethodManager imm = context.getSystemService(InputMethodManager.class);
                if (imm != null) imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
            }, 120L);
        } catch (Throwable error) {
            removeNow();
            Log.e("Cannot attach more-apps overlay", error);
        }
    }

    void removeNow() {
        if (!handler.getLooper().isCurrentThread()) {
            handler.post(this::removeNow);
            return;
        }
        View view = attached;
        attached = null;
        visibleIconRequests.clear();
        handler.removeCallbacks(loadIcons);
        iconLoadScheduled = false;
        WindowManager.LayoutParams params = attachedParams;
        attachedParams = null;
        launchAction = null;
        if (view != null) view.animate().cancel();
        adapter = null;
        tabButtons = null;
        tabs = null;
        grid = null;
        basicList = null;
        indexBar = null;
        indexLetters = Collections.emptyList();
        colorBar = null;
        colorScroll = null;
        clearFilters = null;
        empty = null;
        EditText currentSearch = search;
        search = null;
        if (view == null || windowManager == null) return;
        if (currentSearch != null && params != null
                && (params.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0) {
            InputMethodManager imm = context.getSystemService(InputMethodManager.class);
            if (imm != null) imm.hideSoftInputFromWindow(currentSearch.getWindowToken(), 0);
        }
        try { windowManager.removeViewImmediate(view); }
        catch (Throwable error) { Log.e("Cannot remove more-apps overlay", error); }
        visibilityChanged.run();
    }

    interface LaunchAction { void launch(RuntimeTarget target); }

    private void loadEntriesAsync() {
        if (loading) return;
        loading = true;
        Set<String> packagesToRefresh = Set.copyOf(changedPackages);
        changedPackages.clear();
        boolean refreshShortcutCatalog = shortcutCatalogChanged;
        shortcutCatalogChanged = false;
        loader.execute(() -> {
            long start = android.os.SystemClock.elapsedRealtime();
            List<Entry> entries = loadEntries(cachedEntries, packagesToRefresh,
                    refreshShortcutCatalog, apps -> publishEntries(apps, start, "apps"));
            publishEntries(entries, start, "names");
            handler.post(() -> {
                loading = false;
                if (attached != null && (!changedPackages.isEmpty() || shortcutCatalogChanged)) loadEntriesAsync();
            });
        });
    }

    private void publishEntries(List<Entry> entries, long start, String stage) {
        long loadElapsed = android.os.SystemClock.elapsedRealtime() - start;
        handler.post(() -> {
            cachedEntries = entries;
            loadingShortcuts = "apps".equals(stage);
            if (attached != null && adapter != null) {
                adapter.setEntries(entries);
                applyFilters();
                Log.i("More-apps " + stage + " ready count=" + entries.size()
                        + " load=" + loadElapsed + "ms sinceOpen="
                        + Math.max(0L, android.os.SystemClock.elapsedRealtime()
                        - panelOpenedAt) + "ms");
            }
        });
    }

    private List<Entry> loadEntries(List<Entry> previous, Set<String> changedPackages,
                                    boolean refreshShortcutCatalog,
                                    java.util.function.Consumer<List<Entry>> onAppsReady) {
        android.content.pm.LauncherApps apps = null;
        ArrayList<Entry> out = new ArrayList<>();
        Map<String, Entry> oldByKey = new HashMap<>();
        if (previous != null) {
            for (Entry entry : previous) if (entry.shortcut) oldByKey.put(entry.key, entry);
        }
        Set<String> seen = new LinkedHashSet<>();
        try {
            apps = context.getSystemService(android.content.pm.LauncherApps.class);
            if (apps == null) throw new IllegalStateException("LauncherApps unavailable");
            if (previous == null || activityCatalogFailed) {
                appendActivities(apps.getActivityList(null, Process.myUserHandle()), out, seen);
            } else {
                for (Entry entry : previous) {
                    if (!entry.shortcut && !changedPackages.contains(entry.target.packageName)) {
                        out.add(entry);
                        seen.add(entry.key);
                    }
                }
                for (String pkg : changedPackages) {
                    appendActivities(apps.getActivityList(pkg, Process.myUserHandle()), out, seen);
                }
            }
            activityCatalogFailed = false;
        } catch (RuntimeException error) {
            activityCatalogFailed = true;
            Log.e("Cannot read runtime launcher activities", error);
            restoreSource(previous, out, seen, 0, entry -> !entry.shortcut);
        }

        // The default category can be searched before the shortcut service returns.
        if (previous == null) onAppsReady.accept(sortedEntries(out));
        int sourceStart = out.size();
        boolean systemShortcutsFailed = false;
        try {
            android.content.pm.LauncherApps.ShortcutQuery query =
                    new android.content.pm.LauncherApps.ShortcutQuery().setQueryFlags(
                            android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                                    | android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                                    | android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                                    | android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED);
            List<android.content.pm.ShortcutInfo> shortcuts = apps == null ? null : apps.getShortcuts(query,
                    Process.myUserHandle());
            if (shortcuts == null) throw new IllegalStateException("System shortcut directory unavailable");
            for (android.content.pm.ShortcutInfo info : shortcuts) {
                if (info == null || !info.isEnabled()) continue;
                String key = info.getPackage() + "/" + info.getId();
                if (!seen.add(key)) continue;
                Entry cached = oldByKey.get(key);
                if (cached != null && !cached.target.isLauncherShortcut()
                        && !changedPackages.contains(info.getPackage())) {
                    out.add(cached);
                    continue;
                }
                CharSequence label = info.getShortLabel();
                String name = label == null ? info.getId() : label.toString();
                RuntimeTarget target = new RuntimeTarget(info.getPackage(), info.getId(), name, null);
                out.add(entry(key, name, true, null, target,
                        new IconRequest(null, info, null, null, 0)));
            }
        } catch (RuntimeException error) {
            systemShortcutsFailed = true;
            Log.e("Cannot read runtime system shortcuts", error);
            restoreSource(previous, out, seen, sourceStart,
                    entry -> entry.shortcut && !entry.target.isLauncherShortcut());
        }

        sourceStart = out.size();
        try {
            Bundle catalog = context.getContentResolver().call(
                    ConfigContract.URI, "get_catalog", null, null);
            if (catalog == null) throw new IllegalStateException("Desktop shortcut directory unavailable");
            String raw = catalog.getString(ConfigContract.KEY_SHORTCUT_CATALOG);
            if (raw != null && !raw.isEmpty()) {
                JSONArray items = new JSONArray(raw);
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null || !"launcher".equals(item.optString("kind"))) continue;
                    String pkg = item.optString("package", "");
                    String id = item.optString("id", "");
                    String intentUri = item.optString("intent", "");
                    String key = pkg + "/" + id;
                    if (pkg.isEmpty() || id.isEmpty() || intentUri.isEmpty() || !seen.add(key)) continue;
                    Entry cached = oldByKey.get(key);
                    if (cached != null && cached.target.isLauncherShortcut() && !changedPackages.contains(pkg)
                            && !refreshShortcutCatalog) {
                        out.add(cached);
                        continue;
                    }
                    int userId = item.optInt("userId", 0);
                    String name = item.optString("label", id);
                    RuntimeTarget target = new RuntimeTarget(pkg, id, intentUri, name,
                            null, userId);
                    out.add(entry(key, name, true, null, target,
                            new IconRequest(null, null, pkg, id, userId)));
                }
            }
            shortcutCatalogFailed = systemShortcutsFailed;
        } catch (RuntimeException | org.json.JSONException error) {
            shortcutCatalogFailed = true;
            Log.e("Cannot read runtime desktop shortcuts", error);
            restoreSource(previous, out, seen, sourceStart, entry -> entry.target.isLauncherShortcut());
        }
        return sortedEntries(out);
    }

    private static void restoreSource(List<Entry> previous, List<Entry> out, Set<String> seen,
                                      int sourceStart, java.util.function.Predicate<Entry> fromSource) {
        out.subList(sourceStart, out.size()).clear();
        seen.clear();
        for (Entry entry : out) seen.add(entry.key);
        if (previous == null) return;
        for (Entry entry : previous) {
            if (fromSource.test(entry) && seen.add(entry.key)) out.add(entry);
        }
    }

    private void appendActivities(List<android.content.pm.LauncherActivityInfo> activities,
                                  List<Entry> out, Set<String> seen) {
        for (android.content.pm.LauncherActivityInfo info : activities) {
            ComponentName component = info.getComponentName();
            if (component == null) continue;
            String key = component.flattenToString();
            if (!seen.add(key)) continue;
            CharSequence label = info.getLabel();
            String name = label == null ? component.getPackageName() : label.toString();
            RuntimeTarget target = new RuntimeTarget(component, name, null);
            out.add(entry(key, name, false, null,
                    target, new IconRequest(info, null, null, null, 0)));
        }
    }

    private static List<Entry> sortedEntries(List<Entry> entries) {
        ArrayList<Entry> out = new ArrayList<>(entries);
        out.sort(Comparator.comparing((Entry item) -> item.shortcut)
                .thenComparing(item -> item.initial)
                .thenComparing(item -> item.searchItem.searchLabel)
                .thenComparing(item -> item.key));
        return Collections.unmodifiableList(out);
    }

    private Entry entry(String key, String label, boolean shortcut,
                        Drawable icon, RuntimeTarget target, IconRequest iconRequest) {
        return new Entry(key, target, shortcut, MoreAppsCatalog.initialFor(label),
                iconRequest != null ? MoreAppsCatalog.COLOR_PENDING : iconColor(icon), iconRequest);
    }

    private void requestVisibleIcon(Entry entry) {
        if (entry != null && entry.iconRequest != null && visibleIconRequests.add(entry)) scheduleIconLoad();
    }

    private void scheduleIconLoad() {
        if (iconLoadRunning || iconLoadScheduled || attached == null) return;
        iconLoadScheduled = true;
        handler.post(loadIcons);
    }

    private void loadRequestedIcons() {
        iconLoadScheduled = false;
        View owner = attached;
        if (owner == null || adapter == null) return;
        Set<Entry> batch = new LinkedHashSet<>();
        for (Entry entry : visibleIconRequests) {
            if (entry.iconRequest != null) batch.add(entry);
            if (batch.size() == ICON_BATCH_SIZE) break;
        }
        if (batch.size() < ICON_BATCH_SIZE && selectedColor != MoreAppsCatalog.COLOR_ALL) {
            // Preserve color-filtered initials across the whole matching category.
            MoreAppsCatalog.SearchResult candidates = MoreAppsCatalog.search(adapter.model,
                    mode, query, MoreAppsCatalog.COLOR_ALL, "", false);
            for (MoreAppsCatalog.Item item : candidates.items()) {
                Entry entry = (Entry) item.payload;
                if (entry.iconRequest != null) batch.add(entry);
                if (batch.size() == ICON_BATCH_SIZE) break;
            }
        }
        if (batch.isEmpty()) return;

        iconLoadRunning = true;
        iconLoader.execute(() -> {
            android.content.pm.LauncherApps apps = context.getSystemService(android.content.pm.LauncherApps.class);
            List<LoadedIcon> loaded = new ArrayList<>(batch.size());
            for (Entry entry : batch) {
                if (attached != owner) break;
                Drawable icon = loadIcon(entry, apps);
                loaded.add(new LoadedIcon(entry, icon, iconColor(icon)));
            }
            handler.post(() -> {
                iconLoadRunning = false;
                Set<Entry> updated = new java.util.HashSet<>();
                for (LoadedIcon result : loaded) {
                    visibleIconRequests.remove(result.entry());
                    result.entry().resolveIcon(result.icon(), result.color());
                    updated.add(result.entry());
                }
                refreshBoundIcons(updated);
                if (adapter != null && !updated.isEmpty()) {
                    adapter.setEntries(cachedEntries);
                    if (selectedColor != MoreAppsCatalog.COLOR_ALL) applyFilters();
                }
                if (!visibleIconRequests.isEmpty()
                        || (attached != owner && selectedColor != MoreAppsCatalog.COLOR_ALL)) scheduleIconLoad();
            });
        });
    }

    private Drawable loadIcon(Entry entry, android.content.pm.LauncherApps apps) {
        IconRequest request = entry.iconRequest;
        int density = context.getResources().getDisplayMetrics().densityDpi;
        try {
            if (request.activity != null) return request.activity.getBadgedIcon(density);
            if (request.shortcut != null && apps != null) return apps.getShortcutIconDrawable(request.shortcut, density);
            if (request.packageName != null) {
                return ShortcutIconLoader.load(context, request.packageName, request.shortcutId, request.userId);
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException error) {
            Log.e("Cannot load more-apps icon key=" + entry.key, error);
        }
        return null;
    }

    private void refreshBoundIcons(Set<Entry> updated) {
        ViewGroup list = enhanced ? grid : basicList;
        if (list == null || updated.isEmpty()) return;
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            if (!(row.getTag() instanceof Entry entry) || !updated.contains(entry)) continue;
            ImageView icon = row instanceof GridCell cell ? cell.icon : (ImageView) ((LinearLayout) row).getChildAt(0);
            icon.setImageDrawable(drawableFor(entry.icon));
        }
    }

    private record LoadedIcon(Entry entry, Drawable icon, int color) {}

    private int iconColor(Drawable source) {
        if (source == null) return MoreAppsCatalog.COLOR_GRAY;
        int size = 24;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Drawable drawable = source.getConstantState() == null ? source
                : source.getConstantState().newDrawable(context.getResources()).mutate();
        drawable.setBounds(0, 0, size, size);
        drawable.draw(canvas);
        int[] samples = new int[((size - 4) / 2) * ((size - 4) / 2)];
        int count = 0;
        for (int y = 2; y < size - 2; y += 2) for (int x = 2; x < size - 2; x += 2) {
            int color = bitmap.getPixel(x, y);
            if (Color.alpha(color) < 96) continue;
            samples[count++] = color;
        }
        bitmap.recycle();
        if (count == 0) return MoreAppsCatalog.COLOR_GRAY;
        return MoreAppsCatalog.dominantCategory(java.util.Arrays.copyOf(samples, count));
    }

    private void launchPosition(int position) {
        if (adapter == null) return;
        Entry entry = adapter.getItem(position);
        if (entry == null) return;
        LaunchAction selectedAction = launchAction;
        removeNow();
        if (selectedAction != null) {
            long start = android.os.SystemClock.elapsedRealtime();
            selectedAction.launch(entry.target);
            Log.i("More-apps launch request returned target=" + entry.key + " elapsed="
                    + (android.os.SystemClock.elapsedRealtime() - start) + "ms");
        }
    }

    private void applyFilters() {
        applyFilters(true);
    }

    private void applyFilters(boolean refreshIndex) {
        if (adapter == null) return;
        boolean collectInitials = refreshIndex && enhanced;
        MoreAppsCatalog.SearchResult results = adapter.apply(mode, query, selectedColor,
                selectedInitial, collectInitials);
        if (collectInitials) updateIndexBar(results.initials());
        boolean noResults = adapter.getCount() == 0;
        if (noResults) {
            empty.setText(cachedEntries == null ? "正在加载应用…"
                    : mode == MODE_SHORTCUTS && loadingShortcuts ? "正在加载快捷方式…"
                    : mode == MODE_ALL && activityCatalogFailed ? "应用列表读取失败，重新打开可重试"
                    : mode == MODE_SHORTCUTS && shortcutCatalogFailed ? "快捷方式未完整载入，重新打开可重试"
                    : results.pendingColors()
                            ? "正在识别图标颜色…" : "没有匹配结果");
        }
        empty.setVisibility(noResults ? View.VISIBLE : View.GONE);
        clearFilters.setVisibility(query.isEmpty() && selectedInitial.isEmpty()
                && selectedColor == MoreAppsCatalog.COLOR_ALL ? View.GONE : View.VISIBLE);
        updateColorSelection();
        updateIndexSelection();
        if (grid != null) grid.setVisibility(enhanced ? View.VISIBLE : View.GONE);
        if (basicList != null) basicList.setVisibility(enhanced ? View.GONE : View.VISIBLE);
        if (selectedColor != MoreAppsCatalog.COLOR_ALL) scheduleIconLoad();
    }

    private void setMode(int value) {
        mode = value;
        selectedInitial = "";
        updateTabs();
        applyFilters();
    }

    private void updateTabs() {
        if (tabButtons == null) return;
        for (int i = 0; i < tabButtons.length; i++) {
            boolean selected = i == mode;
            Button button = tabButtons[i];
            button.setSelected(selected);
            button.setTextColor(selected ? palette.accent : palette.secondaryText);
            button.setBackground(selected ? roundRect(palette.selectedTab, dp(10))
                    : roundRect(0x00000000, dp(10)));
        }
    }

    private void updateIndexBar(List<String> present) {
        if (!selectedInitial.isEmpty() && !present.contains(selectedInitial)) {
            present = new ArrayList<>(present);
            present.add(selectedInitial);
            Collections.sort(present);
        }
        if (indexLetters.equals(present)) return;
        indexLetters = present;
        indexBar.removeAllViews();
        updateIndexVisibility();
        for (String letter : present) {
            TextView item = new TextView(context);
            item.setText(letter);
            item.setTextSize(9f);
            item.setGravity(Gravity.CENTER);
            item.setTextColor(palette.indexText);
            indexBar.addView(item, new LinearLayout.LayoutParams(-1, 0, 1f));
        }
    }

    private void updateIndexVisibility() {
        boolean show = enhanced && !compactControls && !indexLetters.isEmpty();
        indexBar.setVisibility(show ? View.VISIBLE : View.GONE);
        grid.setPadding(0, dp(4), show ? dp(INDEX_WIDTH_DP) : 0, dp(6));
    }

    private void updateIndexSelection() {
        for (int i = 0; i < indexBar.getChildCount(); i++) {
            TextView child = (TextView) indexBar.getChildAt(i);
            boolean selected = indexLetters.get(i).equals(selectedInitial);
            if (child.isSelected() == selected) continue;
            child.setSelected(selected);
            child.setTextColor(selected ? palette.accent : palette.indexText);
            child.setBackground(selected ? roundRect(palette.selectedTab, dp(6)) : null);
        }
    }

    private boolean onIndexTouch(MotionEvent event) {
        if (adapter == null || indexBar == null) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) return true;
        List<String> letters = indexLetters;
        if (letters.isEmpty()) return true;
        int index = Math.min(letters.size() - 1,
                Math.max(0, (int) (event.getY() / Math.max(1, indexBar.getHeight()) * letters.size())));
        String nextInitial = letters.get(index);
        if (nextInitial.equals(selectedInitial)) return true;
        selectedInitial = nextInitial;
        applyFilters(false);
        int position = adapter.positionFor(selectedInitial);
        if (position >= 0 && grid != null) grid.setSelection(position);
        return true;
    }

    private boolean animationsEnabled() {
        try {
            return Settings.Global.getFloat(context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private void updateColorSelection() {
        for (int i = 0; i < COLOR_IDS.length; i++) {
            FrameLayout hit = (FrameLayout) colorBar.getChildAt(i);
            boolean selected = COLOR_IDS[i] == selectedColor;
            View chip = hit.getChildAt(0);
            if (hit.isSelected() != selected || chip.getBackground() == null) {
                hit.setSelected(selected);
                chip.setBackground(colorChip(COLOR_SWATCHES[i], selected));
            }
        }
    }

    private Button tab(String label) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextColor(palette.primaryText);
        button.setTextSize(13f);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    private GradientDrawable colorChip(int color, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        drawable.setStroke(dp(selected ? 3 : 1), selected ? palette.accent : palette.chipBorder);
        return drawable;
    }

    private String colorName(int color) {
        switch (color) {
            case MoreAppsCatalog.COLOR_GRAY: return "灰色图标";
            case MoreAppsCatalog.COLOR_RED: return "红色图标";
            case MoreAppsCatalog.COLOR_ORANGE: return "橙色图标";
            case MoreAppsCatalog.COLOR_YELLOW: return "黄色图标";
            case MoreAppsCatalog.COLOR_GREEN: return "绿色图标";
            case MoreAppsCatalog.COLOR_BLUE: return "蓝色图标";
            case MoreAppsCatalog.COLOR_PURPLE: return "紫色图标";
            case MoreAppsCatalog.COLOR_PINK: return "粉色图标";
            default: return "全部颜色";
        }
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private final class PanelRoot extends FrameLayout {
        private final OnBackInvokedCallback close;
        private final OnBackInvokedCallback back = this::handleBack;
        private OnBackInvokedDispatcher dispatcher;
        private boolean imeVisible;
        private View searchRow;
        private View results;

        PanelRoot(Context context, OnBackInvokedCallback close) {
            super(context);
            this.close = close;
            setOnApplyWindowInsetsListener((view, insets) -> {
                imeVisible = insets.isVisible(WindowInsets.Type.ime());
                var safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                setPadding(safe.left + dp(12), safe.top + dp(8),
                        safe.right + dp(12), safe.bottom + dp(8));
                return insets;
            });
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            LinearLayout panel = (LinearLayout) getChildAt(0);
            ViewGroup.LayoutParams bounds = panel.getLayoutParams();
            bounds.width = Math.min(dp(520), Math.max(0,
                    MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight()));
            bounds.height = Math.min(dp(720), Math.max(0,
                    MeasureSpec.getSize(heightSpec) - getPaddingTop() - getPaddingBottom()));
            boolean compact = imeVisible && bounds.height < dp(FULL_CONTROLS_MIN_HEIGHT_DP);
            if (compact != compactControls && tabs != null) {
                compactControls = compact;
                tabs.setVisibility(compact ? View.GONE : View.VISIBLE);
                colorScroll.setVisibility(enhanced && !compact ? View.VISIBLE : View.GONE);
                updateIndexVisibility();
            }
            // With a wide keyboard there may be room for just one row. Keep that
            // row beside the search field without moving focus to another View.
            boolean split = compact && bounds.width >= dp(440) && bounds.height < dp(170);
            int orientation = split ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL;
            if (panel.getOrientation() != orientation) {
                panel.setOrientation(orientation);
                searchRow.setLayoutParams(new LinearLayout.LayoutParams(
                        split ? dp(240) : -1, -2));
                searchRow.setPadding(0, 0, split ? dp(8) : 0, split ? 0 : dp(6));
                results.setLayoutParams(new LinearLayout.LayoutParams(
                        split ? 0 : -1, split ? -1 : 0, 1f));
            }
            super.onMeasure(widthSpec, heightSpec);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            dispatcher = findOnBackInvokedDispatcher();
            if (dispatcher != null) dispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, back);
            requestApplyInsets();
        }

        @Override protected void onDetachedFromWindow() {
            if (dispatcher != null) dispatcher.unregisterOnBackInvokedCallback(back);
            dispatcher = null;
            super.onDetachedFromWindow();
        }

        @Override public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getKeyCode() != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event);
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) handleBack();
            return true;
        }

        private void handleBack() {
            if (imeVisible && search != null) {
                InputMethodManager imm = context.getSystemService(InputMethodManager.class);
                if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
                requestFocus();
                return;
            }
            close.onBackInvoked();
        }
    }

    private static final class Entry {
        final String key;
        final RuntimeTarget target;
        final boolean shortcut;
        final String initial;
        Drawable icon;
        IconRequest iconRequest;
        volatile MoreAppsCatalog.Item searchItem;
        Entry(String key, RuntimeTarget target, boolean shortcut, String initial, int color,
              IconRequest iconRequest) {
            this.key = key; this.target = target; this.shortcut = shortcut;
            this.initial = MoreAppsCatalog.normalizeInitial(initial);
            this.icon = target.icon;
            this.iconRequest = iconRequest;
            this.searchItem = new MoreAppsCatalog.Item(key, target.label, target.packageName,
                    shortcut, this.initial, color, this);
        }

        void resolveIcon(Drawable drawable, int color) {
            icon = drawable;
            iconRequest = null;
            searchItem = new MoreAppsCatalog.Item(key, target.label, target.packageName, shortcut, initial, color, this);
        }
    }

    private static final class IconRequest {
        final android.content.pm.LauncherActivityInfo activity;
        final android.content.pm.ShortcutInfo shortcut;
        final String packageName;
        final String shortcutId;
        final int userId;

        IconRequest(android.content.pm.LauncherActivityInfo activity,
                    android.content.pm.ShortcutInfo shortcut, String packageName,
                    String shortcutId, int userId) {
            this.activity = activity;
            this.shortcut = shortcut;
            this.packageName = packageName;
            this.shortcutId = shortcutId;
            this.userId = userId;
        }
    }

    private final class MoreAdapter extends BaseAdapter {
        private final ArrayList<Entry> visible = new ArrayList<>();
        private final ArrayList<MoreAppsCatalog.Item> model = new ArrayList<>();

        void setEntries(List<Entry> entries) {
            model.clear();
            for (Entry entry : entries) model.add(entry.searchItem);
        }

        MoreAppsCatalog.SearchResult apply(int mode, String query, int color, String initial,
                                          boolean collectInitials) {
            MoreAppsCatalog.SearchResult results = MoreAppsCatalog.search(model,
                    mode, query, color, initial, collectInitials);
            visible.clear();
            for (MoreAppsCatalog.Item item : results.items()) visible.add((Entry) item.payload);
            notifyDataSetChanged();
            return results;
        }
        int positionFor(String letter) {
            for (int i = 0; i < visible.size(); i++) if (visible.get(i).initial.equals(letter)) return i;
            return -1;
        }
        @Override public int getCount() { return visible.size(); }
        @Override public Entry getItem(int position) {
            return position >= 0 && position < visible.size() ? visible.get(position) : null;
        }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            Entry entry = getItem(position);
            requestVisibleIcon(entry);
            if (!enhanced) return listRow(entry, convertView);
            GridCell cell = convertView instanceof GridCell ? (GridCell) convertView : new GridCell(context);
            cell.bind(entry);
            return cell;
        }
    }

    private View listRow(Entry entry, View convertView) {
        LinearLayout row = convertView instanceof LinearLayout ? (LinearLayout) convertView
                : new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(5), dp(8), dp(5));
        ImageView icon;
        if (row.getChildCount() == 0) {
            icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
            TextView label = new TextView(context);
            label.setTextColor(palette.primaryText);
            label.setTextSize(15f);
            label.setMaxLines(1);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.leftMargin = dp(12);
            row.addView(label, lp);
        }
        icon = (ImageView) row.getChildAt(0);
        TextView label = (TextView) row.getChildAt(1);
        icon.setImageDrawable(entry == null ? null : drawableFor(entry.icon));
        label.setText(entry == null ? "" : entry.target.label);
        row.setTag(entry);
        return row;
    }

    private Drawable drawableFor(Drawable drawable) {
        if (drawable == null) return null;
        Drawable.ConstantState state = drawable.getConstantState();
        return state == null ? drawable : state.newDrawable(context.getResources()).mutate();
    }

    private final class GridCell extends LinearLayout {
        private final ImageView icon;
        private final TextView label;
        GridCell(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            setPadding(dp(4), dp(6), dp(4), dp(4));
            setBackground(roundRect(palette.cell, dp(10)));
            setMinimumHeight(dp(98));
            icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            addView(icon, new LayoutParams(dp(48), dp(48)));
            label = new TextView(context);
            label.setTextColor(palette.primaryText);
            label.setTextSize(11f);
            label.setGravity(Gravity.CENTER);
            label.setLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setIncludeFontPadding(false);
            LayoutParams labelParams = new LayoutParams(-1, -2);
            labelParams.topMargin = dp(4);
            addView(label, labelParams);
        }
        void bind(Entry entry) {
            setTag(entry);
            icon.setImageDrawable(entry == null ? null : drawableFor(entry.icon));
            label.setText(entry == null ? "" : entry.target.label);
            setContentDescription(entry == null ? "" : entry.target.label);
            setOnClickListener(v -> {
                int position = adapter == null ? -1 : adapter.visible.indexOf(entry);
                if (position >= 0) launchPosition(position);
            });
            setAlpha(1f);
        }
    }

    private static final class Palette {
        final int scrim;
        final int panel;
        final int search;
        final int cell;
        final int selectedTab;
        final int primaryText;
        final int secondaryText;
        final int indexText;
        final int chipBorder;
        final int accent;

        private Palette(int scrim, int panel, int search, int cell, int selectedTab, int primaryText,
                         int secondaryText, int indexText, int chipBorder, int accent) {
            this.scrim = scrim;
            this.panel = panel;
            this.search = search;
            this.cell = cell;
            this.selectedTab = selectedTab;
            this.primaryText = primaryText;
            this.secondaryText = secondaryText;
            this.indexText = indexText;
            this.chipBorder = chipBorder;
            this.accent = accent;
        }

        static Palette forContext(Context context) {
            int mode = context.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            if (mode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                return new Palette(0x88000000, 0xff292a30, 0xff3a3b43, 0x183a3b43, 0xff3a3b43,
                        0xfff6f6f8, 0xffb8bac2, 0xffc4c7d0, 0xff686b75, 0xff8ab4f8);
            }
            return new Palette(0x55000000, 0xfff7f7f8, 0xffe9e9ee, 0x12000000, 0xffe1e5ef,
                    0xff24242a, 0xff686872, 0xff6e707a, 0xffb5b6be, 0xff3f73d8);
        }
    }

}
