package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.core.util.FieldParserConstants;
import com.researchspace.dao.NfsDao;
import com.researchspace.linkedelements.FieldContents;
import com.researchspace.linkedelements.FieldParser;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.netfiles.NfsClientType;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.netfiles.NfsFieldLink;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.FilestoreAclChecker;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.testutils.GalleryFilestoreTestUtils;
import com.researchspace.testutils.TestFactory;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NfsFieldLinkManagerImplTest {

  @Mock NfsDao nfsDao;
  @Mock FieldParser fieldParser;
  @Mock IPermissionUtils permUtils;
  @Mock FilestoreAclChecker aclChecker;
  @Mock MessageSourceUtils messages;
  @InjectMocks NfsFieldLinkManagerImpl manager;

  private final User user = TestFactory.createAnyUser("any");
  private final Field field = TestFactory.createTextFieldForm().createNewFieldFromForm();
  private final NfsFileStore store =
      GalleryFilestoreTestUtils.createS3FileSystemAndFileStore(1L, "s3", user);

  @BeforeEach
  void setUp() {
    field.setId(5L);
    store.setPath("/data");
    // a soft-deleted filestore still indexes, so reindex must never read the flag
    store.setDeleted(true);
  }

  private FieldContents nfsContents(NfsElement... elements) {
    FieldContents contents = new FieldContents();
    for (NfsElement e : elements) {
      contents.addElement(e, e.toString(), NfsElement.class);
    }
    return contents;
  }

  private void stubParse(String content, NfsElement... elements) {
    when(fieldParser.findFieldElementsInContentForCssClass(
            any(FieldContents.class), eq(content), eq(FieldParserConstants.NET_FS_CLASSNAME)))
        .thenReturn(nfsContents(elements));
  }

  @Test
  void reindexAddsNewLinksAndDeletesStaleRows() {
    NfsFieldLink stale = new NfsFieldLink(field, store, "/old.dat");
    when(nfsDao.getFieldLinks(5L)).thenReturn(List.of(stale));
    stubParse("html", new NfsElement(1L, "/XRD/run.dat"));
    when(nfsDao.getNfsFileStore(1L)).thenReturn(store);

    manager.reindex(field, "html");

    verify(nfsDao).deleteFieldLink(stale);
    ArgumentCaptor<NfsFieldLink> saved = ArgumentCaptor.forClass(NfsFieldLink.class);
    verify(nfsDao).saveFieldLink(saved.capture());
    assertEquals("data/XRD/run.dat", saved.getValue().getAbsolutePath());
    assertEquals("/XRD/run.dat", saved.getValue().getRelPath());
  }

  @Test
  void reindexKeepsRowsStillInContentAndCollapsesRepeatedLinks() {
    when(nfsDao.getFieldLinks(5L)).thenReturn(List.of(new NfsFieldLink(field, store, "/a.dat")));
    stubParse("html", new NfsElement(1L, "/a.dat"), new NfsElement(1L, "/a.dat"));

    manager.reindex(field, "html");

    verify(nfsDao, never()).saveFieldLink(any());
    verify(nfsDao, never()).deleteFieldLink(any());
  }

  @Test
  void reindexSkipsLinksWhoseFilestoreIsUnknown() {
    when(nfsDao.getFieldLinks(5L)).thenReturn(List.of());
    stubParse("html", new NfsElement(99L, "/a.dat"));
    when(nfsDao.getNfsFileStore(99L)).thenReturn(null);

    manager.reindex(field, "html");

    verify(nfsDao, never()).saveFieldLink(any());
  }

  @Test
  void reindexSkipsLinksTooLongForTheIndexInsteadOfFailingTheSave() {
    when(nfsDao.getFieldLinks(5L)).thenReturn(List.of());
    stubParse("html", new NfsElement(1L, "/" + "a".repeat(NfsFieldLink.MAX_PATH_LENGTH)));
    when(nfsDao.getNfsFileStore(1L)).thenReturn(store);

    manager.reindex(field, "html");

    verify(nfsDao, never()).saveFieldLink(any());
  }

  @Test
  void reindexWithBlankContentDeletesAllRowsWithoutParsing() {
    NfsFieldLink row = new NfsFieldLink(field, store, "/a.dat");
    when(nfsDao.getFieldLinks(5L)).thenReturn(List.of(row));

    manager.reindex(field, " ");

    verify(nfsDao).deleteFieldLink(row);
    verifyNoInteractions(fieldParser);
  }

  @Test
  void linkedDocumentsReplaceUnreadableOnesByOwnerPlaceholder() {
    StructuredDocument readable = TestFactory.createAnySD();
    readable.setId(11L);
    readable.setOwner(user);
    StructuredDocument hidden = TestFactory.createAnySD();
    hidden.setId(12L);
    User owner = TestFactory.createAnyUser("owner");
    owner.setFirstName("Grace");
    owner.setLastName("Hopper");
    hidden.setOwner(owner);
    when(nfsDao.getNfsFileStore(1L)).thenReturn(store);
    when(nfsDao.getDocumentsLinkingTo(1L, List.of("data/x.dat"), null, null))
        .thenReturn(List.of(readable, hidden));
    when(permUtils.isPermitted(readable, PermissionType.READ, user)).thenReturn(true);
    when(permUtils.isPermitted(hidden, PermissionType.READ, user)).thenReturn(false);

    List<RecordInformation> result = manager.getLinkedDocuments(1L, "/x.dat/", user);

    assertEquals(2, result.size());
    assertEquals("SD11", result.get(0).getOid().getIdString());
    assertNull(result.get(1).getId());
    assertNull(result.get(1).getOid());
    assertEquals("Grace Hopper", result.get(1).getOwnerFullName());
  }

  @Test
  void lookupByFilestoreAssertsReadAclAndResolvesTheAbsolutePath() {
    when(nfsDao.getNfsFileStore(1L)).thenReturn(store);
    when(nfsDao.getDocumentsLinkingTo(1L, List.of("data/XRD/run.dat"), null, null))
        .thenReturn(List.of());

    manager.getLinkedDocuments(1L, "/XRD/run.dat", user);

    verify(aclChecker).assertCanRead(user, store.getFileSystem());
  }

  @Test
  void lookupByFilestoreOnSftpSearchesFromWhereTheFilestoreBaseStarts() {
    NfsFileStore sftpStore =
        GalleryFilestoreTestUtils.createIrodsFileSystemAndFileStore(3L, "sftp", user);
    sftpStore.getFileSystem().setClientType(NfsClientType.SFTP);
    sftpStore.setPath("/upload");
    StructuredDocument own = TestFactory.createAnySD();
    own.setId(11L);
    when(nfsDao.getNfsFileStore(3L)).thenReturn(sftpStore);
    when(nfsDao.getDocumentsLinkingTo(3L, List.of("upload/hello.txt"), user, true))
        .thenReturn(List.of(own));
    when(permUtils.isPermitted(own, PermissionType.READ, user)).thenReturn(true);

    List<RecordInformation> result = manager.getLinkedDocuments(3L, "/hello.txt", user);

    assertEquals(List.of(11L), result.stream().map(RecordInformation::getId).toList());
  }

  @Test
  void rootRemotePathOfRootFilestoreResolvesToEmptyPath() {
    store.setPath("");
    when(nfsDao.getNfsFileStore(1L)).thenReturn(store);
    when(nfsDao.getDocumentsLinkingTo(1L, List.of(""), null, null)).thenReturn(List.of());

    manager.getLinkedDocuments(1L, "/", user);
  }

  @Test
  void lookupDeniedByTheFilesystemAclNeverReadsTheIndex() {
    when(nfsDao.getNfsFileStore(1L)).thenReturn(store);
    doThrow(new AuthorizationException("denied"))
        .when(aclChecker)
        .assertCanRead(user, store.getFileSystem());

    assertThrows(
        AuthorizationException.class, () -> manager.getLinkedDocuments(1L, "/a.dat", user));
    verify(nfsDao, never()).getDocumentsLinkingTo(any(), any(), any(), any());
  }

  @Test
  void lookupByFilesystemNormalisesEveryPath() {
    StructuredDocument readable = TestFactory.createAnySD();
    readable.setId(11L);
    when(nfsDao.getDocumentsLinkingTo(1L, List.of("XRD", "XRD/run.dat"), null, null))
        .thenReturn(List.of(readable));
    when(permUtils.isPermitted(readable, PermissionType.READ, user)).thenReturn(true);

    List<RecordInformation> result =
        manager.getLinkedDocuments(store.getFileSystem(), List.of("/XRD/", "XRD/run.dat"), user);

    assertEquals("SD11", result.get(0).getOid().getIdString());
  }

  @Test
  void onSftpOnlyTheCallersFilestoresWithAMatchingBaseAreSearched() {
    NfsFileSystem sftp = GalleryFilestoreTestUtils.createIrodsFileSystem(2L);
    sftp.setClientType(NfsClientType.SFTP);
    StructuredDocument fromRoot = TestFactory.createAnySD();
    fromRoot.setId(11L);
    StructuredDocument fromHome = TestFactory.createAnySD();
    fromHome.setId(12L);
    when(nfsDao.getDocumentsLinkingTo(2L, List.of("upload/hello.txt"), user, true))
        .thenReturn(List.of(fromRoot));
    // a path without a leading slash starts at the login's home
    when(nfsDao.getDocumentsLinkingTo(2L, List.of("upload/other.txt"), user, false))
        .thenReturn(List.of(fromHome));
    when(permUtils.isPermitted(any(StructuredDocument.class), eq(PermissionType.READ), eq(user)))
        .thenReturn(true);

    List<RecordInformation> result =
        manager.getLinkedDocuments(sftp, List.of("/upload/hello.txt", "upload/other.txt"), user);

    assertEquals(List.of(11L, 12L), result.stream().map(RecordInformation::getId).toList());
  }

  @Test
  void withoutALoginHomeOnlyTheCallersFilestoresAreSearchedWhateverTheSlash() {
    NfsFileSystem irods = GalleryFilestoreTestUtils.createIrodsFileSystem(2L);
    StructuredDocument own = TestFactory.createAnySD();
    own.setId(11L);
    when(nfsDao.getDocumentsLinkingTo(
            2L, List.of("upload/hello.txt", "upload/other.txt"), user, null))
        .thenReturn(List.of(own));
    when(permUtils.isPermitted(own, PermissionType.READ, user)).thenReturn(true);

    List<RecordInformation> result =
        manager.getLinkedDocuments(irods, List.of("/upload/hello.txt", "upload/other.txt"), user);

    assertEquals(List.of(11L), result.stream().map(RecordInformation::getId).toList());
  }

  @Test
  void lookupByFilesystemQueriesInBatchesAndMergesThemOldestFirst() {
    List<String> paths = IntStream.range(0, 1001).mapToObj(i -> "f" + i).toList();
    StructuredDocument older = TestFactory.createAnySD();
    older.setId(11L);
    StructuredDocument newer = TestFactory.createAnySD();
    newer.setId(12L);
    StructuredDocument newerAgain = TestFactory.createAnySD();
    newerAgain.setId(12L);
    when(nfsDao.getDocumentsLinkingTo(1L, paths.subList(0, 1000), null, null))
        .thenReturn(List.of(newer));
    when(nfsDao.getDocumentsLinkingTo(1L, paths.subList(1000, 1001), null, null))
        .thenReturn(List.of(older, newerAgain));
    when(permUtils.isPermitted(any(StructuredDocument.class), eq(PermissionType.READ), eq(user)))
        .thenReturn(true);

    List<RecordInformation> result = manager.getLinkedDocuments(store.getFileSystem(), paths, user);

    assertEquals(List.of(11L, 12L), result.stream().map(RecordInformation::getId).toList());
  }

  @Test
  void lookupByFilesystemDeniedByTheAclNeverReadsTheIndex() {
    doThrow(new AuthorizationException("denied"))
        .when(aclChecker)
        .assertCanRead(user, store.getFileSystem());

    assertThrows(
        AuthorizationException.class,
        () -> manager.getLinkedDocuments(store.getFileSystem(), List.of("a.dat"), user));
    verify(nfsDao, never()).getDocumentsLinkingTo(any(), any(), any(), any());
  }

  @Test
  void unknownFilestoreIsNotFound() {
    when(nfsDao.getNfsFileStore(404L)).thenReturn(null);
    when(messages.getMessage(eq("netFileStores.errors.fileStoreNotFound"), any()))
        .thenReturn("not found");

    assertThrows(NotFoundException.class, () -> manager.getLinkedDocuments(404L, "/a", user));
  }
}
