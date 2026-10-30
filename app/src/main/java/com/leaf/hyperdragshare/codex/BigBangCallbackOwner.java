package com.leaf.hyperdragshare.codex;

/** Invalidates callbacks when a panel closes or replaces its current content. */
final class BigBangCallbackOwner {
    static final class Token {
        private final long generation;

        private Token(long generation) {
            this.generation = generation;
        }
    }

    private long generation;
    private boolean closed;

    Token token() {
        return new Token(generation);
    }

    void advance() {
        generation++;
    }

    void close() {
        closed = true;
        generation++;
    }

    boolean owns(Token token) {
        return !closed && token != null && token.generation == generation;
    }
}
