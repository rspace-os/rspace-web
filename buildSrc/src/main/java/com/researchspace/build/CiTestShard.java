package com.researchspace.build;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Assigns complete test classes to deterministic, duration-balanced CI shards. */
public final class CiTestShard {
  private CiTestShard() {}

  /** Returns the classes assigned to a one-based shard, including every unknown class. */
  public static List<String> select(
      Collection<String> classes, Map<String, Double> durations, int index, int count) {
    if (index < 1 || count < 1 || index > count) {
      throw new IllegalArgumentException("Shard index must be between 1 and count");
    }
    List<String> ordered = new ArrayList<>(classes);
    ordered.sort(
        Comparator.<String>comparingDouble(name -> durations.getOrDefault(name, 1.0))
            .reversed()
            .thenComparing(Comparator.naturalOrder()));
    double[] loads = new double[count];
    List<String> selected = new ArrayList<>();
    for (String name : ordered) {
      int lightest = 0;
      for (int lane = 1; lane < count; lane++) {
        if (loads[lane] < loads[lightest]) {
          lightest = lane;
        }
      }
      if (lightest == index - 1) {
        selected.add(name);
      }
      // Even a zero-duration class contributes work and must reach only one shard.
      loads[lightest] += Math.max(0.001, durations.getOrDefault(name, 1.0));
    }
    return selected;
  }
}
