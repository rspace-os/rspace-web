package com.researchspace.api.v1.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.researchspace.api.v1.model.ApiDocument;
import com.researchspace.api.v1.model.ApiDocumentField;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.RenameAuditEvent;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.DocumentAlreadyEditedException;
import com.researchspace.service.FormManager;
import com.researchspace.service.RecordManager;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;

public class RecordApiManagerTest extends SpringTransactionalTest {

  @Autowired private RecordApiManager recordApiMgr;
  @Autowired private RecordManager recordMgr;
  @Autowired private FormManager formMgr;
  @Mock private AuditTrailService auditTrailService;

  private User testUser;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    recordApiMgr.setAuditTrailService(auditTrailService);
    testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(testUser);
    assertTrue(testUser.isContentInitialized());
  }

  @Test
  public void testApiRequestsCreateCorrectNumberOfRevisions()
      throws DocumentAlreadyEditedException {

    RSForm basicDocForm = formMgr.getBasicDocumentForm();

    // create empty doc with a name, should be one revision
    ApiDocument apiDoc = new ApiDocument();
    apiDoc.setName("myName");

    Long emptyDocId = recordApiMgr.createNewDocument(apiDoc, basicDocForm, testUser);
    StructuredDocument emptyDoc = recordMgr.get(emptyDocId).asStrucDoc();
    assertEquals("myName", emptyDoc.getName());
    assertEquals(1L, emptyDoc.getUserVersion().getVersion().longValue());
    verifyNoInteractions(auditTrailService);

    // update empty doc tag and field content will create second revision
    apiDoc.setTags("myTag");
    ApiDocumentField field = new ApiDocumentField();
    field.setContent("content");
    apiDoc.getFields().add(field);

    recordApiMgr.createNewRevision(emptyDoc, apiDoc, testUser);
    StructuredDocument updatedDoc = recordMgr.get(emptyDocId).asStrucDoc();
    assertEquals("myName", updatedDoc.getName());
    assertEquals("myTag", updatedDoc.getDocTag());
    assertEquals("content", updatedDoc.getFields().get(0).getFieldData());
    assertEquals(2L, updatedDoc.getUserVersion().getVersion().longValue());
    verifyNoInteractions(auditTrailService);

    // updating name, tag, and content, will create two extra revisions
    apiDoc.setName("myName2");
    apiDoc.setTags("myTag2");
    apiDoc.getFields().get(0).setContent("content2");

    recordApiMgr.createNewRevision(updatedDoc, apiDoc, testUser);
    StructuredDocument updatedDoc2 = recordMgr.get(emptyDocId).asStrucDoc();
    assertEquals("myName2", updatedDoc2.getName());
    assertEquals("myTag2", updatedDoc2.getDocTag());
    assertEquals("content2", updatedDoc2.getFields().get(0).getFieldData());
    assertEquals(4L, updatedDoc2.getUserVersion().getVersion().longValue());
    verify(auditTrailService).notify(any(RenameAuditEvent.class));

    // create another doc with name, tag and fields
    Long basicDocId = recordApiMgr.createNewDocument(apiDoc, basicDocForm, testUser);
    StructuredDocument basicDoc = recordMgr.get(basicDocId).asStrucDoc();
    assertEquals("myName2", basicDoc.getName());
    assertEquals("myTag2", basicDoc.getDocTag());
    assertEquals("content2", basicDoc.getFields().get(0).getFieldData());
    assertEquals(2L, basicDoc.getUserVersion().getVersion().longValue());
    verify(auditTrailService).notify(any(RenameAuditEvent.class));
  }
}
