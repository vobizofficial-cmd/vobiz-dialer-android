package com.grinch.rivo4.controller.recording

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicInteger

class BoundedRemoteCallTest {
    @Test fun successfulDescriptorIsOwnedByCaller() {
        val disposed = AtomicInteger()
        val descriptor = Any()
        assertSame(descriptor, boundedRemoteCall(1000, { disposed.incrementAndGet() }) { descriptor })
        assertEquals(0, disposed.get())
    }
    @Test fun timeoutDisposesDescriptorArrivingAfterAbandonmentExactlyOnce() {
        val started = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val disposed = CountDownLatch(1)
        val count = AtomicInteger()
        try {
            // 500ms (not 30ms): under parallel lint/R8 load the worker thread may take
            // longer than 30ms to start; timeout must fire while call() is in flight,
            // otherwise the task is cancelled pre-start and no descriptor ever exists.
            boundedRemoteCall(500, disposeLate = { _: Any -> count.incrementAndGet(); disposed.countDown() }) {
                started.countDown()
                // Binder does not respond to Thread.interrupt; simulate its late result.
                while (true) {
                    try { gate.await(); break } catch (_: InterruptedException) { }
                }
                Any()
            }
            fail("Expected bounded timeout")
        } catch (_: TimeoutException) {
            assertTrue("worker never started", started.await(2, TimeUnit.SECONDS))
            gate.countDown()
        }
        assertTrue(disposed.await(2, TimeUnit.SECONDS))
        assertEquals(1, count.get())
    }
    @Test fun failedTransactionPropagatesAndNeverDisposesAnUncreatedDescriptor() {
        val count = AtomicInteger()
        try {
            boundedRemoteCall<Any>(1000, { count.incrementAndGet() }) { throw SecurityException() }
            fail("Expected transaction failure")
        } catch (e: ExecutionException) { assertTrue(e.cause is SecurityException) }
        assertEquals(0, count.get())
    }
}
