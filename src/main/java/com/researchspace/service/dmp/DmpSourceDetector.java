package com.researchspace.service.dmp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dmps.DMPUser;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

/** Works out which platform a plan came from by the layout of the file saved at import. */
@Slf4j
public class DmpSourceDetector {

  private static final byte[] PDF_SIGNATURE = "%PDF".getBytes(StandardCharsets.US_ASCII);

  private final FileStore fileStore;

  public DmpSourceDetector(FileStore fileStore) {
    this.fileStore = fileStore;
  }

  /**
   * @param plan a plan with a stored file
   * @return the plan's platform, or {@code UNKNOWN} when it cannot be worked out; never throws
   */
  public DMPSource detect(DMPUser plan) {
    if (plan.getDmpDownloadFile() == null) {
      return DMPSource.UNKNOWN;
    }
    try {
      File file = fileStore.findFile(plan.getDmpDownloadFile().getFileProperty());
      if (startsWithPdfSignature(file)) {
        return DMPSource.DMP_TOOL;
      }
      return fromJsonLayout(new ObjectMapper().readTree(file));
    } catch (IOException | RuntimeException e) {
      // parser messages can quote the file, so only the exception type is logged
      log.warn(
          "Couldn't read the stored file of plan {}: {}",
          plan.getId(),
          e.getClass().getSimpleName());
      return DMPSource.UNKNOWN;
    }
  }

  // DMPTool saves its API response, DMPonline the inner dmp object, Argos its own plan details
  private static DMPSource fromJsonLayout(JsonNode root) {
    if (root.path("items").path(0).has("dmp")) {
      return DMPSource.DMP_TOOL;
    }
    if (root.has("dmp_id")) {
      return DMPSource.DMP_ONLINE;
    }
    if (root.has("label") && root.has("dois")) {
      return DMPSource.ARGOS;
    }
    return DMPSource.UNKNOWN;
  }

  private static boolean startsWithPdfSignature(File file) throws IOException {
    try (InputStream in = Files.newInputStream(file.toPath())) {
      return Arrays.equals(in.readNBytes(PDF_SIGNATURE.length), PDF_SIGNATURE);
    }
  }
}
