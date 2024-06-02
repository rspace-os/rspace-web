package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.EditStatus;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.RestoreEvent;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.AuditManager;
import com.researchspace.service.FieldManager;
import com.researchspace.service.MediaManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.RestoreDeletedItemResult;
import com.researchspace.service.UserManager;
import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockServletContext;

public class RevisionHistoryControllerTest {

  @Rule public MockitoRule mockery = MockitoJUnit.rule();

  @Mock private UserManager userMgr;
  @Mock private RecordManager recordMgr;
  @Mock private MediaManager mediaMgr;
  @Mock private AuditTrailService auditTrail;
  @Mock private AuditManager auditMgr;
  @Mock private IPermissionUtils permissionUtils;
  @Mock private FieldManager fieldManager;
  @Mock private RSChemElementManager chemMgr;

  private RevisionHistoryController revisionHistoryCtrller;
  private Principal mockPrincipal;
  private User user;
  private StaticMessageSource messageSource;
  private MockServletContext context;

  @Before
  public void setUp() {
    messageSource = new StaticMessageSource();

    revisionHistoryCtrller = new RevisionHistoryController();
    revisionHistoryCtrller.setUserManager(userMgr);
    revisionHistoryCtrller.setRecordManager(recordMgr);
    revisionHistoryCtrller.setAuditService(auditTrail);
    revisionHistoryCtrller.setAuditManager(auditMgr);
    revisionHistoryCtrller.setFieldManager(fieldManager);
    revisionHistoryCtrller.setMessageSource(new MessageSourceUtils(messageSource));
    revisionHistoryCtrller.setPermissionUtils(permissionUtils);
    context = new MockServletContext();
    revisionHistoryCtrller.setServletContext(context);
    user = TestFactory.createAnyUser("user");
    mockPrincipal = user::getUsername;
  }

  @After
  public void tearDown() {}

  @Test
  public void restoreRevisionRequiresEditPermission() {
    // no permission
    messageSource.addMessage("restore.failure.message", Locale.getDefault(), "any");
    Mockito.when(userMgr.getUserByUsername(user.getUsername())).thenReturn(user);
    Mockito.when(recordMgr.requestRecordEdit(1L, user, null))
        .thenReturn(EditStatus.CANNOT_EDIT_NO_PERMISSION);

    AjaxReturnObject<String> result =
        revisionHistoryCtrller.restoreVersion(1L, 2, false, mockPrincipal);
    Mockito.verify(recordMgr).unlockRecord(1L, user.getUsername());
    assertNotNull(result.getErrorMsg());
    assertNull(result.getData());

    // with permission
    Mockito.when(recordMgr.requestRecordEdit(1L, user, null)).thenReturn(EditStatus.EDIT_MODE);

    result = revisionHistoryCtrller.restoreVersion(1L, 2, false, mockPrincipal);
    Mockito.verify(auditTrail).notify(Mockito.any(RestoreEvent.class));
    Mockito.verify(auditMgr).restoreRevisionAsCurrent(2, 1L);
    assertNull(result.getErrorMsg());
    assertNotNull(result.getData());
  }

  @Test
  public void restoreDeletedDocument() {
    final StructuredDocument sd = TestFactory.createAnySD();
    sd.setOwner(user);
    sd.setId(1L);
    final int ANY_REVISION = 2;
    generalExpectations();
    setupAssertions(sd);
    AjaxReturnObject<String> result =
        revisionHistoryCtrller.restoreVersion(1L, ANY_REVISION, true, mockPrincipal);
    verifyPostRestore();
    assertNull(result.getErrorMsg());
    assertNotNull(result.getData());
    assertEquals("1", result.getData());
    // restore a media record
    final EcatMediaFile media = TestFactory.createEcatAudio(2L, user);
    setupAssertions(media);
    result = revisionHistoryCtrller.restoreVersion(2L, ANY_REVISION, true, mockPrincipal);
    verifyPostRestore();
    assertNull(result.getErrorMsg());
    assertEquals("Media:2", result.getData());
    // restore a folder
    final Folder folder = TestFactory.createAFolder("any", user);
    folder.setId(3L);
    setupAssertions(folder);
    result = revisionHistoryCtrller.restoreVersion(3L, ANY_REVISION, true, mockPrincipal);
    verifyPostRestore();
    assertNull(result.getErrorMsg());
    assertEquals("Folder:3", result.getData());
  }

  private void verifyPostRestore() {
    Mockito.verify(auditTrail).notify(Mockito.any(RestoreEvent.class));
    Mockito.verify(recordMgr).unlockRecord(Mockito.any(Long.class), Mockito.any(String.class));
  }

  private void setupAssertions(final BaseRecord toRestore) {
    Mockito.reset(auditMgr, auditTrail, recordMgr);
    Mockito.when(auditMgr.fullRestore(toRestore.getId(), user))
        .thenReturn(new RestoreDeletedItemResult(toRestore));
  }

  @Test
  public void restoreDeletedNotebook() {
    generalExpectations();
    final Notebook nb = TestFactory.createANotebook("any", user);
    nb.setId(1L);
    Mockito.when(auditMgr.fullRestore(nb.getId(), user))
        .thenReturn(new RestoreDeletedItemResult(nb));

    AjaxReturnObject<String> result =
        revisionHistoryCtrller.restoreVersion(1L, 2, true, mockPrincipal);
    Mockito.verify(auditTrail).notify(Mockito.any(RestoreEvent.class));
    assertNull(result.getErrorMsg());
    assertNotNull(result.getData());
  }

  @Test
  public void restoreDeletedFolder() {
    // restore notebook
    generalExpectations();
    final Folder toRestore = TestFactory.createAFolder("any", user);
    toRestore.setId(1L);
    Mockito.when(auditMgr.fullRestore(toRestore.getId(), user))
        .thenReturn(new RestoreDeletedItemResult(toRestore));
    AjaxReturnObject<String> result =
        revisionHistoryCtrller.restoreVersion(1L, 2, true, mockPrincipal);
    Mockito.verify(auditTrail).notify(Mockito.any(RestoreEvent.class));
    assertNull(result.getErrorMsg());
    assertNotNull(result.getData());
  }

  @Test
  public void listRevisions() throws IOException {

    final StructuredDocument sd1 = TestFactory.createAnySD();
    sd1.setOwner(user);
    sd1.setId(1L);

    final StructuredDocument sd2 = sd1.copy();
    sd2.setId(1L);
    sd2.setUserVersion(sd1.getUserVersion().increment());

    AuditedRecord auditedSD1 = new AuditedRecord(sd1, 11L);
    AuditedRecord auditedSD2 = new AuditedRecord(sd2, 12L);
    List<AuditedRecord> auditList = Arrays.asList(auditedSD1, auditedSD2);

    generalExpectations();
    Mockito.when(recordMgr.get(1L)).thenReturn(sd2);
    Mockito.when(permissionUtils.isPermitted(sd2, PermissionType.READ, user)).thenReturn(true);
    Mockito.when(auditMgr.getHistory(sd2, null)).thenReturn(auditList);

    AjaxReturnObject<List<RecordInformation>> revisionsResponse =
        revisionHistoryCtrller.getRevisionsJson(sd2.getId(), mockPrincipal);
    assertNotNull(revisionsResponse);
    List<RecordInformation> revisions = revisionsResponse.getData();
    assertEquals(2, revisions.size());
    assertEquals(Long.valueOf(11), revisions.get(0).getRevision());
  }

  private void generalExpectations() {
    Mockito.when(userMgr.getUserByUsername(user.getUsername())).thenReturn(user);
  }
}
