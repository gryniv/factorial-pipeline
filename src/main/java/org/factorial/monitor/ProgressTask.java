package org.factorial.monitor;

import org.factorial.model.Messages;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

public record ProgressTask(
        AtomicInteger linesRead,
        AtomicInteger submitted,
        AtomicInteger completed,
        AtomicInteger errorsCount,
        ThreadPoolExecutor workers,
        int intervalMs
) implements Runnable {
    @Override public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                printProgress();
                Thread.sleep(Math.max(50, intervalMs));
            }
        } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
    private void printProgress() {
        System.out.printf(
                "Progress | read %d | submitted %d | completed %d | errors %d (pool active %d, done %d)\r",
                linesRead.get(), submitted.get(), completed.get(), errorsCount.get(),
                workers.getActiveCount(), workers.getCompletedTaskCount()
        );
        System.out.flush();
    }
}