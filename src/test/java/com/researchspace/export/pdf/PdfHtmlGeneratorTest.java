package com.researchspace.export.pdf;

import static com.researchspace.export.pdf.PdfHtmlGenerator.MAX_TITLE_WIDTH;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.archive.ArchivalNfsFile;
import com.researchspace.model.User;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.testutils.RSpaceTestUtils;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.apache.velocity.app.VelocityEngine;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Entities;
import org.jsoup.nodes.Node;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class PdfHtmlGeneratorTest {

  PdfHtmlGenerator pdfHtmlGenerator;

  ExportProcesserInput input;

  String basicHtmlDoc;

  StructuredDocument doc;

  ExportToFileConfig config;

  @BeforeEach
  public void setUp() throws Exception {
    VelocityEngine velocityEngine =
        RSpaceTestUtils.setupVelocity("src/main/resources/velocityTemplates");
    pdfHtmlGenerator = new PdfHtmlGenerator(velocityEngine, new HTMLUnicodeFontProcesser());
    basicHtmlDoc = RSpaceTestUtils.loadTextResourceFromPdfDir("basic.html");
    User user = new User();
    user.setFirstName("Some");
    user.setLastName("User");
    doc = new StructuredDocument(new RSForm());
    doc.setOwner(user);
    config = new ExportToFileConfig();
    config.setExporter(user);
  }

  private boolean htmlElementContains(String html, String element, String value) {
    org.jsoup.nodes.Document document = Jsoup.parse(html);
    document
        .outputSettings()
        .syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml)
        .escapeMode(Entities.EscapeMode.xhtml)
        .charset("UTF-8");
    Node elementNode = document.selectFirst(element);

    return elementNode.toString().contains(value);
  }

  @ParameterizedTest
  @ValueSource(strings = {"A4", "LETTER"})
  public void cssPageSizeSetAccordingToConfig(String pageSize) {
    config.setPageSize(pageSize);
    input =
        new ExportProcesserInput(
            basicHtmlDoc, Collections.emptyList(), new RevisionInfo(), Collections.emptyList());

    String processedHtml = pdfHtmlGenerator.prepareHtml(input, doc, config);

    Assertions.assertTrue(htmlElementContains(processedHtml, "style", "size: " + pageSize));
  }

  @Test
  public void longDocTitlesAreAbbreviated() {
    String sixtyCharacterTitle = "xL90cb20FbcssVX7yK8nG1RAVX265zVpPo3HwRK4yVNUd64tSJ2Wz2T0zFP1";
    doc.setName(sixtyCharacterTitle);
    // abbreviated titles end with ellipsis ..., replacing the last 3 chars of the shortened title
    String shortenedTitle = sixtyCharacterTitle.substring(0, MAX_TITLE_WIDTH - 3) + "...";
    input =
        new ExportProcesserInput(
            basicHtmlDoc, Collections.emptyList(), new RevisionInfo(), Collections.emptyList());

    String processedHtml = pdfHtmlGenerator.prepareHtml(input, doc, config);

    // output contains the shortened title with ellipsis but not the original one
    Assertions.assertTrue(
        htmlElementContains(processedHtml, "div.runningHeaderCenter", shortenedTitle));
    Assertions.assertFalse(processedHtml.contains(sixtyCharacterTitle));
  }

  @Test
  public void addsComments() {
    List<CommentAppendix> comments = makeComments();
    input =
        new ExportProcesserInput(
            basicHtmlDoc, comments, new RevisionInfo(), Collections.emptyList());

    String processedHtml = pdfHtmlGenerator.prepareHtml(input, doc, config);

    String expectedFirstComment = "Some User on 2024-04-05 11:30: some comment";
    String expectedSecondComment = "Some User on 2024-04-05 11:30: some other comment";
    Assertions.assertTrue(htmlElementContains(processedHtml, "#comments", expectedFirstComment));
    Assertions.assertTrue(htmlElementContains(processedHtml, "#comments", expectedSecondComment));
  }

  @Test
  public void addsNfsLinks() {
    List<ArchivalNfsFile> files = makeArchiveFileList();
    input =
        new ExportProcesserInput(basicHtmlDoc, Collections.emptyList(), new RevisionInfo(), files);
    String nfsTableRowSelector = "#nfs > table > tbody > tr";
    ArchivalNfsFile file = files.get(0);

    String processedHtml = pdfHtmlGenerator.prepareHtml(input, doc, config);

    Assertions.assertTrue(
        htmlElementContains(
            processedHtml,
            nfsTableRowSelector,
            String.format("<td>%s</td>", file.getFileSystemId())));
    Assertions.assertTrue(
        htmlElementContains(
            processedHtml,
            nfsTableRowSelector,
            String.format("<td>%s</td>", file.getFileSystemUrl())));
    Assertions.assertTrue(
        htmlElementContains(
            processedHtml,
            nfsTableRowSelector,
            String.format("<td>%s</td>", file.getFileStorePath() + file.getRelativePath())));
  }

  @Test
  public void addProvenance() {
    RevisionInfo revisionInfo = makeRevisionInfo();
    input =
        new ExportProcesserInput(
            basicHtmlDoc, Collections.emptyList(), revisionInfo, Collections.emptyList());
    String provenanceTableRowSelector = "#provenance-table > tbody > tr";

    String processedHtml = pdfHtmlGenerator.prepareHtml(input, doc, config);

    Assertions.assertTrue(
        htmlElementContains(
            processedHtml,
            provenanceTableRowSelector,
            String.format("<td>%s</td>", revisionInfo.getVersion(0))));
    Assertions.assertTrue(
        htmlElementContains(
            processedHtml,
            provenanceTableRowSelector,
            String.format("<td>%s</td>", revisionInfo.getDate(0))));
    Assertions.assertTrue(
        htmlElementContains(
            processedHtml,
            provenanceTableRowSelector,
            String.format("<td>%s</td>", revisionInfo.getName(0))));
    Assertions.assertTrue(
        htmlElementContains(
            processedHtml,
            provenanceTableRowSelector,
            String.format("<td>%s</td>", revisionInfo.getModifyType(0))));
  }

  @Test
  public void addsPageBreakBeforeProvenance() {
    RevisionInfo revisionInfo = makeRevisionInfo();
    input =
        new ExportProcesserInput(
            basicHtmlDoc, Collections.emptyList(), revisionInfo, Collections.emptyList());

    String processedHtml = pdfHtmlGenerator.prepareHtml(input, doc, config);

    Assertions.assertTrue(
        htmlElementContains(processedHtml, "style", "#provenance { page-break-before: always"));
  }

  @Test
  public void addPageBreakBeforeCommentsWhenNoProvenance() {
    List<CommentAppendix> comments = makeComments();
    input =
        new ExportProcesserInput(
            basicHtmlDoc, comments, new RevisionInfo(), Collections.emptyList());

    String processedHtml = pdfHtmlGenerator.prepareHtml(input, doc, config);

    Assertions.assertTrue(
        htmlElementContains(processedHtml, "style", "#comments { page-break-before: always"));
  }

  @Test
  public void addsPageBreakBeforeNfsLinksWhenNoOtherMetaDataAdded() {
    List<ArchivalNfsFile> files = makeArchiveFileList();
    input =
        new ExportProcesserInput(basicHtmlDoc, Collections.emptyList(), new RevisionInfo(), files);

    String processedHtml = pdfHtmlGenerator.prepareHtml(input, doc, config);

    Assertions.assertTrue(
        htmlElementContains(processedHtml, "style", "#nfs { page-break-before: always"));
  }

  @Test
  public void addsFooter() {
    input =
        new ExportProcesserInput(
            basicHtmlDoc, Collections.emptyList(), new RevisionInfo(), Collections.emptyList());
    String todayDate = LocalDate.now().toString();

    String processedHtml = pdfHtmlGenerator.prepareHtml(input, doc, config);

    Assertions.assertTrue(
        htmlElementContains(processedHtml, "#footer", String.format("Export date: %s", todayDate)));
    Assertions.assertTrue(htmlElementContains(processedHtml, "#footer", "Exported by: Some User"));
  }

  @Test
  public void provenanceNotAddedWhenDisabledInConfig() {
    // add revision info but disable printing of provenance information for pdf
    RevisionInfo revisionInfo = makeRevisionInfo();
    config.setProvenance(false);
    input =
        new ExportProcesserInput(
            basicHtmlDoc, Collections.emptyList(), revisionInfo, Collections.emptyList());

    String processedHtml = pdfHtmlGenerator.prepareHtml(input, doc, config);

    // the provenance information shouldn't be present in the body
    Assertions.assertFalse(htmlElementContains(processedHtml, "body", "provenance"));
  }

  @Test
  public void commentsNotAddedWhenDisabledInConfig() {
    // add comments to doc but disable printing in config
    List<CommentAppendix> comments = makeComments();
    config.setComments(false);
    input =
        new ExportProcesserInput(
            basicHtmlDoc, comments, new RevisionInfo(), Collections.emptyList());

    String processedHtml = pdfHtmlGenerator.prepareHtml(input, doc, config);

    // the comments information shouldn't be present in the body
    Assertions.assertFalse(htmlElementContains(processedHtml, "body", "comments"));
  }

  @Test
  public void footerAtBottomOfEachPageWhenConfigured() {
    config.setIncludeFooterAtEndOnly(false);

    input =
        new ExportProcesserInput(
            basicHtmlDoc, Collections.emptyList(), new RevisionInfo(), Collections.emptyList());

    String processedHtml = pdfHtmlGenerator.prepareHtml(input, doc, config);

    // the footer should be defined as a running-footer, to be applied at the bottom of each page
    Assertions.assertTrue(
        htmlElementContains(
            processedHtml, "style", String.format("Export date: %s", LocalDate.now())));
    Assertions.assertTrue(
        htmlElementContains(processedHtml, "div.runningFooterRight", "Exported by: Some User"));
  }

  @Test
  public void footerAppearsAtEndOfFileWhenConfigured() {
    config.setIncludeFooterAtEndOnly(true);

    input =
        new ExportProcesserInput(
            basicHtmlDoc, Collections.emptyList(), new RevisionInfo(), Collections.emptyList());

    String processedHtml = pdfHtmlGenerator.prepareHtml(input, doc, config);

    // the footer should be defined in the body
    Assertions.assertTrue(
        htmlElementContains(
            processedHtml, "body", String.format("Export date: %s", LocalDate.now())));
    Assertions.assertTrue(htmlElementContains(processedHtml, "body", "Exported by: Some User"));

    // the footer info should not be in the CSS as we don't want it applied on each page
    Assertions.assertFalse(
        htmlElementContains(
            processedHtml, "style", String.format("Export date: %s", LocalDate.now())));
    Assertions.assertFalse(htmlElementContains(processedHtml, "style", "Exported by: Some User"));
  }

  @Test
  public void footerContainsCreatedDateWhenConfigured() {
    config.setDateType("NEW");

    input =
        new ExportProcesserInput(
            basicHtmlDoc, Collections.emptyList(), new RevisionInfo(), Collections.emptyList());

    String processedHtml = pdfHtmlGenerator.prepareHtml(input, doc, config);
    String createDate = pdfHtmlGenerator.simpleDateFmt.format(doc.getCreationDate());

    Assertions.assertTrue(
        htmlElementContains(
            processedHtml, "#footer", String.format("Create date: %s", createDate)));
  }

  @Test
  public void footerContainsLastUpdateDateWhenConfigured() {
    config.setDateType("UPD");

    input =
        new ExportProcesserInput(
            basicHtmlDoc, Collections.emptyList(), new RevisionInfo(), Collections.emptyList());

    String processedHtml = pdfHtmlGenerator.prepareHtml(input, doc, config);

    String modificationDate = pdfHtmlGenerator.simpleDateFmt.format(doc.getModificationDate());

    Assertions.assertTrue(
        htmlElementContains(
            processedHtml, "#footer", String.format("Last updated: %s", modificationDate)));
  }

  @Test
  public void htmlCharsEncodingOfDocNameAndOwner() {
    input =
        new ExportProcesserInput(
            basicHtmlDoc, Collections.emptyList(), new RevisionInfo(), Collections.emptyList());

    doc = new StructuredDocument(TestFactory.createAnyForm());
    doc.setName("name with non-ascii &∅∈∌ and html entities &#x3");

    User owner = new User();
    owner.setFirstName("Dev&Ops");
    owner.setLastName("Team");
    doc.setOwner(owner);

    String processedHtml = pdfHtmlGenerator.prepareHtml(input, doc, config);
    // verify doc name chars converted
    assertFalse(
        "unexpected: " + processedHtml,
        processedHtml.contains("&∅∈∌") || processedHtml.contains("&#x3"));
    assertTrue(
        "unexpected: " + processedHtml, processedHtml.contains("name with non-ascii &amp;</span>"));
    assertTrue(
        "unexpected: " + processedHtml,
        processedHtml.contains("<span style=\"font-family: noto sans math;\">∅∈∌ </span>"));
    assertTrue("unexpected: " + processedHtml, processedHtml.contains("&amp;#x3"));
    // verify owner name chars converted
    assertFalse("unexpected: " + processedHtml, processedHtml.contains("Dev&Ops"));
    assertTrue("unexpected: " + processedHtml, processedHtml.contains("Dev&amp;Ops"));
  }

  private List<ArchivalNfsFile> makeArchiveFileList() {
    ArchivalNfsFile file = new ArchivalNfsFile();
    long fileSystemId = 1234L;
    String url = "/some/path/";
    String path = "/another/path";
    String fileStorePath = "/filestore";
    file.setFileSystemId(fileSystemId);
    file.setFileSystemUrl(url);
    file.setRelativePath(path);
    file.setFileStorePath(fileStorePath);

    return List.of(file);
  }

  private RevisionInfo makeRevisionInfo() {
    RevisionInfo revisionInfo = new RevisionInfo();
    revisionInfo.setVersion("1");
    revisionInfo.setDate("");
    revisionInfo.setName("name");
    revisionInfo.setModifyType("ADD");
    revisionInfo.setEmpty(false);
    return revisionInfo;
  }

  private List<CommentAppendix> makeComments() {
    String userName = "Some User";
    String commentDate = "2024-04-05 11:30";
    String content1 = "some comment";
    String content2 = "some other comment";

    CommentAppendix comment = new CommentAppendix();
    comment.add(userName, commentDate, content1);
    comment.add(userName, commentDate, content2);
    return List.of(comment);
  }
}
