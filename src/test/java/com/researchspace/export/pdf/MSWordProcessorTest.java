package com.researchspace.export.pdf;

import static com.researchspace.testutils.RSpaceTestUtils.setupVelocityWithTextFieldTemplates;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.Convertible;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import com.researchspace.export.stoichiometry.StoichiometryHtmlGenerator;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.EcatImage;
import com.researchspace.model.User;
import com.researchspace.model.core.IRSpaceDoc;
import com.researchspace.testutils.TestFactory;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.test.util.ReflectionTestUtils;

public class MSWordProcessorTest {

  public static final String STOICHIOMETRY_HTML = "<html><div data-stoichiometry-table=\"{\"id\":1, \"revision\":null}\"></div></html>";
  MSWordProcessor mswordExporter;
  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Mock DocumentConversionService converter;
  @Mock ImageRetrieverHelper imageRetriever;
  @Mock IRSpaceDoc rspaceDoc;
  @Mock
  private VelocityEngine velocityEngine;
  @Mock
  private StoichiometryHtmlGenerator stoichiometryHtmlGenerator;
  private RichTextUpdater rtupdater;
  private  ExportToFileConfig cfg;
  @Mock
  private User exporter;
  private String htmlProcessed;
  private File outfile;
  private ExportProcesserInput input;
  private ConversionResult success;

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    cfg = getConfig();
    outfile = File.createTempFile("any", ".doc");
    success = new ConversionResult(outfile, "ms/word");
    input = getAnyHTML();
    mswordExporter = new MSWordProcessor();
    mswordExporter.setDocConverter(converter);
    mswordExporter.setImageHelper(imageRetriever);
    ReflectionTestUtils.setField(mswordExporter, "velocityEngine", velocityEngine);
    ReflectionTestUtils.setField(mswordExporter, "stoichiometryHtmlGenerator", stoichiometryHtmlGenerator);
    rtupdater = new RichTextUpdater();
    rtupdater.setVelocity(setupVelocityWithTextFieldTemplates());
    htmlProcessed = Jsoup.parse(STOICHIOMETRY_HTML).html();
    when(stoichiometryHtmlGenerator.addStoichiometryLinks(eq(htmlProcessed),eq(exporter))).thenReturn(
        htmlProcessed);
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
    cfg.setExportFormat("PDF");
    mswordExporter.makeExport(outfile, input, rspaceDoc, cfg);
    verify(imageRetriever, Mockito.never())
        .getImageBytesFromImgSrc(Mockito.anyString(), any(ExportToFileConfig.class));
    verify(converter, never())
        .convert(any(Convertible.class), eq("doc"), eq(outfile));
  }

  @NotNull
  private ExportProcesserInput getAnyHTML() {
    return createAnyHTML();
  }

  @Test
  public void testMakeExportSimpleHappyCaseNoImages() throws IOException {
    returnSuccesfullExport(outfile, success);
    mswordExporter.makeExport(outfile, input, rspaceDoc, cfg);
    Mockito.verify(imageRetriever, never())
        .getImageBytesFromImgSrc(Mockito.anyString(), any(ExportToFileConfig.class));
    verify(converter, atMost(1))
        .convert(any(Convertible.class), eq("doc"), eq(outfile));
  }

  @Test
  public void testMakeExportAddsStyling() throws IOException {
    returnSuccesfullExport(outfile, success);
    mswordExporter.makeExport(outfile, input, rspaceDoc, cfg);
    verify(velocityEngine).mergeTemplate(eq("doc/styles.vm"),eq("UTF-8"), any(VelocityContext.class), any(Writer.class));
  }

  private void returnSuccesfullExport(File outfile, ConversionResult success) {
    Mockito.when(
            converter.convert(
                any(Convertible.class), eq("doc"), eq(outfile)))
        .thenReturn(success);
  }

  @Test
  public void testStoichiometryExportIsAdded() throws IOException {
    returnSuccesfullExport(outfile, success);
    mswordExporter.makeExport(outfile, input, rspaceDoc, cfg);
    verify(stoichiometryHtmlGenerator, never()).addStoichiometryLinks(STOICHIOMETRY_HTML,cfg.getExporter());
    input = createStoichiometryHTML();
    success = new ConversionResult(outfile, "ms/word");
    returnSuccesfullExport(outfile, success);
    mswordExporter.makeExport(outfile, input, rspaceDoc, cfg);
    verify(stoichiometryHtmlGenerator).addStoichiometryLinks(htmlProcessed,cfg.getExporter());
  }

  @Test
  public void testMakeExportWithImages() throws IOException {
    input = createAnyHTMLWithImage();
    when(converter.convert(any(Convertible.class), eq("doc"), eq(outfile)))
        .thenReturn(success);
    when(imageRetriever.getImageBytesFromImgSrc(Mockito.anyString(), eq(cfg)))
        .thenReturn(new byte[] {1, 2, 3, 4});
    mswordExporter.makeExport(outfile, input, rspaceDoc, cfg);
    verify(imageRetriever, atMost(1))
        .getImageBytesFromImgSrc(Mockito.anyString(), any(ExportToFileConfig.class));
    verify(converter, atMost(1))
        .convert(any(Convertible.class), eq("doc"), eq(outfile));
  }

  private ExportProcesserInput createAnyHTMLWithImage() {
    EcatImage image = TestFactory.createEcatImage(1L);
    String imgHtml = rtupdater.generateURLStringForEcatImageLink(image, 2 + "");
    return new ExportProcesserInput(imgHtml, Collections.emptyList(), null, null);
  }

  private ExportProcesserInput createAnyHTML() {
    return new ExportProcesserInput("<html/>", Collections.emptyList(), null, null);
  }

  private ExportProcesserInput createStoichiometryHTML() {
    return new ExportProcesserInput(STOICHIOMETRY_HTML, Collections.emptyList(), null, null);
  }

  private ExportToFileConfig getConfig() {
    ExportToFileConfig cfg = new ExportToFileConfig();
    cfg.setExportFormat("WORD");
    cfg.setExporter(exporter);
    return cfg;
  }
}
