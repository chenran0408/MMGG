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

    synchronized Transition queued() {
        return transition(Stage.QUEUED, true);
    }

    synchronized Transition requested() {
        return transition(Stage.REQUESTED, true);
    }

    synchronized Transition observed(long requestGeneration) {
        if (requestGeneration != generation || stage != Stage.REQUESTED) return null;
        return transition(Stage.OBSERVED, false);
    }

    synchronized Transition unknown(long requestGeneration) {
        if (requestGeneration != generation || stage == Stage.OBSERVED) return null;
        return transition(Stage.UNKNOWN, false);
    }

    synchronized Stage stage() {
        return stage;
    }

    private Transition transition(Stage next, boolean newGeneration) {
        Stage previous = stage;
        if (newGeneration) generation++;
        stage = next;
        return new Transition(generation, previous, next);
    }

    static final class Transition {
        final long generation;
        final Stage previous;
        final Stage stage;

        Transition(long generation, Stage previous, Stage stage) {
            this.generation = generation;
            this.previous = previous;
            this.stage = stage;
        }
    }
}
