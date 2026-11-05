package com.leaf.hyperdragshare.gestures.xposed;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Runtime launcher surface for targets that do not fit in the configured fan. */
final class MoreAppsOverlayController {
    private final Context context;
    private final Handler handler;
    private final WindowManager windowManager;
    private final ArrayList<RuntimeTarget> recent = new ArrayList<>();
    private View attached;
    private LaunchAction launchAction;

    MoreAppsOverlayController(Context context, Handler handler) {
        this.context = context;
        this.handler = handler;
        this.windowManager = context.getSystemService(WindowManager.class);
    }

    boolean isVisible() { return attached != null; }

    void show(LaunchAction action) {
        removeNow();
        launchAction = action;
        if (windowManager == null) return;
        final FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0x99000000);
        root.setOnClickListener(v -> removeNow());

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(14), dp(16), dp(12));
        panel.setBackground(roundRect(0xff202124, dp(22)));
        panel.setOnClickListener(v -> { });
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                Math.min(dp(430), screenWidth() - dp(28)),
                Math.min(dp(650), screenHeight() - dp(96)), Gravity.CENTER);
        root.addView(panel, panelParams);

        TextView title = new TextView(context);
        title.setText("更多应用");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20f);
        title.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(title, new LinearLayout.LayoutParams(-1, dp(42)));

        EditText search = new EditText(context);
        search.setSingleLine(true);
        search.setHint("搜索应用或快捷方式");
        search.setHintTextColor(0xff9aa0a6);
        search.setTextColor(Color.WHITE);
        search.setTextSize(15f);
        search.setPadding(dp(12), 0, dp(12), 0);
        search.setBackground(roundRect(0xff303134, dp(12)));
        panel.addView(search, new LinearLayout.LayoutParams(-1, dp(44)));

        LinearLayout tabs = new LinearLayout(context);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        Button all = tab("全部");
        Button latest = tab("最近");
        Button shortcuts = tab("快捷方式");
        tabs.addView(all, new LinearLayout.LayoutParams(0, dp(42), 1));
        tabs.addView(latest, new LinearLayout.LayoutParams(0, dp(42), 1));
        tabs.addView(shortcuts, new LinearLayout.LayoutParams(0, dp(42), 1));
        panel.addView(tabs);

        ListView list = new ListView(context);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(false);
        panel.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        ArrayList<Entry> entries = loadEntries();
        MoreAdapter adapter = new MoreAdapter(entries, list);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Entry entry = adapter.getItem(position);
            if (entry == null) return;
            addRecent(entry.target);
            LaunchAction selectedAction = launchAction;
            removeNow();
            if (selectedAction != null) selectedAction.launch(entry.target);
        });
        TextWatcher filter = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.setQuery(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        };
        search.addTextChangedListener(filter);
        all.setOnClickListener(v -> adapter.setMode(0));
        latest.setOnClickListener(v -> adapter.setMode(1));
        shortcuts.setOnClickListener(v -> adapter.setMode(2));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                -1, -1, 2038,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                -3);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setFitInsetsTypes(0);
        params.setTitle("HyperGestureMoreApps");
        try {
            windowManager.addView(root, params);
            attached = root;
            search.requestFocus();
            handler.postDelayed(() -> {
                if (attached == root) {
                    InputMethodManager imm = context.getSystemService(InputMethodManager.class);
                    if (imm != null) imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 120L);
        } catch (Throwable error) {
            Log.e("Cannot attach more-apps overlay", error);
        }
    }

    void removeNow() {
        View view = attached;
        attached = null;
        launchAction = null;
        if (view == null || windowManager == null) return;
        try { windowManager.removeViewImmediate(view); }
        catch (Throwable error) { Log.e("Cannot remove more-apps overlay", error); }
    }

    interface LaunchAction {
        void launch(RuntimeTarget target);
    }

    private ArrayList<Entry> loadEntries() {
        ArrayList<Entry> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try {
            LauncherApps apps = context.getSystemService(LauncherApps.class);
            if (apps != null) {
                List<LauncherActivityInfo> activities = apps.getActivityList(null, Process.myUserHandle());
                if (activities != null) for (LauncherActivityInfo info : activities) {
                    ComponentName component = info.getComponentName();
                    if (component == null || !seen.add(component.flattenToString())) continue;
                    CharSequence label = info.getLabel();
                    out.add(new Entry(new RuntimeTarget(component,
                            label == null ? component.getPackageName() : label.toString(),
                            info.getBadgedIcon(context.getResources().getDisplayMetrics().densityDpi)), false));
                }
                LauncherApps.ShortcutQuery query = new LauncherApps.ShortcutQuery().setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                                | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                                | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                                | LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED);
                List<ShortcutInfo> shortcuts = apps.getShortcuts(query, Process.myUserHandle());
                if (shortcuts != null) for (ShortcutInfo info : shortcuts) {
                    if (info == null || !info.isEnabled()) continue;
                    String key = info.getPackage() + "/" + info.getId();
                    if (!seen.add(key)) continue;
                    CharSequence label = info.getShortLabel();
                    Drawable icon = apps.getShortcutIconDrawable(info,
                            context.getResources().getDisplayMetrics().densityDpi);
                    out.add(new Entry(new RuntimeTarget(info.getPackage(), info.getId(),
                            label == null ? info.getId() : label.toString(), icon), true));
                }
                // Include desktop launcher shortcuts published by the existing catalog source.
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
                        if (pkg.isEmpty() || id.isEmpty() || intentUri.isEmpty()
                                || !seen.add(pkg + "/" + id)) continue;
                        int userId = item.optInt("userId", 0);
                        Drawable icon = ShortcutIconLoader.load(context, pkg, id, userId);
                        out.add(new Entry(new RuntimeTarget(pkg, id, intentUri,
                                item.optString("label", id), icon, userId), true));
                    }
                }
            }
        } catch (Throwable error) {
            Log.e("Cannot read runtime app catalog", error);
        }
        Collections.sort(out, Comparator.comparing(entry -> entry.target.label.toLowerCase(Locale.ROOT)));
        return out;
    }

    private void addRecent(RuntimeTarget target) {
        recent.removeIf(item -> item.positionIdentity().equals(target.positionIdentity()));
        recent.add(0, target);
        if (recent.size() > 16) recent.remove(recent.size() - 1);
    }

    private Button tab(String label) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13f);
        button.setAllCaps(false);
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
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
        final RuntimeTarget target;
        final boolean shortcut;
        Entry(RuntimeTarget target, boolean shortcut) { this.target = target; this.shortcut = shortcut; }
    }

    private final class MoreAdapter extends BaseAdapter {
        private final ArrayList<Entry> allEntries;
        private final ArrayList<Entry> visible = new ArrayList<>();
        private final ListView list;
        private int mode;
        private String query = "";

        MoreAdapter(ArrayList<Entry> entries, ListView list) {
            allEntries = entries;
            this.list = list;
            rebuild();
        }
        void setMode(int mode) { this.mode = mode; rebuild(); }
        void setQuery(String query) { this.query = query.trim().toLowerCase(Locale.ROOT); rebuild(); }
        private void rebuild() {
            visible.clear();
            List<Entry> source = mode == 1 ? recentEntries() : allEntries;
            for (Entry entry : source) {
                if (mode == 2 && !entry.shortcut) continue;
                if (!query.isEmpty() && !entry.target.label.toLowerCase(Locale.ROOT).contains(query)
                        && !entry.target.packageName.toLowerCase(Locale.ROOT).contains(query)) continue;
                visible.add(entry);
            }
            notifyDataSetChanged();
        }
        private List<Entry> recentEntries() {
            ArrayList<Entry> out = new ArrayList<>();
            for (RuntimeTarget target : recent) out.add(new Entry(target, target.isShortcut()));
            return out;
        }
        @Override public int getCount() { return visible.size(); }
        @Override public Entry getItem(int position) { return position >= 0 && position < visible.size() ? visible.get(position) : null; }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
            LinearLayout row = convertView instanceof LinearLayout ? (LinearLayout) convertView : new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(5), dp(8), dp(5));
            ImageView icon = row.getChildCount() > 0 && row.getChildAt(0) instanceof ImageView
                    ? (ImageView) row.getChildAt(0) : new ImageView(context);
            if (row.getChildCount() == 0) row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
            TextView label = row.getChildCount() > 1 && row.getChildAt(1) instanceof TextView
                    ? (TextView) row.getChildAt(1) : new TextView(context);
            if (row.getChildCount() == 1) row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
            Entry entry = getItem(position);
            icon.setImageDrawable(entry == null ? null : entry.target.icon);
            label.setText(entry == null ? "" : entry.target.label);
            label.setTextColor(Color.WHITE);
            label.setTextSize(15f);
            return row;
        }
    }
}
