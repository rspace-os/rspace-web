package com.researchspace.export.pdf;

import static com.researchspace.testutils.RSpaceTestUtils.setupVelocityWithTextFieldTemplates;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.Convertible;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.EcatImage;
import com.researchspace.model.core.IRSpaceDoc;
import com.researchspace.model.record.TestFactory;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class MSWordProcessorTest {
  MSWordProcessor mswordExporter;
  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Mock DocumentConversionService converter;
  @Mock ImageRetrieverHelper imageRetriever;
  @Mock IRSpaceDoc rspaceDoc;
  RichTextUpdater rtupdater;

  @Before
  public void setUp() throws Exception {
    mswordExporter = new MSWordProcessor();
    mswordExporter.setDocConverter(converter);
    mswordExporter.setImageHelper(imageRetriever);
    rtupdater = new RichTextUpdater();
    rtupdater.setVelocity(setupVelocityWithTextFieldTemplates());
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testSupportsFormat() {
    assertTrue(mswordExporter.supportsFormat(ExportFormat.WORD));
    assertFalse(mswordExporter.supportsFormat(ExportFormat.PDF));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testMakeExportThrowsIAEIfExportFormatisPdf() throws IOException {
    File outfile = File.createTempFile("any", ".doc");
    ExportToFileConfig cfg = getConfig();
    cfg.setExportFormat("PDF");
    ExportProcesserInput input = createAnyHTML();
    mswordExporter.makeExport(outfile, input, rspaceDoc, cfg);
    verify(imageRetriever, Mockito.never())
        .getImageBytesFromImgSrc(Mockito.anyString(), Mockito.any(ExportToFileConfig.class));
    verify(converter, never())
        .convert(Mockito.any(Convertible.class), Mockito.eq("doc"), Mockito.eq(outfile));
  }

  @Test
  public void testMakeExportSimpleHappyCaseNoImages() throws IOException {
    File outfile = File.createTempFile("any", ".doc");
    ExportToFileConfig cfg = getConfig();
    ExportProcesserInput input = createAnyHTML();
    ConversionResult success = new ConversionResult(outfile, "ms/word");
    Mockito.when(
            converter.convert(
                Mockito.any(Convertible.class), Mockito.eq("doc"), Mockito.eq(outfile)))
        .thenReturn(success);
    mswordExporter.makeExport(outfile, input, rspaceDoc, cfg);
    Mockito.verify(imageRetriever, never())
        .getImageBytesFromImgSrc(Mockito.anyString(), Mockito.any(ExportToFileConfig.class));
    verify(converter, atMost(1))
        .convert(Mockito.any(Convertible.class), Mockito.eq("doc"), Mockito.eq(outfile));
  }

  @Test
  public void testMakeExportWithImages() throws IOException {
    File outfile = File.createTempFile("any", ".doc");
    ExportToFileConfig cfg = getConfig();
    ExportProcesserInput input = createAnyHTMLWithImage();
    ConversionResult success = new ConversionResult(outfile, "ms/word");
    when(converter.convert(Mockito.any(Convertible.class), Mockito.eq("doc"), Mockito.eq(outfile)))
        .thenReturn(success);
    when(imageRetriever.getImageBytesFromImgSrc(Mockito.anyString(), Mockito.eq(cfg)))
        .thenReturn(new byte[] {1, 2, 3, 4});
    mswordExporter.makeExport(outfile, input, rspaceDoc, cfg);
    verify(imageRetriever, atMost(1))
        .getImageBytesFromImgSrc(Mockito.anyString(), Mockito.any(ExportToFileConfig.class));
    verify(converter, atMost(1))
        .convert(Mockito.any(Convertible.class), Mockito.eq("doc"), Mockito.eq(outfile));
    System.err.println(outfile.length());
  }

  private ExportProcesserInput createAnyHTMLWithImage() {
    EcatImage image = TestFactory.createEcatImage(1L);
    String imgHtml = rtupdater.generateURLStringForEcatImageLink(image, 2 + "");
    return new ExportProcesserInput(imgHtml, Collections.emptyList(), null, null);
  }

  private ExportProcesserInput createAnyHTML() {
    return new ExportProcesserInput("<html/>", Collections.emptyList(), null, null);
  }

  private ExportToFileConfig getConfig() {
    ExportToFileConfig cfg = new ExportToFileConfig();
    cfg.setExportFormat("WORD");
    return cfg;
  }
}
