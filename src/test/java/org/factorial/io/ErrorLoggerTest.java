package org.factorial.io;

import org.factorial.model.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ErrorLogger tests: file and inline modes, concurrency, and flushing")
class ErrorLoggerTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("toFile(): writes plain logs and formatted error lines to disk")
    void toFile_writesLogAndErrorLine() throws Exception {
        Path errors = tmp.resolve("errors.txt");
        try (var logger = ErrorLogger.toFile(errors)) {
            logger.log("simple error");
            logger.logErrorLine(0, "abc", "not int");
        }
        String text = Files.readString(errors);
        assertTrue(text.contains("simple error"));
        assertTrue(text.contains("Line 1: [abc] -> not int"));
    }

    @Test
    @DisplayName("inline(): enqueues RAW_ERROR into the provided queue only (no file I/O)")
    void inline_putsRawErrorIntoQueue_only() throws Exception {
        BlockingQueue<Messages.Msg> q = new ArrayBlockingQueue<>(10);
        var logger = ErrorLogger.inline(q);

        logger.logErrorLine(2, "oops", "ignored");

        var msg = q.poll(500, TimeUnit.MILLISECONDS);
        assertNotNull(msg, "Expected message in queue");
        assertEquals(Messages.Type.RAW_ERROR, msg.type());
        assertEquals(2, msg.index());
        assertEquals("oops", msg.rawLine());
    }

    @Test
    @DisplayName("toFile(): creates missing parent directories before opening the log file")
    void toFile_createsMissingDirectories() throws Exception {
        Path nestedDir = tmp.resolve("a/b/c");
        Path errors = nestedDir.resolve("errors.log");
        assertFalse(Files.exists(nestedDir));

        try (var logger = ErrorLogger.toFile(errors)) {
            logger.log("created with parents");
        }
        assertTrue(Files.exists(nestedDir), "Parent directories must be created");
        assertTrue(Files.exists(errors), "Errors file must exist");

        String text = Files.readString(errors);
        assertTrue(text.contains("created with parents"));
    }

    @Test
    @DisplayName("toFile(): thread-safe under concurrent logging of log() and logErrorLine()")
    void toFile_concurrentLoggingIsThreadSafe() throws Exception {
        Path errors = tmp.resolve("concurrent.txt");
        int N = 50;

        try (var logger = ErrorLogger.toFile(errors)) {
            ExecutorService pool = Executors.newFixedThreadPool(8);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done  = new CountDownLatch(N);

            for (int i = 0; i < N; i++) {
                final int idx = i;
                pool.submit(() -> {
                    try {
                        start.await();
                        logger.log("concurrent-" + idx);
                        logger.logErrorLine(idx, "bad" + idx, "ex");
                    } catch (Exception e) {
                        fail("No exception expected: " + e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS), "Workers must finish");
            pool.shutdownNow();
        }

        List<String> lines = Files.readAllLines(errors);
        long logCount = lines.stream().filter(s -> s.contains("concurrent-")).count();
        long formatted = lines.stream().filter(s -> s.startsWith("Line ")).count();
        assertEquals(50, logCount);
        assertEquals(50, formatted);
        assertTrue(lines.size() >= 100);
    }

    @Test
    @DisplayName("toFile(): all buffered content is flushed to disk on close()")
    void toFile_flushOnClose() throws Exception {
        Path errors = tmp.resolve("flush.txt");
        try (var logger = ErrorLogger.toFile(errors)) {
            for (int i = 0; i < 5; i++) {
                logger.log("e" + i);
            }
        }
        String text = Files.readString(errors);
        for (int i = 0; i < 5; i++) {
            assertTrue(text.contains("e" + i));
        }
    }

    @Test
    @DisplayName("inline(): preserves FIFO order of multiple RAW_ERROR entries")
    void inline_multipleErrorsPreserveOrder() throws Exception {
        BlockingQueue<Messages.Msg> q = new ArrayBlockingQueue<>(10);
        var logger = ErrorLogger.inline(q);

        logger.logErrorLine(0, "a", "x");
        logger.logErrorLine(1, "b", "x");
        logger.logErrorLine(2, "c", "x");

        assertEquals("a", q.take().rawLine());
        assertEquals("b", q.take().rawLine());
        assertEquals("c", q.take().rawLine());
    }

    @Test
    @DisplayName("toFile(): close() can be called (auto-closeable) without throwing")
    void closeDoesNotThrowWhenCalledOnce() {
        Path errors = tmp.resolve("once.txt");
        assertDoesNotThrow(() -> {
            try (var logger = ErrorLogger.toFile(errors)) {
                logger.log("ok");
            }
        });
    }
}
