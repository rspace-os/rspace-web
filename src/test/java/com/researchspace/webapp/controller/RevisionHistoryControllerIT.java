package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

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
    assertThrows(
        AuthorizationException.class,
        () -> {
          StructuredDocument sd = setUpLoginAsPIUserAndCreateADocument();
          User other = createAndSaveUser(getRandomAlphabeticString("revHistory"));
          try {
            revisionHistoryController.getListOfVersions(
                sd.getId(),
                model,
                "",
                mockPrincipal,
                createDefaultAuditedRecordListPagCrit(),
                createSearchCriteria());
          } catch (AuthorizationException ae) {
            fail("Should be allowed");
          }

          logoutAndLoginAs(other);
          revisionHistoryController.getListOfVersions(
              sd.getId(),
              model,
              "",
              new MockPrincipal(other.getUsername()),
              createDefaultAuditedRecordListPagCrit(),
              createSearchCriteria());
        });
  }

  protected RevisionSearchCriteria createSearchCriteria() {
    return new RevisionSearchCriteria();
  }

  @Test
  public void testExceptionThrownForUnauthorisedRevisionViewAccess() throws Exception {
    assertThrows(
        AuthorizationException.class,
        () -> {
          StructuredDocument sd = setUpLoginAsPIUserAndCreateADocument();
          User other = createAndSaveUser(getRandomAlphabeticString("revHistory"));

          AuditedRecord sdAudit = null;
          try {
            // this should be allowed - user is document owner
            revisionHistoryController.getListOfVersions(
                sd.getId(),
                model,
                "",
                mockPrincipal,
                createDefaultAuditedRecordListPagCrit(),
                createSearchCriteria());
            List<AuditedRecord> audits = (List) modelTss.get("history");
            sdAudit = audits.get(0);
            // should be able to get a
            structuredDocumentController.getDocumentRevision(
                sd.getId(), sdAudit.getRevision().intValue(), "", model, mockPrincipal, null);

          } catch (AuthorizationException ae) {
            fail("Should be allowed");
          }

          logoutAndLoginAs(other);
          structuredDocumentController.getDocumentRevision(
              sd.getId(),
              sdAudit.getRevision().intValue(),
              "",
              model,
              new MockPrincipal(other.getUsername()),
              null);
        });
  }
}
