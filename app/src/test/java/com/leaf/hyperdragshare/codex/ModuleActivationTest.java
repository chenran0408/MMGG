package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class ModuleActivationTest {
    @Before public void clearRuntimeReport() {
        ModuleActivation.clearPortalReport(RuntimeEnvironment.getApplication());
    }

    private Bundle report(int pid, Lifetime lifetime) {
        Bundle report = new Bundle();
        report.putLong(ModuleActivation.EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE);
        report.putInt(ModuleActivation.EXTRA_PROCESS_ID, pid);
        report.putBinder(ModuleActivation.EXTRA_LIFETIME_TOKEN, lifetime.token);
        return report;
    }

    private Bundle readyReport(int pid, Lifetime lifetime) {
        Bundle report = report(pid, lifetime);
        report.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, true);
        report.putBoolean(ModuleActivation.EXTRA_PORTAL_SERVICE_READY, true);
        report.putBoolean(ModuleActivation.EXTRA_ROOT_INPUT_READY, true);
        report.putBoolean(ModuleActivation.EXTRA_PRESS_READER_READY, true);
        return report;
    }

    @Test
    public void injectionAndPortalRootReportsMustMatchTheRunningBuild() {
        assertTrue(ModuleActivation.matchesCurrentBuild(BuildConfig.VERSION_CODE));
        assertFalse(ModuleActivation.matchesCurrentBuild(BuildConfig.VERSION_CODE - 1L));

        Context context = RuntimeEnvironment.getApplication();
        Lifetime lifetime = new Lifetime();
        Bundle rootDenied = report(10, lifetime);
        rootDenied.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, false);
        ModuleActivation.recordInjected(context, rootDenied);
        assertTrue(ModuleActivation.isCurrentBuildInjected(context));
        assertFalse(ModuleActivation.isCurrentBuildPortalRootGranted(context));

        Bundle rootGranted = report(10, lifetime);
        rootGranted.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, true);
        ModuleActivation.recordInjected(context, rootGranted);
        assertTrue(ModuleActivation.isCurrentBuildPortalRootGranted(context));

        Bundle staleRootReport = new Bundle();
        staleRootReport.putLong(
                ModuleActivation.EXTRA_VERSION_CODE,
                BuildConfig.VERSION_CODE - 1L);
        staleRootReport.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, false);
        ModuleActivation.recordInjected(context, staleRootReport);
        assertTrue(ModuleActivation.isCurrentBuildPortalRootGranted(context));
    }

    @Test public void missingSessionIsUnknownButExplicitComponentFailuresAreUnavailable() {
        Context context = RuntimeEnvironment.getApplication();
        assertEquals(ModuleActivation.ReportedStatus.UNKNOWN,
                ModuleActivation.injectionStatus(context));
        assertEquals(ModuleActivation.ReportedStatus.UNKNOWN,
                ModuleActivation.portalServiceStatus(context));

        Lifetime lifetime = new Lifetime();
        Bundle report = report(10, lifetime);
        report.putBoolean(ModuleActivation.EXTRA_PORTAL_SERVICE_READY, false);
        report.putString(ModuleActivation.EXTRA_PORTAL_ROOT_OUTCOME,
                ModuleActivation.RootAccessOutcome.DENIED.name());
        ModuleActivation.recordInjected(context, report);

        assertEquals(ModuleActivation.ReportedStatus.READY,
                ModuleActivation.injectionStatus(context));
        assertEquals(ModuleActivation.ReportedStatus.UNAVAILABLE,
                ModuleActivation.portalServiceStatus(context));
        assertEquals(ModuleActivation.ReportedStatus.UNAVAILABLE,
                ModuleActivation.portalRootStatus(context));
    }

    @Test public void timedOutPortalRootProbeRemainsUnknownInsteadOfDenied() {
        Context context = RuntimeEnvironment.getApplication();
        Lifetime lifetime = new Lifetime();
        Bundle timedOut = report(10, lifetime);
        timedOut.putBoolean(ModuleActivation.EXTRA_PORTAL_SERVICE_READY, true);
        timedOut.putString(ModuleActivation.EXTRA_PORTAL_ROOT_OUTCOME,
                ModuleActivation.RootAccessOutcome.UNKNOWN.name());
        ModuleActivation.recordInjected(context, timedOut);

        assertEquals(ModuleActivation.ReportedStatus.READY, ModuleActivation.injectionStatus(context));
        assertEquals(ModuleActivation.ReportedStatus.UNKNOWN, ModuleActivation.portalRootStatus(context));
        assertFalse(ModuleActivation.isCurrentBuildPortalRootGranted(context));
    }

    @Test public void emptyErrorWithoutReadySignalDoesNotClaimRootInputFailure() {
        Context context = RuntimeEnvironment.getApplication();
        Lifetime lifetime = new Lifetime();
        Bundle connecting = readyReport(10, lifetime);
        connecting.putBoolean(ModuleActivation.EXTRA_ROOT_INPUT_READY, false);
        connecting.putString(ModuleActivation.EXTRA_ROOT_INPUT_ERROR, "");
        ModuleActivation.recordInjected(context, connecting);

        assertEquals(ModuleActivation.ReportedStatus.UNKNOWN, ModuleActivation.rootInputStatus(context));
        assertEquals(ModuleActivation.ReportedStatus.READY, ModuleActivation.pressReaderStatus(context));
    }

    @Test public void onlyLiveCurrentHostSupportingRecheckReceivesTheCommand() {
        Context context = RuntimeEnvironment.getApplication();
        Lifetime host = new Lifetime();
        assertFalse(ModuleActivation.requestPortalRootRecheck(context));
        ModuleActivation.recordInjected(context, readyReport(41, host));
        assertFalse(ModuleActivation.requestPortalRootRecheck(context));
        assertEquals(0, host.rechecks);
        assertTrue(ModuleActivation.isCurrentBuildPortalRootGranted(context));

        host.descriptor = new ModuleActivation.PortalRootToken(context).getInterfaceDescriptor();
        assertTrue(ModuleActivation.requestPortalRootRecheck(context));
        assertEquals(1, host.rechecks);
        host.alive = false;
        assertFalse(ModuleActivation.requestPortalRootRecheck(context));
        assertEquals(1, host.rechecks);
        host.alive = true;
        context.getSharedPreferences(ModuleActivation.PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putLong("injected_version", BuildConfig.VERSION_CODE - 1).commit();
        assertFalse(ModuleActivation.requestPortalRootRecheck(context));
        assertEquals(1, host.rechecks);
    }

    @Test public void actualHostCheckInvalidatesOnlyRootAndPublishesEvenRepeatedGranted() {
        Context context = RuntimeEnvironment.getApplication();
        Lifetime host = new Lifetime();
        ModuleActivation.recordInjected(context, readyReport(41, host));
        SharedPreferences prefs = context.getSharedPreferences(ModuleActivation.PREFS_NAME, Context.MODE_PRIVATE);
        List<Boolean> changes = new ArrayList<>();
        SharedPreferences.OnSharedPreferenceChangeListener listener = (p, key) -> {
            if (ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED.equals(key)) changes.add(p.contains(key));
        };
        prefs.registerOnSharedPreferenceChangeListener(listener);
        try {
            Bundle started = report(41, host);
            started.putString(ModuleActivation.EXTRA_ROOT_CONNECTION_OUTCOME, "portal_root_probe_started");
            ModuleActivation.recordInjected(context, started);
            assertEquals(ModuleActivation.ReportedStatus.CHECKING, ModuleActivation.portalRootStatus(context));
            assertTrue(ModuleActivation.isPortalServiceReady(context));
            assertTrue(ModuleActivation.isRootInputReady(context));
            assertTrue(ModuleActivation.isPressReaderReady(context));

            Bundle granted = report(41, host);
            granted.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, true);
            granted.putString(ModuleActivation.EXTRA_PORTAL_ROOT_OUTCOME, "GRANTED");
            String attempt = RootConnectionDiagnostics.newAttempt();
            granted.putString(ModuleActivation.EXTRA_ROOT_CONNECTION_ATTEMPT, attempt);
            ModuleActivation.recordInjected(context, granted);
            assertEquals(attempt, prefs.getString(ModuleActivation.EXTRA_ROOT_CONNECTION_ATTEMPT, null));
            assertTrue(ModuleActivation.isCurrentBuildPortalRootGranted(context));
            assertEquals(List.of(false, true), changes);
        } finally {
            prefs.unregisterOnSharedPreferenceChangeListener(listener);
        }
    }

    @Test public void previousHostsLateRootProbeCannotReplaceLiveHostOrItsResult() {
        Context context = RuntimeEnvironment.getApplication();
        Lifetime old = new Lifetime();
        ModuleActivation.recordInjected(context, readyReport(41, old));
        ModuleActivation.recordInjected(context, readyReport(42, new Lifetime()));
        Bundle started = report(41, old);
        started.putString(ModuleActivation.EXTRA_ROOT_CONNECTION_OUTCOME, "portal_root_probe_started");
        ModuleActivation.recordInjected(context, started);
        Bundle oldResult = report(41, old);
        oldResult.putString(ModuleActivation.EXTRA_PORTAL_ROOT_OUTCOME, "UNKNOWN");
        ModuleActivation.recordInjected(context, oldResult);
        assertEquals(42, ModuleActivation.injectedProcessId(context));
        assertTrue(ModuleActivation.isCurrentBuildPortalRootGranted(context));
    }

    @Test public void receivingPortalRejectsAnUnrelatedUidBeforeLaunchingTheRootProbe() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ModuleActivation.PortalRootToken token = new ModuleActivation.PortalRootToken(context);
        Bundle response = new Bundle();
        response.putInt(ModuleActivation.EXTRA_MODULE_UID, Binder.getCallingUid() + 1);
        token.acceptReportResponse(response);
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(token.getInterfaceDescriptor());
            data.setDataPosition(0);
            SecurityException rejected = org.junit.Assert.assertThrows(SecurityException.class,
                    () -> token.onTransact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0));
            assertEquals("MMGG UID required", rejected.getMessage());
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    @Test
    public void runtimeStartupUsesTheExistingForegroundServiceWithoutOpeningAnActivity() {
        String command = ModuleActivation.portalStartCommand();
        assertEquals("am start-foreground-service --user current -n "
                + "com.miui.contentextension/.services.TextContentExtensionService", command);
        assertFalse(command.contains("content query"));
        assertFalse(command.contains("com.miui.contentcatcher"));
        assertFalse(command.contains("force-stop"));
    }

    @Test public void providerInjectionCannotClaimServiceReadinessAndServiceStopClearsIt() {
        Context context = RuntimeEnvironment.getApplication();
        Lifetime lifetime = new Lifetime();
        Bundle report = readyReport(10, lifetime);
        report.remove(ModuleActivation.EXTRA_PORTAL_SERVICE_READY);
        ModuleActivation.recordInjected(context, report);
        assertTrue(ModuleActivation.isCurrentBuildInjected(context));
        assertTrue(ModuleActivation.isRootInputReady(context));
        assertFalse(ModuleActivation.isPortalServiceReady(context));

        report.putBoolean(ModuleActivation.EXTRA_PORTAL_SERVICE_READY, true);
        ModuleActivation.recordInjected(context, report);
        assertTrue(ModuleActivation.isPortalServiceReady(context));
        report.putBoolean(ModuleActivation.EXTRA_PORTAL_SERVICE_READY, false);
        ModuleActivation.recordInjected(context, report);
        assertFalse(ModuleActivation.isPortalServiceReady(context));
        assertTrue(ModuleActivation.isCurrentBuildInjected(context));
    }

    @Test
    public void explicitReloadClearsOldReadinessButKeepsUserSettings() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("drag_share_settings", Context.MODE_PRIVATE).edit()
                .putBoolean("thumb_trigger_enabled", true).commit();
        Bundle report = report(1234, new Lifetime());
        report.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, true);
        ModuleActivation.recordInjected(context, report);
        assertEquals(1234, ModuleActivation.injectedProcessId(context));
        ModuleActivation.clearPortalReport(context);
        assertFalse(ModuleActivation.isCurrentBuildInjected(context));
        assertFalse(ModuleActivation.isCurrentBuildPortalRootGranted(context));
        assertEquals(0, ModuleActivation.injectedProcessId(context));
        assertTrue(context.getSharedPreferences("drag_share_settings", Context.MODE_PRIVATE)
                .getBoolean("thumb_trigger_enabled", false));
        report.putLong(ModuleActivation.EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE - 1L);
        ModuleActivation.recordInjected(context, report);
        assertEquals(0, ModuleActivation.injectedProcessId(context));
    }

    @Test
    public void blacklistCommandStartsOnlyTheRequestedPortalActivity() {
        assertEquals(
                "am start --user current -n com.miui.contentextension/"
                        + "com.miui.contentextension.setting.whitelist.BlacklistSettingActivity",
                ModuleActivation.portalBlacklistCommand());
    }

    @Test
    public void newHostCannotInheritOldRootOrReaderReadiness() {
        Context context = RuntimeEnvironment.getApplication();
        Bundle report = readyReport(42, new Lifetime());
        ModuleActivation.recordInjected(context, report);
        assertTrue(ModuleActivation.isPressReaderReady(context));
        assertTrue(ModuleActivation.isRootInputReady(context));
        assertTrue(ModuleActivation.isPortalServiceReady(context));

        Bundle restarted = report(43, new Lifetime());
        ModuleActivation.recordInjected(context, restarted);
        assertTrue(ModuleActivation.isCurrentBuildInjected(context));
        assertFalse(ModuleActivation.isCurrentBuildPortalRootGranted(context));
        assertFalse(ModuleActivation.isPressReaderReady(context));
        assertFalse(ModuleActivation.isRootInputReady(context));
        assertFalse(ModuleActivation.isPortalServiceReady(context));
        assertEquals("", ModuleActivation.pressReaderError(context));
    }

    @Test
    public void readerFailurePersistsWithoutClaimingRootWasDenied() {
        Context context = RuntimeEnvironment.getApplication();
        Bundle report = report(10, new Lifetime());
        report.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, true);
        report.putBoolean(ModuleActivation.EXTRA_PRESS_READER_READY, false);
        report.putString(ModuleActivation.EXTRA_PRESS_READER_ERROR, "Root 子进程已退出");
        ModuleActivation.recordInjected(context, report);
        assertTrue(ModuleActivation.isCurrentBuildPortalRootGranted(context));
        assertFalse(ModuleActivation.isPressReaderReady(context));
        assertEquals("Root 子进程已退出", ModuleActivation.pressReaderError(context));
        ModuleActivation.clearPortalReport(context);
        assertEquals("", ModuleActivation.pressReaderError(context));
    }

    @Test
    public void inputFailureRemainsVisibleWhenReaderIsReady() {
        Context context = RuntimeEnvironment.getApplication();
        Bundle report = report(10, new Lifetime());
        report.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, true);
        report.putBoolean(ModuleActivation.EXTRA_PRESS_READER_READY, true);
        report.putBoolean(ModuleActivation.EXTRA_ROOT_INPUT_READY, false);
        report.putString(ModuleActivation.EXTRA_ROOT_INPUT_ERROR, "Root 输入命令被系统结束（137）");
        ModuleActivation.recordInjected(context, report);
        assertTrue(ModuleActivation.isCurrentBuildPortalRootGranted(context));
        assertTrue(ModuleActivation.isPressReaderReady(context));
        assertFalse(ModuleActivation.isRootInputReady(context));
        assertEquals("Root 输入命令被系统结束（137）", ModuleActivation.rootInputError(context));
        ModuleActivation.clearPortalReport(context);
        assertEquals("", ModuleActivation.rootInputError(context));
    }

    @Test public void currentPortalDeathClearsAllOfItsReadiness() {
        Context context = RuntimeEnvironment.getApplication();
        Lifetime lifetime = new Lifetime();
        ModuleActivation.recordInjected(context, readyReport(70, lifetime));
        assertTrue(ModuleActivation.isCurrentBuildInjected(context));
        assertTrue(ModuleActivation.isCurrentBuildPortalRootGranted(context));
        assertTrue(ModuleActivation.isPortalServiceReady(context));
        assertTrue(ModuleActivation.isRootInputReady(context));
        assertTrue(ModuleActivation.isPressReaderReady(context));

        // Binder can already be dead before the queued death callback reaches the module.
        lifetime.alive = false;
        assertFalse(ModuleActivation.isCurrentBuildInjected(context));
        assertFalse(ModuleActivation.isPortalServiceReady(context));
        assertFalse(ModuleActivation.isRootInputReady(context));
        lifetime.die();
        assertFalse(ModuleActivation.isCurrentBuildInjected(context));
        assertFalse(ModuleActivation.isCurrentBuildPortalRootGranted(context));
        assertFalse(ModuleActivation.isRootInputReady(context));
        assertFalse(ModuleActivation.isPressReaderReady(context));
        assertEquals(0, ModuleActivation.injectedProcessId(context));
        assertTrue(context.getSharedPreferences(ModuleActivation.PREFS_NAME, Context.MODE_PRIVATE).getAll().isEmpty());
    }

    @Test public void queuedDeathFromOldPortalCannotClearItsReplacement() {
        Context context = RuntimeEnvironment.getApplication();
        Lifetime old = new Lifetime();
        Lifetime replacement = new Lifetime();
        ModuleActivation.recordInjected(context, readyReport(70, old));
        IBinder.DeathRecipient queuedDeath = old.death;
        ModuleActivation.recordInjected(context, readyReport(71, replacement));
        assertEquals(1, old.unlinks);

        queuedDeath.binderDied();
        assertEquals(71, ModuleActivation.injectedProcessId(context));
        assertTrue(ModuleActivation.isCurrentBuildInjected(context));
        assertTrue(ModuleActivation.isRootInputReady(context));
        assertTrue(ModuleActivation.isPressReaderReady(context));
    }

    @Test public void historicalReadyPreferencesWithoutAnInMemoryTokenAreNotActivation() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(ModuleActivation.PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putLong("injected_version", BuildConfig.VERSION_CODE).putInt("process_id", 70)
                .putLong("portal_root_version", BuildConfig.VERSION_CODE).putBoolean("portal_root_granted", true)
                .putLong("root_input_version", BuildConfig.VERSION_CODE).putBoolean("root_input_ready", true)
                .putLong("press_reader_version", BuildConfig.VERSION_CODE).putBoolean("press_reader_ready", true)
                .commit();
        assertFalse(ModuleActivation.isCurrentBuildInjected(context));
        assertFalse(ModuleActivation.isCurrentBuildPortalRootGranted(context));
        assertFalse(ModuleActivation.isRootInputReady(context));
        assertFalse(ModuleActivation.isPressReaderReady(context));
        assertEquals(0, ModuleActivation.injectedProcessId(context));
    }

    @Test public void deadTokenAtLinkTimeLeavesNoHistoricalActivation() {
        Context context = RuntimeEnvironment.getApplication();
        ModuleActivation.recordInjected(context, readyReport(70, new Lifetime()));
        Lifetime ended = new Lifetime();
        ended.alive = false;
        ModuleActivation.recordInjected(context, readyReport(71, ended));
        assertEquals(1, ended.links);
        assertFalse(ModuleActivation.isCurrentBuildInjected(context));
        assertFalse(ModuleActivation.isRootInputReady(context));
        assertFalse(ModuleActivation.isPressReaderReady(context));
    }

    @Test public void statusUpdatesShareOneDeathSubscriptionAndMissingTokenIsRejected() {
        Context context = RuntimeEnvironment.getApplication();
        Lifetime lifetime = new Lifetime();
        ModuleActivation.recordInjected(context, report(70, lifetime));
        ModuleActivation.recordInjected(context, readyReport(70, lifetime));
        assertEquals(1, lifetime.links);
        assertTrue(ModuleActivation.isRootInputReady(context));

        Bundle incomplete = readyReport(70, lifetime);
        incomplete.remove(ModuleActivation.EXTRA_LIFETIME_TOKEN);
        ModuleActivation.recordInjected(context, incomplete);
        assertFalse(ModuleActivation.isCurrentBuildInjected(context));
        assertEquals(1, lifetime.unlinks);
    }

    @Test public void malformedOptionalRootDiagnosticCannotBlockReadinessStatus() {
        Context context = RuntimeEnvironment.getApplication();
        Lifetime lifetime = new Lifetime();
        Bundle report = readyReport(72, lifetime);
        report.putInt(ModuleActivation.EXTRA_ROOT_CONNECTION_ATTEMPT, 42);
        report.putString(ModuleActivation.EXTRA_ROOT_CONNECTION_PROC, "portal-72");
        report.putString(ModuleActivation.EXTRA_ROOT_CONNECTION_OUTCOME, "lease_ready");
        report.putLong(ModuleActivation.EXTRA_ROOT_CONNECTION_AT, System.currentTimeMillis());

        ModuleActivation.recordInjected(context, report);

        assertEquals(ModuleActivation.ReportedStatus.READY, ModuleActivation.injectionStatus(context));
        assertEquals(ModuleActivation.ReportedStatus.READY, ModuleActivation.pressReaderStatus(context));
    }

    /** A controllable remote Binder boundary; no processes or Root commands are started. */
    private static final class Lifetime {
        boolean alive = true;
        int links;
        int unlinks;
        int rechecks;
        String descriptor;
        IBinder.DeathRecipient death;
        final IBinder token = (IBinder) Proxy.newProxyInstance(IBinder.class.getClassLoader(),
                new Class<?>[]{IBinder.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isBinderAlive":
                        case "pingBinder": return alive;
                        case "getInterfaceDescriptor": return descriptor;
                        case "transact":
                            assertEquals(IBinder.FIRST_CALL_TRANSACTION, args[0]);
                            Parcel data = (Parcel) args[1];
                            data.setDataPosition(0);
                            data.enforceInterface(descriptor);
                            Parcel reply = (Parcel) args[2];
                            reply.writeNoException();
                            reply.setDataPosition(0);
                            rechecks++;
                            return true;
                        case "linkToDeath":
                            links++;
                            if (!alive) throw new RemoteException("Portal already ended");
                            death = (IBinder.DeathRecipient) args[0];
                            return null;
                        case "unlinkToDeath":
                            if (death != args[0]) return false;
                            unlinks++;
                            death = null;
                            return true;
                        case "toString": return "PortalLifetime";
                        default: throw new AssertionError("Unexpected Binder call: " + method.getName());
                    }
                });

        void die() {
            alive = false;
            death.binderDied();
        }
    }
}
