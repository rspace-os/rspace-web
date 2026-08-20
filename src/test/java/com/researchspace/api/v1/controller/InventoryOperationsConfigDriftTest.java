package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guards the stage-1 contract of DevDocs/adr/0015: the backend's copy of the operation definitions
 * is a VERBATIM copy of the frontend's. The frontend renders and gates the wizard from its copy;
 * the backend validates API requests against this one. If they drift, the two sides disagree about
 * what a valid operation is, so drift is a red build, not a warning.
 */
class InventoryOperationsConfigDriftTest {

  private static final Path FRONTEND_CONFIG =
      Path.of("src/main/webapp/ui/src/Inventory/components/Operations/operations_config.json");
  private static final Path BACKEND_CONFIG =
      Path.of("src/main/resources/inventory/operations_config.json");

  @Test
  void backendConfigIsAByteIdenticalCopyOfTheFrontendConfig() throws IOException {
    assertTrue(Files.exists(FRONTEND_CONFIG), "frontend config missing: " + FRONTEND_CONFIG);
    assertTrue(Files.exists(BACKEND_CONFIG), "backend config missing: " + BACKEND_CONFIG);
    assertArrayEquals(
        Files.readAllBytes(FRONTEND_CONFIG),
        Files.readAllBytes(BACKEND_CONFIG),
        "The two operations_config.json copies have drifted. They must stay byte-identical"
            + " (DevDocs/adr/0015): after editing one, sync the other with\n  cp "
            + FRONTEND_CONFIG
            + " "
            + BACKEND_CONFIG
            + "\n(or the reverse direction if the backend copy was edited).");
  }
}
