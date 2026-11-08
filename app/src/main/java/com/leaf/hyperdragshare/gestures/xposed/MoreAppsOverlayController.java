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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Runtime launcher surface for targets that do not fit in the configured fan. */
final class MoreAppsOverlayController {
    private static final int MODE_ALL = 0;
    private static final int MODE_RECENT = 1;
    private static final int MODE_SHORTCUTS = 2;
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
    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MoreAppsCatalog");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private final ArrayList<RuntimeTarget> recent = new ArrayList<>();
    private volatile List<Entry> cachedEntries;
    private volatile String changedPackage;
    private volatile boolean shortcutCatalogChanged;
    private boolean loading;
    private View attached;
    private LaunchAction launchAction;
    private MoreAdapter adapter;
    private GridView grid;
    private ListView basicList;
    private TextView empty;
    private LinearLayout indexBar;
    private List<String> indexLetters = Collections.emptyList();
    private EditText search;
    private boolean enhanced;
    private int mode = MODE_ALL;
    private int selectedColor = MoreAppsCatalog.COLOR_ALL;
    private String selectedInitial = "";
    private String query = "";
    private long panelOpenedAt;

    private final BroadcastReceiver packageChanges = new BroadcastReceiver() {
        @Override public void onReceive(Context ignored, Intent intent) {
            String pkg = intent.getData() == null ? null : intent.getData().getSchemeSpecificPart();
            if (pkg != null) changedPackage = pkg;
            if (attached != null) loadEntriesAsync();
        }
    };

    private final ContentObserver catalogChanges;

    MoreAppsOverlayController(Context context, Handler handler) {
        this.context = context;
        this.handler = handler;
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
        filter.addDataScheme("package");
        try { context.registerReceiver(packageChanges, filter, Context.RECEIVER_NOT_EXPORTED); }
        catch (Throwable error) { Log.e("Cannot observe app catalog changes", error); }
        context.getContentResolver().registerContentObserver(ConfigContract.CATALOG_URI,
                false, catalogChanges);
    }

    boolean isVisible() { return attached != null; }

