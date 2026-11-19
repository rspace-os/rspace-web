package com.researchspace.model.field.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class FieldKeysTest {

  @Test
  void newFieldKeyIsLowercaseVersion7Uuid() {
    String key = FieldKeys.newFieldKey();

    UUID uuid = UUID.fromString(key);
    assertEquals(7, uuid.version());
    assertEquals(2, uuid.variant());
    assertEquals(uuid.toString(), key);
  }

  @Test
  void newFieldKeysAreDistinctAndIncreasing() {
    String first = FieldKeys.newFieldKey();
    String second = FieldKeys.newFieldKey();

    assertNotEquals(first, second);
    assertTrue(first.compareTo(second) < 0);
  }
}
