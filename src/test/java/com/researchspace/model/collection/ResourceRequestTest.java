package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ResourceRequestTest {

  @Test
  void acceptsPageSizeBoundaries() {
    assertEquals(1, new ResourceRequest.Page(1, 1).size());
    assertEquals(
        CollectionQueryLimits.MAX_PAGE_SIZE,
        new ResourceRequest.Page(1, CollectionQueryLimits.MAX_PAGE_SIZE).size());
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, CollectionQueryLimits.MAX_PAGE_SIZE + 1, Integer.MAX_VALUE})
  void rejectsSizesOutsideTheSharedBound(int size) {
    assertThrows(IllegalArgumentException.class, () -> new ResourceRequest.Page(1, size));
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0})
  void rejectsNonPositivePageNumbers(int number) {
    assertThrows(IllegalArgumentException.class, () -> new ResourceRequest.Page(number, 1));
  }
}
