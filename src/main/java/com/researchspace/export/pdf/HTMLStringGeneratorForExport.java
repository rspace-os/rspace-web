package com.researchspace.export.pdf;

import static org.apache.commons.lang.StringEscapeUtils.escapeHtml;
import static org.apache.commons.lang.StringUtils.isEmpty;

import com.researchspace.archive.ArchivalNfsFile;
import com.researchspace.archive.model.ArchiveModelFactory;
import com.researchspace.core.util.FieldParserConstants;
import com.researchspace.linkedelements.FieldContents;
import com.researchspace.linkedelements.FieldParser;
import com.researchspace.model.EcatCommentItem;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.elninventory.MaterialUsage;
import com.researchspace.model.field.ChoiceField;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.TextField;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.repository.spi.ExternalId;
import com.researchspace.repository.spi.IdentifierScheme;
import com.researchspace.service.AuditManager;
import com.researchspace.service.EcatCommentManager;
import com.researchspace.service.NfsManager;
import com.researchspace.service.UserExternalIdResolver;
import com.researchspace.session.SessionTimeZoneUtils;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Document.OutputSettings;
import org.jsoup.nodes.Document.OutputSettings.Syntax;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * Generates single complete string of HTML from a structured document, for converting to an export
 * format. <br>
 * It also:
 *
 * <ul>
 *   <li>Prefixes attachment links with the baseURL so that links to attachments can still work from
 *       the export artifact
 *   <li>Scales images to the configured page size.
 *   <li>Extracts revision and comment information
 * </ul>
 *
 * <h3>Implementation note</h3>
 *
 * Pdf generator expects XHTML input. JSoup by default generates HTML (e.g. &lt;img&gt; tags lack
 * closing tags in HTML). <br>
 * So any code in this process that converts a JSoup document to string must set OutputFormatter to
 * Syntax.xml, e.g.
 *
 * <pre>
 * OutputSettings output = new OutputSettings().syntax(Syntax.xml);
 * jsoupDoc.outputSettings(output);
 * </pre>
 *
 * before calling jsoupDoc.html()
 *
 * <p>SECURITY NOTE: Make sure to `escapeHtml()` any strings with user input in this class, because
 * we return the final HTML here.
 */
public class HTMLStringGeneratorForExport implements HTMLStringGenerator {

  @Value("${server.urls.prefix}")
  private String urlPrefix;

  private @Autowired EcatCommentManager commentManager;
  private @Autowired AuditManager auditManager;
  private @Autowired NfsManager nfsManager;
  private @Autowired FieldParser fieldParser;
  private @Autowired UserExternalIdResolver extIdResolver;

  void setUrlPrefix(String urlPrefix) {
    this.urlPrefix = urlPrefix;
  }

  private Logger log = LoggerFactory.getLogger(getClass());

  List<CommentAppendix> parseCommentImg(
      StructuredDocumentHTMLViewConfig exportConfig, org.jsoup.nodes.Document jsoupDoc) {
    List<Long> lst = new ArrayList<Long>();
    int cnt = 1;
    try {
      Elements cms = jsoupDoc.getElementsByClass("commentIcon");
      for (Element cm : cms) {
        String cmmId = cm.attr("id");
        lst.add(Long.parseLong(cmmId));
        String idc = " [comment: " + Integer.toString(cnt) + "] ";
        cm.replaceWith(new org.jsoup.nodes.Element(Tag.valueOf("span"), "").text(idc));
        // cm.append(idc);
        log.info(cm.toString());
        cnt++;
      }
    } catch (Exception ex) {
      log.error(ex.toString());
    }
    return createCommentAppendix(exportConfig, lst);
  }

