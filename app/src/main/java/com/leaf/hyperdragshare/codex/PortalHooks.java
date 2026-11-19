package com.leaf.hyperdragshare.codex;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.leaf.hyperdragshare.gestures.config.OverlayTouchRegions;

import com.leaf.hyperdragshare.compat.xposed.XC_MethodHook;
import com.leaf.hyperdragshare.compat.xposed.XposedBridge;
import com.leaf.hyperdragshare.compat.xposed.XposedHelpers;

final class PortalHooks {
    private static final String TAG = "DragShare/Taplus";
    private static final String SERVICE_CLASS =
            "com.miui.contentextension.services.TextContentExtensionService";
    private static final String CALLBACK_CLASS = SERVICE_CLASS + "$1";
    private static final String CATCHER_RESULT_CLASS =
            "miui.contentcatcher.sdk.ClientCatcherResult";
    private static final String ON_PICK_TEXT_METHOD = "onPickText";
    private static final String ON_PICK_IMAGE_METHOD = "onPickImage";
    private static final String BASE_FLOAT_CLASS =
            "com.miui.contentextension.text.floatview.BaseFloatView";
    private static final String MOTION_KEY = "MotionEvent";
    private static final Handler INPUT_MAIN = new Handler(Looper.getMainLooper());
    private static final PortalInputHandoff inputHandoff = new PortalInputHandoff();
    private static final GestureDiagnostics.Stream callbackDiagnostics =
            new GestureDiagnostics.Stream("portal_callback_observed");
    private static final String CONTROL_KEY = "observe_control_event";
    private static final Object DEFERRED_HOST_LOCK = new Object();
    private static final ArrayDeque<DeferredHostCall> DEFERRED_HOST_CALLS = new ArrayDeque<>();
    private static final ThreadLocal<Boolean> REPLAYING_HOST_CALL = new ThreadLocal<>();

    private static final Set<Class<?>> HOOKED_CALLBACK_CLASSES = Collections.newSetFromMap(
            new ConcurrentHashMap<>());

    @SuppressLint("StaticFieldLeak")
    private static volatile DragShareController controller;
    private static volatile RootTouchSource rootTouchSource;
    private static volatile RootGateway rootGateway;
    private static volatile MiuiMotionSource motionSource;
    private static volatile PressCaptureSource thumbCaptureSource;
    @SuppressLint("StaticFieldLeak")
    private static android.app.Service portalService;
    private static String appIdleHookError;
    private static volatile boolean hostCancelIgnoredLogged;
    private static volatile boolean controlIgnoredLogged;
    private static volatile boolean activationReported;
    private static volatile Context portalContext;
    private static volatile ClassLoader portalClassLoader;
    private static volatile boolean currentPickCallbacksHooked;
    private static volatile ContentObserver settingsObserver;
    private static volatile String xposedBridgeVersion = "unavailable";
    private static boolean overlayTouchObserved;

    private PortalHooks() {}

    static void install(ClassLoader classLoader) {
        try {
            portalClassLoader = classLoader;
            xposedBridgeVersion = readXposedBridgeVersion();
            Class<?> serviceClass = XposedHelpers.findClass(SERVICE_CLASS, classLoader);
            suppressOriginalFloatWindows(classLoader);
            hookApplicationStartup(classLoader);
            hookServiceLifecycle(serviceClass, classLoader);
            hookAppIdleExit(classLoader);
            hookShareStart(serviceClass);
            hookKnownCallbackClass(classLoader);
            hookClipboardCard(serviceClass, classLoader);
            log("portal hooks installed");
        } catch (Throwable error) {
            log("unable to install Taplus hooks", error);
        }
    }

