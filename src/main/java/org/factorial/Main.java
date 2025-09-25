package org.factorial;

import org.factorial.config.AppConfig;
import org.factorial.pipeline.FactorialPipeline;

import java.nio.file.Path;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Path cfgPath = Path.of("config.properties");
        AppConfig cfg = AppConfig.loadOrDefault(cfgPath);
        int poolSize = resolvePoolSize(args);
        new FactorialPipeline(poolSize, cfg).run();
    }

    private static int resolvePoolSize(String[] args) {
        if (args.length >= 1) {
            try {
                int p = Integer.parseInt(args[0]);
                if (p <= 0) throw new NumberFormatException();
                return p;
            } catch (NumberFormatException e) {
                System.err.println("Error: poolSize must be a positive integer.");
                System.exit(1);
            }
        }
        try (Scanner sc = new Scanner(System.in)) {
            int v = -1;
            while (v <= 0) {
                System.out.print("Enter pool size (positive integer): ");
                String line = sc.nextLine().trim();
                try {
                    v = Integer.parseInt(line);
                    if (v <= 0) System.err.println("Error: must be a positive integer.");
                } catch (NumberFormatException e) {
                    System.err.println("Error: not a valid integer.");
                }
            }
            return v;
        }
    }
}
