package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.netfiles.NfsFieldLink;
import com.researchspace.model.netfiles.NfsFileStore;
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
    when(nfsDao.getDocumentsLinkingTo(1L, List.of("data/x.dat")))
        .thenReturn(List.of(readable, hidden));
    when(permUtils.isPermitted(readable, PermissionType.READ, user)).thenReturn(true);
    when(permUtils.isPermitted(hidden, PermissionType.READ, user)).thenReturn(false);

    List<RecordInformation> result =
        manager.getLinkedDocuments(store.getFileSystem(), List.of("/data/x.dat/"), user);

    assertEquals(2, result.size());
    assertEquals("SD11", result.get(0).getOid().getIdString());
    assertNull(result.get(1).getId());
    assertNull(result.get(1).getOid());
    assertEquals("Grace Hopper", result.get(1).getOwnerFullName());
  }

  @Test
  void lookupByFilestoreAssertsReadAclAndResolvesTheAbsolutePath() {
    when(nfsDao.getNfsFileStore(1L)).thenReturn(store);
    when(nfsDao.getDocumentsLinkingTo(1L, List.of("data/XRD/run.dat"))).thenReturn(List.of());

    manager.getLinkedDocuments(1L, "/XRD/run.dat", user);

    verify(aclChecker).assertCanRead(user, store.getFileSystem());
  }

  @Test
  void rootRemotePathOfRootFilestoreResolvesToEmptyPath() {
    store.setPath("");
    when(nfsDao.getNfsFileStore(1L)).thenReturn(store);
    when(nfsDao.getDocumentsLinkingTo(1L, List.of(""))).thenReturn(List.of());

    manager.getLinkedDocuments(1L, "/", user);

    verify(nfsDao).getDocumentsLinkingTo(1L, List.of(""));
  }

  @Test
  void unknownFilestoreIsNotFound() {
    when(nfsDao.getNfsFileStore(404L)).thenReturn(null);
    when(messages.getMessage(eq("netFileStores.errors.fileStoreNotFound"), any()))
        .thenReturn("not found");

    assertThrows(NotFoundException.class, () -> manager.getLinkedDocuments(404L, "/a", user));
  }
}
