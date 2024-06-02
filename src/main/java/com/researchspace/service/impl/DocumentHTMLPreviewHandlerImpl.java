package com.researchspace.service.impl;

import com.researchspace.export.pdf.ExportProcesserInput;
import com.researchspace.export.pdf.HTMLStringGenerator;
import com.researchspace.export.pdf.StructuredDocumentHTMLViewConfig;
import com.researchspace.model.User;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.DocHtmlPreview;
import com.researchspace.service.DocumentHTMLPreviewHandler;
import com.researchspace.service.RecordManager;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;

public class DocumentHTMLPreviewHandlerImpl implements DocumentHTMLPreviewHandler {

  private @Autowired HTMLStringGenerator htmlGenerator;
  private @Autowired RecordManager recordManager;

  // Cache is deleted for a document when recordManager#save called which changes content.
  // Cache policy is to cache on demand when requested.
  @Override
  @Cacheable(cacheNames = "com.researchspace.documentPreview", key = "#docId")
  public DocHtmlPreview generateHtmlPreview(Long docId, User subject) {
    StructuredDocument structuredDocument =
        recordManager.getRecordWithFields(docId, subject).asStrucDoc();
    ExportProcesserInput htmlConcat =
        htmlGenerator.extractHtmlStr(structuredDocument, new StructuredDocumentHTMLViewConfig() {});
    String html = htmlConcat.getDocumentAsHtml();
    return new DocHtmlPreview(Jsoup.parse(html).body().html());
  }
}
