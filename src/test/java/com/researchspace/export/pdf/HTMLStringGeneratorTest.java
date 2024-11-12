package com.researchspace.export.pdf;

import static com.researchspace.model.netfiles.NetFilesTestFactory.createAnyNfsFileStore;
import static com.researchspace.model.record.TestFactory.createAnySD;
import static com.researchspace.model.record.TestFactory.createAnySDWithText;
import static com.researchspace.model.record.TestFactory.createAnyUser;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.researchspace.linkedelements.FieldContents;
import com.researchspace.linkedelements.FieldParser;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.field.ChoiceFieldForm;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.repository.spi.ExternalId;
import com.researchspace.repository.spi.IdentifierScheme;
import com.researchspace.service.AuditManager;
import com.researchspace.service.EcatCommentManager;
import com.researchspace.service.NfsManager;
import com.researchspace.service.UserExternalIdResolver;
import com.researchspace.testutils.VelocityTestUtils;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.app.VelocityEngine;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class HTMLStringGeneratorTest {

  private static final String OrcidId = "1234-5678";
  @Rule public MockitoRule rule = MockitoJUnit.rule();
  private @Mock EcatCommentManager commentMgr;
  private @Mock AuditManager auditManager;
  private @Mock NfsManager netFileManager;
  private @Mock FieldParser fieldParser;
  private @Mock UserExternalIdResolver resolver;

  @InjectMocks HTMLStringGeneratorForExport htmlGenerator;
  ExportToFileConfig cfg;
  RichTextUpdater rtu;
  private final int EXPECTED_FIXED_META_COUNT = 6;

  static final String HTML_WITH_EMPTY_COLSPAN =
      "<table><tbody> <tr> <td colspan=\"\" rowspan=\"\" style=\"display:"
          + " table-cell;\">P.1</td></tr><tr> <td colspan=\"2\" rowspan=\"3\" style=\"display:"
          + " table-cell;\">P.2</td></tr><tr> <td colspan=\"\" rowspan=\"\" style=\"display:"
          + " table-cell;\">P.2</td></tr></tbody></table>";

  @Before
  public void setUp() throws Exception {
    ExportFormat.valueOf("WORD");

    htmlGenerator.setUrlPrefix("http://test.com");
    rtu = new RichTextUpdater();
    cfg = new ExportToFileConfig();

    VelocityEngine vel =
        VelocityTestUtils.setupVelocity("src/main/resources/velocityTemplates/textFieldElements");
    rtu.setVelocity(vel);
    // empty nfs elements by default
    when(fieldParser.findFieldElementsInContentForCssClass(
            Mockito.any(FieldContents.class), Mockito.anyString(), Mockito.anyString()))
        .thenReturn(new FieldContents());
    when(resolver.getExternalIdForUser(
            Mockito.any(User.class), Mockito.any(IdentifierScheme.class)))
        .thenReturn(Optional.empty());
  }

  @Test
  public void testGetNfsElements() {
    // given a document with an NfsLink
    final long fileStoreId = 21L;
    final String relativeFilePath = "/file.txt";
    final String relativeFolderPath = "/files";
    String nfsStr =
        rtu.generateURLStringForNfs(fileStoreId, relativeFilePath, false)
            + rtu.generateURLStringForNfs(fileStoreId, relativeFolderPath, true);

    StructuredDocument anyDoc = createAnySDWithText(nfsStr);
    anyDoc.setId(1L);
    // and some parsed content
    FieldContents fieldWithNfsLink = new FieldContents();
    NfsElement nfsFile = new NfsElement(fileStoreId, relativeFilePath);
    fieldWithNfsLink.addElement(nfsFile, "/anylink", NfsElement.class);
    NfsElement nfsFolder = new NfsElement(fileStoreId, relativeFilePath);
    fieldWithNfsLink.addElement(nfsFolder, "/anylink", NfsElement.class);

    Mockito.when(
            fieldParser.findFieldElementsInContentForCssClass(
                Mockito.any(FieldContents.class), Mockito.anyString(), Mockito.anyString()))
        .thenReturn(fieldWithNfsLink);
    NfsFileStore afs = createAnyNfsFileStore(createAnyUser("any"));
    afs.setId(fileStoreId);
    Mockito.when(netFileManager.getNfsFileStore(fileStoreId)).thenReturn(afs);
    // when HTML is generated for PDF
    ExportProcesserInput documentData = htmlGenerator.extractHtmlStr(anyDoc, cfg);
    // then NFS links are set into document Data
    assertTrue(documentData.hasNfsLinks());
    assertEquals(documentData.getNfsLinks().size(), 2);
    assertEquals(
        afs.getFileSystem().getUrl(), documentData.getNfsLinks().get(0).getFileSystemUrl());
    assertEquals(
        afs.getFileSystem().getUrl(), documentData.getNfsLinks().get(1).getFileSystemUrl());
  }

  @Test
  public void testGetComments() {
    String commentStr = rtu.generateURLStringForCommentLink("1");
    StructuredDocument anyDoc = createAnySDWithText(commentStr);
    anyDoc.setId(1L);

    ExportToFileConfig cfg = makeConfig();
    // now set config to not include comments
    cfg.setComments(false);
    ExportProcesserInput documentData = htmlGenerator.extractHtmlStr(anyDoc, cfg);
    verify(commentMgr, never()).getCommentItems(1L);
    assertEquals(0, documentData.getComments().size());
    // now set config to  include comments
    cfg.setComments(true);
    documentData = htmlGenerator.extractHtmlStr(anyDoc, cfg);
    verify(commentMgr, times(1)).getCommentItems(1L);
    assertEquals(1, documentData.getComments().size());
    assertTrue(documentData.hasComments());
    assertNull(documentData.getRevisionInfo());
  }

  // this is so that links in PDFs will work.
  private ExportToFileConfig makeConfig() {
    ExportToFileConfig cfg = new ExportToFileConfig();
    cfg.setComments(true);
    cfg.setProvenance(false);
    return cfg;
  }

  @Test
  public void testMakeLinksAbsolute() {
    EcatDocumentFile doc = TestFactory.createEcatDocument(2L, createAnyUser("any"));
    String attachmentHTML = rtu.generateURLString(doc);
    StructuredDocument anydoc = TestFactory.createAnySDWithText(attachmentHTML);
    anydoc.setId(1L);
    htmlGenerator.setUrlPrefix("http://demo.researchspace.com");
    ExportToFileConfig cfg = makeConfig();
    ExportProcesserInput documentData = htmlGenerator.extractHtmlStr(anydoc, cfg);
    assertTrue(
        documentData.getDocumentAsHtml().contains("http://demo.researchspace.com/Streamfile/2"));

    BaseRecord any = createAnySD();
    any.setId(3L);
    String linkedRecordhtml = rtu.generateURLStringForInternalLink(any);
    anydoc = TestFactory.createAnySDWithText(linkedRecordhtml);
    anydoc.setId(1L);
    documentData = htmlGenerator.extractHtmlStr(anydoc, cfg);
    assertTrue(
        documentData.getDocumentAsHtml(),
        documentData.getDocumentAsHtml().contains("http://demo.researchspace.com/globalId/SD3"));

    String externalLink = rtu.generateAnyURLStringForExternalDocLink();
    anydoc = TestFactory.createAnySDWithText(externalLink);
    anydoc.setId(1L);
    documentData = htmlGenerator.extractHtmlStr(anydoc, cfg);
    String documentAsHtml = documentData.getDocumentAsHtml();
    assertTrue(documentAsHtml.contains("http://demo.researchspace.com"));
    // check metadata elements are added
    org.jsoup.nodes.Document htmlDoc = Jsoup.parse(documentAsHtml);
    assertEquals(EXPECTED_FIXED_META_COUNT, htmlDoc.getElementsByTag("meta").size());

    assertEquals(
        documentAsHtml,
        2,
        StringUtils.countMatches(documentAsHtml, "http://externanalLink.com/someId"));
  }

  @Test
  public void testIncludeMetaInformation() {
    when(resolver.getExternalIdForUser(
            Mockito.any(User.class), Mockito.any(IdentifierScheme.class)))
        .thenReturn(Optional.of(new ExternalId(IdentifierScheme.ORCID, OrcidId)));
    StructuredDocument anyDoc = createAnySDWithText("any");
    anyDoc.setId(1L);
    ExportProcesserInput input = htmlGenerator.extractHtmlStr(anyDoc, cfg);
    String documentAsHtml = input.getDocumentAsHtml();
    // check metadata elements are added
    org.jsoup.nodes.Document htmlDoc = Jsoup.parse(documentAsHtml);
    assertEquals(EXPECTED_FIXED_META_COUNT + 1, htmlDoc.getElementsByTag("meta").size());
    assertTrue(
        htmlDoc.getElementsByTag("meta").stream()
            .anyMatch(el -> el.attr("content").contains(OrcidId)));
    // assert we can put utf chars into MSWord export
    assertTrue(
        htmlDoc.getElementsByTag("meta").stream()
            .anyMatch(el -> el.attr("charset").contains("UTF-8")));
  }

  @Test
  public void testIncludeListOfMaterials() {
    User anyUser = createAnyUser("any");

    // create a doc with list of materials
    StructuredDocument anyDoc = createAnySDWithText("any");
    anyDoc.setId(1L);
    ListOfMaterials lom = new ListOfMaterials();
    lom.setName("test lom");
    Sample testSample = TestFactory.createBasicSampleOutsideContainer(anyUser);
    SubSample testSubSample = testSample.getSubSamples().get(0);
    lom.addMaterial(testSample, null);
    lom.addMaterial(testSubSample, testSubSample.getQuantity());
    anyDoc.getFields().get(0).addListOfMaterials(lom);

    ExportProcesserInput input = htmlGenerator.extractHtmlStr(anyDoc, cfg);
    String documentAsHtml = input.getDocumentAsHtml();
    assertTrue(documentAsHtml, documentAsHtml.contains("test lom"));
    assertTrue(documentAsHtml, documentAsHtml.contains("SAMPLE"));
    assertTrue(documentAsHtml, documentAsHtml.contains("SUBSAMPLE"));
    assertFalse(documentAsHtml, documentAsHtml.contains("CONTAINER"));
  }

  @Test
  public void testScaleImages() {
    String html = "<img width ='1000' height = '1000'/>";
    StructuredDocument anydoc = TestFactory.createAnySDWithText(html);
    anydoc.setId(1L);
    ExportToFileConfig cfg = makeConfig();
    ExportProcesserInput documentData = htmlGenerator.extractHtmlStr(anydoc, cfg);
    // default sclae factor = 67.3% from page width to A4
    assertTrue(documentData.getDocumentAsHtml().contains("width=\"673\""));
    assertTrue(documentData.getDocumentAsHtml().contains("height=\"673\""));

    // this is 69.2% scaling
    cfg.setPageSize("LETTER");
    documentData = htmlGenerator.extractHtmlStr(anydoc, cfg);

    assertTrue(documentData.getDocumentAsHtml().contains("width=\"692\""));
    assertTrue(documentData.getDocumentAsHtml().contains("height=\"692\""));
  }

  @Test
  public void testEmbedIframeFragment() {
    String html = "iframe: <iframe src='https://dummy.source/a?b=c&d=e'/>";
    StructuredDocument anydoc = TestFactory.createAnySDWithText(html);
    anydoc.setId(1L);
    ExportToFileConfig cfg = makeConfig();
    ExportProcesserInput documentData = htmlGenerator.extractHtmlStr(anydoc, cfg);
    assertTrue(
        "unexpected content: " + documentData.getDocumentAsHtml(),
        documentData
            .getDocumentAsHtml()
            .contains(
                "iframe: \n"
                    + "  <p><i>&lt;embedded code from <a"
                    + " href=\"https://dummy.source/a?b=c&amp;d=e\">"
                    + "https://dummy.source/a?b=c&amp;d=e</a>&gt;</i></p>"));
  }

  /** Tests that XSS doesn't work in the document's name & field names on export / preview */
  @Test
  public void testHtmlEscaping() {
    RSForm form = new RSForm("form", "desc", createAnyUser("user"));
    TextFieldForm fieldForm = new TextFieldForm();
    fieldForm.setName("<img src='' onerror='alert(1);'>");
    fieldForm.setDefaultValue("hmmm");
    form.addFieldForm(fieldForm);
    StructuredDocument doc = createAnySD(form);
    doc.setId(1L);
    doc.setName("<img src='' onerror='alert(2);'>name with special html chars &∅∈∌");
    ExportToFileConfig cfg = makeConfig();
    ExportProcesserInput documentData = htmlGenerator.extractHtmlStr(doc, cfg);
    String data = documentData.getDocumentAsHtml();
    assertFalse(data.contains("<img"));
    // Additional checks to be sure the data wasn't just thrown out completely
    assertTrue(data.contains("&lt;img"));
    assertTrue(data.contains("alert(1)"));
    assertTrue(data.contains("alert(2)"));
    // html chars in name escaped
    assertFalse(data.contains("special html chars &∅∈∌"));
    assertTrue(
        "unexpected:" + data,
        data.contains("special html chars &amp;amp;&amp;empty;&amp;isin;&amp;#8716;"));
  }

  @Test
  // rspac-2486
  public void testEmptyColRowSpanStripping() {

    assertTrue(HTML_WITH_EMPTY_COLSPAN.contains("colspan=\"\""));
    assertTrue(HTML_WITH_EMPTY_COLSPAN.contains("rowspan=\"\""));
    Document jsoupDoc = Jsoup.parse(HTML_WITH_EMPTY_COLSPAN);
    htmlGenerator.preProcess(jsoupDoc);
    String htmlStr = jsoupDoc.html();

    assertFalse(htmlStr.contains("colspan=\"\""));
    assertFalse(htmlStr.contains("rowspan=\"\""));
    assertTrue(htmlStr.contains("rowspan=\"3\""));
    assertTrue(htmlStr.contains("colspan=\"2\""));
  }

  @Test
  public void choiceFieldsPrintCorrectly() {
    ChoiceFieldForm choiceFieldForm = new ChoiceFieldForm();
    choiceFieldForm.setName("A choice name");
    choiceFieldForm.setChoiceOptions("fieldChoices=a&fieldChoices=b&fieldChoices=c");
    choiceFieldForm.setDefaultChoiceOption("fieldSelectedChoices=a&fieldSelectedChoices=b");
    RSForm form = new RSForm("form", "desc", createAnyUser("user"));
    form.setFieldForms(List.of(choiceFieldForm));

    StructuredDocument structuredDocument = createAnySD(form);
    structuredDocument.setId(1L);
    ExportToFileConfig config = makeConfig();

    ExportProcesserInput exportProcesserInput =
        htmlGenerator.extractHtmlStr(structuredDocument, config);
    String html = exportProcesserInput.getDocumentAsHtml();

    assertTrue(
        "html doesn't contain selected choices 'a, b'. html is: " + html,
        html.contains("<p>a, b</p>"));
    assertFalse(
        "html incorrectly contains 'fieldSelectedChoices=' which should be stripped. html is: "
            + html,
        html.contains("fieldSelectedChoices="));
    assertFalse(
        "html incorrectly contains 'fieldChoices=' which should be stripped. html is: " + html,
        html.contains("fieldChoices="));
  }
}
