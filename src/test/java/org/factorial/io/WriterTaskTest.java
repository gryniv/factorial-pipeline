package org.factorial.io;

import org.factorial.model.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WriterTask tests: ordering, message type precedence, and no-op types")
class WriterTaskTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("Writes RESULT messages in index order")
    void writesResultsInOrder() throws Exception {
        Path out = tmp.resolve("out1.txt");
        BlockingQueue<Messages.Msg> q = new ArrayBlockingQueue<>(10);

        Thread t = new Thread(new WriterTask(out, q), "writer-test-1");
        t.start();

        q.put(Messages.value(0, 3, new BigInteger("6")));
        q.put(Messages.value(1, 4, new BigInteger("24")));
        q.put(Messages.poison());

        t.join(3000);
        List<String> lines = Files.readAllLines(out);

        assertEquals(List.of("3 = 6", "4 = 24"), lines);
    }

    @Test
    @DisplayName("When same index is not involved, RAW_ERROR at index 0 is written before RESULT at index 1")
    void prefersResultOverRawErrorForSameIndex() throws Exception {
        Path out = tmp.resolve("out2.txt");
        BlockingQueue<Messages.Msg> q = new ArrayBlockingQueue<>(10);

        Thread t = new Thread(new WriterTask(out, q), "writer-test-2");
        t.start();

        
        q.put(Messages.raw(0, "bad"));
        q.put(Messages.value(1, 5, new BigInteger("120")));
        q.put(Messages.poison());

        t.join(3000);
        List<String> lines = Files.readAllLines(out);

        assertEquals(2, lines.size());
        assertEquals("bad", lines.get(0));
        assertEquals("5 = 120", lines.get(1));
    }

    @Test
    @DisplayName("Writes RAW_ERROR line when no RESULT arrives for that index")
    void writesRawErrorWhenNoResultComes() throws Exception {
        Path out = tmp.resolve("out3.txt");
        BlockingQueue<Messages.Msg> q = new ArrayBlockingQueue<>(10);

        Thread t = new Thread(new WriterTask(out, q), "writer-test-3");
        t.start();

        q.put(Messages.raw(0, "not-a-number"));
        q.put(Messages.poison());

        t.join(3000);
        List<String> lines = Files.readAllLines(out);

        assertEquals(1, lines.size());
        assertEquals("not-a-number", lines.get(0));
    }

    @Test
    @DisplayName("Writes ERROR_MSG content verbatim to the output")
    void writesErrorMsgType() throws Exception {
        Path out = tmp.resolve("out4.txt");
        BlockingQueue<Messages.Msg> q = new ArrayBlockingQueue<>(10);

        Thread t = new Thread(new WriterTask(out, q), "writer-test-4");
        t.start();

        q.put(new Messages.Msg(0, Messages.Type.ERROR_MSG, null, null, null, "explanation"));
        q.put(Messages.poison());

        t.join(3000);
        List<String> lines = Files.readAllLines(out);

        assertEquals(1, lines.size());
        assertEquals("explanation", lines.get(0));
    }

    @Test
    @DisplayName("SKIP and POISON messages produce no output lines")
    void skipAndPoisonProduceNoOutput() throws Exception {
        Path out = tmp.resolve("out5.txt");
        BlockingQueue<Messages.Msg> q = new ArrayBlockingQueue<>(10);

        Thread t = new Thread(new WriterTask(out, q), "writer-test-5");
        t.start();

        q.put(Messages.skip(0));
        q.put(Messages.poison());

        t.join(3000);
        List<String> lines = Files.readAllLines(out);

        assertTrue(lines.isEmpty(), "SKIP and POISON should not produce any output");
    }

    @Test
    @DisplayName("Out-of-order indices are written in correct ascending index order")
    void writesMultipleOutOfOrderIndexesInCorrectOrder() throws Exception {
        Path out = tmp.resolve("out6.txt");
        BlockingQueue<Messages.Msg> q = new ArrayBlockingQueue<>(10);

        Thread t = new Thread(new WriterTask(out, q), "writer-test-6");
        t.start();

        
        q.put(Messages.value(2, 7, new BigInteger("5040")));
        q.put(Messages.value(0, 3, new BigInteger("6")));
        q.put(Messages.value(1, 4, new BigInteger("24")));
        q.put(Messages.poison());

        t.join(3000);
        List<String> lines = Files.readAllLines(out);

        assertEquals(List.of("3 = 6", "4 = 24", "7 = 5040"), lines);
    }
}
