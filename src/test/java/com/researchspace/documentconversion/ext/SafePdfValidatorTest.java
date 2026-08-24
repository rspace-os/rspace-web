package com.researchspace.documentconversion.ext;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

class SafePdfValidatorTest {

  @Test
  void acceptsOrdinaryPdf() throws Exception {
    try (PDDocument document = new PDDocument()) {
      document.addPage(new PDPage(PDRectangle.A4));

      assertDoesNotThrow(() -> SafePdfValidator.validate(document));
    }
  }

  @Test
  void rejectsJavascriptAction() throws Exception {
    try (PDDocument document = new PDDocument()) {
      document.addPage(new PDPage(PDRectangle.A4));
      COSDictionary action = new COSDictionary();
      action.setName(COSName.S, "JavaScript");
      document.getDocumentCatalog().getCOSObject().setItem(COSName.OPEN_ACTION, action);

      IOException error =
          assertThrows(IOException.class, () -> SafePdfValidator.validate(document));

      assertEquals(DocumentConversionError.OUTPUT_INVALID.code(), error.getMessage());
    }
  }

  @Test
  void rejectsUnreasonablePageDimensions() throws Exception {
    try (PDDocument document = new PDDocument()) {
      document.addPage(new PDPage(new PDRectangle(20_000, 100)));

      assertThrows(IOException.class, () -> SafePdfValidator.validate(document));
    }
  }
}
