package com.researchspace.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GlobalIdentifierTest {

  @BeforeEach
  public void setUp() throws Exception {}

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testGetRoundTrip() {
    GlobalIdentifier gid = new GlobalIdentifier(GlobalIdPrefix.FD + "12345");
    GlobalIdentifier gid2 = new GlobalIdentifier(GlobalIdPrefix.FD, 12345L);
    assertEquals(gid, gid2);
    GlobalIdentifier gid3 = new GlobalIdentifier(gid2.getIdString());
    assertEquals(gid, gid3);
    GlobalIdentifier gid4 = new GlobalIdentifier(gid.getIdString());
    assertEquals(gid3, gid4);
  }

  @Test
  public void testGlobalIdentifierNotNullString() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          assertFalse(GlobalIdentifier.isValid(null));
          new GlobalIdentifier(null);
        });
  }

  @Test
  public void testGlobalIdentifierStringNotEmpty() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          assertFalse(GlobalIdentifier.isValid(""));
          new GlobalIdentifier("");
        });
  }

  @Test
  public void testGlobalIdentifierStringWrongSyntax() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          assertFalse(GlobalIdentifier.isValid("12345"));
          new GlobalIdentifier("12345");
        });
  }

  @Test
  public void testGlobalIdentifierStringUnknownPrefix() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          assertFalse(GlobalIdentifier.isValid("XX12345"));
          new GlobalIdentifier("XX12345");
        });
  }

  @Test
  public void testGetPrefix() {
    GlobalIdentifier gid = new GlobalIdentifier(GlobalIdPrefix.FD + "12345");
    assertTrue(GlobalIdentifier.isValid(GlobalIdPrefix.FD + "12345"));
    assertEquals(GlobalIdPrefix.FD, gid.getPrefix());
    assertEquals(12345, gid.getDbId().intValue());
  }
}
