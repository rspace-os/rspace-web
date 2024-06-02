package com.researchspace.documentconversion.ext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

/***
 * Checks whether aspose can handle a document conversion
 */
@Service
public class AsposeConversionChecker implements ConversionChecker {

  @Override
  public boolean supportsConversion(String fromFormat, String toFormat) {
    return supportsConversionFrom(fromFormat) && supportsConversionTo(toFormat);
  }

  /***
   * Checks the list of supported output types allowed by rspace
   */
  private boolean supportsConversionTo(String extension) {
    List<String> supportedFormats = Arrays.asList("png", "pdf", "html", "htm");
    return supportedFormats.contains(extension);
  }

  /***
   * Checks the list of file types which aspose supports conversion from.
   */
  private boolean supportsConversionFrom(String extension) {
    // list taken from com.aspose.cells.FileFormatUtil
    List<String> spreadsheetFormats =
        Arrays.asList("csv", "xls", "xlsx", "txt", "ods", "xml", "xlsb", "numbers", "fods");

    // list taken from com.researchspace.documentconversion.aspose.impl.SlidesConversionService
    List<String> presentationFormats = Arrays.asList("ppt", "pptx", "odp");

    // list taken from obfuscated method called from com.aspose.words.FileFormatUtil
    List<String> wordFormats =
        Arrays.asList(
            "tif",
            "tiff",
            "bmp",
            "png",
            "jpeg",
            "jpg",
            "emf",
            "wmf",
            "pict",
            "pct",
            "gif",
            "xps",
            "oxps",
            "pdf",
            "ps",
            "pcl",
            "svg",
            "epub",
            "html",
            "htm",
            "xhtml",
            "txt",
            "md",
            "markdown",
            "markdn",
            "mdown",
            "mdwn",
            "mkdn",
            "mkd",
            "doc",
            "dot",
            "rtf",
            "wml",
            "wordml",
            "xml",
            "mht",
            "mhtml",
            "mhtm",
            "msg",
            "eml",
            "mobi",
            "chm",
            "docx",
            "docm",
            "dotx",
            "dotm",
            "odt",
            "ott",
            "fopc",
            "xamlpac",
            "xaml",
            "mov",
            "ico",
            "odttf");

    List<String> supportedFormats = new ArrayList<>();
    supportedFormats.addAll(spreadsheetFormats);
    supportedFormats.addAll(presentationFormats);
    supportedFormats.addAll(wordFormats);
    return supportedFormats.contains(extension);
  }
}
