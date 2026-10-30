package com.leaf.hyperdragshare.gestures.xposed;

/** Tracks what the bridge has observed about its most recent launch request. */
final class FreeformLaunchEvidencePolicy {
    enum Stage {
        QUEUED,
        REQUESTED,
        OBSERVED,
        UNKNOWN
    }

    private long generation;
    private Stage stage = Stage.UNKNOWN;

    synchronized long queued() {
        stage = Stage.QUEUED;
        return ++generation;
    }

    synchronized long requested() {
        stage = Stage.REQUESTED;
        return ++generation;
    }

    synchronized boolean observed(long requestGeneration) {
        if (requestGeneration != generation || stage != Stage.REQUESTED) return false;
        stage = Stage.OBSERVED;
        return true;
    }

    synchronized boolean unknown(long requestGeneration) {
        if (requestGeneration != generation || stage == Stage.OBSERVED) return false;
        stage = Stage.UNKNOWN;
        return true;
    }

    synchronized Stage stage() {
        return stage;
    }
}
