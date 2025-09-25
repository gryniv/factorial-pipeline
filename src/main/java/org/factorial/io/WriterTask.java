package org.factorial.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListMap;

public record WriterTask(Path outputPath, BlockingQueue<org.factorial.model.Messages.Msg> resultsQueue) implements Runnable {

    private static int flushContiguousPrefix(BufferedWriter bw,
                                             ConcurrentSkipListMap<Integer, org.factorial.model.Messages.Msg> pending,
                                             int nextToWrite) throws IOException {
        org.factorial.model.Messages.Msg m;
        while ((m = pending.remove(nextToWrite)) != null) {
            writeOne(bw, m);
            bw.flush();
            nextToWrite++;
        }
        return nextToWrite;
    }

    private static org.factorial.model.Messages.Msg prefer(org.factorial.model.Messages.Msg a, org.factorial.model.Messages.Msg b) {
        return Comparator.comparingInt((org.factorial.model.Messages.Msg m) -> priority(m.type()))
                .reversed()
                .thenComparingInt(org.factorial.model.Messages.Msg::index)
                .compare(a, b) >= 0 ? a : b;
    }

    private static int priority(org.factorial.model.Messages.Type t) {
        return switch (t) {
            case RESULT -> 4;
            case RAW_ERROR -> 3;
            case ERROR_MSG -> 2;
            case SKIP -> 1;
            case POISON -> 0;
        };
    }

    private static void writeOne(BufferedWriter bw, org.factorial.model.Messages.Msg m) throws IOException {
        switch (m.type()) {
            case RESULT -> { bw.write(m.value() + " = " + m.factorial()); bw.newLine(); }
            case RAW_ERROR -> { bw.write(m.rawLine()); bw.newLine(); }
            case ERROR_MSG -> { bw.write(m.errorMsg()); bw.newLine(); }
            case SKIP, POISON -> {  }
        }
    }

    @Override public void run() {
        try (BufferedWriter bw = Files.newBufferedWriter(
                outputPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        )) {
            ConcurrentSkipListMap<Integer, org.factorial.model.Messages.Msg> pending = new ConcurrentSkipListMap<>();
            int nextToWrite = 0;
            while (true) {
                org.factorial.model.Messages.Msg m = resultsQueue.take();
                if (m.type() == org.factorial.model.Messages.Type.POISON) break;
                pending.merge(m.index(), m, WriterTask::prefer);
                nextToWrite = flushContiguousPrefix(bw, pending, nextToWrite);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (IOException ioe) {
            System.err.println("I/O error in writer: " + ioe.getMessage());
        }
    }
}
