package com.leaf.hyperdragshare.gestures.xposed;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MoreAppsCatalogTest {
    private MoreAppsCatalog.Item item(String key, String label, String pkg,
                                      boolean shortcut, String initial, int color) {
        return new MoreAppsCatalog.Item(key, label, pkg, shortcut, initial, color, key);
    }

    @Test public void sortsEntriesAndBuildsLetterIndexFromCachedInitials() {
        assertEquals("W", MoreAppsCatalog.initialFor("微信"));
        assertEquals("B", MoreAppsCatalog.initialFor("Browser"));
        List<MoreAppsCatalog.Item> sorted = MoreAppsCatalog.sorted(Arrays.asList(
                item("2", "微信", "com.tencent.mm", false, "W", 5),
                item("1", "Browser", "org.browser", false, "B", 3),
                item("3", "123", "com.example", false, "#", 0)));
        assertEquals("#", sorted.get(0).initial);
        assertEquals(1, MoreAppsCatalog.indexOfLetter(sorted, "B"));
        assertEquals(2, MoreAppsCatalog.indexOfLetter(sorted, "W"));
        assertEquals(-1, MoreAppsCatalog.indexOfLetter(sorted, "Q"));
    }

    @Test public void combinesShortcutColorInitialAndTextFilters() {
        List<MoreAppsCatalog.Item> source = Arrays.asList(
                item("1", "微信", "com.tencent.mm", false, "W", 5),
                item("2", "Weather shortcut", "org.weather", true, "W", 5),
                item("3", "Browser", "org.browser", false, "B", 5),
                item("4", "Wallet", "org.wallet", true, "W", 1));
        List<MoreAppsCatalog.Item> matched = MoreAppsCatalog.filter(source, 2,
                "weather", 5, "W");
        assertEquals(1, matched.size());
        assertEquals("2", matched.get(0).key);
        assertTrue(MoreAppsCatalog.filter(source, 0, "", -1, "").size() == 4);
        assertFalse(MoreAppsCatalog.filter(source, 0, "nothing", -1, "").size() > 0);
    }

    @Test public void classifiesIconColorsIntoStableGroups() {
        assertEquals(MoreAppsCatalog.COLOR_RED, MoreAppsCatalog.colorCategory(0xffff0000));
        assertEquals(MoreAppsCatalog.COLOR_ORANGE, MoreAppsCatalog.colorCategory(0xffff8000));
        assertEquals(MoreAppsCatalog.COLOR_YELLOW, MoreAppsCatalog.colorCategory(0xffffff00));
        assertEquals(MoreAppsCatalog.COLOR_GREEN, MoreAppsCatalog.colorCategory(0xff00aa00));
        assertEquals(MoreAppsCatalog.COLOR_BLUE, MoreAppsCatalog.colorCategory(0xff0000ff));
        assertEquals(MoreAppsCatalog.COLOR_PURPLE, MoreAppsCatalog.colorCategory(0xff8000ff));
        assertEquals(MoreAppsCatalog.COLOR_PINK, MoreAppsCatalog.colorCategory(0xffff66bb));
        assertEquals(MoreAppsCatalog.COLOR_GRAY, MoreAppsCatalog.colorCategory(0xff888888));
    }
}
