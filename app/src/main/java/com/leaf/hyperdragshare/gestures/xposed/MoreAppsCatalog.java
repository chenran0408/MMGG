package com.leaf.hyperdragshare.gestures.xposed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Immutable searchable metadata shared by the enhanced app drawer and its tests. */
final class MoreAppsCatalog {
    static final String[] LETTERS = {
            "#", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
            "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"
    };
    static final int COLOR_ALL = -1;
    static final int COLOR_PENDING = -2;
    static final int MODE_APPS = 0;
    static final int MODE_SHORTCUTS = 1;
    static final int COLOR_GRAY = 0;
    static final int COLOR_RED = 1;
    static final int COLOR_ORANGE = 2;
    static final int COLOR_YELLOW = 3;
    static final int COLOR_GREEN = 4;
    static final int COLOR_BLUE = 5;
    static final int COLOR_PURPLE = 6;
    static final int COLOR_PINK = 7;
    private static final ThreadLocal<android.icu.text.Transliterator> PINYIN =
            ThreadLocal.withInitial(() -> android.icu.text.Transliterator.getInstance(
                    "Han-Latin; Latin-ASCII"));

    static final class Item {
        final String key;
        final String searchLabel;
        final String searchPackageName;
        final boolean shortcut;
        final String initial;
        final int color;
        final Object payload;

        Item(String key, String label, String packageName, boolean shortcut,
             String initial, int color, Object payload) {
            this.key = key;
            this.searchLabel = label == null ? "" : label.toLowerCase(Locale.ROOT);
            this.searchPackageName = packageName == null ? "" : packageName.toLowerCase(Locale.ROOT);
            this.shortcut = shortcut;
            this.initial = normalizeInitial(initial);
            this.color = color;
            this.payload = payload;
        }
    }

    record SearchResult(List<Item> items, List<String> initials, boolean pendingColors) {}

    private MoreAppsCatalog() { }

    static List<Item> sorted(List<Item> input) {
        ArrayList<Item> result = new ArrayList<>(input);
        result.sort(Comparator.comparing((Item item) -> item.initial)
                .thenComparing(item -> item.searchLabel)
                .thenComparing(item -> item.key));
        return Collections.unmodifiableList(result);
    }

    static SearchResult search(List<Item> source, int mode, String query,
                               int color, String initial, boolean collectInitials) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String normalizedInitial = initial == null || initial.isEmpty()
                ? "" : normalizeInitial(initial);
        ArrayList<Item> items = new ArrayList<>();
        Set<String> initials = collectInitials ? new LinkedHashSet<>() : Collections.emptySet();
        boolean pendingColors = false;
        for (Item item : source) {
            if (mode == MODE_APPS && item.shortcut) continue;
            if (mode == MODE_SHORTCUTS && !item.shortcut) continue;
            if (!normalizedQuery.isEmpty()
                    && !item.searchLabel.contains(normalizedQuery)
                    && !item.searchPackageName.contains(normalizedQuery)) continue;
            boolean matchesInitial = normalizedInitial.isEmpty() || normalizedInitial.equals(item.initial);
            if (color != COLOR_ALL && item.color != color) {
                if (item.color == COLOR_PENDING && matchesInitial) pendingColors = true;
                continue;
            }
            if (collectInitials) initials.add(item.initial);
            if (matchesInitial) items.add(item);
        }
        return new SearchResult(items, List.copyOf(initials), pendingColors);
    }

    static int indexOfLetter(List<Item> source, String letter) {
        String wanted = normalizeInitial(letter);
        for (int i = 0; i < source.size(); i++) {
            if (source.get(i).initial.equals(wanted)) return i;
        }
        return -1;
    }

    static String normalizeInitial(String value) {
        if (value == null || value.isEmpty()) return "#";
        char first = Character.toUpperCase(value.charAt(0));
        return first >= 'A' && first <= 'Z' ? String.valueOf(first) : "#";
    }

    static String initialFor(String label) {
        if (label == null || label.trim().isEmpty()) return "#";
        String value = label.trim();
        char first = value.charAt(0);
        if ((first >= 'a' && first <= 'z') || (first >= 'A' && first <= 'Z')) {
            return String.valueOf(Character.toUpperCase(first));
        }
        if (first >= '\u4e00' && first <= '\u9fff') {
            try {
                String latin = PINYIN.get().transliterate(value);
                for (int i = 0; i < latin.length(); i++) {
                    char candidate = Character.toUpperCase(latin.charAt(i));
                    if (candidate >= 'A' && candidate <= 'Z') return String.valueOf(candidate);
                }
            } catch (Throwable ignored) { }
        }
        return "#";
    }

    static int colorCategory(int argb) {
        int alpha = (argb >>> 24) & 0xff;
        if (alpha < 64) return COLOR_GRAY;
        int red = (argb >>> 16) & 0xff;
        int green = (argb >>> 8) & 0xff;
        int blue = argb & 0xff;
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        int delta = max - min;
        if (max == 0 || delta * 100 / max < 18) return COLOR_GRAY;
        float hue;
        if (max == red) hue = 60f * (green - blue) / delta;
        else if (max == green) hue = 60f * (2f + (blue - red) / (float) delta);
        else hue = 60f * (4f + (red - green) / (float) delta);
        if (hue < 0f) hue += 360f;
        if (hue < 15f || hue >= 345f) return COLOR_RED;
        if (hue < 45f) return COLOR_ORANGE;
        if (hue < 70f) return COLOR_YELLOW;
        if (hue < 165f) return COLOR_GREEN;
        if (hue < 255f) return COLOR_BLUE;
        if (hue < 290f) return COLOR_PURPLE;
        return COLOR_PINK;
    }

    static int dominantCategory(int[] pixels) {
        int[] counts = new int[8];
        for (int pixel : pixels) {
            if (((pixel >>> 24) & 0xff) < 96) continue;
            counts[colorCategory(pixel)]++;
        }
        int winner = COLOR_GRAY;
        for (int category = COLOR_RED; category <= COLOR_PINK; category++) {
            if (counts[category] > counts[winner]) winner = category;
        }
        return winner;
    }
}
