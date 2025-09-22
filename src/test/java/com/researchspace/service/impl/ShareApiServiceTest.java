package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.controller.ApiGenericSearchConfig;
import com.researchspace.api.v1.controller.DocumentApiPaginationCriteria;
import com.researchspace.api.v1.model.ApiShareSearchResult;
import com.researchspace.api.v1.model.ApiSharingResult;
import com.researchspace.api.v1.model.GroupSharePostItem;
import com.researchspace.api.v1.model.SharePermissionUpdate;
import com.researchspace.api.v1.model.SharePost;
import com.researchspace.api.v1.model.UserSharePostItem;
import com.researchspace.auth.PermissionUtils;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.ServiceOperationResultCollection;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.DetailedRecordInformationProvider;
import com.researchspace.service.FolderManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RecordManager;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.SharingHandler;
import com.researchspace.service.mapping.DocumentSharesBuilder;
import java.util.ArrayList;
import javax.ws.rs.NotFoundException;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.ObjectRetrievalFailureException;

@ExtendWith(MockitoExtension.class)
class ShareApiServiceTest {

  @Mock private SharingHandler sharingHandler;
  @Mock private RecordSharingManager recordSharingManager;
  @Mock private DetailedRecordInformationProvider detailedRecordInformationProvider;
  @Mock private RecordManager recordManager;
  @Mock private FolderManager folderManager;
  @Mock private DocumentSharesBuilder documentSharesBuilder;
  @Mock private PermissionUtils permissionUtils;

  @Mock private IPropertyHolder properties;
  @Mock private MessageSourceUtils messages;

  private ShareApiServiceImpl service;

  private User user;

  @BeforeEach
  void setup() throws Exception {
    service =
        new ShareApiServiceImpl(
            sharingHandler,
            recordSharingManager,
            detailedRecordInformationProvider,
            recordManager,
            folderManager,
            documentSharesBuilder,
            permissionUtils,
            properties,
            messages);

    user = TestFactory.createAnyUser("someUser");
  }

  private void setupResourceNotFoundMessageMock() {
    when(messages.getResourceNotFoundMessage(anyString(), anyLong()))
        .thenAnswer(inv -> inv.getArgument(0) + ":" + inv.getArgument(1));
  }

  @Test
  void shareItemsCountsSuccessAndFailures() {
    SharePost post =
        SharePost.builder()
            .itemToShare(10L)
            .groupSharePostItem(
                GroupSharePostItem.builder().id(2L).permission("READ").sharedFolderId(3L).build())
            .userSharePostItem(UserSharePostItem.builder().id(5L).permission("EDIT").build())
            .build();

    StructuredDocument okRecord = new StructuredDocument(TestFactory.createAnyForm());
    okRecord.setId(111L);
    okRecord.setName("okRecord");
    RecordGroupSharing ok = new RecordGroupSharing();
    ok.setShared(okRecord);
    ok.setSharee(user);

    StructuredDocument failedRecord = new StructuredDocument(TestFactory.createAnyForm());
    failedRecord.setId(222L);
    failedRecord.setName("failedRecord");
    RecordGroupSharing failed = new RecordGroupSharing();
    failed.setShared(failedRecord);

    ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> rc =
        new ServiceOperationResultCollection<>();
    rc.addResult(ok);
    rc.addFailure(failed);

    when(sharingHandler.shareRecords(any(), eq(user))).thenReturn(rc);

    ApiSharingResult result = service.shareItems(post, user);

    assertEquals(1, result.getShareInfos().size());
    assertEquals(1, result.getFailedShares().size());
    assertEquals(111L, result.getShareInfos().get(0).getId());
    assertEquals(222L, result.getFailedShares().get(0));
  }

  @Test
  void shareItems_allAuthExceptions_throwsAuthorizationException() {
    SharePost post = makeSharePost();

    ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> rc =
        new ServiceOperationResultCollection<>();
    rc.addException(new AuthorizationException("nope"));
    rc.addException(new AuthorizationException("denied"));

    when(sharingHandler.shareRecords(any(), eq(user))).thenReturn(rc);

    assertThrows(AuthorizationException.class, () -> service.shareItems(post, user));
  }

  @Test
  void shareItems_illegalAddChild_leadsToIllegalArgumentException() {
    SharePost post = makeSharePost();

    ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> rc =
        new ServiceOperationResultCollection<>();
    rc.addException(new IllegalAddChildOperation("child err"));

    when(sharingHandler.shareRecords(any(), eq(user))).thenReturn(rc);

    assertThrows(IllegalArgumentException.class, () -> service.shareItems(post, user));
  }

