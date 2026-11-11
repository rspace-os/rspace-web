package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.record.RecordInformation;
import org.junit.jupiter.api.Test;

class ApiGalleryFilestoreLinkedDocumentTest {

  @Test
  void placeholderForAnUnreadableDocumentSerialisesOnlyTheOwnerName() throws Exception {
    RecordInformation placeholder = new RecordInformation();
    placeholder.setOwnerFullName("Grace Hopper");

    assertEquals(
        "{\"ownerFullName\":\"Grace Hopper\"}",
        new ObjectMapper().writeValueAsString(new ApiGalleryFilestoreLinkedDocument(placeholder)));
  }
}