  /* (non-Javadoc)
   * @see com.researchspace.export.pdf.HTMLStringGenerator#extractHtmlStr(com.researchspace.model.record.StructuredDocument, com.researchspace.export.pdf.StructuredDocumentHTMLViewConfig)
   */
  @Override
  public ExportProcesserInput extractHtmlStr(
      StructuredDocument strucDoc, StructuredDocumentHTMLViewConfig exportConfig) {
    StringBuffer sbf = new StringBuffer();
    String docName = escapeHtml(strucDoc.getName());
    String globalidLink = makeGlobalIdLink(strucDoc.getGlobalIdentifier());
    String nameLink = "<p>" + docName + "&nbsp;" + globalidLink + "</p>";
    sbf.append(nameLink);
    List<Field> flds = strucDoc.getFields();
    for (Field field : flds) {
      try {
        String fieldName = escapeHtml(field.getName());
        sbf.append("<h3>" + fieldName + "</h3>");
        String fieldContent =
            !(field instanceof ChoiceField)
                ? field.getFieldData()
                : ((ChoiceField) field).getChoiceOptionSelectedAsString();
        if (fieldContent == null || fieldContent.length() < 1) {
          fieldContent = " ";
        }
        if (field instanceof TextField) {
          sbf.append(fieldContent);
        } else {
          fieldContent = "<p>" + fieldContent + "</p>";
          sbf.append(fieldContent);
        }
        if (exportConfig.isIncludeFieldLastModifiedDate()) {
          appendFieldLastModifiedDate(sbf, field);
        }

        if (!field.getListsOfMaterials().isEmpty()) {
          appendListsOfMaterials(sbf, field.getListsOfMaterials());
        }

      } catch (Exception ex) {
        log.warn("Unexpected exception: " + ex.getMessage());
      } // may be cast failed
    }
    String htmlStr = sbf.toString();
    org.jsoup.nodes.Document jsoupDoc = Jsoup.parse(htmlStr);
    preProcess(jsoupDoc);
    addMetaData(strucDoc, jsoupDoc);
    List<CommentAppendix> comments = new ArrayList<>();
    if (exportConfig.isComments()) {
      comments = parseCommentImg(exportConfig, jsoupDoc);
    }
    List<ArchivalNfsFile> nfsLinks = extractNfsLinks(strucDoc, htmlStr);
    scaleImages(exportConfig, jsoupDoc);
    addBaseURLToInternalLinks(jsoupDoc);
    replaceIframesWithEmbedCodeLink(jsoupDoc);

    OutputSettings output = new OutputSettings().syntax(Syntax.xml);
    jsoupDoc.outputSettings(output);
    htmlStr = jsoupDoc.html();
    RevisionInfo revisionInfo = null;
    if (exportConfig.isProvenance()) {
      revisionInfo = getAuditHistory(strucDoc);
    }
    htmlStr = new SvgToPngConverter().replaceSvgObjectWithImg(htmlStr);
    return new ExportProcesserInput(htmlStr, comments, revisionInfo, nfsLinks);
  }

  /*
   * package-scoped for testing
   * Modifies JSoup document in place.
   */
  void preProcess(Document jsoupDoc) {
    removeEmptyRowColSpans(jsoupDoc);
  }

  private void removeEmptyRowColSpans(Document jsoupDoc) {
    var spans = List.of("colspan", "rowspan");
    String template = "[%s=\"\"]";
    for (String span : spans) {
      var select = String.format(template, span);
      Elements els = jsoupDoc.select(select);
      for (Element el : els) {
        el.removeAttr(span);
      }
    }
  }

  private void appendListsOfMaterials(StringBuffer sbf, List<ListOfMaterials> listsOfMaterials) {
    for (ListOfMaterials lom : listsOfMaterials) {
      sbf.append(
          String.format(
              "<br/><br/>List of materials \"%s\" (description: \"%s\")",
              lom.getName(), lom.getDescription()));
      for (MaterialUsage mu : lom.getMaterials()) {
        sbf.append(
            String.format(
                "<br/> - item: %s, type: %s, name: \"%s\"",
                makeGlobalIdLink(mu.getInventoryRecord().getGlobalIdentifier()),
                mu.getInventoryRecord().getType().toString(),
                mu.getInventoryRecord().getName()));
        if (mu.getUsedQuantity() != null) {
          sbf.append(", usage: " + mu.getUsedQuantityPlainString());
        }
      }
    }
  }

  private void replaceIframesWithEmbedCodeLink(Document doc) {
    Elements iframes = doc.getElementsByTag("iframe");
    for (Element iframe : iframes) {
      String iframeReplacement =
          String.format(
              "<p><i>&lt;embedded code from <a href='%s'>%s</a>&gt;</i></p>",
              iframe.attr("src"), iframe.attr("src"));
      Element elem = Jsoup.parse(iframeReplacement, "", Parser.xmlParser()).selectFirst("p");
      iframe.replaceWith(elem);
    }
  }

  private void appendFieldLastModifiedDate(StringBuffer sbf, Field field) {
    sbf.append(
        String.format(
            "<br/><em> Last modified: %s</em>",
            getFormattedDate(field.getModificationDateAsDate())));
  }

  private String getFormattedDate(Date anyDate) {
    return new SessionTimeZoneUtils().formatDateTimeForClient(anyDate);
  }

  private List<ArchivalNfsFile> extractNfsLinks(StructuredDocument strucDoc, String htmlStr) {
    FieldContents contents = new FieldContents();
    ArchiveModelFactory archiveModelFactory = new ArchiveModelFactory();
    contents =
        fieldParser.findFieldElementsInContentForCssClass(
            contents, htmlStr, FieldParserConstants.NET_FS_CLASSNAME);
    List<ArchivalNfsFile> allNfs = new ArrayList<>();
    for (NfsElement nfs : contents.getElements(NfsElement.class).getElements()) {
      NfsFileStore fileStore = nfsManager.getNfsFileStore(nfs.getFileStoreId());
      ArchivalNfsFile archiveNfs = archiveModelFactory.createArchivalNfs(fileStore, nfs);
      allNfs.add(archiveNfs);
    }
    return allNfs;
  }

