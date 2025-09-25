package org.factorial.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Messages record factory methods and semantics")
class MessagesTest {

    @Test
    @DisplayName("value(): sets all fields for RESULT message")
    void valueFactory_setsFields() {
        var m = Messages.value(7, 10, new BigInteger("3628800"));
        assertAll(
                () -> assertEquals(7, m.index()),
                () -> assertEquals(Messages.Type.RESULT, m.type()),
                () -> assertEquals("10", m.value()),
                () -> assertEquals(new BigInteger("3628800"), m.factorial()),
                () -> assertNull(m.rawLine()),
                () -> assertNull(m.errorMsg()),
                () -> assertFalse(m.isPoison())
        );
    }

    @Test
    @DisplayName("raw(): sets fields for RAW_ERROR message")
    void rawFactory_setsFields() {
        var m = Messages.raw(3, "bad-line");
        assertAll(
                () -> assertEquals(3, m.index()),
                () -> assertEquals(Messages.Type.RAW_ERROR, m.type()),
                () -> assertNull(m.value()),
                () -> assertNull(m.factorial()),
                () -> assertEquals("bad-line", m.rawLine()),
                () -> assertNull(m.errorMsg()),
                () -> assertFalse(m.isPoison())
        );
    }

    @Test
    @DisplayName("skip(): creates SKIP message with only index")
    void skipFactory_typeAndIndex() {
        var m = Messages.skip(5);
        assertAll(
                () -> assertEquals(5, m.index()),
                () -> assertEquals(Messages.Type.SKIP, m.type()),
                () -> assertNull(m.value()),
                () -> assertNull(m.factorial()),
                () -> assertNull(m.rawLine()),
                () -> assertNull(m.errorMsg()),
                () -> assertFalse(m.isPoison())
        );
    }

    @Test
    @DisplayName("poison(): creates POISON message with isPoison()==true")
    void poisonFactory_typeAndFlag() {
        var m = Messages.poison();
        assertAll(
                () -> assertEquals(Messages.Type.POISON, m.type()),
                () -> assertTrue(m.isPoison()),
                () -> assertEquals(Integer.MAX_VALUE, m.index()),
                () -> assertNull(m.value()),
                () -> assertNull(m.factorial()),
                () -> assertNull(m.rawLine()),
                () -> assertNull(m.errorMsg())
        );
    }

    @Test
    @DisplayName("Record semantics: equals() and hashCode() work for same content")
    void equalsAndHashCode_onSameContent() {
        var a = new Messages.Msg(1, Messages.Type.RESULT, "3", new BigInteger("6"), null, null);
        var b = new Messages.Msg(1, Messages.Type.RESULT, "3", new BigInteger("6"), null, null);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("toString(): contains diagnostic info (type and index)")
    void toString_containsTypeAndIndex() {
        var m = Messages.raw(9, "x");
        var s = m.toString();
        assertTrue(s.contains("index=9") || s.contains("(9") || s.contains("index= 9"),
                "toString() must contain index");
        assertTrue(s.toLowerCase().contains("raw") || s.contains(Messages.Type.RAW_ERROR.name()),
                "toString() must contain RAW_ERROR type");
    }
}
