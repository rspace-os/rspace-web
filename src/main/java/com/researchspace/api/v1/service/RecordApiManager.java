package com.researchspace.api.v1.service;

import com.researchspace.api.v1.model.ApiDocument;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.DocumentAlreadyEditedException;

/** Handles API actions about the record, so for now creating new record or new revision. */
public interface RecordApiManager {

  /**
   * @param apiDocument
   * @param docForm
   * @param user
   * @returns id of a newly created document
   */
  Long createNewDocument(ApiDocument apiDocument, RSForm docForm, User user);

  /**
   * @throws DocumentAlreadyEditedException
   */
  void createNewRevision(StructuredDocument doc, ApiDocument apiDocument, User user)
      throws DocumentAlreadyEditedException;

  /***
   * For testing purposes
   *
   * @param auditTrailService
   */
  void setAuditTrailService(AuditTrailService auditTrailService);
}
