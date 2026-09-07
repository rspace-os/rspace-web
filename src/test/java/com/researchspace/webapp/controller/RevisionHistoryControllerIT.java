package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.dtos.RevisionSearchCriteria;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.List;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;

@WebAppConfiguration
public class RevisionHistoryControllerIT extends RealTransactionSpringTestBase {

  @Autowired private RevisionHistoryController revisionHistoryController;

  @Autowired private StructuredDocumentController structuredDocumentController;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void testExceptionThrownForUnauthorisedRevisionListAccess() throws Exception {
    StructuredDocument sd = setUpLoginAsPIUserAndCreateADocument();
    User other = createAndSaveUser(getRandomAlphabeticString("revHistory"));
    Long documentId = sd.getId();
    revisionHistoryController.getListOfVersions(
        documentId,
        model,
        "",
        mockPrincipal,
        createDefaultAuditedRecordListPagCrit(),
        createSearchCriteria());

    logoutAndLoginAs(other);
    MockPrincipal otherPrincipal = new MockPrincipal(other.getUsername());
    var pagination = createDefaultAuditedRecordListPagCrit();
    RevisionSearchCriteria searchCriteria = createSearchCriteria();

    assertThrows(
        AuthorizationException.class,
        () ->
            revisionHistoryController.getListOfVersions(
                documentId, model, "", otherPrincipal, pagination, searchCriteria));
  }

  protected RevisionSearchCriteria createSearchCriteria() {
    return new RevisionSearchCriteria();
  }

  @Test
  public void testExceptionThrownForUnauthorisedRevisionViewAccess() throws Exception {
    StructuredDocument sd = setUpLoginAsPIUserAndCreateADocument();
    User other = createAndSaveUser(getRandomAlphabeticString("revHistory"));
    Long documentId = sd.getId();
    revisionHistoryController.getListOfVersions(
        documentId,
        model,
        "",
        mockPrincipal,
        createDefaultAuditedRecordListPagCrit(),
        createSearchCriteria());
    List<AuditedRecord> audits = (List) modelTss.get("history");
    AuditedRecord sdAudit = audits.get(0);
    int revision = sdAudit.getRevision().intValue();
    structuredDocumentController.getDocumentRevision(
        documentId, revision, "", model, mockPrincipal, null);

    logoutAndLoginAs(other);
    MockPrincipal otherPrincipal = new MockPrincipal(other.getUsername());

    assertThrows(
        AuthorizationException.class,
        () ->
            structuredDocumentController.getDocumentRevision(
                documentId, revision, "", model, otherPrincipal, null));
  }
}