    private static void hookApplicationStartup(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod("com.miui.contentextension.App", classLoader, "onCreate",
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!MainHook.TAPLUS_PACKAGE.equals(android.app.Application.getProcessName())) return;
                        Context context = ((Context) param.thisObject).getApplicationContext();
                        try {
                            portalContext = context;
                            observeOverlayTouchRegions(context);
                            registerSettingsObserver(context);
                            applyPortalRuntime();
                            activationReported = reportPortalActivation(context);
                            log("application input initialized; foreground service startup reported separately");
                        } catch (RuntimeException | LinkageError error) {
                            stopPortalRuntime(false);
                            log("cannot initialize application press input; keeping host behavior", error);
                            android.widget.Toast.makeText(context, "按压监听启动失败：" + error.getMessage(),
                                    android.widget.Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private static void hookAppIdleExit(ClassLoader classLoader) {
        String handlerClass = "com.miui.contentextension.setting.whitelist.utils.StopSelfHandler";
        try {
            XposedHelpers.findAndHookMethod(handlerClass, classLoader, "handleMessage", android.os.Message.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            android.os.Message message = (android.os.Message) param.args[0];
                            if (message.what == 1 && thumbCaptureSource != null) {
                                log("press input active; skipping settings idle exit");
                                param.setResult(null);
                            }
                        }
                    });
            log("settings idle exit hook installed");
        } catch (RuntimeException | LinkageError | XposedHelpers.ClassNotFoundError error) {
            appIdleHookError = "传送门空闲退出Hook不可用，按压监听可能随进程退出";
            log("cannot hook settings idle exit; keeping host behavior", error);
        }
    }

    private static void hookClipboardCard(Class<?> serviceClass, ClassLoader classLoader) {
        String listener = "com.miui.contentextension.text.floatview.TaplusSplashFloatView$13";
        try {
            XposedHelpers.findAndHookMethod(listener, classLoader, "onClick", android.view.View.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Context context = ((android.view.View) param.args[0]).getContext();
                            Object owner;
                            try {
                                android.os.Bundle settings = context.getContentResolver().call(
                                        ImageStagingClient.BASE_URI, DragShareSettings.METHOD_GET_SETTINGS,
                                        null, null);
                                if (settings == null || !settings.getBoolean(
                                        IntegrationSettings.EXTRA_COPY_DIRECT_ENABLED, false)) return;
                                String text = AicrClipboardText.read(context.getSystemService(
                                        android.content.ClipboardManager.class).getPrimaryClip());
                                if (text == null || !text.equals(
                                        PortalContentCaptureSource.clipboardCardText(serviceClass))) return;
                                owner = PortalContentCaptureSource.clipboardCardOwner(param.thisObject);
                                android.os.Bundle input = new android.os.Bundle();
                                input.putBoolean("copy_direct_source", true);
                                input.putString("text", text);
                                android.os.Bundle result = context.getContentResolver().call(
                                        ImageStagingClient.BASE_URI, BigBangOverlayLauncher.METHOD_OPEN,
                                        null, input);
                                if (result == null || !result.getBoolean("accepted")) {
                                    throw new IllegalStateException("分词窗口未接受请求");
                                }
                            } catch (RuntimeException | LinkageError | XposedHelpers.InvocationTargetError error) {
                                log("clipboard card handoff failed; keeping the host editor", error);
                                android.widget.Toast.makeText(context, "无法打开分词窗口，保留原编辑操作："
                                        + error.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                                return;
                            }
                            param.setResult(null);
                            log("opened original-page text window from portal clipboard card");
                            try {
                                PortalContentCaptureSource.dismissClipboardCard(owner);
                            } catch (RuntimeException | LinkageError | XposedHelpers.InvocationTargetError error) {
                                log("text window opened but the clipboard card could not close", error);
                                android.widget.Toast.makeText(context, "分词窗口已打开，但复制卡片未能收起",
                                        android.widget.Toast.LENGTH_LONG).show();
                            }
                        }
                    });
            log("portal clipboard card hook installed");
        } catch (RuntimeException | LinkageError error) {
            log("clipboard card hook unavailable; keeping the host editor", error);
        }
    }

