package org.factorial.model;

import java.math.BigInteger;

public final class Messages {
    public static Msg value(int index, int value, BigInteger factorial) {
        return new Msg(index, Type.RESULT, String.valueOf(value), factorial, null, null);
    }
    public static Msg raw(int index, String originalLine) {
        return new Msg(index, Type.RAW_ERROR, null, null, originalLine, null);
    }
    public static Msg skip(int index) { return new Msg(index, Type.SKIP, null, null, null, null); }
    public static Msg poison() { return new Msg(Integer.MAX_VALUE, Type.POISON, null, null, null, null); }
    public enum Type {RESULT, RAW_ERROR, ERROR_MSG, SKIP, POISON}
    public record Msg(int index, Type type, String value, BigInteger factorial, String rawLine, String errorMsg) {
        public boolean isPoison() { return type == Type.POISON; }
    }
}
