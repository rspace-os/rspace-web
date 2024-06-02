package com.researchspace.webapp.controller;

import static com.researchspace.core.util.TransformerUtils.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.Constants;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.permissions.ACLElement;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.DefaultPermissionFactory;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionFactory;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.ACLPropagationPolicy;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.ChildAddPolicy;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.model.record.Snippet;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RecordManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

public class WorkspacePermissionsDTOFBuilderTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Mock ISearchResults<BaseRecord> results;
  @Mock FolderManager folderMger;
  @Mock RecordManager recMger;
  @InjectMocks WorkspacePermissionsDTOBuilder dtoBuilder;
  PermissionFactory permFac;
  Model model;
  User user;
  Folder parentFolder;
  final long DOC_ID = 1l;
  RecordFactory recordFac;

  @Before
  public void setUp() throws Exception {
    model = new ExtendedModelMap();
    permFac = new DefaultPermissionFactory();
    user = TestFactory.createAnyUser("any");
    parentFolder = TestFactory.createAFolder("folder", user);
    recordFac = new RecordFactory();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void optionsForMediaRecords() throws IllegalAddChildOperation {
    // now lets check with a media record:
    final EcatMediaFile media = TestFactory.createEcatAudio(1L, user);

    media.setId(DOC_ID);

    permFac.setUpACLForUserRoot(user, parentFolder);
    Long parentFolderId = 1L; // any
    final List<BaseRecord> records = toList(media);

    parentFolder.addChild(media, user);
    Mockito.when(results.getResults()).thenReturn(records);
    Mockito.when(recMger.canMove(media, parentFolder, user)).thenReturn(true);

    // folder owned by user enables crud operations on contents
    ActionPermissionsDTO result =
        dtoBuilder.addCreateAndOptionsMenuPermissions(
            parentFolder, user, model, results.getResults(), parentFolderId, false);

    assertTrue(can(result, DOC_ID, PermissionType.RENAME));
    assertTrue(can(result, DOC_ID, PermissionType.DELETE));
    // can't copy or move from workspace
    assertFalse(can(result, DOC_ID, PermissionType.SEND));
    assertFalse(can(result, DOC_ID, PermissionType.COPY));
  }

  @Test
  // RSPAC-430
  public void movePermissionsShowsGroupRootForSubfolder() throws IllegalAddChildOperation {
    final Folder grandparentFolder = TestFactory.createAFolder("sharedGroup", user);
    // default situration, we're not in a group folder...
    grandparentFolder.addChild(parentFolder, user);

    grandparentFolder.setId(2L);

    parentFolder.setId(1L);

    Mockito.when(results.getResults()).thenReturn(new ArrayList<BaseRecord>());
    Mockito.verify(folderMger, Mockito.never())
        .getGroupOrIndividualShrdFolderRootFromSharedSubfolder(
            Mockito.anyLong(), Mockito.any(User.class));

    ActionPermissionsDTO result =
        dtoBuilder.addCreateAndOptionsMenuPermissions(
            parentFolder, user, model, results.getResults(), 1L, false);
    // make sure we get the gandparent Id
    String moveRoot = (String) model.asMap().get("movetargetRoot");
    assertEquals("/", moveRoot);

    // now check if we're in shared folder..
    final long SHARED_ROOT_ID = 2;
    final long SHAREDSUBFOLDER_ID = 1;
    grandparentFolder.addType(RecordType.SHARED_GROUP_FOLDER_ROOT);
    parentFolder.addType(RecordType.SHARED_FOLDER);
    when(folderMger.getGroupOrIndividualShrdFolderRootFromSharedSubfolder(SHAREDSUBFOLDER_ID, user))
        .thenReturn(Optional.of(grandparentFolder));

    result =
        dtoBuilder.addCreateAndOptionsMenuPermissions(
            parentFolder, user, model, results.getResults(), 1L, false);
    // make sure we get the gandparent Id,i i.e., the shared folder
    moveRoot = (String) model.asMap().get("movetargetRoot");
    assertEquals(SHARED_ROOT_ID + "", moveRoot);
  }

  // RSPAC-999
  @Test
  public void snippetsCantBeMovedOrExported() {
    final Snippet snip = TestFactory.createAnySnippet(user);
    snip.setId(DOC_ID);
    permFac.setUpACLForUserRoot(user, parentFolder);
    Long parentFolderId = 1L; // any
    final List<BaseRecord> records = toList(snip);
    parentFolder.addChild(snip, user);
    when(results.getResults()).thenReturn(records);
    when(recMger.canMove(Mockito.eq(snip), Mockito.eq(parentFolder), Mockito.any(User.class)))
        .thenReturn(true);
    ActionPermissionsDTO result =
        dtoBuilder.addCreateAndOptionsMenuPermissions(
            parentFolder, user, model, results.getResults(), parentFolderId, false);
    assertFalse(can(result, DOC_ID, PermissionType.SEND));
    assertFalse(can(result, DOC_ID, PermissionType.EXPORT));
    result =
        dtoBuilder.addCreateAndOptionsMenuPermissions(
            parentFolder, user, model, results.getResults(), parentFolderId, true);
    assertFalse(can(result, DOC_ID, PermissionType.SEND));
    assertFalse(can(result, DOC_ID, PermissionType.EXPORT));
  }

  @Test
  public void onlyOwnerCanDeleteOrMoveForSearchResults() {
    final StructuredDocument sdoc = TestFactory.createAnySD();
    sdoc.setOwner(user);
    sdoc.setId(DOC_ID);

    permFac.setUpACLForUserRoot(user, parentFolder);
    Long parentFolderId = 1L; // any
    final List<BaseRecord> records = toList(sdoc);
    parentFolder.addChild(sdoc, user);
    final User other = TestFactory.createAnyUser("other");
    when(results.getResults()).thenReturn(records);
    when(recMger.canMove(Mockito.eq(sdoc), Mockito.eq(parentFolder), Mockito.any(User.class)))
        .thenReturn(true);

    // simulate search results; can delete
    ActionPermissionsDTO result =
        dtoBuilder.addCreateAndOptionsMenuPermissions(
            parentFolder, user, model, results.getResults(), parentFolderId, false);
    assertTrue(can(result, DOC_ID, PermissionType.DELETE));
    assertTrue(can(result, DOC_ID, PermissionType.SEND));
    assertTrue(can(result, DOC_ID, PermissionType.EXPORT));
    // other user, without permissions, can't delete:

    result =
        dtoBuilder.addCreateAndOptionsMenuPermissions(
            parentFolder, other, model, results.getResults(), parentFolderId, false);
    assertFalse(can(result, DOC_ID, PermissionType.DELETE));

    // now we'll give other permission to delete the doc:
    ConstraintBasedPermission cbp =
        new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.DELETE);
    // can delete, (when NOT in search results)
    sdoc.getSharingACL().addACLElement(new ACLElement(other.getUsername(), cbp));
    result =
        dtoBuilder.addCreateAndOptionsMenuPermissions(
            parentFolder, other, model, results.getResults(), parentFolderId, false);
    assertTrue(can(result, DOC_ID, PermissionType.DELETE));

    // now set is Search results = true:
    result =
        dtoBuilder.addCreateAndOptionsMenuPermissions(
            parentFolder, other, model, results.getResults(), parentFolderId, true);
    assertFalse(can(result, DOC_ID, PermissionType.DELETE));
    assertFalse(can(result, DOC_ID, PermissionType.SEND));
  }

  @Test
  public void testGetMoveFolderRoot() throws IllegalAddChildOperation {
    final StructuredDocument sdoc = TestFactory.createAnySD();

    sdoc.setId(DOC_ID);
    permFac.setUpACLForUserRoot(user, parentFolder);
    Folder template = TestFactory.createTemplateFolder(user);
    Folder templatesub = TestFactory.createAFolder("any", user);
    templatesub.setId(4L);
    template.setId(3L);
    assertTrue(template.isTemplateFolder());
    parentFolder.addChild(template, ChildAddPolicy.DEFAULT, user, ACLPropagationPolicy.NULL_POLICY);
    template.addChild(templatesub, user);

    Mockito.when(folderMger.getTemplateFolderForUser(user)).thenReturn(template);
    ActionPermissionsDTO result =
        dtoBuilder.addCreateAndOptionsMenuPermissions(
            templatesub, user, model, results.getResults(), null, true);
    assertEquals(template.getId() + "", model.asMap().get("movetargetRoot").toString());
    result =
        dtoBuilder.addCreateAndOptionsMenuPermissions(
            template, user, model, results.getResults(), null, true);
    assertEquals(template.getId() + "", model.asMap().get("movetargetRoot").toString());
    result =
        dtoBuilder.addCreateAndOptionsMenuPermissions(
            parentFolder, user, model, results.getResults(), null, true);
    assertEquals("/", model.asMap().get("movetargetRoot").toString());
  }

  // RSPAC-940
  @Test
  public void notebookOwnerCanCopySharedNotebookEntry() throws IllegalAddChildOperation {

    User pi = TestFactory.createAnyUserWithRole("pi", Constants.PI_ROLE);
    parentFolder = TestFactory.createAFolder("folder", pi);
    permFac.setUpACLForUserRoot(pi, parentFolder);
    final Notebook nb = TestFactory.createANotebook("nb", pi);
    StructuredDocument doc = TestFactory.createAnySD();
    doc.setOwner(pi);
    doc.setId(DOC_ID);
    parentFolder.addChild(nb, pi);
    nb.addChild(doc, pi);
    Group grp = TestFactory.createAnyGroup(pi, new User[] {});

    Folder grpFolder = new RecordFactory().createCommunalGroupFolder(grp, pi);
    parentFolder.addChild(grpFolder, pi);
    grpFolder.addChild(nb, pi);
    final List<BaseRecord> records = toList(doc);
    Mockito.when(results.getResults()).thenReturn(records);
    ActionPermissionsDTO result =
        dtoBuilder.addCreateAndOptionsMenuPermissions(
            nb, pi, model, results.getResults(), null, false);
    assertTrue(can(result, DOC_ID, PermissionType.COPY));
    ActionPermissionsDTO result2 =
        dtoBuilder.addCreateAndOptionsMenuPermissions(
            nb, user, model, results.getResults(), null, false);
    assertFalse(can(result2, DOC_ID, PermissionType.COPY));
    assertTrue(can(result, DOC_ID, PermissionType.EXPORT));
  }

  @Test
  public void testLabGroupFolderPerms() throws IllegalAddChildOperation {
    user.addRole(Role.PI_ROLE);
    Group anyGroup = TestFactory.createAnyGroup(user);
    Folder labGroupFolder = recordFac.createCommunalGroupFolder(anyGroup, user);
    labGroupFolder.setId(DOC_ID);
    permFac.setUpACLForGroupSharedRootFolder(anyGroup, labGroupFolder);
    final List<BaseRecord> records = toList(labGroupFolder);
    Mockito.when(results.getResults()).thenReturn(records);
    parentFolder.addChild(labGroupFolder, user);

    // RSPAC-1636 We can't rename or delete group shared folder.
    ActionPermissionsDTO result =
        dtoBuilder.addCreateAndOptionsMenuPermissions(
            parentFolder, user, model, results.getResults(), 1L, false);
    assertFalse(can(result, DOC_ID, PermissionType.COPY));
    assertFalse(can(result, DOC_ID, PermissionType.RENAME));
    assertFalse(can(result, DOC_ID, PermissionType.DELETE));
    assertFalse(can(result, DOC_ID, PermissionType.SEND)); // can't move this folder
  }

  @Test
  public void testAddCreateAndOptionsMenuPermissions() throws IllegalAddChildOperation {

    final StructuredDocument sdoc = TestFactory.createAnySD();
    sdoc.setId(DOC_ID);

    permFac.setUpACLForUserRoot(user, parentFolder);
    Long parentFolderId = 1L; // any
    final List<BaseRecord> records = toList(sdoc);

    parentFolder.addChild(sdoc, user);
    Mockito.when(results.getResults()).thenReturn(records);
    when(recMger.canMove(sdoc, parentFolder, user)).thenReturn(true);

    // folder owned by user enables crud operations on contents
    ActionPermissionsDTO result =
        dtoBuilder.addCreateAndOptionsMenuPermissions(
            parentFolder, user, model, results.getResults(), parentFolderId, false);
    assertTrue(can(result, DOC_ID, PermissionType.COPY));
    assertTrue(can(result, DOC_ID, PermissionType.RENAME));
    assertTrue(can(result, DOC_ID, PermissionType.DELETE));
    assertTrue(can(result, DOC_ID, PermissionType.SEND));

    // remove ACLs; now can't do anything
    clearACLs(user, parentFolder, sdoc);
    result =
        dtoBuilder.addCreateAndOptionsMenuPermissions(
            parentFolder, user, model, results.getResults(), parentFolderId, false);
    assertFalse(can(result, DOC_ID, PermissionType.COPY));
    assertFalse(can(result, DOC_ID, PermissionType.RENAME));
    assertFalse(can(result, DOC_ID, PermissionType.DELETE));

    // now create a notebook and make sdoc an entry;
    final Notebook nb = TestFactory.createANotebook("nb", user);
    permFac.setUpACLForUserRoot(user, nb);
    // sdoc is now in notebook
    sdoc.move(parentFolder, nb, user);
    when(recMger.canMove(sdoc, nb, user)).thenReturn(true);

    result =
        dtoBuilder.addCreateAndOptionsMenuPermissions(
            nb, user, model, results.getResults(), parentFolderId, false);
    assertTrue(can(result, DOC_ID, PermissionType.COPY));
    assertTrue(can(result, DOC_ID, PermissionType.RENAME));
    assertTrue(can(result, DOC_ID, PermissionType.DELETE));
    // notebook entries can't be moved out from:
    assertFalse(can(result, DOC_ID, PermissionType.SEND));

    parentFolder.addChild(nb, user);
    // now let's add notebook entry to shared folder and check it can be
    // moved around the sahred folder.
    final Folder sharedFolder = TestFactory.createAFolder("shared", user);
    sharedFolder.addType(RecordType.SHARED_GROUP_FOLDER_ROOT);

    sharedFolder.addChild(sdoc, user);
    when(recMger.canMove(sdoc, sharedFolder, user)).thenReturn(true);

    result =
        dtoBuilder.addCreateAndOptionsMenuPermissions(
            sharedFolder, user, model, results.getResults(), parentFolderId, false);
    assertTrue(can(result, DOC_ID, PermissionType.SEND));
  }

  private void clearACLs(User user, Folder parentFolder, StructuredDocument sdoc)
      throws IllegalAddChildOperation {
    parentFolder.removeChild(sdoc);
    parentFolder.getSharingACL().clear();
    parentFolder.addChild(sdoc, user);
  }

  private Boolean can(ActionPermissionsDTO result, long docId, PermissionType permType) {
    return result.getInstancePermissions().get(docId).get(permType.name());
  }
}
