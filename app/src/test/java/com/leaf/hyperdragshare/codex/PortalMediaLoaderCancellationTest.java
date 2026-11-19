package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class PortalMediaLoaderCancellationTest {
    @Test public void cancelsAnActiveReadByClosingTheStream() throws Exception {
        CountDownLatch enteredRead = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        InputStream blocking = new InputStream() {
            @Override public int read() throws IOException {
                enteredRead.countDown();
                try {
                    if (!closed.await(5, TimeUnit.SECONDS)) throw new AssertionError("Stream remained open");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Cancellation relied solely on interrupt", interrupted);
                }
                throw new IOException("closed");
            }
            @Override public void close() { closed.countDown(); }
        };
        PortalMediaLoader.Cancellation cancellation = new PortalMediaLoader.Cancellation();
        var result = new FutureTask<>(() -> PortalMediaLoader.loadStream(blocking, cancellation));
        Thread worker = new Thread(result, "test-original-read");
        worker.start();
        try {
            assertTrue(enteredRead.await(5, TimeUnit.SECONDS));
            cancellation.cancel(Runnable::run);
            ExecutionException error = assertThrows(ExecutionException.class, () -> result.get(1, TimeUnit.SECONDS));
            assertTrue(error.getCause() instanceof IOException);
            assertEquals(0, closed.getCount());
        } finally {
            blocking.close();
            worker.join();
        }
    }

    @Test public void cancellationBeforeAnOpenDoesNotEnterTheStream() {
        AtomicInteger reads = new AtomicInteger();
        InputStream notOpened = new InputStream() {
            @Override public int read() { reads.incrementAndGet(); return -1; }
        };
        PortalMediaLoader.Cancellation cancellation = new PortalMediaLoader.Cancellation();
        cancellation.cancel(Runnable::run);

        assertThrows(InterruptedIOException.class, () -> PortalMediaLoader.loadStream(notOpened, cancellation));
        assertEquals(0, reads.get());
    }
}
