
package org.factorial.io;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.BlockingQueue;

public final class ErrorLogger implements Closeable {
    private final PrintWriter out;
    private final Object lock = new Object();
    private final BlockingQueue<org.factorial.model.Messages.Msg> outQueue;
    private final boolean inlineMode;

    private ErrorLogger(PrintWriter out, BlockingQueue<org.factorial.model.Messages.Msg> outQueue, boolean inlineMode) {
        this.out = out;
        this.outQueue = outQueue;
        this.inlineMode = inlineMode;
    }

    public static ErrorLogger toFile(Path errorsPath) throws IOException {
        Files.createDirectories(errorsPath.toAbsolutePath().normalize().getParent());
        BufferedWriter bw = Files.newBufferedWriter(
                errorsPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
        return new ErrorLogger(new PrintWriter(bw), null, false);
    }

    public static ErrorLogger inline(BlockingQueue<org.factorial.model.Messages.Msg> outQueue) {
        return new ErrorLogger(null, outQueue, true);
    }

    public void log(String msg) {
        System.err.println(msg);
        if (!inlineMode) {
            synchronized (lock) {
                out.println(msg);
                out.flush();
            }
        }
    }

    public void logErrorLine(int index, String originalLine, String explanation)
            throws IOException, InterruptedException {
        if (inlineMode) {
            outQueue.put(org.factorial.model.Messages.raw(index, originalLine));
        } else {
            String msg = "Line " + (index + 1) + ": [" + originalLine + "] -> " + explanation;
            synchronized (lock) {
                out.println(msg);
                out.flush();
            }
            System.err.println(msg);
        }
    }

    @Override public void close() {
        if (!inlineMode) {
            synchronized (lock) {
                out.flush();
                out.close();
            }
        }
    }
}

