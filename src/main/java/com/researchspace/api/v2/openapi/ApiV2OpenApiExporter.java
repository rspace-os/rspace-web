package com.researchspace.api.v2.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class ApiV2OpenApiExporter {

  private final ApiV2OpenApiDocumentService documents;
  private final ObjectMapper objectMapper;

  public ApiV2OpenApiExporter(ApiV2OpenApiDocumentService documents, ObjectMapper objectMapper) {
    this.documents = Objects.requireNonNull(documents, "OpenAPI document service");
    this.objectMapper = Objects.requireNonNull(objectMapper, "Object mapper");
  }

  public void writeJson(Path output) throws IOException {
    Objects.requireNonNull(output, "OpenAPI output path");
    Path parent = output.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Map<String, Object> document = documents.generate();
    try (OutputStream stream = Files.newOutputStream(output)) {
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(stream, document);
    }
  }
}
