package com.researchspace.export.pdf;

import com.researchspace.archive.ArchivalNfsFile;
import com.researchspace.export.pdf.ExportToFileConfig.DATE_FOOTER_PREF;
import com.researchspace.model.core.IRSpaceDoc;
import com.researchspace.model.record.StructuredDocument;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang.StringUtils;
import org.apache.velocity.app.VelocityEngine;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ui.velocity.VelocityEngineUtils;

/**
 * Creates the HTML output required for conversion of {@link StructuredDocument} to PDF. In addition
 * to the conversion from {@link StructuredDocument} to HTML performed by {@link
 * HTMLStringGenerator#extractHtmlStr}, for PDF export we additionally need to add the HTML
 * representation of "document extras" such as comments, provenance etc., as well as adding the HTML
 * for headers and footers.
 */
@Service
public class PdfHtmlGenerator {

  private final VelocityEngine velocityEngine;

  private HTMLUnicodeFontProcesser htmlUnicodeFontProcesser;

  public static final int MAX_TITLE_WIDTH = 50;

  public final SimpleDateFormat simpleDateFmt = new SimpleDateFormat("yyyy-MM-dd");

  @Autowired
  public PdfHtmlGenerator(VelocityEngine velocityEngine, HTMLUnicodeFontProcesser fontProcessor) {
    this.velocityEngine = velocityEngine;
    this.htmlUnicodeFontProcesser = fontProcessor;
  }

  public String prepareHtml(
      ExportProcesserInput documentData, IRSpaceDoc doc, ExportToFileConfig config) {
    String docTitle = StringUtils.abbreviate(doc.getName(), MAX_TITLE_WIDTH);
    String pageSize = config.getPageSize().equals("A4") ? "A4" : "LETTER";
    String footerFormattedDate = formatFooterDate(doc, config);

    String html = documentData.getDocumentAsHtml();
    html =
        addStyleElement(
            html,
            makeHtmlStyleElement(
                pageSize, !config.isIncludeFooterAtEndOnly(), footerFormattedDate));
    html =
        addEachPageElems(
            html, doc.getOwner().getFullName(), docTitle, config.getExporter().getFullName());
    html = prepareTables(html);
    html = addDocExtras(documentData, config, html);
    if (config.isIncludeFooterAtEndOnly()) {
      html = addFooterAtEnd(html, config.getExporter().getFullName(), footerFormattedDate);
    }
    return html;
  }

  private String formatFooterDate(IRSpaceDoc doc, ExportToFileConfig config) {
    String footerFormattedDate;
    DATE_FOOTER_PREF format = config.getDateTypeEnum();
    if (format == DATE_FOOTER_PREF.NEW) {
      footerFormattedDate = "Create date: " + simpleDateFmt.format(doc.getCreationDate());
    } else if (format == DATE_FOOTER_PREF.UPD) {
      footerFormattedDate =
          "Last updated: " + simpleDateFmt.format(doc.getModificationDateAsDate());
    } else {
      footerFormattedDate = "Export date: " + LocalDate.now();
    }
    return footerFormattedDate;
  }

  private String makeHtmlStyleElement(String pageSize, boolean footerEachPage, String footerDate) {
    Map<String, Object> context = new HashMap<>();
    context.put("pageSize", pageSize);
    context.put("footerEachPage", footerEachPage);
    context.put("footerDate", footerDate);
    return VelocityEngineUtils.mergeTemplateIntoString(
        velocityEngine, "pdf/styles.vm", "UTF-8", context);
  }

