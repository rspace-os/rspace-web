package com.researchspace.conversion;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConversionWorkspaceTest {
  @TempDir Path directory;

  @Test
  void deletesAbandonedRequestsAndBrokenAliasesWithoutFollowingLinks() throws Exception {
    Path request = Files.createDirectory(directory.resolve("request-abandoned"));
    Files.writeString(request.resolve("input.docx"), "partial");
    Path unrelated = Files.writeString(directory.resolve("keep"), "unrelated");
    Files.createSymbolicLink(directory.resolve("pdf-link"), unrelated);
    Files.createSymbolicLink(
        directory.resolve("OSL_PIPE_10001_rspace-broken"), directory.resolve("missing"));
    ConversionWorkspace.clean(directory, "request-", "pdf-", "OSL_PIPE_10001_rspace-");
    try (var files = Files.list(directory)) {
      assertEquals(List.of(unrelated), files.toList());
    }
    assertEquals("unrelated", Files.readString(unrelated));
  }
}
