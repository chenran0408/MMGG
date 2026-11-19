package com.leaf.hyperdragshare.codex;

import org.junit.Test;

import static org.junit.Assert.*;

public class ScopeRestartTest {
    @Test public void explicitSystemUiRestartDoesNotTouchPortalOrRebootAndroid() {
        String commands = ScopeRestart.systemUiRestartCommand();
        assertTrue(commands.contains("com.android.systemui"));
        assertTrue(commands.contains("com.miui.securitycenter:ui"));
        assertFalse(commands.contains("com.miui.contentextension"));
        assertFalse(commands.contains("com.xiaomi.aicr"));
        assertFalse(commands.contains("zygote"));
        assertFalse(commands.contains("reboot"));
    }

    @Test public void portalAndCopyDirectSelectTheirOwnProcesses() {
        assertTrue(ScopeRestart.appScopeCommands(false, false).isEmpty());
        assertEquals(java.util.List.of("am force-stop --user current com.xiaomi.aicr"),
                ScopeRestart.appScopeCommands(false, true));
        assertTrue(ScopeRestart.appScopeCommands(true, false)
                .contains(ModuleActivation.portalStartCommand()));
        for (String command : ScopeRestart.appScopeCommands(true, true)) {
            assertFalse(command.contains("com.android.systemui"));
            assertFalse(command.contains("com.miui.securitycenter"));
            assertFalse(command.contains("zygote"));
            assertFalse(command.contains("reboot"));
        }
    }
}
