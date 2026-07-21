package com.researchspace.service;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import org.openprovenance.prov.model.Document;
import org.openprovenance.prov.model.ProvFactory;

public interface ProvManager {
  static final String RS = "rs";
  static final String RS_USER = "rs-user";
  static final String RS_RESOURCE = "rs-resource";
  static final String DCT = "dcterms";
  static final String FOAF = "foaf";
  static final String OWL = "owl";

  Document createDocument(ISearchResults<AuditTrailSearchResult> res);

  ProvFactory getProvFactory();
}
