package com.researchspace.conversion;

import com.researchspace.documentconversion.validation.SafeOfficeArchiveValidator;
import java.io.IOException;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Maps shared hostile-archive validation failures onto the sidecar HTTP error contract. */
@Component
class ArchiveValidator {

  private static final Logger LOG = LoggerFactory.getLogger(ArchiveValidator.class);

  void validate(Path archive, String extension) {
    try {
      SafeOfficeArchiveValidator.validateInput(archive, extension);
    } catch (IOException e) {
      LOG.warn("Office archive validation rejected a {} input", extension, e);
      boolean tooLarge = SafeOfficeArchiveValidator.INPUT_TOO_LARGE.equals(e.getMessage());
      throw new ConversionException(
          tooLarge ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          tooLarge ? ConversionError.INPUT_TOO_LARGE : ConversionError.INPUT_INVALID,
          "The uploaded archive failed validation",
          e);
    }
  }
}
