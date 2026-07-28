package com.researchspace.model.permissions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GroupConstraintTest {

  GroupConstraint g1, g2, g3;

  @BeforeEach
  public void setUp() throws Exception {}

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testSatisfies() {
    g1 = new GroupConstraint("g1");
    g2 = new GroupConstraint("g1");
    g3 = new GroupConstraint("g3");
    assertTrue(g1.satisfies(g2));
    assertTrue(g2.satisfies(g1));
    assertFalse(g2.satisfies(g3));
  }
}
