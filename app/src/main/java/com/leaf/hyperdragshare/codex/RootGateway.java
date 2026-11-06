package com.leaf.hyperdragshare.codex;

import android.content.Context;

/** Small health wrapper that keeps the root input authority explicit. */
final class RootGateway {
    enum Health { STOPPED, INPUT_DOWN, READY }

    private final RootTouchSource input;
    private volatile boolean started;

    RootGateway(Context context, RootTouchSource.Listener listener,
                RootTouchSource.StateListener stateListener) {
        input = new RootTouchSource(context, listener, stateListener);
    }

    void startInput() {
        started = true;
        input.start();
    }

    void stopInput() {
        started = false;
        input.stop();
    }

    boolean isInputReady() {
        return started && input.isReady();
    }

    Health health() {
        if (!started) return Health.STOPPED;
        return input.isReady() ? Health.READY : Health.INPUT_DOWN;
    }

    RootTouchSource input() {
        return input;
    }
}
