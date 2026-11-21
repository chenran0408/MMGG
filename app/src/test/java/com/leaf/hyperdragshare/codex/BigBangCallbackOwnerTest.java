package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BigBangCallbackOwnerTest {
    @Test public void closeInvalidatesCallbacksAlreadyQueued() {
        BigBangCallbackOwner owner = new BigBangCallbackOwner();
        BigBangCallbackOwner.Token callback = owner.token();

        owner.close();

        assertFalse(owner.owns(callback));
    }

    @Test public void replacingContentInvalidatesOnlyPreviousGeneration() {
        BigBangCallbackOwner owner = new BigBangCallbackOwner();
        BigBangCallbackOwner.Token oldCallback = owner.token();

        owner.advance();
        BigBangCallbackOwner.Token currentCallback = owner.token();

        assertFalse(owner.owns(oldCallback));
        assertTrue(owner.owns(currentCallback));
    }

    @Test public void tokenFromAnotherOwnerIsRejected() {
        BigBangCallbackOwner first = new BigBangCallbackOwner();
        BigBangCallbackOwner second = new BigBangCallbackOwner();

        assertFalse(second.owns(first.token()));
    }
}
