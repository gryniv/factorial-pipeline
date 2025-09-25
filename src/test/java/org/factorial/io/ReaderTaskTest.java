package org.factorial.io;

import org.factorial.compute.FactorialService;
import org.factorial.compute.RateLimiter;
import org.factorial.config.AppConfig;
import org.factorial.model.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReaderTask tests: parsing, follow mode, and rejection handling")
class ReaderTaskTest {

    @TempDir
    Path tmp;

    private static ThreadPoolExecutor fixedPool(int nThreads) {
        return (ThreadPoolExecutor) Executors.newFixedThreadPool(nThreads);
    }

    private static AppConfig cfgWithPaths(Path in, Path out, Path err) {
        return AppConfig.withPaths(in, out, err);
    }

    @Test
    @DisplayName("Parses valid/invalid/empty lines → emits RESULT, RAW_ERROR, and SKIP; updates counters")
    void readsValidInvalidAndEmptyLines_producesResultsRawErrorsAndSkips() throws Exception {
        Path in = tmp.resolve("input.txt");
        Path out = tmp.resolve("out.txt");
        Path err = tmp.resolve("err.txt");


        Files.writeString(
                in,
                String.join(System.lineSeparator(), List.of("3", "x", "5", "-1", "7", "  ")),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
        );

        BlockingQueue<Messages.Msg> q = new LinkedBlockingQueue<>();
        ErrorLogger logger = ErrorLogger.inline(q);
        RateLimiter limiter = new RateLimiter(10_000);

        ThreadPoolExecutor workers = fixedPool(2);
        Semaphore slots = new Semaphore(2, true);

        AtomicInteger linesRead = new AtomicInteger();
        AtomicInteger submitted  = new AtomicInteger();
        AtomicInteger completed  = new AtomicInteger();
        AtomicInteger errors     = new AtomicInteger();

        var factorialService = new FactorialService(cfgWithPaths(in, out, err));

        var task = new ReaderTask(
                in, q, logger, limiter, workers,
                linesRead, submitted, completed, errors,
                slots, factorialService, 200 
        );

        Thread t = new Thread(task, "reader-test-1");
        t.start();
        t.join(3000);

        int resultsCnt = 0, rawErrCnt = 0, skipCnt = 0;
        BigInteger f3 = null, f5 = null, f7 = null;

        long until = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < until) {
            Messages.Msg m = q.poll(50, TimeUnit.MILLISECONDS);
            if (m == null) continue;
            switch (m.type()) {
                case RESULT -> {
                    resultsCnt++;
                    if ("3".equals(m.value())) f3 = m.factorial();
                    if ("5".equals(m.value())) f5 = m.factorial();
                    if ("7".equals(m.value())) f7 = m.factorial();
                }
                case RAW_ERROR -> rawErrCnt++;
                case SKIP -> skipCnt++;
                default -> {}
            }
            if (resultsCnt >= 3 && rawErrCnt >= 3 && skipCnt >= 3) break;
        }

        assertEquals(new BigInteger("6"),    f3);
        assertEquals(new BigInteger("120"),  f5);
        assertEquals(new BigInteger("5040"), f7);

        assertEquals(3, resultsCnt, "Expected 3 valid lines");
        assertEquals(3, rawErrCnt,  "Expected RAW_ERROR for x, -1, and empty");
        assertEquals(3, skipCnt,    "Expected SKIP placeholders for invalid lines");

        assertEquals(6, linesRead.get());
        assertEquals(3, submitted.get());
        assertEquals(3, completed.get());
        assertEquals(3, errors.get());

        workers.shutdownNow();
    }

    @Test
    @DisplayName("Follow mode: reads appended lines until idle timeout")
    void followsAppendedLines_untilIdleTimeout() throws Exception {
        Path in = tmp.resolve("tail.txt");
        Path out = tmp.resolve("out.txt");
        Path err = tmp.resolve("err.txt");

        Files.writeString(in, "3\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        BlockingQueue<Messages.Msg> q = new LinkedBlockingQueue<>();
        ErrorLogger logger = ErrorLogger.inline(q);
        RateLimiter limiter = new RateLimiter(10_000);

        ThreadPoolExecutor workers = fixedPool(2);
        Semaphore slots = new Semaphore(2, true);

        AtomicInteger linesRead = new AtomicInteger();
        AtomicInteger submitted  = new AtomicInteger();
        AtomicInteger completed  = new AtomicInteger();
        AtomicInteger errors     = new AtomicInteger();

        var factorialService = new FactorialService(cfgWithPaths(in, out, err));

        
        var task = new ReaderTask(
                in, q, logger, limiter, workers,
                linesRead, submitted, completed, errors,
                slots, factorialService, 1000
        );

        Thread t = new Thread(task, "reader-test-2");
        t.start();

        
        Thread.sleep(150);
        Files.writeString(in, "4\nx\n", StandardOpenOption.APPEND);

        t.join(4000);

        boolean has3=false, has4=false, hasX=false;
        long until = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < until && !(has3 && has4 && hasX)) {
            Messages.Msg m = q.poll(50, TimeUnit.MILLISECONDS);
            if (m == null) continue;
            if (m.type() == Messages.Type.RESULT) {
                if ("3".equals(m.value())) has3 = true;
                if ("4".equals(m.value())) has4 = true;
            } else if (m.type() == Messages.Type.RAW_ERROR) {
                if ("x".equals(m.rawLine())) hasX = true;
            }
        }

        assertTrue(has3, "must contain factorial(3)");
        assertTrue(has4, "must contain factorial(4)");
        assertTrue(hasX, "must contain raw error for 'x'");

        workers.shutdownNow();
    }

    @Test
    @DisplayName("RejectedExecutionException: emits SKIP and increments error counter")
    void rejectedExecution_producesSkipAndError() throws Exception {
        Path in = tmp.resolve("rejected.txt");
        Path out = tmp.resolve("out.txt");
        Path err = tmp.resolve("err.txt");
        Files.writeString(in, "3\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        BlockingQueue<Messages.Msg> q = new LinkedBlockingQueue<>();
        ErrorLogger logger = ErrorLogger.inline(q);
        RateLimiter limiter = new RateLimiter(10_000);

        ThreadPoolExecutor workers = fixedPool(1);
        
        workers.shutdown();

        Semaphore slots = new Semaphore(1, true);

        AtomicInteger linesRead = new AtomicInteger();
        AtomicInteger submitted  = new AtomicInteger();
        AtomicInteger completed  = new AtomicInteger();
        AtomicInteger errors     = new AtomicInteger();

        var factorialService = new FactorialService(cfgWithPaths(in, out, err));

        var task = new ReaderTask(
                in, q, logger, limiter, workers,
                linesRead, submitted, completed, errors,
                slots, factorialService, 200
        );

        Thread t = new Thread(task, "reader-test-3");
        t.start();
        t.join(2000);

        boolean sawSkip = false;
        long until = System.currentTimeMillis() + 1000;
        while (System.currentTimeMillis() < until && !sawSkip) {
            Messages.Msg m = q.poll(50, TimeUnit.MILLISECONDS);
            if (m == null) continue;
            if (m.type() == Messages.Type.SKIP) sawSkip = true;
        }

        assertTrue(sawSkip, "Expected SKIP due to RejectedExecutionException");
        assertEquals(1, errors.get(), "Error counter must increment on rejection");

        workers.shutdownNow();
    }
}
