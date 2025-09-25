package org.factorial.pipeline;

import org.factorial.config.AppConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


@DisplayName("FactorialPipeline: end-to-end behavior across output modes and pool sizes")
class FactorialPipelineTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("Writes valid results to output and logs invalid lines to a separate errors file")
    void run_writesResults_andErrorsToSeparateFile() throws Exception {
        Path in  = tmp.resolve("in.txt");
        Path out = tmp.resolve("out.txt");
        Path err = tmp.resolve("err.txt");

        
        Files.writeString(in, String.join(System.lineSeparator(), "3", "-1", "x", "5"));

        var cfg = AppConfig.withPaths(in, out, err);
        new FactorialPipeline(2, cfg).run();

        List<String> outLines = Files.readAllLines(out);
        assertTrue(outLines.contains("3 = 6"));
        assertTrue(outLines.contains("5 = 120"));
        assertEquals(2, outLines.size(), "Only valid results should be present");

        assertTrue(Files.exists(err));
        String errText = Files.readString(err);
        
        assertTrue(errText.contains("Line 2"), "Expected entry for '-1'");
        assertTrue(errText.contains("Line 3"), "Expected entry for 'x'");
    }

    @Test
    @DisplayName("Inline errors mode: results and raw error lines are mixed in the same output file")
    void run_inlineErrors_whenErrorsPathEqualsOutputPath() throws Exception {
        Path inOutSame = tmp.resolve("combined.txt"); 
        Path in        = tmp.resolve("in_inline.txt");

        
        Files.writeString(in, String.join(System.lineSeparator(), "4", "y", "6"));

        var cfg = AppConfig.withPaths(in, inOutSame, inOutSame);
        new FactorialPipeline(2, cfg).run();

        assertTrue(Files.exists(inOutSame));
        List<String> lines = Files.readAllLines(inOutSame);

        
        assertTrue(lines.contains("4 = 24"), "Must contain result for 4");
        assertTrue(lines.contains("6 = 720"), "Must contain result for 6");
        assertTrue(lines.contains("y"), "Must contain raw problematic line 'y'");
    }

    @Test
    @DisplayName("With poolSize=1, output preserves strict input index ordering")
    void run_withPoolSize1_preservesIndexOrdering() throws Exception {
        Path in  = tmp.resolve("ordered_in.txt");
        Path out = tmp.resolve("ordered_out.txt");
        Path err = tmp.resolve("ordered_err.txt");

        
        Files.writeString(in, String.join(System.lineSeparator(), "2", "3", "4"));

        var cfg = AppConfig.withPaths(in, out, err);
        new FactorialPipeline(1, cfg).run(); 

        List<String> outLines = Files.readAllLines(out);
        assertEquals(List.of("2 = 2", "3 = 6", "4 = 24"), outLines,
                "Results must follow the input indices");
        
        assertTrue(Files.exists(err));
        String errText = Files.readString(err);
        assertTrue(errText.isBlank(), "No errors expected in this scenario");
    }

    @Test
    @DisplayName("Empty input file produces an empty output (and empty/absent errors file)")
    void run_emptyInput_producesEmptyOutput() throws Exception {
        Path in  = tmp.resolve("empty_in.txt");
        Path out = tmp.resolve("empty_out.txt");
        Path err = tmp.resolve("empty_err.txt");

        Files.writeString(in, ""); 

        var cfg = AppConfig.withPaths(in, out, err);
        new FactorialPipeline(2, cfg).run();

        List<String> outLines = Files.readAllLines(out);
        assertTrue(outLines.isEmpty(), "Empty input → empty output");

        String errText = Files.exists(err) ? Files.readString(err) : "";
        assertTrue(errText.isBlank(), "Errors file should be empty (or not created)");
    }
}
