package com.researchspace.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class VersionTest {
  Version v1, v2, v3;

  @BeforeEach
  public void setUp() throws Exception {
    v1 = null;
    v2 = null;
    v3 = null;
    setUpVersions();
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testNullConstructorThrowsIAE() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new Version(null);
        });
  }

  @Test
  public void testEqualsObject() {
    Version v1Equal = new Version(1L);
    assertEquals(v1, v1Equal);
  }

  @Test
  public void testHashCode() {
    Version v1Equal = new Version(1L);
    assertTrue(v1.hashCode() == v1Equal.hashCode());
  }

  @Test
  public void testVersion() {

    assertEquals(Long.valueOf(1), v1.getVersion());
  }

  @Test
  public void testIncrementVersionReturnsNewVersion() {

    Version v2 = v1.increment();
    assertEquals(Long.valueOf(2), v2.getVersion());
    // v1 is unchanged
    assertEquals(Long.valueOf(1), v1.getVersion());
  }

  @Test
  public void testCompareTo() {

    Version v4 = new Version(3L);
    assertEquals(-1, v1.compareTo(v2));
    assertEquals(1, v3.compareTo(v2));
    assertEquals(0, v3.compareTo(v4));
  }

  private void setUpVersions() {
    v1 = new Version(1L);
    v2 = new Version(2L);
    v3 = new Version(3L);
  }

  @Test
  public void testBefore() {

    assertTrue(v1.before(v2));
    assertFalse(v2.before(v1));
  }

  @Test
  public void testAfter() {

    assertTrue(v2.after(v1));
    assertFalse(v1.after(v3));
  }

  @Test
  public void testToString() {
    assertTrue(v1.toString().length() > 0);
  }

  @Test
  public void testAsString() {
    assertEquals("1", v1.asString());
  }
}
