package com.researchspace.service.resourceaccess;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Bounded grantee lookup response, without collection paging metadata. */
public record ResourceGranteeDirectoryDocument(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ResourceGranteeDirectoryEntry> docs) {

  public ResourceGranteeDirectoryDocument {
    docs = List.copyOf(docs);
  }
}
