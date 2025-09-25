package org.factorial.compute;


import org.factorial.config.AppConfig;


import java.math.BigInteger;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;


public final class FactorialService {
    private static final ConcurrentHashMap<Integer, BigInteger> cache = new ConcurrentHashMap<>();
    private static int smallMax;
    private static BigInteger[] precomputed = new BigInteger[0];


    public FactorialService(AppConfig cfg) {
        smallMax = cfg.factorialSmallMax;


        precomputed = new BigInteger[smallMax + 1];
        precomputed[0] = BigInteger.ONE;

        for (int i = 1; i <= smallMax; i++) {
            precomputed[i] = precomputed[i - 1].multiply(BigInteger.valueOf(i));
        }


        cache.put(0, precomputed[0]);
        if (smallMax >= 1) cache.put(1, precomputed[1]);
    }


    public BigInteger factorial(int n) {
        if (n < 0) throw new IllegalArgumentException("Negative numbers are not supported");
        if (n <= smallMax) return precomputed[n];
        return cache.computeIfAbsent(n, FactorialService::computeFactorial);
    }


    private static BigInteger computeFactorial(int n) {

        return IntStream.rangeClosed(2, n)
                .mapToObj(BigInteger::valueOf)
                .reduce(BigInteger.ONE, BigInteger::multiply);
    }
}