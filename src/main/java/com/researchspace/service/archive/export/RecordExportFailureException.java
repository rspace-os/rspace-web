package com.researchspace.service.archive.export;

import com.researchspace.model.field.LocalizedException;
import java.util.function.BiFunction;

/**
 * A record could not be written to an archive. Users see the record and an error reference; the
 * underlying error stays in the server log under that reference.
 */
public class RecordExportFailureException extends ExportFailureException
    implements LocalizedException {

  static final String MESSAGE_CODE = "export.errors.recordWriteFailed";

  private final String recordName;
  private final String globalId;
  private final String errorId;

  public RecordExportFailureException(
      String recordName, String globalId, String errorId, Exception cause) {
    super("Could not write " + globalId + " to the archive, error id " + errorId, cause);
    this.recordName = recordName == null ? globalId : recordName;
    this.globalId = globalId;
    this.errorId = errorId;
  }

  @Override
  public String resolve(BiFunction<String, Object[], String> resolver) {
    return resolver.apply(MESSAGE_CODE, new Object[] {recordName, globalId, errorId});
  }

  private static final long serialVersionUID = 1L;
}
