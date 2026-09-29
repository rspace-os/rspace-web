package com.researchspace.core.util;

import static com.researchspace.core.util.RSCollectionUtils.mergeLists;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class RSCollectionUtilsTest {

  @Test
  public void testMergeLists() {

    List<Integer> l1 = Arrays.asList(new Integer[] {1, 3, 5, 7, 9});
    List<Integer> l2 = Arrays.asList(new Integer[] {7, 9, 11, 13});
    List<Integer> EXPECTED = Arrays.asList(new Integer[] {1, 3, 5, 7, 9, 11, 13});

    List<Integer> result = mergeLists(l1, l2);
    assertThat(EXPECTED).containsExactlyInAnyOrderElementsOf(result);

    List<Integer> result2 = mergeLists(l2, l1);
    assertThat(EXPECTED).containsExactlyInAnyOrderElementsOf(result2);

    l1 = Arrays.asList(new Integer[] {1, 3});
    l2 = Arrays.asList(new Integer[] {7, 9, 11, 13});
    assertNull(mergeLists(l1, l2));

    l1 = Arrays.asList(new Integer[] {1, 3, 7});
    l2 = Arrays.asList(new Integer[] {1, 3, 7});
    assertThat(l1).containsExactlyInAnyOrderElementsOf(mergeLists(l1, l2));
    assertThat(l2).containsExactlyInAnyOrderElementsOf(mergeLists(l1, l2));

    l1 = Arrays.asList(new Integer[] {});
    l2 = Arrays.asList(new Integer[] {});
    // 2 empty collections have no common element
    assertNull(mergeLists(l1, l2));

    // noncontiguous:
    l1 = Arrays.asList(new Integer[] {1, 3, 5, 7, 9});
    l2 = Arrays.asList(new Integer[] {5, 6, 7, 8});
    System.err.println(mergeLists(l1, l2));
  }
}
