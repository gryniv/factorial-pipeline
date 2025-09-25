
package org.factorial.io;

import org.factorial.compute.FactorialService;
import org.factorial.compute.RateLimiter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

public record ReaderTask(
        Path inputPath,
        BlockingQueue<org.factorial.model.Messages.Msg> resultsQueue,
        ErrorLogger errorLogger,
        RateLimiter limiter,
        ThreadPoolExecutor workers,
        AtomicInteger linesRead,
        AtomicInteger submitted,
        AtomicInteger completed,
        AtomicInteger errorsCount,
        Semaphore slots,
        FactorialService factorialService,
        int followIdleMs
) implements Runnable {

    private static final int POLL_MS = 200;

    @Override public void run() {
        try { readWithFollow(); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        catch (IOException ioe) { errorLogger.log("I/O error in reader: " + ioe.getMessage()); }
    }

    private void readWithFollow() throws IOException, InterruptedException {
        try (var raf = new java.io.RandomAccessFile(inputPath.toFile(), "r")) {
            long pos = 0, idleMs = 0; int idx = 0;
            while (true) {
                raf.seek(pos);
                String line; boolean any = false;
                while ((line = raf.readLine()) != null) {
                    any = true; processLine(idx++, line); pos = raf.getFilePointer();
                }
                if (any) { idleMs = 0; continue; }
                Thread.sleep(POLL_MS);
                long len = raf.length();
                if (len > pos) { idleMs = 0; continue; }
                if (len < pos) { pos = 0; idleMs = 0; continue; }
                idleMs += POLL_MS; if (idleMs >= Math.max(0, followIdleMs)) break;
            }
        }
    }

    private void processLine(int idx, String originalLine) throws InterruptedException {
        linesRead.incrementAndGet();
        final String s = originalLine.trim();
        if (s.isEmpty()) { handleInvalidLine(idx, originalLine, "empty/whitespace line"); return; }
        try {
            int v = Integer.parseInt(s);
            if (v < 0) { handleInvalidLine(idx, originalLine, "negative number not allowed (" + s + ")"); return; }
            submitFactorialTask(idx, v);
        } catch (NumberFormatException nfe) {
            handleInvalidLine(idx, originalLine, "not a valid integer (" + s + ")");
        }
    }

    private void handleInvalidLine(int idx, String originalLine, String message) throws InterruptedException {
        try { errorLogger.logErrorLine(idx, originalLine, message); }
        catch (IOException ioe) { errorLogger.log("I/O error writing error line: " + ioe.getMessage()); }
        errorsCount.incrementAndGet();
        resultsQueue.put(org.factorial.model.Messages.skip(idx));
    }

    private void submitFactorialTask(int idx, int value) throws InterruptedException {
        slots.acquire();
        try {
            workers.submit(() -> {
                try {
                    limiter.acquire();
                    var fact = factorialService.factorial(value);
                    resultsQueue.put(org.factorial.model.Messages.value(idx, value, fact));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } finally {
                    completed.incrementAndGet();
                    slots.release();
                }
            });
            submitted.incrementAndGet();
        } catch (RejectedExecutionException rex) {
            resultsQueue.put(org.factorial.model.Messages.skip(idx));
            errorsCount.incrementAndGet();
            slots.release();
        }
    }
}