  private String addEachPageElems(
      String html, String docOwner, String docTitle, String exporterFullName) {

    Map<String, Object> context = new HashMap<>();
    context.put("docOwner", docOwner);
    context.put("docTitle", docTitle);
    context.put("exporterFullName", exporterFullName);
    String runningPageHtml =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocityEngine, "pdf/runningPageElems.vm", "UTF-8", context);
    runningPageHtml = htmlUnicodeFontProcesser.apply(runningPageHtml);
    return appendToStartOfBody(html, runningPageHtml);
  }

  private Document stringToJsoupDoc(String html) {
    Document doc = Jsoup.parse(html);
    doc.outputSettings()
        .syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml)
        .escapeMode(Entities.EscapeMode.xhtml)
        .charset("UTF-8");
    return doc;
  }

  /*
  On tables created within documents, some <col> and <td> elements come with specified widths.
  These need to be removed otherwise the styling we apply (using the added class 'docContentTable')
  to help fit tables onto a page is ignored.
  */
  private String prepareTables(String html) {
    Document doc = stringToJsoupDoc(html);
    Elements cols = doc.getElementsByTag("col");
    for (Element col : cols) {
      col.removeAttr("width");
    }

    Elements tds = doc.getElementsByTag("td");
    for (Element td : tds) {
      td.removeAttr("style");
      td.removeAttr("width");
    }

    Elements tables = doc.getElementsByTag("table");
    for (Element table : tables) {
      table.attr("class", "docContentTable");
    }
    return doc.html();
  }

  private String addDocExtras(
      ExportProcesserInput documentData, ExportToFileConfig config, String html) {
    // a page break should be inserted (only) once after the main content and before the extra info.
    // where it should be inserted depends on which extras (if any) are to be included in the
    // exported doc.
    boolean newPageAddedForDocExtras = false;
    if (config.isComments() && documentData.hasComments()) {
      html = addNewPageBefore(html, "comments");
      newPageAddedForDocExtras = true;
      html = addComments(html, documentData.getComments());
    }

    if (config.isProvenance() && documentData.hasRevisionInfo()) {
      if (!newPageAddedForDocExtras) {
        html = addNewPageBefore(html, "provenance");
        newPageAddedForDocExtras = true;
      }
      html = addProvenance(html, documentData.getRevisionInfo());
    }

    if (documentData.hasNfsLinks()) {
      if (!newPageAddedForDocExtras) {
        html = addNewPageBefore(html, "nfs");
      }
      html = addNfsLinks(html, documentData.getNfsLinks());
    }
    return html;
  }

  private String addNewPageBefore(String html, String element) {
    Document doc = Jsoup.parse(html);
    Element styleTag = doc.getElementsByTag("style").first();
    String originalStyles = styleTag.html();
    styleTag.text(
        originalStyles.replace(
            "#" + element + " {", "#" + element + " { page-break-before: always; "));
    return doc.toString();
  }

  private String addNfsLinks(String html, List<ArchivalNfsFile> nfsLinks) {
    List<NfsTableData> files = new ArrayList<>();
    for (ArchivalNfsFile nfsLink : nfsLinks) {
      files.add(
          new NfsTableData(
              nfsLink.getFileSystemId().toString(),
              nfsLink.getFileSystemUrl(),
              nfsLink.getFileStorePath() + nfsLink.getRelativePath().replaceAll("//", "/")));
    }
    Map<String, Object> context = new HashMap<>();
    context.put("files", files);
    String nfsTableHtml =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocityEngine, "pdf/nfs-table.vm", "UTF-8", context);
    return appendToEndOfBody(html, nfsTableHtml);
  }

  private String addProvenance(String html, RevisionInfo revisionInfo) {
    List<ProvenanceTableData> modifications = new ArrayList<>();
    for (int i = 0; i < revisionInfo.getSize(); i++) {
      String version = revisionInfo.getVersion(i);
      String date = revisionInfo.getDate(i);
      String name = revisionInfo.getName(i);
      String modifyType = revisionInfo.getModifyType(i);
      ProvenanceTableData modification = new ProvenanceTableData(version, date, name, modifyType);
      modifications.add(modification);
    }
    Map<String, Object> context = new HashMap<>();
    context.put("modifications", modifications);
    String provenanceTableHtml =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocityEngine, "pdf/provenance-table.vm", "UTF-8", context);
    return appendToEndOfBody(html, provenanceTableHtml);
  }

  private String addComments(String html, List<CommentAppendix> commentAppendices) {
    Map<Integer, List<String>> commentNumberToComments = new HashMap<>();
    int count = 1;
    for (CommentAppendix commentAppendix : commentAppendices) {
      List<String> comments = new ArrayList<>();
      for (int i = 0; i < commentAppendix.getSize(); i++) {
        String name = commentAppendix.getItemName(i);
        String date = commentAppendix.getItemDate(i);
        String content = commentAppendix.getItemContent(i);
        String comment = name + date + content;
        comments.add(comment);
      }
      commentNumberToComments.put(count, comments);
      count++;
    }
    Map<String, Object> context = new HashMap<>();
    context.put("commentNumberToComments", commentNumberToComments);
    String commentsHtml =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocityEngine, "pdf/comments.vm", "UTF-8", context);
    return appendToEndOfBody(html, commentsHtml);
  }

  private String addFooterAtEnd(String html, String fullName, String footerFormattedDate) {
    Map<String, Object> context = new HashMap<>();
    context.put("name", fullName);
    context.put("dateLabel", footerFormattedDate);
    String footerHtml =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocityEngine, "pdf/footer.vm", "UTF-8", context);
    return appendToEndOfBody(html, footerHtml);
  }

  private String addStyleElement(String html, String styles) {
    Document document = stringToJsoupDoc(html);
    Element head = document.head();
    head.append(styles);
    return document.toString();
  }

  private String appendToStartOfBody(String html, String toAppend) {
    Document document = stringToJsoupDoc(html);
    List<Node> bodyChildren = document.selectFirst("body").childNodes();
    bodyChildren.get(0).before(toAppend);
    return document.toString();
  }

  private String appendToEndOfBody(String html, String toAppend) {
    Document document = stringToJsoupDoc(html);
    List<Node> bodyChildren = document.selectFirst("body").childNodes();
    bodyChildren.get(bodyChildren.size() - 1).after(toAppend);
    return document.toString();
  }

  @Data
  @AllArgsConstructor
  public static class NfsTableData {
    private final String fileSystemId;
    private final String fileSystemUrl;
    private final String fileStorePath;
  }

  @Data
  @AllArgsConstructor
  public static class ProvenanceTableData {
    private final String version;
    private final String date;
    private final String name;
    private final String modificationType;
  }
}
