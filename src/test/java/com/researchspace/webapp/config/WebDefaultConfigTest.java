package com.researchspace.webapp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WebDefaultConfigTest {

  @Test
  void boundsJsonDocumentsBeforeTreeMaterializationCanGrowWithoutLimit() {
    ObjectMapper mapper = new WebDefaultConfig().objectMapper();
    StreamReadConstraints constraints = mapper.getFactory().streamReadConstraints();

    assertEquals(WebDefaultConfig.MAX_JSON_DOCUMENT_LENGTH, constraints.getMaxDocumentLength());
    assertEquals(WebDefaultConfig.MAX_JSON_STRING_LENGTH, constraints.getMaxStringLength());
    assertEquals(WebDefaultConfig.MAX_JSON_TOKEN_COUNT, constraints.getMaxTokenCount());
    assertEquals(WebDefaultConfig.MAX_JSON_NESTING_DEPTH, constraints.getMaxNestingDepth());
  }
}
