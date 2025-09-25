


package org.factorial.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class AppConfig {
    public final Path inputPath;
    public final Path outputPath;
    public final Path errorsPath;

    public final int progressIntervalMs;
    public final int ratePerSecond;
    public final int factorialSmallMax;

    private AppConfig(Properties p) {
        ratePerSecond      = parseInt(p, "rate.per.second",      100, 1, 100_000);
        factorialSmallMax  = parseInt(p, "factorial.small.max",    20, 0, 100_000);
        progressIntervalMs = parseInt(p, "progress.interval.ms", 1000, 50, 60_000);
        inputPath  = Paths.get(p.getProperty("input.path",  "input.txt"));
        outputPath = Paths.get(p.getProperty("output.path", "output.txt"));
        errorsPath = Paths.get(p.getProperty("errors.path", "errors.txt"));
    }

    private static int parseInt(Properties p, String key, int def, int min, int max) {
        try {
            int v = Integer.parseInt(p.getProperty(key, String.valueOf(def)).trim());
            return (v < min || v > max) ? def : v;
        } catch (Exception e) {
            return def;
        }
    }

    public static AppConfig loadOrDefault(Path path) {
        Properties p = new Properties();
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) { p.load(in); } catch (IOException ignored) { }
        }
        return new AppConfig(p);
    }

    public static AppConfig withPaths(Path input, Path output, Path errors) {
        Properties p = new Properties();
        p.setProperty("input.path", input.toString());
        p.setProperty("output.path", output.toString());
        p.setProperty("errors.path", errors.toString());
        return new AppConfig(p);
    }
}
