package com.researchspace.model.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class IdConstraintTest {
  Set<Long> ids = null;
  IdConstraint constraint;

  @BeforeEach
  public void setUp() throws Exception {}

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testInvariants2() {
    assertThrows(
        UnsupportedOperationException.class,
        () -> {
          createConstraintFromLongArray(new Long[] {2L, 3L, 1L});
          ids.clear();
          assertEquals(3, constraint.getId().size());
          assertEquals(1L, constraint.getId().iterator().next().longValue());

          constraint.getId().clear();
        });
  }

  @Test
  public void testSatisfiesNormalCase() {
    createConstraintFromLongArray(new Long[] {1L, 2L});

    assertTrue(constraint.satisfies(1L));
    assertFalse(constraint.satisfies(4L));
    // test
    createConstraintFromLongArray(new Long[] {});
    assertFalse(constraint.satisfies(1L));
  }

  @Test
  public void testGetString() {
    createConstraintFromLongArray(new Long[] {1L, 2L});
    assertEquals("id=1,2", constraint.getString());

    createConstraintFromLongArray(new Long[] {1L, 2L});
    assertEquals("id=1,2", constraint.getString());

    createConstraintFromLongArray(new Long[] {1L});
    assertEquals("id=1", constraint.getString());
  }

  void createConstraintFromLongArray(Long[] array) {
    ids = new HashSet<>(Arrays.asList(array));

    constraint = new IdConstraint(ids);
  }
}
