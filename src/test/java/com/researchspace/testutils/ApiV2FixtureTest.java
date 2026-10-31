package com.researchspace.testutils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ApiV2FixtureTest {

  @Test
  void cleanupRunsAllActionsInReverseAndClearsThemAfterFailure() throws Exception {
    var constructor = ApiV2Fixture.class.getDeclaredConstructors()[0];
    constructor.setAccessible(true);
    ApiV2Fixture fixture = ApiV2Fixture.class.cast(constructor.newInstance(new Object[8]));
    List<Integer> calls = new ArrayList<>();
    RuntimeException firstFailure = new IllegalStateException("first failure");
    Error laterFailure = new AssertionError("later failure");
    List<Runnable> actions = new ArrayList<>();
    actions.add(() -> calls.add(1));
    actions.add(
        () -> {
          calls.add(2);
          throw laterFailure;
        });
    actions.add(
        () -> {
          calls.add(3);
          throw firstFailure;
        });
    ReflectionTestUtils.setField(fixture, "teardown", actions);

    RuntimeException thrown = assertThrows(RuntimeException.class, fixture::cleanUp);

    assertSame(firstFailure, thrown);
    assertEquals(List.of(3, 2, 1), calls);
    assertArrayEquals(new Throwable[] {laterFailure}, thrown.getSuppressed());
    fixture.cleanUp();
    assertEquals(List.of(3, 2, 1), calls);
  }
}
