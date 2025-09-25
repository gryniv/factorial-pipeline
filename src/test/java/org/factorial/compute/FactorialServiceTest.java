package org.factorial.compute;

import org.factorial.config.AppConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FactorialServiceTest {

    private static AppConfig cfg() {
        return AppConfig.withPaths(Path.of("in.txt"), Path.of("out.txt"), Path.of("err.txt"));
    }

    @Test
    @DisplayName("Precomputed values: factorial(0), factorial(5), factorial(20)")
    void smallPrecomputed() {
        var svc = new FactorialService(cfg());
        assertEquals(BigInteger.ONE, svc.factorial(0));
        assertEquals(new BigInteger("120"), svc.factorial(5));
        assertEquals(new BigInteger("2432902008176640000"), svc.factorial(20));
    }

    @Test
    @DisplayName("Large factorial computed correctly using streams (25!)")
    void largeComputedByStreams() {
        var svc = new FactorialService(cfg());
        
        assertEquals(new BigInteger("15511210043330985984000000"), svc.factorial(25));
    }

    @Test
    @DisplayName("Negative input throws IllegalArgumentException")
    void negativeThrows() {
        var svc = new FactorialService(cfg());
        assertThrows(IllegalArgumentException.class, () -> svc.factorial(-1));
    }

    @Test
    @DisplayName("0! should equal 1")
    void zeroFactorialIsOne() {
        var svc = new FactorialService(cfg());
        assertEquals(BigInteger.ONE, svc.factorial(0));
    }

    @Test
    @DisplayName("1! should equal 1")
    void oneFactorialIsOne() {
        var svc = new FactorialService(cfg());
        assertEquals(BigInteger.ONE, svc.factorial(1));
    }

    @Test
    @DisplayName("Results for the same input should come from cache (reference equality)")
    void factorialCachingWorks() {
        var svc = new FactorialService(cfg());
        BigInteger first = svc.factorial(25);
        BigInteger second = svc.factorial(25);
        assertSame(first, second, "Expected cached reference to be reused for same input");
    }

    @Test
    @DisplayName("Factorials grow monotonically as input increases")
    void increasingFactorialsGrowMonotonically() {
        var svc = new FactorialService(cfg());
        BigInteger prev = BigInteger.ONE;
        for (int i = 2; i <= 15; i++) {
            BigInteger current = svc.factorial(i);
            assertTrue(current.compareTo(prev) > 0, "factorial(" + i + ") should be larger");
            prev = current;
        }
    }

    @Test
    @DisplayName("Very large factorial (100!) does not overflow and has expected size")
    void veryLargeFactorialDoesNotOverflow() {
        var svc = new FactorialService(cfg());
        
        BigInteger fact100 = svc.factorial(100);
        assertTrue(fact100.toString().length() > 150, "100! should have more than 150 digits");
    }
}
