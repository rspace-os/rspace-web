package com.researchspace.service.inventory.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PidinstFieldsTest {

  /**
   * The import sets each link's relation from IMPORTED_LINKS, and the create path refuses a
   * relation outside the field's whitelist with a 422 that fails the whole import (RSDEV-1528).
   */
  @Test
  void eachImportedLinkRelationIsAllowedByItsFieldInTheSeededTemplate() throws Exception {
    JsonNode template;
    try (InputStream in =
        new ClassPathResource("inventory/defaultInstrumentTemplate-PIDINST-1.0.json")
            .getInputStream()) {
      template = new ObjectMapper().readTree(in);
    }
    for (PidinstFields.ImportedLink imported : PidinstFields.IMPORTED_LINKS) {
      List<String> allowed = new ArrayList<>();
      for (JsonNode field : template.get("fields")) {
        if (imported.fieldName().equals(field.get("name").asText())) {
          field.get("allowedRelationTypes").forEach(relation -> allowed.add(relation.asText()));
        }
      }
      assertThat(allowed).as(imported.fieldName()).contains(imported.relationType());
    }
  }
}
