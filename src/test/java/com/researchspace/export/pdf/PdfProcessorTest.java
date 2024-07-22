package com.researchspace.export.pdf;

import static com.researchspace.testutils.RSpaceTestUtils.loadTextResourceFromPdfDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.researchspace.model.User;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.UserExternalIdResolver;
import com.researchspace.testutils.TestRunnerController;
import java.io.File;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PdfProcessorTest {
  @Mock private UserExternalIdResolver externalIdResolver;

  @Mock private HTMLUnicodeFontProcesser htmlUnicodeFontProcesser;

  @Mock private HtmlImageResolver htmlImageResolver;

  @Mock private PdfHtmlGenerator pdfHtmlGenerator;

  @InjectMocks private PdfProcessor pdfProcessor;

  File outputFile;

  StructuredDocument document;

  ExportToFileConfig config;

  ExportProcesserInput exportProcesserInput;

  @BeforeAll
  public static void setup() throws Exception {
    TestRunnerController.ignoreIfFastRun();
  }

  @BeforeEach
  public void init() throws Exception {
    outputFile = createTempPdfFile();
    User user = new User();
    user.setFirstName("Some");
    user.setLastName("User");
    config = new ExportToFileConfig();
    config.setExporter(user);
  }

  /*
  The characters in these strings require unifont (for chinese), noto sans (for greek)
  and noto sans math fonts to be registered with the pdf processor, so we test that when these
  strings are present in the input, they also appear in the output pdf
   */
  @ParameterizedTest
  @ValueSource(strings = {"一些测试文本", "κάποιο δοκιμαστικό κείμενο", "∑∆∏πφ"})
  public void outputNonAsciiCharactersTest(String nonAscii) throws Exception {
    String htmlWithNonAsciiAlphabet = loadTextResourceFromPdfDir("DocWithNonStandardChars.html");
    exportProcesserInput =
        new ExportProcesserInput(
            htmlWithNonAsciiAlphabet,
            Collections.emptyList(),
            new RevisionInfo(),
            Collections.emptyList());

    when(pdfHtmlGenerator.prepareHtml(any(), any(), any())).thenReturn(htmlWithNonAsciiAlphabet);

    pdfProcessor.makeExport(outputFile, exportProcesserInput, document, config);
    String output = readPdfContent(outputFile);

    assertTrue(output.contains(nonAscii));
  }

  @Test
  public void concatenatesFiles() throws Exception {
    String output = concatTwoDocuments();
    assertTrue(output.contains("This is document 1."));
    assertTrue(output.contains("This is document 2."));
  }

  private String concatTwoDocuments() throws Exception{
    // create 2 pdf docs then concatenate and verify the output doc contains the 2 inputs
    String doc1Html = loadTextResourceFromPdfDir("doc1.html");
    ExportProcesserInput exportProcesserInput1 =
        new ExportProcesserInput(
            doc1Html, Collections.emptyList(), new RevisionInfo(), Collections.emptyList());

    File pdfDoc1 = createTempPdfFile();
    when(pdfHtmlGenerator.prepareHtml(any(), any(), any())).thenReturn(doc1Html);
    pdfProcessor.makeExport(pdfDoc1, exportProcesserInput1, document, config);

    String doc2Html = loadTextResourceFromPdfDir("doc2.html");
    ExportProcesserInput exportProcesserInput2 =
        new ExportProcesserInput(
            doc2Html, Collections.emptyList(), new RevisionInfo(), Collections.emptyList());
    File pdfDoc2 = createTempPdfFile();
    when(pdfHtmlGenerator.prepareHtml(any(), any(), any())).thenReturn(doc2Html);
    pdfProcessor.makeExport(pdfDoc2, exportProcesserInput2, document, config);

    List<File> filesToConcatenate = List.of(pdfDoc1, pdfDoc2);
    pdfProcessor.concatenateExportedFilesIntoOne(outputFile, filesToConcatenate, config);
    return readPdfContent(outputFile);
  }

  @Test
  public void testPageNumbersRestartedEachDoc() throws Exception {
    concatTwoDocuments();

    // config has start page reset to 0 i.e. next doc would begin at page 1, which is the default
    assertEquals(0, config.getStartPage());
  }

  @Test
  public void testPageNumbersContiguous() throws Exception {
    config.setRestartPageNumberPerDoc(false);
    concatTwoDocuments();

    // config has start page as page 3 i.e. next doc would begin at page 4, since the config
    // property to restart the page number on each doc is set to false
    assertEquals(3, config.getStartPage());
  }

  private String readPdfContent(File outFile) throws IOException {
    PdfReader reader = new PdfReader(outFile.getAbsolutePath());
    PdfTextExtractor textExtractor = new PdfTextExtractor(reader);
    StringBuilder content = new StringBuilder();
    for (int i = 1; i <= reader.getNumberOfPages(); i++) {
      content.append(textExtractor.getTextFromPage(i));
    }
    return content.toString();
  }

  private File createTempPdfFile() throws IOException {
    return File.createTempFile("pdftesst", ".pdf");
  }
}