    void show(LaunchAction action, boolean enhanced) {
        removeNow();
        launchAction = action;
        this.enhanced = enhanced;
        mode = MODE_ALL;
        selectedColor = MoreAppsCatalog.COLOR_ALL;
        selectedInitial = "";
        query = "";
        if (windowManager == null) return;
        long start = android.os.SystemClock.elapsedRealtime();
        panelOpenedAt = start;

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0x99000000);
        root.setOnClickListener(v -> removeNow());
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackground(roundRect(0xff202124, dp(20)));
        panel.setOnClickListener(v -> { });
        int maxWidth = Math.max(dp(280), Math.min(dp(520), screenWidth() - dp(24)));
        int maxHeight = Math.max(dp(300), Math.min(dp(720), screenHeight() - dp(64)));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                maxWidth, maxHeight, Gravity.CENTER);
        root.addView(panel, panelParams);

        TextView title = new TextView(context);
        title.setText("更多应用");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18f);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(4), 0, dp(4), 0);
        panel.addView(title, new LinearLayout.LayoutParams(-1, dp(42)));

        LinearLayout searchHost = new LinearLayout(context);
        searchHost.setVisibility(View.GONE);
        search = new EditText(context);
        search.setSingleLine(true);
        search.setHint("搜索应用或快捷方式");
        search.setHintTextColor(0xff9aa0a6);
        search.setTextColor(Color.WHITE);
        search.setTextSize(15f);
        search.setPadding(dp(12), 0, dp(12), 0);
        search.setBackground(roundRect(0xff303134, dp(12)));
        searchHost.addView(search, new LinearLayout.LayoutParams(-1, dp(44)));
        panel.addView(searchHost);

        LinearLayout tabs = new LinearLayout(context);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        Button all = tab("全部");
        Button latest = tab("最近");
        Button shortcuts = tab("快捷方式");
        tabs.addView(all, new LinearLayout.LayoutParams(0, dp(40), 1));
        tabs.addView(latest, new LinearLayout.LayoutParams(0, dp(40), 1));
        tabs.addView(shortcuts, new LinearLayout.LayoutParams(0, dp(40), 1));
        panel.addView(tabs);
        all.setOnClickListener(v -> setMode(MODE_ALL));
        latest.setOnClickListener(v -> setMode(MODE_RECENT));
        shortcuts.setOnClickListener(v -> setMode(MODE_SHORTCUTS));

        FrameLayout content = new FrameLayout(context);
        panel.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        grid = new GridView(context);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(76));
        grid.setHorizontalSpacing(dp(2));
        grid.setVerticalSpacing(dp(4));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(android.R.color.transparent);
        grid.setVerticalScrollBarEnabled(false);
        grid.setPadding(0, dp(4), dp(18), dp(6));
        content.addView(grid, new FrameLayout.LayoutParams(-1, -1));

        basicList = new ListView(context);
        basicList.setDivider(null);
        basicList.setVerticalScrollBarEnabled(false);
        basicList.setVisibility(enhanced ? View.GONE : View.VISIBLE);
        content.addView(basicList, new FrameLayout.LayoutParams(-1, -1));

        empty = new TextView(context);
        empty.setText("正在加载应用…");
        empty.setTextColor(0xffb0b4b8);
        empty.setTextSize(14f);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.VISIBLE);
        content.addView(empty, new FrameLayout.LayoutParams(-1, -1));

        indexBar = new LinearLayout(context);
        indexBar.setOrientation(LinearLayout.VERTICAL);
        indexBar.setGravity(Gravity.CENTER);
        indexBar.setPadding(dp(2), 0, 0, 0);
        content.addView(indexBar, new FrameLayout.LayoutParams(dp(18), -1,
                Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        indexBar.setOnTouchListener((v, event) -> onIndexTouch(event));

        HorizontalScrollView colorScroll = new HorizontalScrollView(context);
        colorScroll.setHorizontalScrollBarEnabled(false);
        colorScroll.setVisibility(enhanced ? View.VISIBLE : View.GONE);
        LinearLayout colorBar = new LinearLayout(context);
        colorBar.setGravity(Gravity.CENTER_VERTICAL);
        colorBar.setPadding(dp(2), dp(6), dp(2), 0);
        for (int i = 0; i < COLOR_IDS.length; i++) {
            final int color = COLOR_IDS[i];
            TextView chip = new TextView(context);
            chip.setText(color == MoreAppsCatalog.COLOR_ALL ? "全" : "");
            chip.setTextColor(color == MoreAppsCatalog.COLOR_ALL ? 0xff202124 : Color.WHITE);
            chip.setTextSize(10f);
            chip.setGravity(Gravity.CENTER);
            chip.setContentDescription(colorName(color));
            chip.setBackground(colorChip(COLOR_SWATCHES[i], color == selectedColor));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(dp(28), dp(28));
            chipParams.rightMargin = dp(7);
            colorBar.addView(chip, chipParams);
            chip.setOnClickListener(v -> {
                selectedColor = selectedColor == color && color != MoreAppsCatalog.COLOR_ALL
                        ? MoreAppsCatalog.COLOR_ALL : color;
                selectedInitial = "";
                updateColorSelection(colorBar);
                applyFilters();
            });
        }
        ImageButton searchButton = new ImageButton(context);
        searchButton.setImageResource(android.R.drawable.ic_menu_search);
        searchButton.setBackgroundColor(Color.TRANSPARENT);
        searchButton.setContentDescription("搜索");
        colorBar.addView(searchButton, new LinearLayout.LayoutParams(dp(44), dp(40)));
        searchButton.setOnClickListener(v -> {
            boolean visible = searchHost.getVisibility() == View.VISIBLE;
            searchHost.setVisibility(visible ? View.GONE : View.VISIBLE);
            if (!visible) {
                search.requestFocus();
                search.post(() -> {
                    InputMethodManager imm = context.getSystemService(InputMethodManager.class);
                    if (imm != null) imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
                });
            } else {
                search.setText("");
                selectedInitial = "";
                selectedColor = MoreAppsCatalog.COLOR_ALL;
            updateColorSelection(colorBar);
                applyFilters();
            }
        });
        colorScroll.addView(colorBar);
        panel.addView(colorScroll, new LinearLayout.LayoutParams(-1, dp(42)));
        if (!enhanced) searchHost.setVisibility(View.VISIBLE);

        adapter = new MoreAdapter();
        grid.setAdapter(adapter);
        basicList.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> launchPosition(position));
        basicList.setOnItemClickListener((parent, view, position, id) -> launchPosition(position));
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
        try {
            windowManager.addView(root, params);
            attached = root;
            root.setAlpha(1f);
            if (animationsEnabled()) {
                panel.setAlpha(0.94f);
                panel.animate().alpha(1f).setDuration(100L)
                        .setInterpolator(new DecelerateInterpolator()).start();
            } else panel.setAlpha(1f);
            updateIndexBar();
            List<Entry> snapshot = cachedEntries;
            if (snapshot != null) {
                adapter.setEntries(snapshot);
                applyFilters();
                Log.i("More-apps panel opened cache=hit elapsed="
                        + (android.os.SystemClock.elapsedRealtime() - start) + "ms");
                if (changedPackage != null || shortcutCatalogChanged) loadEntriesAsync();
            } else {
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
            attached = null;
            Log.e("Cannot attach more-apps overlay", error);
        }
    }

    void removeNow() {
        View view = attached;
        attached = null;
        launchAction = null;
        if (view != null) view.animate().cancel();
        adapter = null;
        grid = null;
        basicList = null;
        indexBar = null;
        search = null;
        if (view == null || windowManager == null) return;
        try { windowManager.removeViewImmediate(view); }
        catch (Throwable error) { Log.e("Cannot remove more-apps overlay", error); }
    }

    interface LaunchAction { void launch(RuntimeTarget target); }

    private void loadEntriesAsync() {
        if (loading || attached == null) return;
        loading = true;
        String packageToRefresh = changedPackage;
        boolean refreshShortcutCatalog = shortcutCatalogChanged;
        loader.execute(() -> {
            long start = android.os.SystemClock.elapsedRealtime();
            List<Entry> entries = loadEntries(cachedEntries, packageToRefresh,
                    refreshShortcutCatalog);
            if (packageToRefresh != null && packageToRefresh.equals(changedPackage)) {
                changedPackage = null;
            }
            if (refreshShortcutCatalog) shortcutCatalogChanged = false;
            cachedEntries = entries;
            long elapsed = android.os.SystemClock.elapsedRealtime() - start;
            handler.post(() -> {
                loading = false;
                if (attached == null || adapter == null) return;
                adapter.setEntries(entries);
                applyFilters();
                Log.i("More-apps catalog ready count=" + entries.size() + " load=" + elapsed
                        + "ms sinceOpen="
                        + (android.os.SystemClock.elapsedRealtime() - panelOpenedAt) + "ms");
                if (changedPackage != null || shortcutCatalogChanged) loadEntriesAsync();
            });
        });
    }

    private List<Entry> loadEntries(List<Entry> previous, String changedPackage,
                                    boolean refreshShortcutCatalog) {
        ArrayList<Entry> out = new ArrayList<>();
        Map<String, Entry> oldByKey = new HashMap<>();
        if (previous != null) for (Entry entry : previous) oldByKey.put(entry.key, entry);
        Set<String> seen = new LinkedHashSet<>();
        try {
            android.content.pm.LauncherApps apps = context.getSystemService(
                    android.content.pm.LauncherApps.class);
            if (apps != null) {
                List<android.content.pm.LauncherActivityInfo> activities =
                        apps.getActivityList(null, Process.myUserHandle());
                if (activities != null) for (android.content.pm.LauncherActivityInfo info : activities) {
                    ComponentName component = info.getComponentName();
                    if (component == null) continue;
                    String key = component.flattenToString();
                    if (!seen.add(key)) continue;
                    Entry cached = oldByKey.get(key);
                    if (cached != null && !component.getPackageName().equals(changedPackage)) {
                        out.add(cached);
                        continue;
                    }
                    CharSequence label = info.getLabel();
                    String name = label == null ? component.getPackageName() : label.toString();
                    Drawable icon = info.getBadgedIcon(context.getResources().getDisplayMetrics().densityDpi);
                    out.add(entry(key, name, component.getPackageName(), false, icon,
                            new RuntimeTarget(component, name, icon)));
                }
                android.content.pm.LauncherApps.ShortcutQuery query =
                        new android.content.pm.LauncherApps.ShortcutQuery().setQueryFlags(
                                android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                                        | android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                                        | android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                                        | android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED);
                List<android.content.pm.ShortcutInfo> shortcuts = apps.getShortcuts(query,
                        Process.myUserHandle());
                if (shortcuts != null) for (android.content.pm.ShortcutInfo info : shortcuts) {
                    if (info == null || !info.isEnabled()) continue;
                    String key = info.getPackage() + "/" + info.getId();
                    if (!seen.add(key)) continue;
                    Entry cached = oldByKey.get(key);
                    if (cached != null && !info.getPackage().equals(changedPackage)) {
                        out.add(cached);
                        continue;
                    }
                    CharSequence label = info.getShortLabel();
                    String name = label == null ? info.getId() : label.toString();
                    Drawable icon = apps.getShortcutIconDrawable(info,
                            context.getResources().getDisplayMetrics().densityDpi);
                    out.add(entry(key, name, info.getPackage(), true, icon,
                            new RuntimeTarget(info.getPackage(), info.getId(), name, icon)));
                }
                Bundle catalog = context.getContentResolver().call(
                        ConfigContract.URI, "get_catalog", null, null);
                String raw = catalog == null ? null
                        : catalog.getString(ConfigContract.KEY_SHORTCUT_CATALOG);
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
                        if (cached != null && !pkg.equals(changedPackage)
                                && !refreshShortcutCatalog) {
                            out.add(cached);
                            continue;
                        }
                        int userId = item.optInt("userId", 0);
                        Drawable icon = ShortcutIconLoader.load(context, pkg, id, userId);
                        String name = item.optString("label", id);
                        out.add(entry(key, name, pkg, true, icon,
                                new RuntimeTarget(pkg, id, intentUri, name, icon, userId)));
                    }
                }
            }
        } catch (Throwable error) {
            Log.e("Cannot read runtime app catalog", error);
            if (previous != null) return previous;
        }
        out.sort(Comparator.comparing((Entry item) -> item.initial)
                .thenComparing(item -> item.target.label.toLowerCase(Locale.ROOT))
                .thenComparing(item -> item.key));
        return Collections.unmodifiableList(out);
    }

    private Entry entry(String key, String label, String pkg, boolean shortcut,
                        Drawable icon, RuntimeTarget target) {
        return new Entry(key, target, shortcut, MoreAppsCatalog.initialFor(label),
                iconColor(icon));
    }

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
        addRecent(entry.target);
        LaunchAction selectedAction = launchAction;
        removeNow();
        if (selectedAction != null) {
            long start = android.os.SystemClock.elapsedRealtime();
            selectedAction.launch(entry.target);
            Log.i("More-apps launch request returned target=" + entry.key + " elapsed="
                    + (android.os.SystemClock.elapsedRealtime() - start) + "ms");
        }
    }

    private void addRecent(RuntimeTarget target) {
        recent.removeIf(item -> item.positionIdentity().equals(target.positionIdentity()));
        recent.add(0, target);
        if (recent.size() > 16) recent.remove(recent.size() - 1);
    }

    private void applyFilters() {
        applyFilters(true);
    }

    private void applyFilters(boolean refreshIndex) {
        if (adapter == null) return;
        adapter.apply(mode, query, selectedColor, selectedInitial);
        if (refreshIndex) updateIndexBar();
        boolean noResults = adapter.getCount() == 0;
        empty.setVisibility(noResults ? View.VISIBLE : View.GONE);
        if (grid != null) grid.setVisibility(enhanced ? View.VISIBLE : View.GONE);
        if (basicList != null) basicList.setVisibility(enhanced ? View.GONE : View.VISIBLE);
    }

    private void setMode(int value) {
        mode = value;
        selectedInitial = "";
        applyFilters();
    }

    private void updateIndexBar() {
        if (indexBar == null) return;
        indexBar.removeAllViews();
        if (!enhanced || adapter == null || mode == MODE_RECENT) {
            indexLetters = Collections.emptyList();
            indexBar.setVisibility(View.GONE);
            return;
        }
        List<String> present = adapter.presentInitials(mode, selectedColor, query);
        indexLetters = present;
        if (present.isEmpty()) {
            indexBar.setVisibility(View.GONE);
            return;
        }
        indexBar.setVisibility(View.VISIBLE);
        for (String letter : present) {
            TextView item = new TextView(context);
            item.setText(letter);
            item.setTextSize(9f);
            item.setGravity(Gravity.CENTER);
            item.setTextColor(letter.equals(selectedInitial) ? 0xff8ab4f8 : 0xffc4c7c5);
            indexBar.addView(item, new LinearLayout.LayoutParams(-1, 0, 1f));
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
        for (int i = 0; i < indexBar.getChildCount(); i++) {
            TextView child = (TextView) indexBar.getChildAt(i);
            child.setTextColor(child.getText().toString().equals(selectedInitial)
                    ? 0xff8ab4f8 : 0xffc4c7c5);
        }
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

    private void updateColorSelection(LinearLayout bar) {
        for (int i = 0; i < Math.min(COLOR_IDS.length, bar.getChildCount() - 1); i++) {
            View child = bar.getChildAt(i);
            child.setBackground(colorChip(COLOR_SWATCHES[i], COLOR_IDS[i] == selectedColor));
        }
    }

    private Button tab(String label) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextColor(Color.WHITE);
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
        drawable.setStroke(dp(selected ? 3 : 1), selected ? 0xff8ab4f8 : 0xff5f6368);
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
    private int screenWidth() { return context.getResources().getDisplayMetrics().widthPixels; }
    private int screenHeight() { return context.getResources().getDisplayMetrics().heightPixels; }

    private static final class Entry {
        final String key;
        final RuntimeTarget target;
        final boolean shortcut;
        final String initial;
        final int color;
        Entry(String key, RuntimeTarget target, boolean shortcut, String initial, int color) {
            this.key = key; this.target = target; this.shortcut = shortcut;
            this.initial = MoreAppsCatalog.normalizeInitial(initial); this.color = color;
        }
        MoreAppsCatalog.Item model() {
            return new MoreAppsCatalog.Item(key, target.label, target.packageName,
                    shortcut, initial, color, this);
        }
    }

    private final class MoreAdapter extends BaseAdapter {
        private List<Entry> allEntries = Collections.emptyList();
        private final ArrayList<Entry> visible = new ArrayList<>();
        private final ArrayList<MoreAppsCatalog.Item> model = new ArrayList<>();

        void setEntries(List<Entry> entries) {
            allEntries = entries;
            model.clear();
            for (Entry entry : entries) model.add(entry.model());
            apply(mode, query, selectedColor, selectedInitial);
        }

        void apply(int mode, String query, int color, String initial) {
            visible.clear();
            if (mode == MODE_RECENT) {
                for (RuntimeTarget target : recent) {
                    for (Entry entry : allEntries) {
                        if (entry.target.positionIdentity().equals(target.positionIdentity())
                                && !MoreAppsCatalog.filter(Collections.singletonList(entry.model()),
                                MODE_ALL, query, color, initial).isEmpty()) {
                            visible.add(entry); break;
                        }
                    }
                }
            } else {
                List<MoreAppsCatalog.Item> filtered = MoreAppsCatalog.filter(model,
                        mode, query, color, initial);
                for (MoreAppsCatalog.Item item : filtered) visible.add((Entry) item.payload);
            }
            notifyDataSetChanged();
        }
        List<String> presentInitials(int mode, int color, String query) {
            ArrayList<String> letters = new ArrayList<>();
            for (Entry entry : allEntries) {
                if (mode == MODE_SHORTCUTS && !entry.shortcut) continue;
                if (color != MoreAppsCatalog.COLOR_ALL && entry.color != color) continue;
                if (query != null && !query.trim().isEmpty()
                        && !entry.target.label.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT))
                        && !entry.target.packageName.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT))) continue;
                if (!letters.contains(entry.initial)) letters.add(entry.initial);
            }
            return letters;
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
            label.setTextColor(Color.WHITE);
            label.setTextSize(15f);
            label.setMaxLines(1);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.leftMargin = dp(12);
            row.addView(label, lp);
        }
        icon = (ImageView) row.getChildAt(0);
        TextView label = (TextView) row.getChildAt(1);
        icon.setImageDrawable(entry == null ? null : drawableFor(entry.target.icon));
        label.setText(entry == null ? "" : entry.target.label);
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
            setBackground(roundRect(0x00303134, dp(10)));
            setMinimumHeight(dp(98));
            icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            addView(icon, new LayoutParams(dp(48), dp(48)));
            label = new TextView(context);
            label.setTextColor(Color.WHITE);
            label.setTextSize(11f);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setIncludeFontPadding(false);
            LayoutParams labelParams = new LayoutParams(-1, dp(34));
            labelParams.topMargin = dp(4);
            addView(label, labelParams);
        }
        void bind(Entry entry) {
            icon.setImageDrawable(entry == null ? null : drawableFor(entry.target.icon));
            label.setText(entry == null ? "" : entry.target.label);
            setContentDescription(entry == null ? "" : entry.target.label);
            setOnClickListener(v -> {
                int position = adapter == null ? -1 : adapter.visible.indexOf(entry);
                if (position >= 0) launchPosition(position);
            });
            setAlpha(1f);
        }
    }

}
