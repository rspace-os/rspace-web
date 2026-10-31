package com.researchspace.api.v2.model;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiV2ListResultTest {
  @Test
  void sliceNavigationDoesNotRequireTotalsIncludingPastTheEnd() throws Exception {
    for (int page : List.of(1, 2, 3, Integer.MAX_VALUE)) {
      for (boolean next : List.of(false, true)) {
        var result = ApiV2ListResult.slice(List.of(), null, 20, page, next);
        assertNull(result.totalDocs());
        assertNull(result.totalPages());
        assertEquals(page > 1, result.hasPrevPage());
        assertEquals(page > 1 ? page - 1 : null, result.prevPage());
        assertEquals(next, result.hasNextPage());
        assertEquals(next ? page + 1 : null, result.nextPage());
        var json = new ObjectMapper().valueToTree(result);
        assertFalse(json.has("totalDocs"));
        assertFalse(json.has("totalPages"));
        assertTrue(json.has("prevPage"));
      }
    }
    assertFalse(ApiV2ListResult.slice(List.of(), 2L, 2, 3, false).hasPrevPage());
    assertTrue(ApiV2ListResult.counted(List.of(), 2, 2, 3, false).hasPrevPage());
    assertFalse(ApiV2ListResult.counted(List.of(), 2, 2, 3, true).hasPrevPage());
  }
}
