package com.researchspace.export.pdf;

import com.researchspace.model.record.StructuredDocument;

public interface HTMLStringGenerator {

  /**
   * Given a structured document, will generate HTML to link field contents together. Also performs
   * some processing of links /images to scale.
   *
   * @param strucDoc
   * @param exportConfig
   */
  ExportProcesserInput extractHtmlStr(
      StructuredDocument strucDoc, StructuredDocumentHTMLViewConfig exportConfig);
}
