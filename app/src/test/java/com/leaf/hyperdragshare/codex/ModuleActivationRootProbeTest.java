package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class ModuleActivationRootProbeTest {
    @Test public void completedRootCheckIsGrantedAndProcessReleased() {
        Probe process = new Probe(0, true, "0\n");
        assertEquals(ModuleActivation.RootAccessOutcome.GRANTED,
                ModuleActivation.checkRootAccess(process));
        assertEquals(1, process.destroyed);
    }

    @Test public void successfulCommandMustActuallyReportUidZero() {
        for (String output : new String[]{"", "2000\n", "0\n2000", "granted"}) {
            Probe process = new Probe(0, true, output);
            assertEquals(ModuleActivation.RootAccessOutcome.UNKNOWN, ModuleActivation.checkRootAccess(process));
            assertEquals(1, process.destroyed);
        }
    }

    @Test public void failedCommandDoesNotProvePermissionDenial() {
        for (int exit : new int[]{1, 126, 127, 137}) {
            Probe process = new Probe(exit, true, "0\n");
            assertEquals("shell/root-manager failure is not an authorization verdict",
                    ModuleActivation.RootAccessOutcome.UNKNOWN,
                    ModuleActivation.checkRootAccess(process));
            assertEquals(1, process.destroyed);
        }
    }

    @Test public void pendingAuthorizationTimeoutRemainsUnknownAndIsStopped() {
        Probe process = new Probe(1, false, "");
        assertEquals(ModuleActivation.RootAccessOutcome.UNKNOWN,
                ModuleActivation.checkRootAccess(process));
        assertEquals(1, process.forced);
        assertEquals(1, process.destroyed);
    }

    private static final class Probe extends Process {
        final int exit;
        final boolean completed;
        final String output;
        int destroyed;
        int forced;
        Probe(int exit, boolean completed, String output) {
            this.exit = exit;
            this.completed = completed;
            this.output = output;
        }
        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public InputStream getInputStream() {
            return new ByteArrayInputStream(output.getBytes(StandardCharsets.US_ASCII));
        }
        @Override public InputStream getErrorStream() { return getInputStream(); }
        @Override public int waitFor() { return exit; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) { return completed; }
        @Override public int exitValue() { return exit; }
        @Override public void destroy() { destroyed++; }
        @Override public Process destroyForcibly() { forced++; return this; }
    }
}
