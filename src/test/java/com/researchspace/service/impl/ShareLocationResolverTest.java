package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.researchspace.Constants;
import com.researchspace.dao.FolderDao;
import com.researchspace.model.Group;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.mapping.ShareLocationResolver;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ShareLocationResolverTest {
  @Mock private FolderDao folderDao;
  @InjectMocks private ShareLocationResolver resolver;

  User sharer;
  User recipientUser;
  Group recipientGroup;
  StructuredDocument doc;

  @BeforeEach
  public void setUp() throws Exception {
    sharer = TestFactory.createAnyUser("sharer");
    recipientUser = TestFactory.createAnyUserWithRole("recipientUser", Constants.PI_ROLE);
    recipientGroup = TestFactory.createAnyGroup(recipientUser, sharer);
    doc = new StructuredDocument(TestFactory.createAnyForm());
  }

  @Test
  public void userToUserShareTest() {
    RecordGroupSharing share = makeShare(false);
    BaseRecord sharedLocation = resolver.resolveLocation(share, doc);
    assertEquals("sharer-recipientUser", sharedLocation.getName());
  }

  @Test
  public void groupShareTest() {
    RecordGroupSharing share = makeShare(true);
    when(folderDao.getSharedFolderForGroup(recipientGroup))
        .thenReturn(share.getShared().getParent());
    BaseRecord sharedLocation = resolver.resolveLocation(share, doc);
    assertEquals(recipientGroup.getDisplayName() + "_Shared", sharedLocation.getName());
  }

  @Test
  public void groupShareToSubFolderTest() {
    Folder sharedFolderRoot =
        newFolder(123, recipientGroup.getDisplayName() + "_Shared", false, true);
    Folder sharedSubFolder = newFolder(456, "shared_sub_folder", false, true);
    sharedFolderRoot.setType("SHARED_GROUP_FOLDER_ROOT");
    sharedSubFolder.setType("SHARED_FOLDER");
    sharedFolderRoot.addChild(sharedSubFolder, sharer);

    RecordGroupSharing share = new RecordGroupSharing();
    share.setSharedBy(sharer);
    share.setSharee(recipientGroup);

    Set<RecordToFolder> parents = new HashSet<>();
    parents.add(makeRecordToFolder(doc, sharedSubFolder));
    doc.setParents(parents);
    doc.setId(7001L);
    share.setShared(doc);

    when(folderDao.getSharedFolderForGroup(recipientGroup)).thenReturn(sharedFolderRoot);
    BaseRecord sharedLocation = resolver.resolveLocation(share, doc);

    assertEquals(sharedSubFolder.getName(), sharedLocation.getName());
  }

  @Test
  public void notebookShareTest() {
    Notebook notebook = new Notebook();
    notebook.setId(123L);
    notebook.setName("my notebook");
    notebook.setSharedStatus(BaseRecord.SharedStatus.SHARED);
    notebook.setType("NOTEBOOK");
    notebook.addChild(doc, sharer);

    RecordGroupSharing share = new RecordGroupSharing();
    share.setSharedBy(sharer);
    share.setSharee(recipientGroup);

    Set<RecordToFolder> parents = new HashSet<>();
    parents.add(makeRecordToFolder(doc, notebook));
    doc.setParents(parents);
    doc.setId(7001L);
    // for notebooks the share is the notebook, and the doc is shared by being a part of that
    // notebook
    share.setShared(notebook);

    BaseRecord sharedLocation = resolver.resolveLocation(share, doc);

    assertEquals(notebook.getName(), sharedLocation.getName());
  }

  @Test
  public void userToUserShareToSubFolderTest() {
    Folder sharedFolderRoot =
        newFolder(8000L, sharer.getUsername() + "-" + recipientUser.getUniqueName(), false, true);
    sharedFolderRoot.setType("SHARED_FOLDER");

    Folder sharedSubFolder = newFolder(8001L, "u2u_shared_sub_folder", false, true);
    sharedSubFolder.setType("SHARED_FOLDER");
    sharedFolderRoot.addChild(sharedSubFolder, sharer);

    RecordGroupSharing share = new RecordGroupSharing();
    share.setSharedBy(sharer);
    share.setSharee(recipientUser);

    Set<RecordToFolder> parents = new HashSet<>();
    parents.add(makeRecordToFolder(doc, sharedSubFolder));
    doc.setParents(parents);
    doc.setId(7002L);
    share.setShared(doc);

    BaseRecord sharedLocation = resolver.resolveLocation(share, doc);

    assertEquals(sharedSubFolder.getName(), sharedLocation.getName());
  }

  @Test
  public void docWithinMultipleNotebooksTest() {
    // Document belongs to two notebooks (e.g. one in the users workspace and one in a shared
    // location)
    Notebook nb1 = new Notebook();
    nb1.setId(9001L);
    nb1.setName("Notebook A");
    nb1.setSharedStatus(BaseRecord.SharedStatus.SHARED);
    nb1.setType("NOTEBOOK");

    Notebook nb2 = new Notebook();
    nb2.setId(9002L);
    nb2.setName("Notebook B");
    nb2.setSharedStatus(BaseRecord.SharedStatus.SHARED);
    nb2.setType("NOTEBOOK");

    nb1.addChild(doc, sharer);
    nb2.addChild(doc, sharer);

    Set<RecordToFolder> parents = new HashSet<>();
    parents.add(makeRecordToFolder(doc, nb1));
    parents.add(makeRecordToFolder(doc, nb2));
    doc.setParents(parents);
    doc.setId(7003L);

    RecordGroupSharing nb1Share = new RecordGroupSharing();
    nb1Share.setSharedBy(sharer);
    nb1Share.setSharee(recipientGroup);
    nb1Share.setShared(nb1);

    RecordGroupSharing nb2Share = new RecordGroupSharing();
    nb2Share.setSharedBy(sharer);
    nb2Share.setSharee(recipientGroup);
    nb2Share.setShared(nb2);

    BaseRecord sharedLocation1 = resolver.resolveLocation(nb1Share, doc);
    assertEquals(nb1.getName(), sharedLocation1.getName());
    assertEquals(nb1.getId(), sharedLocation1.getId());

    BaseRecord sharedLocation2 = resolver.resolveLocation(nb2Share, doc);
    assertEquals(nb2.getName(), sharedLocation2.getName());
    assertEquals(nb2.getId(), sharedLocation2.getId());
  }

  private RecordGroupSharing makeShare(boolean recipientIsGroup) {
    RecordGroupSharing rgs = new RecordGroupSharing();
    rgs.setSharedBy(sharer);
    rgs.setSharee(recipientIsGroup ? recipientGroup : recipientUser);

    Set<RecordToFolder> parents = new HashSet<>();

    String name =
        recipientIsGroup
            ? recipientGroup.getDisplayName() + "_Shared"
            : sharer.getUsername() + "-" + recipientUser.getUniqueName();
    Folder sharedFolderRoot = newFolder(6000L, name, false, true);
    sharedFolderRoot.setType("SHARED_GROUP_FOLDER_ROOT");
    parents.add(makeRecordToFolder(doc, sharedFolderRoot));

    doc.setParents(parents);
    doc.setId(7001L);
    rgs.setShared(doc);
    return rgs;
  }

  private static RecordToFolder makeRecordToFolder(BaseRecord record, Folder folder) {
    return new RecordToFolder(record, folder, "username");
  }

  private static Folder newFolder(long id, String name, boolean notebook, boolean shared) {
    Folder f = new Folder();
    f.setId(id);
    f.setName(name);
    if (notebook) {
      f.setType("NOTEBOOK");
    }

    if (shared) {
      f.setSharedStatus(BaseRecord.SharedStatus.SHARED);
    }
    return f;
  }
}
