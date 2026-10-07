package com.researchspace.model.sort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class SortKeyTest {

  private static final List<Class<? extends Enum<?>>> ALL_SORTS =
      List.of(
          UserSort.class,
          GroupSort.class,
          CommunitySort.class,
          FormSort.class,
          RecordSort.class,
          DeletedRecordSort.class,
          CommunicationSort.class,
          SharedRecordSort.class,
          InventorySort.class,
          AuditTrailSort.class);

  @Test
  public void blankResolvesToTheListingDefault() {
    assertEquals(UserSort.LAST_NAME, UserSort.fromRequest(null));
    assertEquals(UserSort.LAST_NAME, UserSort.fromRequest(""));
    assertEquals(UserSort.LAST_NAME, UserSort.fromRequest("  "));
    assertEquals(RecordSort.MODIFICATION_DATE_MILLIS, RecordSort.fromRequest(null));
  }

  @Test
  public void unknownKeyIsRejectedWithTheAllowedKeys() {
    UnknownSortKeyException e =
        assertThrows(UnknownSortKeyException.class, () -> UserSort.fromRequest("name,rand()"));
    assertEquals("name,rand()", e.getRequestedKey());
    assertTrue(e.getAllowedKeys().contains("lastName"));
    assertTrue(e.getAllowedKeys().contains("fileUsage"));

    // matching is exact: no case folding, no trimming, no legacy dotted or bracketed forms
    assertThrows(UnknownSortKeyException.class, () -> UserSort.fromRequest("LastName"));
    assertThrows(UnknownSortKeyException.class, () -> UserSort.fromRequest(" lastName"));
    assertThrows(UnknownSortKeyException.class, () -> UserSort.fromRequest("fileUsage()"));
    assertThrows(UnknownSortKeyException.class, () -> GroupSort.fromRequest("owner.lastName"));
    assertThrows(
        UnknownSortKeyException.class,
        () -> CommunicationSort.fromRequest("communication.creationTime"));
  }

  @Test
  public void aggregateUserKeysAreRejectedWhenAUserColumnIsRequired() {
    UnknownSortKeyException e =
        assertThrows(UnknownSortKeyException.class, () -> UserSort.fromColumnRequest("fileUsage"));

    assertEquals("fileUsage", e.getRequestedKey());
    assertTrue(e.getAllowedKeys().contains("lastName"));
    assertFalse(e.getAllowedKeys().contains("fileUsage"));
    assertFalse(e.getAllowedKeys().contains("recordCount"));
  }

  @Test
  public void unknownKeyCannotAddLinesToLogs() {
    UnknownSortKeyException e =
        assertThrows(
            UnknownSortKeyException.class, () -> UserSort.fromRequest("name\r\nforged entry"));

    assertEquals("nameforged entry", e.getRequestedKey());
    assertFalse(e.getMessage().contains("\r"));
    assertFalse(e.getMessage().contains("\n"));
  }

  @Test
  public void everyKeyIsABareUniqueToken() {
    for (Class<? extends Enum<?>> type : ALL_SORTS) {
      Set<String> seen = new HashSet<>();
      for (Enum<?> constant : type.getEnumConstants()) {
        String key = ((SortKey) constant).key();
        assertTrue(key.matches("[A-Za-z][A-Za-z0-9]*"), type.getSimpleName() + ": " + key);
        assertTrue(seen.add(key), type.getSimpleName() + " duplicates key " + key);
      }
    }
  }
}
