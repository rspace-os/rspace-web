package com.researchspace.model.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LocationConstraintTest {

  LocationConstraint toTest = new LocationConstraint("/a/b/c/d/e/f/g");

  @BeforeEach
  public void setUp() throws Exception {}

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testGetString() {
    LocationConstraint lc = new LocationConstraint("/a/b/c/d/e/f/g/h");
    assertEquals("location=/a/b/c/d/e/f/g/h", lc.getString());
  }

  LocationConstraint createLocationConstraint(String path) {
    return new LocationConstraint(path);
  }

  @Test
  public void testSatisfies() {
    LocationConstraint lc = createLocationConstraint("/*");
    assertTrue(lc.satisfies(toTest));

    lc = createLocationConstraint("/a/b/c/d/e/f/g/h");
    assertFalse(lc.satisfies(toTest));

    // does not allow subfolders of a/b/c
    lc = createLocationConstraint("/a/b/c/");
    assertFalse(lc.satisfies(toTest));

    // trailing delimter ignored
    lc = createLocationConstraint("/a/b/c/d/e/f/g/");
    assertTrue(lc.satisfies(toTest));

    // perfect match
    lc = createLocationConstraint("/a/b/c/d/e/f/g");
    assertTrue(lc.satisfies(toTest));

    lc = createLocationConstraint("/*/e/f/g");
    assertTrue(lc.satisfies(toTest));

    // target does not have 'h' subfolder
    lc = createLocationConstraint("/*/e/f/g/h");
    assertFalse(lc.satisfies(toTest));

    // target does not have 'h' subfolder
    lc = createLocationConstraint("/*/h");
    assertFalse(lc.satisfies(toTest));

    // wrong order
    lc = createLocationConstraint("/*/c/f/e/g");
    assertFalse(lc.satisfies(toTest));

    // ok match
    lc = createLocationConstraint("/*/g");
    assertTrue(lc.satisfies(toTest));
  }
}