  @Test
  void shareItems_genericException_resultsInRuntimeException() {
    SharePost post = makeSharePost();

    ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> rc =
        new ServiceOperationResultCollection<>();
    rc.addException(new RuntimeException("boom"));

    when(sharingHandler.shareRecords(any(), eq(user))).thenReturn(rc);

    assertThrows(RuntimeException.class, () -> service.shareItems(post, user));
  }

  @Test
  void deleteShare_success_callsUnshare() {
    Long id = 123L;

    StructuredDocument shared = new StructuredDocument(TestFactory.createAnyForm());
    RecordGroupSharing rgs = new RecordGroupSharing();
    rgs.setShared(shared);

    when(recordSharingManager.get(id)).thenReturn(rgs);

    service.deleteShare(id, user);

    verify(permissionUtils).assertIsPermitted(shared, PermissionType.SHARE, user, "unshare doc");
    verify(sharingHandler).unshare(id, user);
  }

  @Test
  void deleteShare_dataAccess_leadsToNotFound() {
    Long id = 123L;
    setupResourceNotFoundMessageMock();

    StructuredDocument shared = new StructuredDocument(TestFactory.createAnyForm());
    RecordGroupSharing rgs = new RecordGroupSharing();
    rgs.setShared(shared);
    when(recordSharingManager.get(id)).thenReturn(rgs);

    doThrow(mock(DataAccessException.class)).when(sharingHandler).unshare(id, user);

    assertThrows(NotFoundException.class, () -> service.deleteShare(id, user));
  }

  @Test
  void deleteShare_permissionDenied_leadsToNotFound() {
    Long id = 123L;
    setupResourceNotFoundMessageMock();

    when(recordSharingManager.get(id))
        .thenThrow(new ObjectRetrievalFailureException("RecordGroupSharing", id));

    assertThrows(NotFoundException.class, () -> service.deleteShare(id, user));
  }

  @Test
  void updateShare_errorListHasErrors_throwsIllegalArgument() {
    Long id = 123L;
    SharePermissionUpdate update = new SharePermissionUpdate();
    update.setShareId(id);
    update.setPermission("read");

    StructuredDocument shared = new StructuredDocument(TestFactory.createAnyForm());
    RecordGroupSharing rgs = new RecordGroupSharing();
    rgs.setShared(shared);
    when(recordSharingManager.get(id)).thenReturn(rgs);

    ErrorList errors = new ErrorList();
    errors.addErrorMsg("err1");
    errors.addErrorMsg("err2");

    when(recordSharingManager.updatePermissionForRecord(eq(id), eq("READ"), eq(user.getUsername())))
        .thenReturn(errors);

    assertThrows(IllegalArgumentException.class, () -> service.updateShare(update, user));
  }

  @Test
  void updateShare_success_doesNotThrow() {
    Long id = 123L;
    SharePermissionUpdate update = new SharePermissionUpdate();
    update.setShareId(id);
    update.setPermission("edit");

    StructuredDocument shared = new StructuredDocument(TestFactory.createAnyForm());
    RecordGroupSharing rgs = new RecordGroupSharing();
    rgs.setShared(shared);
    when(recordSharingManager.get(id)).thenReturn(rgs);

    when(recordSharingManager.updatePermissionForRecord(eq(id), eq("EDIT"), eq(user.getUsername())))
        .thenReturn(null);

    assertDoesNotThrow(() -> service.updateShare(update, user));
  }

  @Test
  void getShares_convertsSearchResults() throws Exception {
    DocumentApiPaginationCriteria pg =
        new DocumentApiPaginationCriteria(
            0, 10, DocumentApiPaginationCriteria.LAST_MODIFIED_DESC_API_PARAM);
    ApiGenericSearchConfig cfg = new ApiGenericSearchConfig();
    cfg.setQuery("abc");
    when(properties.getServerUrl()).thenReturn("http://localhost");

    @SuppressWarnings("unchecked")
    ISearchResults<RecordGroupSharing> internal = mock(ISearchResults.class);
    when(internal.getTotalHits()).thenReturn(3L);
    when(internal.getPageNumber()).thenReturn(0);
    when(internal.getResults()).thenReturn(new ArrayList<>());

    when(recordSharingManager.listSharedRecordsForUser(eq(user), any())).thenReturn(internal);

    ApiShareSearchResult result = service.getShares(pg, cfg, user);

    assertEquals(3L, result.getTotalHits());
    assertEquals(Integer.valueOf(0), result.getPageNumber());
    assertNotNull(result.getShares());
  }

  @Test
  void getAllSharesForDoc_nullId_illegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> service.getAllSharesForDoc(null, user));
  }

  private static SharePost makeSharePost() {
    return SharePost.builder()
        .itemToShare(1L)
        .groupSharePostItem(
            GroupSharePostItem.builder().id(2L).permission("READ").sharedFolderId(3L).build())
        .userSharePostItem(UserSharePostItem.builder().id(4L).permission("READ").build())
        .build();
  }
}
