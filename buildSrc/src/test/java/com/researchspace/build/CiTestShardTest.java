package com.researchspace.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CiTestShardTest {
  @Test
  void coversEveryClassExactlyOnceIncludingUnknownAndZeroDurationClasses() {
    List<String> classes = List.of("known", "unknown", "zero");
    Map<String, Double> times = Map.of("known", 7.0, "zero", 0.0);
    var seen = new HashSet<String>();
    for (int index = 1; index <= 4; index++) {
      for (String name : CiTestShard.select(classes, times, index, 4)) {
        assertTrue(seen.add(name), "Class appeared in more than one shard: " + name);
      }
    }
    assertEquals(new HashSet<>(classes), seen);
  }

  @Test
  void balancesDurationsWithStableAssignmentsRegardlessOfInputOrder() {
    List<String> classes = List.of("tiny", "short", "medium", "slow");
    Map<String, Double> times = Map.of("tiny", 1.0, "short", 2.0, "medium", 5.0, "slow", 8.0);
    assertEquals(List.of("slow"), CiTestShard.select(classes, times, 1, 2));
    assertEquals(List.of("medium", "short", "tiny"), CiTestShard.select(classes, times, 2, 2));
    assertEquals(
        CiTestShard.select(classes, times, 1, 2),
        CiTestShard.select(List.of("slow", "medium", "short", "tiny"), times, 1, 2));
    assertEquals(List.of("a", "c"), CiTestShard.select(List.of("c", "b", "a"), Map.of(), 1, 2));
  }

  @Test
  void rejectsInvalidShardCoordinates() {
    List<String> classes = List.of("test");
    Map<String, Double> times = Map.of();
    assertThrows(IllegalArgumentException.class, () -> CiTestShard.select(classes, times, 0, 2));
    assertThrows(IllegalArgumentException.class, () -> CiTestShard.select(classes, times, 3, 2));
    assertThrows(IllegalArgumentException.class, () -> CiTestShard.select(classes, times, 1, 0));
  }
}
