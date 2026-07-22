package com.researchspace.export.pdf;

import com.researchspace.archive.ArchivalNfsFile;
import com.researchspace.core.util.StringAbbreviationUtils;
import com.researchspace.export.pdf.ExportToFileConfig.DATE_FOOTER_PREF;
import com.researchspace.export.stoichiometry.StoichiometryHtmlGenerator;
import com.researchspace.model.core.IRSpaceDoc;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.LocaleBoundMessages;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserLocaleService;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.spring.VelocityEngineUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Creates the HTML output required for conversion of {@link StructuredDocument} to PDF. In addition
 * to the conversion from {@link StructuredDocument} to HTML performed by {@link
 * HTMLStringGenerator#extractHtmlStr}, for PDF export we additionally need to add the HTML
 * representation of "document extras" such as comments, provenance etc., as well as adding the HTML
 * for headers and footers.
 */
@Service
public class PdfHtmlGenerator {
  @Autowired private StoichiometryHtmlGenerator stoichiometryHtmlGenerator;
  @Autowired private MessageSourceUtils messages;
  @Autowired private UserLocaleService userLocaleService;
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
      ExportProcessorInput documentData, IRSpaceDoc doc, ExportToFileConfig config) {
    Locale locale = userLocaleService.getLocaleFor(config.getExporter());
    String docTitle = StringAbbreviationUtils.abbreviate(doc.getName(), MAX_TITLE_WIDTH);
    String pageSize = config.getPageSize().equals("A4") ? "A4" : "LETTER";
    String footerFormattedDate = formatFooterDate(doc, config, locale);

    String html = documentData.getDocumentAsHtml();
    html =
        addStyleElement(
            html,
            makeHtmlStyleElement(
                pageSize, !config.isIncludeFooterAtEndOnly(), footerFormattedDate, locale));
    html =
        addEachPageElems(
            html,
            doc.getOwner().getFullName(),
            docTitle,
            config.getExporter().getFullName(),
            locale);
    html = prepareTables(html);
    html = addDocExtras(documentData, config, html, locale);
    if (config.isIncludeFooterAtEndOnly()) {
      html = addFooterAtEnd(html, config.getExporter().getFullName(), footerFormattedDate, locale);
    }
    return html;
  }

  String formatFooterDate(IRSpaceDoc doc, ExportToFileConfig config, Locale locale) {
    DATE_FOOTER_PREF format = config.getDateTypeEnum();
    if (format == DATE_FOOTER_PREF.NEW) {
      return messages.getMessage(
          "export.pdf.footer.createDate",
          new Object[] {simpleDateFmt.format(doc.getCreationDate())},
          locale);
    } else if (format == DATE_FOOTER_PREF.UPD) {
      return messages.getMessage(
          "export.pdf.footer.lastUpdated",
          new Object[] {simpleDateFmt.format(doc.getModificationDateAsDate())},
          locale);
    } else {
      return messages.getMessage(
          "export.pdf.footer.exportDate", new Object[] {LocalDate.now().toString()}, locale);
    }
  }

  private String makeHtmlStyleElement(
      String pageSize, boolean footerEachPage, String footerDate, Locale locale) {
    Map<String, Object> context = new HashMap<>();
    context.put("pageSize", pageSize);
    context.put("footerEachPage", footerEachPage);
    context.put("footerDate", footerDate);
    context.put(
        "pageLabel",
        messages.getMessage("export.pdf.header.pageLabel", null, locale).replace("'", "\\'"));
    return VelocityEngineUtils.mergeTemplateIntoString(
        velocityEngine, "pdf/styles.vm", "UTF-8", context);
  }

  private String addEachPageElems(
      String html, String docOwner, String docTitle, String exporterFullName, Locale locale) {

    Map<String, Object> context = new HashMap<>();
    context.put("docOwner", StringEscapeUtils.escapeHtml4(docOwner));
    context.put("docTitle", StringEscapeUtils.escapeHtml4(docTitle));
    context.put("exporterFullName", StringEscapeUtils.escapeHtml4(exporterFullName));
    context.put("msg", new LocaleBoundMessages(messages, locale));
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
      table.addClass("docContentTable");
    }
    return doc.html();
  }

  private String addDocExtras(
      ExportProcessorInput documentData, ExportToFileConfig config, String html, Locale locale) {
    // a page break should be inserted (only) once after the main content and before the extra info.
    // where it should be inserted depends on which extras (if any) are to be included in the
    // exported doc.
    boolean newPageAddedForDocExtras = false;
    if (config.isComments() && documentData.hasComments()) {
      html = addNewPageBefore(html, "comments");
      newPageAddedForDocExtras = true;
      html = addComments(html, documentData.getComments(), locale);
    }

    if (config.isProvenance() && documentData.hasRevisionInfo()) {
      if (!newPageAddedForDocExtras) {
        html = addNewPageBefore(html, "provenance");
        newPageAddedForDocExtras = true;
      }
      html = addProvenance(html, documentData.getRevisionInfo(), locale);
    }
    if (documentData.hasStoichiometryTable()) {
      html = stoichiometryHtmlGenerator.addStoichiometryLinks(html, config.getExporter());
    }

    if (documentData.hasNfsLinks()) {
      if (!newPageAddedForDocExtras) {
        html = addNewPageBefore(html, "nfs");
      }
      html = addNfsLinks(html, documentData.getNfsLinks(), locale);
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

  private String addNfsLinks(String html, List<ArchivalNfsFile> nfsLinks, Locale locale) {
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
    context.put("msg", new LocaleBoundMessages(messages, locale));
    String nfsTableHtml =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocityEngine, "pdf/nfs-table.vm", "UTF-8", context);
    return appendToEndOfBody(html, nfsTableHtml);
  }

  private String addProvenance(String html, RevisionInfo revisionInfo, Locale locale) {
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
    context.put("msg", new LocaleBoundMessages(messages, locale));
    String provenanceTableHtml =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocityEngine, "pdf/provenance-table.vm", "UTF-8", context);
    return appendToEndOfBody(html, provenanceTableHtml);
  }

  private String addComments(String html, List<CommentAppendix> commentAppendices, Locale locale) {
    Map<Integer, List<String>> commentNumberToComments = new HashMap<>();
    int count = 1;
    for (CommentAppendix commentAppendix : commentAppendices) {
      List<String> comments = new ArrayList<>();
      for (int i = 0; i < commentAppendix.getSize(); i++) {
        String name = commentAppendix.getItemName(i);
        String date = commentAppendix.getItemDate(i);
        String content = commentAppendix.getItemContent(i);
        String comment =
            messages.getMessage(
                "export.pdf.comments.line", new Object[] {name, date, content}, locale);
        comments.add(comment);
      }
      commentNumberToComments.put(count, comments);
      count++;
    }
    Map<String, Object> context = new HashMap<>();
    context.put("commentNumberToComments", commentNumberToComments);
    context.put("msg", new LocaleBoundMessages(messages, locale));
    String commentsHtml =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocityEngine, "pdf/comments.vm", "UTF-8", context);
    return appendToEndOfBody(html, commentsHtml);
  }

  private String addFooterAtEnd(
      String html, String fullName, String footerFormattedDate, Locale locale) {
    Map<String, Object> context = new HashMap<>();
    context.put("name", fullName);
    context.put("dateLabel", footerFormattedDate);
    context.put("msg", new LocaleBoundMessages(messages, locale));
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