    private static void suppressOriginalFloatWindows(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(
                BASE_FLOAT_CLASS,
                classLoader,
                "addToWindow",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        DragShareController current = controller;
                        boolean suppress = current != null && current.shouldSuppressPortalHostFloatWindow();
                        DragShareLog.i(TAG, "host float window addToWindow suppressed=" + suppress
                                + " view=" + param.thisObject.getClass().getName());
                        if (suppress) {
                            param.setResult(null);
                        }
                    }
                });
    }

    private static void hookServiceLifecycle(Class<?> serviceClass, ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(serviceClass, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Context context = ((Context) param.thisObject).getApplicationContext();
                portalContext = context;
                portalService = (android.app.Service) param.thisObject;
                portalClassLoader = classLoader;
                DragShareLog.configure(DragShareSettings.readFromProvider(context));
                DragShareDiagnostics.captureRuntimeOnce(
                        context,
                        "portal service created",
                        xposedBridgeVersion);
                activationReported = reportPortalActivation(context);
                registerSettingsObserver(context);
                applyPortalRuntime();
                hookCallbackFromService(param.thisObject);
            }
        });

        XposedHelpers.findAndHookMethod(serviceClass, "onDestroy", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                unregisterSettingsObserver();
                portalService = null;
                stopPortalRuntime(false);
                ModuleActivation.reportInjected((Context) param.thisObject, false);
                portalContext = null;
            }
        });

        XposedHelpers.findAndHookMethod(serviceClass, "scheduleStopSelfJob", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (thumbCaptureSource != null) {
                    param.setResult(null);
                    log("press input active; skipping host idle stop job");
                }
            }
        });

        XposedHelpers.findAndHookMethod(
                serviceClass,
                "onStartCommand",
                Intent.class,
                int.class,
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Context context = ((Context) param.thisObject).getApplicationContext();
                        activationReported = reportPortalActivation(context)
                                || activationReported;
                    }
                });

        XposedHelpers.findAndHookMethod(serviceClass, "cancelTask", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (isReplayingHostCall()) {
                    return;
                }
                DragShareController current = controller;
                if (current != null) {
                    if (deferHostCallIfRootDragActive(param, "cancelTask")) {
                        current.traceDiagnostic("HOST_CANCEL_DEFERRED", "root_owns_release", null);
                        // Let the root ACTION_UP replay the original method after the
                        // physical gesture has ended. Running it now poisons Taplus'
                        // task state and prevents the next long press in the same app.
                        param.setResult(null);
                        if (!hostCancelIgnoredLogged) {
                            hostCancelIgnoredLogged = true;
                            log("ignoring host cancel while root drag is active");
                        }
                    } else {
                        invalidateLegacyPick();
                        current.onHostTaskCancelled();
                    }
                }
            }
        });
    }

    private static void hookShareStart(Class<?> serviceClass) {
        XC_MethodHook showHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!activationReported) {
                    Context context = ((Context) param.thisObject).getApplicationContext();
                    activationReported = reportPortalActivation(context);
                }
                Context context = ((Context) param.thisObject).getApplicationContext();
                DragShareSettings settings = DragShareSettings.readFromProvider(context);
                applyPortalRuntime();
                DragShareController current = controller;
                if (current != null) {
                    String methodName = param.method.getName();
                    Object catcherResult = null;
                    boolean currentPickCallback = ON_PICK_TEXT_METHOD.equals(methodName)
                            || ON_PICK_IMAGE_METHOD.equals(methodName);
                    if (currentPickCallback && param.args != null && param.args.length > 0) {
                        catcherResult = param.args[0];
                    }
                    // The newer Taplus chain delivers the authoritative result
                    // through onPick*; startPick* only arms the capture and still
                    // points at the previous static result.
                    if (currentPickCallbacksHooked && catcherResult == null
                            && ("startPickTextTask".equals(methodName)
                            || "startPickImageTask".equals(methodName))) {
                        return;
                    }
                    PortalContentCaptureSource.Pick pick = PortalContentCaptureSource.capturePick(
                            param.thisObject, settings, catcherResult);
                    if (pick != null) dispatchPortalPick(param.thisObject, catcherResult, settings, pick);
                }
            }
        };
        XposedHelpers.findAndHookMethod(serviceClass, "startPickTextTask", showHook);
        XposedHelpers.findAndHookMethod(serviceClass, "startPickImageTask", showHook);
        try {
            Class<?> catcherResultClass = XposedHelpers.findClassIfExists(
                    CATCHER_RESULT_CLASS, portalClassLoader);
            if (catcherResultClass != null) {
                XposedHelpers.findAndHookMethod(
                        serviceClass, ON_PICK_TEXT_METHOD, catcherResultClass, showHook);
                XposedHelpers.findAndHookMethod(
                        serviceClass, ON_PICK_IMAGE_METHOD, catcherResultClass, showHook);
                currentPickCallbacksHooked = true;
                log("hooked Taplus current pick callbacks");
            } else {
                currentPickCallbacksHooked = false;
                log("Taplus current pick result class unavailable; using legacy entry points");
            }
        } catch (Throwable error) {
            currentPickCallbacksHooked = false;
            log("unable to hook Taplus current pick callbacks", error);
        }
    }

    /** Native callbacks can arrive on Binder threads; qualification owns the main queue. */
    static void dispatchPortalContent(Object service, Object result,
                                      DragShareSettings settings, CapturedContent content) {
        dispatchPortalPick(service, result, settings, new PortalContentCaptureSource.Pick(content, null));
    }

    private static long legacyPickGeneration;
    private static Runnable legacyPickCancellation;

    private static void invalidateLegacyPick() {
        if (thumbCaptureSource != null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) clearLegacyPick();
        else new Handler(Looper.getMainLooper()).post(PortalHooks::clearLegacyPick);
    }

    private static void clearLegacyPick() {
        legacyPickGeneration++;
        Runnable cancellation = legacyPickCancellation;
        legacyPickCancellation = null;
        if (cancellation != null) cancellation.run();
    }

    private static void dispatchPortalPick(Object service, Object result, DragShareSettings settings,
                                           PortalContentCaptureSource.Pick pick) {
        DragShareController current = controller;
        PressCaptureSource press = thumbCaptureSource;
        Runnable deliver = () -> {
            CapturedContent preview = pick.preview();
            var original = pick.original();
            if (current == null || current != controller || press != thumbCaptureSource) {
                recyclePortalPreview(preview);
                return;
            }
            if (press != null) {
                if (preview != null && !press.acceptPortalContent(preview)) {
                    recyclePortalPreview(preview);
                    log("portal pick ignored without a qualified live press");
                    return;
                }
                if (original != null) press.loadPortalOriginal(service, original, preview != null);
                return;
            }
            if (current.isAwaitingRootRelease()) {
                recyclePortalPreview(preview);
                log("portal pick ignored while Root press owns the gesture");
                return;
            }
            PointF point = PortalContentCaptureSource.touchPointFromResult(result);
            if (point == null) point = PortalContentCaptureSource.initialPoint(
                    service, current.latestPointerX(), current.latestPointerY());
            float x = point == null ? -1f : point.x;
            float y = point == null ? -1f : point.y;
            clearLegacyPick();
            if (original == null) {
                showLegacyPick(current, settings, preview, x, y);
                return;
            }
            // Legacy capture has no press-owned preview to upgrade. Use the prior
            // display deadline asynchronously and keep just one load.
            long generation = legacyPickGeneration;
            Handler main = new Handler(Looper.getMainLooper());
            class LegacyPickResult {
                boolean delivered;
                PortalContentCaptureSource.OriginalImageRequest request;
                final Runnable showPreview = () -> deliver(null);

                void cancel() {
                    if (delivered) return;
                    delivered = true;
                    main.removeCallbacks(showPreview);
                    request.cancel();
                    recyclePortalPreview(preview);
                }

                void deliver(CapturedContent image) {
                    if (delivered) {
                        recyclePortalPreview(image);
                        return;
                    }
                    delivered = true;
                    main.removeCallbacks(showPreview);
                    if (generation == legacyPickGeneration) legacyPickCancellation = null;
                    if (image == null) request.cancel();
                    if (generation != legacyPickGeneration || controller != current
                            || thumbCaptureSource != null
                            || current.isAwaitingRootRelease()) {
                        recyclePortalPreview(image);
                        recyclePortalPreview(preview);
                        return;
                    }
                    if (image != null) recyclePortalPreview(preview);
                    showLegacyPick(current, settings, image == null ? preview : image, x, y);
                }
            }
            LegacyPickResult completion = new LegacyPickResult();
            completion.request = PortalContentCaptureSource.loadOriginalImageAsync(
                    service, original, image -> main.post(() -> completion.deliver(image)));
            legacyPickCancellation = completion::cancel;
            if (preview != null) main.postDelayed(completion.showPreview, 250L);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) deliver.run();
        else new Handler(Looper.getMainLooper()).post(deliver);
    }

    private static void showLegacyPick(DragShareController current, DragShareSettings settings,
                                       CapturedContent content, float x, float y) {
        if (content == null) return;
        current.reservePortalHostFloatWindowSuppression();
        current.show(content, x, y, settings);
    }

    private static void recyclePortalPreview(CapturedContent content) {
        if (content != null && content.isImage()) content.bitmap.recycle();
    }

    private static void hookKnownCallbackClass(ClassLoader classLoader) {
        Class<?> callbackClass = XposedHelpers.findClassIfExists(CALLBACK_CLASS, classLoader);
        if (callbackClass != null) {
            hookCallbackClass(callbackClass);
        }
    }

    private static void hookCallbackFromService(Object service) {
        try {
            Object callback = XposedHelpers.getObjectField(service, "mCallback");
            if (callback != null) {
                hookCallbackClass(callback.getClass());
            }
        } catch (Throwable error) {
            log("unable to discover Taplus callback", error);
        }
    }

    private static void hookCallbackClass(Class<?> callbackClass) {
        if (!HOOKED_CALLBACK_CLASSES.add(callbackClass)) {
            return;
        }

        try {
            Method target = null;
            for (Method method : callbackClass.getDeclaredMethods()) {
                if ("onContentReceived".equals(method.getName())
                        && method.getParameterTypes().length == 1) {
                    target = method;
                    break;
                }
            }
            if (target == null) {
                throw new NoSuchMethodException(callbackClass.getName() + ".onContentReceived");
            }
            target.setAccessible(true);
            XposedBridge.hookMethod(target, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    handleCallback(param);
                }
            });
            log("hooked callback " + callbackClass.getName());
        } catch (Throwable error) {
            HOOKED_CALLBACK_CLASSES.remove(callbackClass);
            log("unable to hook Taplus callback", error);
        }
    }

    @SuppressWarnings("unchecked")
    private static void handleCallback(XC_MethodHook.MethodHookParam param) {
        try {
            if (isReplayingHostCall()) {
                return;
            }
            DragShareController current = controller;
            // The callback hook remains installed after switching to accessibility
            // mode. Let the host receive its own events when our runtime is stopped.
            if (current == null) {
                return;
            }
            Map<String, Object> properties = (Map<String, Object>)
                    XposedHelpers.callMethod(param.args[0], "getPropertyMap");
            if (properties == null) {
                return;
            }

            Object motion = properties.get(MOTION_KEY);
            if (!(motion instanceof MotionEvent)
                    || ((MotionEvent) motion).getActionMasked() != MotionEvent.ACTION_MOVE) {
                Object control = properties.get(CONTROL_KEY);
                DragShareLog.dSampled(TAG, "callback:" + String.valueOf(control), 1_000L,
                        () -> "portal callback keys=" + properties.keySet()
                                + " motion=" + (motion instanceof MotionEvent)
                                + " control=" + String.valueOf(control));
            }
            if (motion instanceof MotionEvent) {
                callbackDiagnostics.motion((MotionEvent) motion, current.diagnosticPackage(),
                        "HOST_CALLBACK_OBSERVED", null, null, null, "miui_motion_fallback");
                offerMotion((MotionEvent) motion);
                // A motion-only result is not valid Taplus pick content. The
                // fallback above has already received the event when Root is
                // not ready.
                param.setResult(null);
                return;
            }

            Object control = properties.get(CONTROL_KEY);
            if ("258".equals(control)) {
                current.traceDiagnostic("HOST_CONTROL_258", "portal_control_received", null);
                // A confirmed press owns this same root gesture through release too.
                if (current.isAwaitingRootRelease()) {
                    current.traceDiagnostic("HOST_CONTROL_258_IGNORED", "root_owns_release", null);
                    param.setResult(null);
                }
            } else if ("257".equals(control)) {
                current.traceDiagnostic("HOST_CONTROL_257", "portal_control_received", null);
                if (deferHostCallIfRootDragActive(param, "control-257")) {
                    current.traceDiagnostic("HOST_CONTROL_257_DEFERRED", "root_owns_release", null);
                    if (!controlIgnoredLogged) {
                        controlIgnoredLogged = true;
                        log("ignoring host finish; waiting for root ACTION_UP");
                    }
                    param.setResult(null);
                } else {
                    if (current != null) {
                        invalidateLegacyPick();
                        current.finishFromControlEvent();
                    }
                }
            }
        } catch (Throwable error) {
            log("callback handling failed", error);
        }
    }

    private static boolean reportPortalActivation(Context context) {
        boolean reported = ModuleActivation.reportInjected(context, portalService != null);
        if (reported) {
            ModuleActivation.probePortalRootAccessAsync(context);
        }
        return reported;
    }

    private static boolean hasReadyRootSource() {
        RootGateway gateway = rootGateway;
        if (gateway != null) return gateway.isInputReady();
        RootTouchSource source = rootTouchSource;
        return source != null && source.isReady();
    }

    private static synchronized void applyPortalRuntime() {
        Context context = portalContext;
        ClassLoader classLoader = portalClassLoader;
        if (context == null || classLoader == null) {
            return;
        }
        DragShareSettings settings = DragShareSettings.readFromProvider(context);
        DragShareLog.configure(settings);
        DragShareLog.d(TAG, "portal runtime settings logLevel=" + settings.logLevel
                + " logDestination=" + settings.logDestination);
        if ((portalService == null
                && !ThumbTriggerSettings.readFromProvider(context).enabled)) {
            stopPortalRuntime(true);
            return;
        }
        if (controller != null) {
            configureThumbCapture(context, settings);
            return;
        }
        hostCancelIgnoredLogged = false;
        controlIgnoredLogged = false;
        clearDeferredHostCalls();
        controller = new DragShareController(context, OverlayWindowPolicy.portal());
        rootGateway = new RootGateway(context, PortalHooks::dispatchRootPointer,
                (ready, reason) -> {
                    syncFallbackMotion(ready);
                    ModuleActivation.reportRootInput(context, ready, reason);
                });
        rootTouchSource = rootGateway.input();
        rootTouchSource.setFallbackOwnsInput(inputHandoff::fallbackActive);
        motionSource = new MiuiMotionSource(classLoader, PortalHooks::offerMotion);
        configureThumbCapture(context, settings);
        rootGateway.startInput();
        log("portal runtime started");
    }

    private static synchronized void stopPortalRuntime(boolean replayDeferredCalls) {
        PressCaptureSource thumb = thumbCaptureSource;
        thumbCaptureSource = null;
        if (thumb != null) thumb.stop();
        invalidateLegacyPick();
        updateHostIdleTimer(false);
        if (replayDeferredCalls) {
            replayDeferredHostCalls();
        }
        MiuiMotionSource fallback = motionSource;
        motionSource = null;
        inputHandoff.finishFallback();
        if (fallback != null) fallback.stop();
        RootTouchSource rootSource = rootTouchSource;
        rootTouchSource = null;
        if (rootSource != null) {
            RootGateway gateway = rootGateway;
            rootGateway = null;
            if (gateway != null) gateway.stopInput();
            else rootSource.stop();
        }
        DragShareController current = controller;
        controller = null;
        if (current != null) {
            current.destroy();
        }
        clearDeferredHostCalls();
    }

    private static void registerSettingsObserver(Context context) {
        unregisterSettingsObserver();
        ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                applyPortalRuntime();
            }
        };
        try {
            context.getContentResolver().registerContentObserver(
                    DragShareSettings.settingsUri(),
                    false,
                    observer);
            settingsObserver = observer;
        } catch (Throwable error) {
            log("unable to observe settings changes", error);
        }
    }

    private static synchronized void observeOverlayTouchRegions(Context context) {
        if (overlayTouchObserved) return;
        OverlayTouchRegions.observe(context, new Handler(Looper.getMainLooper()), () -> {});
        overlayTouchObserved = true;
    }

    private static void unregisterSettingsObserver() {
        ContentObserver observer = settingsObserver;
        settingsObserver = null;
        Context context = portalContext;
        if (observer == null || context == null) {
            return;
        }
        try {
            context.getContentResolver().unregisterContentObserver(observer);
        } catch (Throwable ignored) {
            // The process may be leaving while the resolver is already gone.
        }
    }

    private static void dispatchRootPointer(
            int action, float x, float y, long eventTime, int rawTouchMajor, int rawTouchMinor, int rawWidthMajor) {
        if (action != MotionEvent.ACTION_MOVE) {
            invalidateLegacyPick();
            DragShareLog.dSampled(TAG, "root-pointer:" + action, 250L,
                    () -> "root pointer action=" + MotionEvent.actionToString(action)
                            + " point=" + Math.round(x) + "," + Math.round(y)
                            + " time=" + eventTime);
        }
        DragShareController current = controller;
        if (current != null) {
            current.acceptPointerEvent(
                    action,
                    x,
                    y,
                    eventTime,
                    "root",
                    action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                            ? PortalHooks::replayDeferredHostCalls
                            : null);
        }
        PressCaptureSource thumb = thumbCaptureSource;
        if (thumb != null && (current == null || !current.hasInteractivePendingImagePreview())) {
            thumb.onPointerEvent(action, x, y, eventTime);
        }
    }

    private static void syncFallbackMotion(boolean rootReady) {
        MiuiMotionSource fallback = motionSource;
        if (fallback == null) return;
        if (inputHandoff.shouldListen(rootReady)) fallback.start();
        else fallback.stop();
    }

    /** Only copy a borrowed event when it will cross onto the main queue. */
    private static void offerMotion(MotionEvent event) {
        MiuiMotionSource fallback = motionSource;
        if (fallback == null || !inputHandoff.shouldListen(hasReadyRootSource())) return;
        if (Looper.myLooper() == INPUT_MAIN.getLooper()) {
            dispatchMotion(event);
            return;
        }
        MotionEvent copy = MotionEvent.obtainNoHistory(event);
        INPUT_MAIN.post(() -> {
            try {
                if (motionSource == fallback) dispatchMotion(copy);
            } finally {
                copy.recycle();
            }
        });
    }

    /** New gestures prefer Root. A fallback gesture already in progress keeps its source. */
    private static void dispatchMotion(MotionEvent event) {
        boolean rootReady = hasReadyRootSource();
        int action = event.getActionMasked();
        long downTime = event.getDownTime();
        if (action == MotionEvent.ACTION_DOWN && inputHandoff.missedFallbackEnd(downTime, rootReady)) {
            deliverMotion(event, MotionEvent.ACTION_CANCEL);
            inputHandoff.finishFallback();
            syncFallbackMotion(rootReady);
            return;
        }
        if (!inputHandoff.acceptFallback(action, downTime, rootReady)) return;
        deliverMotion(event, action);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            inputHandoff.finishFallback();
            syncFallbackMotion(rootReady);
        }
    }

    private static void deliverMotion(MotionEvent event, int action) {
        DragShareController current = controller;
        PressCaptureSource press = thumbCaptureSource;
        float x = event.getRawX();
        float y = event.getRawY();
        long time = event.getEventTime();
        if (current != null && (action == MotionEvent.ACTION_DOWN
                || action == MotionEvent.ACTION_MOVE
                || action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL)) {
            if (action != MotionEvent.ACTION_MOVE) invalidateLegacyPick();
            current.acceptPointerEvent(action, x, y, time, "miui",
                    action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                            ? PortalHooks::replayDeferredHostCalls : null);
        }
        if (press == null) return;
        int pointerCount = event.getPointerCount();
        if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) && pointerCount == 1) {
            press.onPointerEvent(action, x, y, time);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            press.onContactEvent(action, x, y,
                    pointerCount > 1 ? event.getRawX(1) : Float.NaN,
                    pointerCount > 1 ? event.getRawY(1) : Float.NaN,
                    pointerCount, time);
            press.onPointerEvent(action, x, y, time);
        } else if (pointerCount > 1) {
            press.onContactEvent(action, x, y, event.getRawX(1), event.getRawY(1),
                    pointerCount, time);
        }
    }

    private static void configureThumbCapture(Context context, DragShareSettings settings) {
        try {
            ThumbTriggerSettings thumb = ThumbTriggerSettings.readFromProvider(context);
            if (thumbCaptureSource != null && thumb.enabled) {
                thumbCaptureSource.updateSettings(settings);
                return;
            }
            PressCaptureSource previous = thumbCaptureSource;
            thumbCaptureSource = null;
            if (previous != null) previous.stop();
            if (thumb.enabled) {
                invalidateLegacyPick();
                thumbCaptureSource = new PressCaptureSource(
                        context, rootTouchSource, controller, settings);
                log("reference press trigger enabled; no calibration required");
                if (appIdleHookError != null) {
                    new Handler(Looper.getMainLooper()).post(() -> android.widget.Toast.makeText(context,
                            appIdleHookError, android.widget.Toast.LENGTH_LONG).show());
                }
            }
            updateHostIdleTimer(thumb.enabled);
        } catch (SecurityException | IllegalStateException error) {
            log("cannot load press trigger settings", error);
        }
    }

    private static void updateHostIdleTimer(boolean pressEnabled) {
        android.app.Service service = portalService;
        if (service == null) return;
        try {
            XposedHelpers.callMethod(service, pressEnabled ? "cancelStopSelfJob" : "scheduleStopSelfJob");
        } catch (RuntimeException | LinkageError | XposedHelpers.InvocationTargetError error) {
            log("cannot update Portal idle job", error);
            new Handler(Looper.getMainLooper()).post(() -> android.widget.Toast.makeText(service,
                    "传送门空闲管理不兼容，按压入口可能随服务退出", android.widget.Toast.LENGTH_LONG).show());
        }
    }

    private static boolean deferHostCallIfRootDragActive(
            XC_MethodHook.MethodHookParam param,
            String kind) {
        synchronized (DEFERRED_HOST_LOCK) {
            DragShareController current = controller;
            if (current == null || !current.isAwaitingRootRelease() || !hasReadyRootSource()) {
                return false;
            }
            for (DeferredHostCall existing : DEFERRED_HOST_CALLS) {
                if (kind.equals(existing.kind)) {
                    return true;
                }
            }
            Object[] args = param.args == null ? new Object[0] : param.args.clone();
            DEFERRED_HOST_CALLS.addLast(
                    new DeferredHostCall(kind, param.method, param.thisObject, args));
        }
        log("deferred host call=" + kind + " until root ACTION_UP");
        return true;
    }

    private static void replayDeferredHostCalls() {
        ArrayList<DeferredHostCall> calls;
        synchronized (DEFERRED_HOST_LOCK) {
            if (DEFERRED_HOST_CALLS.isEmpty()) {
                return;
            }
            calls = new ArrayList<>(DEFERRED_HOST_CALLS);
            DEFERRED_HOST_CALLS.clear();
        }

        REPLAYING_HOST_CALL.set(Boolean.TRUE);
        try {
            for (DeferredHostCall call : calls) {
                try {
                    XposedBridge.invokeOriginalMethod(call.method, call.receiver, call.args);
                    log("replayed host call=" + call.kind);
                } catch (Throwable error) {
                    log("unable to replay host call=" + call.kind, error);
                }
            }
        } finally {
            REPLAYING_HOST_CALL.remove();
        }
    }

    private static void clearDeferredHostCalls() {
        synchronized (DEFERRED_HOST_LOCK) {
            DEFERRED_HOST_CALLS.clear();
        }
    }

    private static boolean isReplayingHostCall() {
        return Boolean.TRUE.equals(REPLAYING_HOST_CALL.get());
    }

    private static String readXposedBridgeVersion() {
        try {
            Method method = XposedBridge.class.getMethod("getXposedVersion");
            Object value = method.invoke(null);
            return String.valueOf(value);
        } catch (Throwable error) {
            return "unavailable:" + error.getClass().getSimpleName();
        }
    }

    private static final class DeferredHostCall {
        final String kind;
        final Member method;
        final Object receiver;
        final Object[] args;

        DeferredHostCall(String kind, Member method, Object receiver, Object[] args) {
            this.kind = kind;
            this.method = method;
            this.receiver = receiver;
            this.args = args;
        }
    }

    private static void log(String message) {
        DragShareLog.i(TAG, message);
    }

    private static void log(String message, Throwable error) {
        DragShareLog.w(TAG, message, error);
    }
}
