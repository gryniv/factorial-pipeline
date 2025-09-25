package org.factorial.compute;


import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;


public final class RateLimiter implements AutoCloseable {
    private final long nanosPerPermit;
    private final AtomicLong nextFreeNanos;


    public RateLimiter(int permitsPerSecond) {
        this.nanosPerPermit = TimeUnit.SECONDS.toNanos(1) / Math.max(1, permitsPerSecond);
        this.nextFreeNanos = new AtomicLong(System.nanoTime());
    }


    public void acquire() {
        for (;;) {
            long now = System.nanoTime();
            long prev = nextFreeNanos.get();
            long target = Math.max(prev, now);
            long next = target + nanosPerPermit;
            if (nextFreeNanos.compareAndSet(prev, next)) {
                long waitNanos = target - now;
                if (waitNanos > 0) LockSupport.parkNanos(waitNanos);
                return;
            }
        }
    }


    @Override public void close() { }
    public void shutdown() { }
}