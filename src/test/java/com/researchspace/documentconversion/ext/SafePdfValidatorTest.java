package com.researchspace.documentconversion.ext;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
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
  void acceptsThousandPageTaggedPdfWithParagraphLayout() throws Exception {
    try (PDDocument document = new PDDocument()) {
      PDStructureTreeRoot structure = new PDStructureTreeRoot();
      document.getDocumentCatalog().setStructureTreeRoot(structure);
      for (int pageIndex = 0; pageIndex < 1_000; pageIndex++) {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        for (int paragraphIndex = 0; paragraphIndex < 20; paragraphIndex++) {
          PDStructureElement paragraph = new PDStructureElement("P", structure);
          paragraph.setPage(page);
          paragraph.setActualText("Page " + pageIndex + ", paragraph " + paragraphIndex);
          paragraph.getCOSObject().setInt(COSName.K, paragraphIndex);
          COSDictionary layout = new COSDictionary();
          layout.setName(COSName.O, "Layout");
          layout.setItem(
              COSName.BBOX, new PDRectangle(72, 72 + paragraphIndex * 30, 450, 24).getCOSArray());
          paragraph.getCOSObject().setItem(COSName.A, layout);
          structure.appendKid(paragraph);
        }
      }

      assertDoesNotThrow(() -> SafePdfValidator.validate(document));
    }
  }

  @Test
  void ignoresScalarValuesInContainerBudget() throws Exception {
    try (PDDocument document = new PDDocument()) {
      document.addPage(new PDPage(PDRectangle.A4));
      COSArray numbers = new COSArray();
      for (int i = 0; i < 2_000_001; i++) {
        numbers.add(new COSFloat(i));
      }
      document.getDocumentCatalog().getCOSObject().setItem("Numbers", numbers);

      assertDoesNotThrow(() -> SafePdfValidator.validate(document));
    }
  }

  @Test
  void acceptsDeepAcyclicDictionaryChainWithoutOverflowing() throws Exception {
    try (PDDocument document = new PDDocument()) {
      document.addPage(new PDPage(PDRectangle.A4));
      COSDictionary chain = new COSDictionary();
      document.getDocumentCatalog().getCOSObject().setItem("Chain", chain);
      for (int i = 0; i < 12_000; i++) {
        COSDictionary next = new COSDictionary();
        chain.setItem(COSName.NEXT, next);
        chain = next;
      }

      assertDoesNotThrow(() -> SafePdfValidator.validate(document));
    }
  }

  @Test
  void rejectsGraphAboveStructuralContainerBudget() throws Exception {
    try (PDDocument document = new PDDocument()) {
      document.addPage(new PDPage(PDRectangle.A4));
      COSArray containers = new COSArray();
      for (int i = 0; i < 2_000_001; i++) {
        containers.add(new COSArray());
      }
      document.getDocumentCatalog().getCOSObject().setItem("Containers", containers);

      IOException error =
          assertThrows(IOException.class, () -> SafePdfValidator.validate(document));

      assertEquals(DocumentConversionError.INPUT_INVALID.code(), error.getMessage());
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
