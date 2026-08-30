package com.researchspace.documentconversion.ext;

/** Carries a conversion failure across Java layers without encoding its type in message text. */
public final class DocumentConversionException extends IllegalStateException {

  private final DocumentConversionError error;

  public DocumentConversionException(DocumentConversionError error) {
    super(error.code());
    this.error = error;
  }

  public DocumentConversionException(DocumentConversionError error, Throwable cause) {
    super(error.code(), cause);
    this.error = error;
  }

  public DocumentConversionError error() {
    return error;
  }

  public static DocumentConversionError errorFrom(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current instanceof DocumentConversionException conversionFailure) {
        return conversionFailure.error();
      }
    }
    return DocumentConversionError.FAILED;
  }
}
