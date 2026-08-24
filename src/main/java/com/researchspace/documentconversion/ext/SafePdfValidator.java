package com.researchspace.documentconversion.ext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/** Rejects PDFs containing active content, attachments, or unreasonable rendering dimensions. */
public final class SafePdfValidator {

  private static final int MAX_PAGES = 10_000;
  private static final float MAX_PAGE_DIMENSION_POINTS = 14_400;
  private static final Set<String> UNSAFE_ACTIONS =
      Set.of("GoToE", "GoToR", "ImportData", "JavaScript", "Launch", "SubmitForm");

  private SafePdfValidator() {}

  public static void validate(Path output) throws IOException {
    try (var stream = Files.newInputStream(output)) {
      if (!"%PDF-".equals(new String(stream.readNBytes(5), StandardCharsets.US_ASCII))) {
        throw invalid();
      }
    }
    try (PDDocument document = Loader.loadPDF(output.toFile())) {
      validate(document);
    }
  }

  public static void validate(PDDocument document) throws IOException {
    if (document.isEncrypted()
        || document.getNumberOfPages() < 1
        || document.getNumberOfPages() > MAX_PAGES) {
      throw invalid();
    }
    for (PDPage page : document.getPages()) {
      validatePageBox(page.getMediaBox());
      validatePageBox(page.getCropBox());
    }
    Set<COSBase> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    validateCosTree(document.getDocumentCatalog().getCOSObject(), visited);
  }

  private static void validatePageBox(PDRectangle box) throws IOException {
    if (box == null
        || !Float.isFinite(box.getWidth())
        || !Float.isFinite(box.getHeight())
        || box.getWidth() <= 0
        || box.getHeight() <= 0
        || box.getWidth() > MAX_PAGE_DIMENSION_POINTS
        || box.getHeight() > MAX_PAGE_DIMENSION_POINTS) {
      throw invalid();
    }
  }

  private static void validateCosTree(COSBase value, Set<COSBase> visited) throws IOException {
    if (value == null) {
      return;
    }
    if (value instanceof COSObject object) {
      validateCosTree(object.getObject(), visited);
      return;
    }
    if (!visited.add(value)) {
      return;
    }
    if (value instanceof COSDictionary dictionary) {
      String action = dictionary.getNameAsString(COSName.S);
      String type = dictionary.getNameAsString(COSName.TYPE);
      if ((action != null && UNSAFE_ACTIONS.contains(action))
          || "EmbeddedFile".equals(type)
          || dictionary.containsKey(COSName.getPDFName("EmbeddedFiles"))
          || dictionary.containsKey(COSName.getPDFName("JavaScript"))
          || dictionary.containsKey(COSName.getPDFName("EF"))
          || dictionary.containsKey(COSName.AA)) {
        throw invalid();
      }
      for (COSName key : dictionary.keySet()) {
        if (COSName.OPEN_ACTION.equals(key)
            && dictionary.getDictionaryObject(key) instanceof COSDictionary) {
          throw invalid();
        }
        validateCosTree(dictionary.getDictionaryObject(key), visited);
      }
    } else if (value instanceof COSArray array) {
      for (COSBase item : array) {
        validateCosTree(item, visited);
      }
    }
  }

  private static IOException invalid() {
    return new IOException(DocumentConversionError.OUTPUT_INVALID.code());
  }
}
