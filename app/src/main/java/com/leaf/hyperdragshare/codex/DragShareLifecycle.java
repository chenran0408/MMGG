package com.leaf.hyperdragshare.codex;

/** Main-thread ownership tokens for asynchronous drag callbacks and overlay exits. */
final class DragShareLifecycle {
    static final class SessionToken {
        private SessionToken() {}
    }

    static final class PreviewExitToken {
        private PreviewExitToken() {}
    }

    private SessionToken currentSession;
    private PreviewExitToken currentPreviewExit;
    private boolean destroyed;

    SessionToken beginSession() {
        currentSession = destroyed ? null : new SessionToken();
        return currentSession;
    }

    boolean isCurrent(SessionToken token) {
        return !destroyed && token != null && token == currentSession;
    }

    PreviewExitToken beginPreviewExit() {
        currentPreviewExit = destroyed ? null : new PreviewExitToken();
        return currentPreviewExit;
    }

    boolean isCurrent(PreviewExitToken token) {
        return !destroyed && token != null && token == currentPreviewExit;
    }

    void cancelPreviewExit() {
        currentPreviewExit = null;
    }

    void destroy() {
        destroyed = true;
        currentSession = null;
        currentPreviewExit = null;
    }
}
