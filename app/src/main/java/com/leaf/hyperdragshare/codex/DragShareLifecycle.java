package com.leaf.hyperdragshare.codex;

/**
 * Main-thread lifecycle tokens for a drag session and its asynchronous overlay exits.
 *
 * <p>The controller still owns the windows and payloads. This class only answers whether a
 * callback belongs to the current session or to the currently exiting preview.</p>
 */
final class DragShareLifecycle {
    static final class SessionToken {
        private final long generation;

        private SessionToken(long generation) {
            this.generation = generation;
        }
    }

    static final class PreviewExitToken {
        private final long generation;

        private PreviewExitToken(long generation) {
            this.generation = generation;
        }
    }

    private long sessionGeneration;
    private SessionToken currentSession;
    private long previewExitGeneration;
    private PreviewExitToken currentPreviewExit;
    private boolean destroyed;

    SessionToken beginSession() {
        invalidateSession();
        if (destroyed) {
            return null;
        }
        SessionToken token = new SessionToken(++sessionGeneration);
        currentSession = token;
        return token;
    }

    void invalidateSession() {
        sessionGeneration++;
        currentSession = null;
    }

    boolean isCurrent(SessionToken token) {
        return !destroyed && token != null && token == currentSession;
    }

    long version() {
        return sessionGeneration;
    }

    boolean isVersionCurrent(long version) {
        return !destroyed && version == sessionGeneration;
    }

    PreviewExitToken beginPreviewExit() {
        PreviewExitToken token = new PreviewExitToken(++previewExitGeneration);
        currentPreviewExit = token;
        return token;
    }

    boolean isCurrent(PreviewExitToken token) {
        return !destroyed && token != null && token == currentPreviewExit;
    }

    void cancelPreviewExit() {
        previewExitGeneration++;
        currentPreviewExit = null;
    }

    void destroy() {
        destroyed = true;
        invalidateSession();
        cancelPreviewExit();
    }
}
