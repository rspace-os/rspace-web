package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PageOffsetsTest {

  @ParameterizedTest
  @CsvSource({
    "1, 20, 0",
    "2, 20, 20",
    "2147483647, 100, 214748364600",
    "-2147483648, 100, -214748364900",
    "2, 0, 0",
    "2, -1, -1"
  })
  void computesOffsetsWithoutIntegerOverflowOrRequestValidation(int page, int size, long expected) {
    assertEquals(expected, PageOffsets.offset(page, size));
  }

  @Test
  void retainsTheCatalogueIntegerOffsetBoundary() {
    int lastPage = Integer.MAX_VALUE / RuntimeFieldCatalogQuery.MAX_LIMIT + 1;
    RuntimeFieldCatalogQuery query =
        new RuntimeFieldCatalogQuery(null, Set.of(), lastPage, RuntimeFieldCatalogQuery.MAX_LIMIT);

    assertEquals(2_147_483_600, query.offset());
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new RuntimeFieldCatalogQuery(
                    null, Set.of(), lastPage + 1, RuntimeFieldCatalogQuery.MAX_LIMIT));
    assertEquals("Page number is out of range", exception.getMessage());
  }
}
