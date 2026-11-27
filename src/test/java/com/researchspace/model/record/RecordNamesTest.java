package com.researchspace.model.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RecordNamesTest {

  @ParameterizedTest
  @CsvSource({"BA.2/BA.3,BA.2-BA.3", "a//b/,a--b-", "no slash,no slash", "'',''"})
  void replaceInvalidCharacters(String name, String expected) {
    assertEquals(expected, RecordNames.replaceInvalidCharacters(name));
  }

  @Test
  void nullIsAllowed() {
    assertNull(RecordNames.replaceInvalidCharacters(null));
    assertFalse(RecordNames.hasInvalidCharacters(null));
  }

  @Test
  void hasInvalidCharacters() {
    assertTrue(RecordNames.hasInvalidCharacters("a/b"));
    assertFalse(RecordNames.hasInvalidCharacters("a-b"));
  }
}