  private void addMetaData(StructuredDocument strucDoc, Document jsoupDoc) {
    Element el = jsoupDoc.getElementsByTag("head").iterator().next();
    el.appendElement("meta").attr("name", "name").attr("content", escapeHtml(strucDoc.getName()));
    el.appendElement("meta").attr("name", "provenance").attr("content", "rspace");
    el.appendElement("meta")
        .attr("name", "globalId")
        .attr("content", strucDoc.getGlobalIdentifier());
    el.appendElement("meta")
        .attr("name", "owner")
        .attr("content", strucDoc.getOwner().getFullName());
    if (!isEmpty(strucDoc.getDocTag())) {
      el.appendElement("meta")
          .attr("name", "tags")
          .attr("content", escapeHtml(strucDoc.getDocTag()));
    }
    if (!isEmpty(urlPrefix)) {
      el.appendElement("meta").attr("name", "baseURL").attr("content", urlPrefix);
    }
    Optional<ExternalId> extId =
        extIdResolver.getExternalIdForUser(strucDoc.getOwner(), IdentifierScheme.ORCID);
    if (extId.isPresent()) {
      el.appendElement("meta").attr("name", "OrcidID").attr("content", extId.get().getIdentifier());
    }
    el.appendElement("meta").attr("charset", "UTF-8");
  }

  private String makeGlobalIdLink(String globalId) {
    return "<a href=\"" + urlPrefix + "/globalId/" + globalId + "\">" + globalId + "</a>";
  }

  void addBaseURLToInternalLinks(Document jsoupDoc) {
    List<Element> links = jsoupDoc.getElementsByTag("a");
    for (Element el : links) {
      if (el.hasClass(FieldParserConstants.ATTACHMENT_CLASSNAME)
          || el.hasClass(FieldParserConstants.LINKEDRECORD_CLASS_NAME)) {
        String href = el.attr("href");
        if (href != null && !href.startsWith("http")) {
          urlPrefix = StringUtils.removeEnd(urlPrefix, "/");
          href = urlPrefix + href;
          el.attr("href", href);
        }
      }
    }
  }

  void scaleImages(StructuredDocumentHTMLViewConfig exportConfig, Document jsoupDoc) {
    List<Element> images = jsoupDoc.getElementsByTag("img");
    float scaleFactor = exportConfig.getPageSizeEnum().scaleFactor();
    for (Element el : images) {
      int width = -1;
      int height = -1;
      if (el.hasAttr("width")) {
        try {
          width = Integer.parseInt(el.attr("width"));
        } catch (NumberFormatException e) {
          log.warn("width could not be parsed for element {}", el);
        }
      }
      if (el.hasAttr("height")) {
        try {
          height = Integer.parseInt(el.attr("height"));
        } catch (NumberFormatException e) {
          log.warn("height could not be parsed for element {}", el);
        }
      }
      if (width != -1 && height != -1) {
        width = (int) (width * scaleFactor);
        height = (int) (height * scaleFactor);
        el.attr("width", width + "");
        el.attr("height", height + "");
      }
    }
  }

  private List<CommentAppendix> createCommentAppendix(
      StructuredDocumentHTMLViewConfig exportConfig, List<Long> lst1) {
    List<CommentAppendix> capx = new ArrayList<CommentAppendix>();
    if (exportConfig.isComments()) {
      for (int i = 0; i < lst1.size(); i++) {
        long comId = lst1.get(i);
        @SuppressWarnings("unchecked")
        List<EcatCommentItem> cms = commentManager.getCommentItems(comId);
        CommentAppendix commentAppendx = new CommentAppendix();
        for (int j = 0; j < cms.size(); j++) {
          EcatCommentItem itm1 = cms.get(j);
          String username = itm1.getLastUpdater();
          String dt1 = itm1.getFormatDate();
          String content = escapeHtml(itm1.getItemContent());
          commentAppendx.add(username, dt1, content);
        }
        capx.add(commentAppendx);
      }
    }
    return capx;
  }

  private RevisionInfo getAuditHistory(StructuredDocument strucDoc) {
    RevisionInfo appendix = new RevisionInfo();
    appendix.setEmpty(false);
    PaginationCriteria<AuditedRecord> pgCrit =
        PaginationCriteria.createDefaultForClass(AuditedRecord.class);
    pgCrit.setResultsPerPage(Integer.MAX_VALUE); // we want all revision
    // history
    List<AuditedRecord> revisionHistory = auditManager.getHistory(strucDoc, pgCrit);
    if (revisionHistory == null || revisionHistory.size() < 1) {
      return null;
    }
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    for (AuditedRecord rcd : revisionHistory) {
      Number userVersion = rcd.getRecordAsDocument().getUserVersion().getVersion();
      String ver = userVersion.toString();
      BaseRecord record = rcd.getRecord();
      String dateString = sdf.format(record.getModificationDateAsDate());
      String userName = record.getModifiedBy();
      String revisionType = rcd.getRevisionTypeString();
      appendix.add(ver, dateString, userName, revisionType);
    }
    return appendix;
  }
}
