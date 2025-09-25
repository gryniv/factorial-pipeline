package org.factorial.pipeline;

import org.factorial.compute.FactorialService;
import org.factorial.compute.RateLimiter;
import org.factorial.config.AppConfig;
import org.factorial.io.ErrorLogger;
import org.factorial.io.ReaderTask;
import org.factorial.io.WriterTask;

import org.factorial.model.Messages;
import org.factorial.monitor.ProgressTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class FactorialPipeline {

    private final int requestedPoolSize;
    private final AppConfig cfg;

    private final AtomicInteger linesRead = new AtomicInteger(0);
    private final AtomicInteger submitted = new AtomicInteger(0);
    private final AtomicInteger completed = new AtomicInteger(0);
    private final AtomicInteger errorsCount = new AtomicInteger(0);

    private BlockingQueue<Messages.Msg> resultsQueue;
    private RateLimiter limiter;

    public FactorialPipeline(int poolSize, AppConfig cfg) {
        this.requestedPoolSize = poolSize;
        this.cfg = cfg;
    }

    private static boolean sameFile(Path a, Path b) {
        if (a == null || b == null) return false;
        try { return Files.isSameFile(a, b); }
        catch (IOException e) { return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize()); }
    }

    public void run() {
        long startNs = System.nanoTime();
        this.resultsQueue = new LinkedBlockingQueue<>();
        this.limiter = new RateLimiter(cfg.ratePerSecond);
        boolean inlineErrors = sameFile(cfg.errorsPath, cfg.outputPath);

        System.out.printf("CLI pool=%d, effective pool=%d, rate=%d/s, inlineErrors=%s%n",
                requestedPoolSize, requestedPoolSize, cfg.ratePerSecond, inlineErrors);

        final FactorialService factorialService = new FactorialService(cfg);

        try (ErrorLogger errorLogger = inlineErrors ? ErrorLogger.inline(resultsQueue) : ErrorLogger.toFile(cfg.errorsPath)) {
            ThreadPoolExecutor workers = createWorkers(requestedPoolSize);
            workers.prestartAllCoreThreads();
            Thread writer = startWriter(cfg.outputPath);
            Semaphore slots = new Semaphore(requestedPoolSize, true);
            Thread progress = startProgress(workers, cfg.progressIntervalMs);
            Thread reader = startReader(cfg.inputPath, errorLogger, workers, slots, factorialService);

            reader.join();
            while (completed.get() < submitted.get()) Thread.sleep(10);

            workers.shutdown();
            limiter.shutdown();

            resultsQueue.put(Messages.poison());
            writer.join();
            if (progress.isAlive()) progress.interrupt();

            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            System.out.printf(
                    "Progress | read %d | submitted %d | completed %d | errors %d | pool %d | rate %d/s | mode=%s | elapsed %s (%d ms)%n",
                    linesRead.get(), submitted.get(), completed.get(), errorsCount.get(),
                    requestedPoolSize, cfg.ratePerSecond,
                    inlineErrors ? "INLINE_TO_OUTPUT" : "SEPARATE_FILE",
                    format(Duration.ofMillis(elapsedMs)), elapsedMs
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            System.err.println("Fatal I/O: " + e.getMessage());
        }
    }

    private static String format(Duration d) {
        long h = d.toHours();
        long m = d.minusHours(h).toMinutes();
        long s = d.minusHours(h).minusMinutes(m).toSeconds();
        long ms = d.minusHours(h).minusMinutes(m).minusSeconds(s).toMillis();
        return String.format("%02d:%02d:%02d.%03d", h, m, s, ms);
    }

    private ThreadPoolExecutor createWorkers(int size) {
        ThreadFactory tf = r -> {
            Thread t = new Thread(r);
            t.setName("worker-" + t.getId());
            t.setDaemon(false);
            return t;
        };
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(size * 4);
        ThreadPoolExecutor exec = new ThreadPoolExecutor(
                size, size,
                0L, TimeUnit.MILLISECONDS,
                queue, tf,
                new ThreadPoolExecutor.AbortPolicy()
        );
        exec.allowCoreThreadTimeOut(false);
        return exec;
    }

    private Thread startWriter(Path out) throws IOException {
        Thread writer = new Thread(new WriterTask(out, resultsQueue), "writer");
        writer.start();
        return writer;
    }

    private Thread startReader(Path in, ErrorLogger logger, ThreadPoolExecutor workers, Semaphore slots, FactorialService factorialService) {
        Thread reader = new Thread(new ReaderTask(
                in, resultsQueue, logger, limiter, workers,
                linesRead, submitted, completed, errorsCount,
                slots, factorialService, 2000
        ), "reader");
        reader.start();
        return reader;
    }

    private Thread startProgress(ThreadPoolExecutor workers, int intervalMs) {
        Thread progress = new Thread(new ProgressTask(
                linesRead, submitted, completed, errorsCount, workers, intervalMs
        ), "progress");
        progress.setDaemon(true);
        progress.start();
        return progress;
    }
}


