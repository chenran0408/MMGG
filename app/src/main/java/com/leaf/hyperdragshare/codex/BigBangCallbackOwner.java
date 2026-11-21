package com.leaf.hyperdragshare.codex;

/** Invalidates callbacks when a panel closes or replaces its current content. */
final class BigBangCallbackOwner {
    static final class Token {
        private final BigBangCallbackOwner owner;
        private final long generation;

        private Token(BigBangCallbackOwner owner, long generation) {
            this.owner = owner;
            this.generation = generation;
        }
    }

    private long generation;
    private boolean closed;

    Token token() {
        return new Token(this, generation);
    }

    void advance() {
        generation++;
    }

    void close() {
        closed = true;
        generation++;
    }

    boolean owns(Token token) {
        return !closed && token != null && token.owner == this && token.generation == generation;
    }
}
