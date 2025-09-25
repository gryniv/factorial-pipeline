package org.factorial.compute;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    @DisplayName("Respects approximate rate: elapsed time ~ (permits - 1) / rate")
    void respectsApproximateRate() {
        int rate = 10;
        int permits = 5;
        var rl = new RateLimiter(rate);

        long t0 = System.nanoTime();
        for (int i = 0; i < permits; i++) rl.acquire();
        double elapsedSec = (System.nanoTime() - t0) / 1_000_000_000.0;

        double expected = (permits - 1) / (double) rate;
        assertTrue(elapsedSec >= expected * 0.8 && elapsedSec <= expected * 1.8,
                () -> "elapsed=" + elapsedSec + "s not within bounds around " + expected + "s");
    }

    @Test
    @DisplayName("First acquire should be nearly immediate (no waiting)")
    void firstAcquireIsImmediate() {
        var rl = new RateLimiter(5); 
        long t0 = System.nanoTime();
        rl.acquire();
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(elapsedMs < 5, "First acquire should be nearly immediate");
    }

    @Test
    @DisplayName("Multiple acquires introduce expected delay between permits")
    void multipleAcquiresIntroduceDelay() {
        int rate = 2; 
        var rl = new RateLimiter(rate);

        rl.acquire(); 
        long t0 = System.nanoTime();
        rl.acquire(); 
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        assertTrue(elapsedMs >= 400 && elapsedMs <= 700,
                "Second acquire should be delayed ~500ms, got " + elapsedMs + "ms");
    }

    @Test
    @DisplayName("Very high rate behaves almost like no throttling")
    void highRateBehavesLikeNoThrottle() {
        var rl = new RateLimiter(100_000); 
        long t0 = System.nanoTime();
        for (int i = 0; i < 50; i++) {
            rl.acquire();
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(elapsedMs < 20, "High rate limiter should not introduce noticeable delay");
    }

    @Test
    @DisplayName("After pause longer than interval, acquire should be immediate again")
    void rateLimiterIsReusableAfterPause() throws InterruptedException {
        var rl = new RateLimiter(5); 
        rl.acquire();
        rl.acquire();

        
        Thread.sleep(500);

        long t0 = System.nanoTime();
        rl.acquire(); 
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(elapsedMs < 50, "Acquire after pause should be immediate, got " + elapsedMs + "ms");
    }

    @Test
    @DisplayName("close() and shutdown() should not throw exceptions")
    void closeAndShutdownDoNotThrow() {
        var rl = new RateLimiter(10);
        assertDoesNotThrow(() -> {
            rl.close();
            rl.shutdown();
        });
    }
}
