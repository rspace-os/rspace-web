package com.researchspace.webapp.controller;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.service.ProvManager;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import java.io.ByteArrayOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.openprovenance.prov.interop.Formats.ProvFormat;
import org.openprovenance.prov.interop.InteropFramework;
import org.openprovenance.prov.model.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Slf4j
public class AuditTrailSearchResultProvGenerator {
  private static final String ATTACHMENT_FILENAME_RSPACE_AUDIT_TRAIL_PROV =
      "attachment; filename=\"rspace-audit-prov.json\"";

  private @Autowired ProvManager provManager;

  ResponseEntity<String> convertToProvJson(ISearchResults<AuditTrailSearchResult> res) {
    Document document = provManager.createDocument(res);
    ByteArrayOutputStream os = new ByteArrayOutputStream(10000);
    InteropFramework interopF = new InteropFramework(provManager.getProvFactory());
    interopF.writeDocument(os, ProvFormat.JSON, document);
    log.info(
        "Wrote {} provenance statements into JSON from {} audit records",
        document.getStatementOrBundle().size(),
        res.getResults().size());
    return createProvEntityResponse(os.toString());
  }

  private ResponseEntity<String> createProvEntityResponse(String prov) {
    HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.setContentType(MediaType.parseMediaType("application/json"));
    responseHeaders.add("Content-Disposition", ATTACHMENT_FILENAME_RSPACE_AUDIT_TRAIL_PROV);
    ResponseEntity<String> rc = new ResponseEntity<>(prov, responseHeaders, HttpStatus.OK);
    return rc;
  }
}
