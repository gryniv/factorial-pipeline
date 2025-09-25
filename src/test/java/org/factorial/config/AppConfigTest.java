package org.factorial.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("When config file is missing, defaults are applied")
    void loadOrDefault_whenFileMissing_usesDefaults() {
        var cfg = AppConfig.loadOrDefault(tmp.resolve("no-such.properties"));
        assertNotNull(cfg.inputPath);
        assertNotNull(cfg.outputPath);
        assertNotNull(cfg.errorsPath);
        assertTrue(cfg.ratePerSecond >= 1);
        assertTrue(cfg.factorialSmallMax >= 0);
        assertTrue(cfg.progressIntervalMs >= 50);
    }

    @Test
    @DisplayName("withPaths overrides only paths, other values remain valid defaults")
    void withPaths_overridesOnlyPaths() {
        Path in = tmp.resolve("in.txt");
        Path out = tmp.resolve("out.txt");
        Path err = tmp.resolve("err.txt");

        var cfg = AppConfig.withPaths(in, out, err);

        assertEquals(in, cfg.inputPath);
        assertEquals(out, cfg.outputPath);
        assertEquals(err, cfg.errorsPath);

        assertTrue(cfg.ratePerSecond >= 1);
        assertTrue(cfg.factorialSmallMax >= 0);
        assertTrue(cfg.progressIntervalMs >= 50);
    }

    @Test
    @DisplayName("Properties file: all supported keys are read correctly")
    void loadFromProperties_readsAllSupportedKeys() throws IOException {
        Path in  = tmp.resolve("inA.txt");
        Path out = tmp.resolve("outA.txt");
        Path err = tmp.resolve("errA.txt");

        Path props = tmp.resolve("cfg.properties");
        String body = String.join("\n",
                "input.path=" + in,
                "output.path=" + out,
                "errors.path=" + err,
                "rate.per.second=123",
                "factorial.small.max=15",
                "progress.interval.ms=250"
        );
        Files.writeString(props, body);

        var cfg = AppConfig.loadOrDefault(props);

        assertEquals(in,  cfg.inputPath);
        assertEquals(out, cfg.outputPath);
        assertEquals(err, cfg.errorsPath);
        assertEquals(123, cfg.ratePerSecond);
        assertEquals(15,  cfg.factorialSmallMax);
        assertEquals(250, cfg.progressIntervalMs);
    }

    @Test
    @DisplayName("Properties file: values are trimmed of surrounding whitespace")
    void loadFromProperties_trimsWhitespace() throws IOException {
        Path props = tmp.resolve("cfg.properties");
        Files.writeString(props, String.join("\n",
                "rate.per.second=   200  ",
                "factorial.small.max=\t21 ",
                "progress.interval.ms=  500 "
        ));

        var cfg = AppConfig.loadOrDefault(props);
        assertEquals(200, cfg.ratePerSecond);
        assertEquals(21,  cfg.factorialSmallMax);
        assertEquals(500, cfg.progressIntervalMs);
    }

    @Test
    @DisplayName("Properties file: missing keys → defaults applied")
    void loadFromProperties_missingSomeKeys_applyDefaultsForMissing() throws IOException {
        Path in  = tmp.resolve("i.txt");
        Path out = tmp.resolve("o.txt");

        Path props = tmp.resolve("cfg.properties");
        Files.writeString(props, String.join("\n",
                "input.path=" + in,
                "output.path=" + out
        ));

        var cfg = AppConfig.loadOrDefault(props);
        assertEquals(in,  cfg.inputPath);
        assertEquals(out, cfg.outputPath);
        assertNotNull(cfg.errorsPath);
        assertTrue(cfg.ratePerSecond >= 1);
        assertTrue(cfg.factorialSmallMax >= 0);
        assertTrue(cfg.progressIntervalMs >= 50);
    }

    @Test
    @DisplayName("Invalid numbers fall back to defaults")
    void invalidNumbers_fallBackToDefaults() throws IOException {
        Path props = tmp.resolve("cfg.properties");
        Files.writeString(props, String.join("\n",
                "rate.per.second=not_a_number",
                "org.factorial.small.max= fifteen ",
                "progress.interval.ms= NaN "
        ));

        var cfg = AppConfig.loadOrDefault(props);
        assertEquals(100,  cfg.ratePerSecond);
        assertEquals(20,   cfg.factorialSmallMax);
        assertEquals(1000, cfg.progressIntervalMs);
    }

    @Test
    @DisplayName("Out of range numbers fall back to defaults")
    void outOfRangeNumbers_fallBackToDefaults() throws IOException {
        Path props = tmp.resolve("cfg.properties");
        Files.writeString(props, String.join("\n",
                "rate.per.second=0",
                "org.factorial.small.max=-1",
                "progress.interval.ms=60001"
        ));

        var cfg = AppConfig.loadOrDefault(props);
        assertEquals(100,  cfg.ratePerSecond);
        assertEquals(20,   cfg.factorialSmallMax);
        assertEquals(1000, cfg.progressIntervalMs);
    }

    @Test
    @DisplayName("Boundary values at the edges of valid ranges are accepted")
    void boundaryValues_areAccepted() throws IOException {
        Path props = tmp.resolve("cfg.properties");
        Files.writeString(props, String.join("\n",
                "rate.per.second=1",
                "factorial.small.max=0",
                "progress.interval.ms=50"
        ));

        var low = AppConfig.loadOrDefault(props);
        assertEquals(1,  low.ratePerSecond);
        assertEquals(0,  low.factorialSmallMax);
        assertEquals(50, low.progressIntervalMs);

        Files.writeString(props, String.join("\n",
                "rate.per.second=100000",
                "factorial.small.max=100000",
                "progress.interval.ms=60000"
        ));

        var high = AppConfig.loadOrDefault(props);
        assertEquals(100000, high.ratePerSecond);
        assertEquals(100000, high.factorialSmallMax);
        assertEquals(60000,  high.progressIntervalMs);
    }
}
