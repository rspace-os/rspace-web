package com.researchspace.api.v2.model;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiV2ListResultTest {
  @Test
  void sliceNavigationDoesNotRequireTotalsIncludingPastTheEnd() {
    for (int page : List.of(1, 2, 3, Integer.MAX_VALUE)) {
      for (boolean next : List.of(false, true)) {
        var result = ApiV2ListResult.slice(List.of(), page, 20, next, null, 10);
        assertNull(result.totalDocs());
        assertNull(result.totalIsLowerBound());
        assertEquals(page, result.page());
        assertEquals(next, result.hasNextPage());
        var json = new ObjectMapper().valueToTree(result);
        assertFalse(json.has("totalDocs"));
        assertFalse(json.has("totalIsLowerBound"));
        for (String removed :
            List.of("totalPages", "pagingCounter", "hasPrevPage", "prevPage", "nextPage"))
          assertFalse(json.has(removed));
      }
    }
  }

  @Test
  void countedNavigationAndSliceLookaheadAreIndependentOfCappedTotals() {
    for (boolean totals : List.of(false, true)) {
      var result = ApiV2ListResult.counted(List.of(), 41, 2, 20, totals, 10);
      assertTrue(result.hasNextPage());
      assertEquals(totals ? 10L : null, result.totalDocs());
      assertEquals(totals ? true : null, result.totalIsLowerBound());
    }
    assertFalse(ApiV2ListResult.counted(List.of(), 2, 3, 2, true, null).hasNextPage());
    for (long total : List.of(9L, 10L, 11L)) {
      var result = ApiV2ListResult.slice(List.of(), 3, 2, true, total, 10);
      assertTrue(result.hasNextPage());
      assertEquals(Math.min(total, 10), result.totalDocs());
      assertEquals(total > 10, result.totalIsLowerBound());
    }
  }
}
