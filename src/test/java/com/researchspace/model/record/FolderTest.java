package com.researchspace.model.record;

import static com.researchspace.model.record.Folder.SHARED_FOLDER_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.permissions.ACLElement;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.ConstraintPermissionResolver;
import com.researchspace.model.permissions.DefaultPermissionFactory;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionType;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class FolderTest {

  private Folder f, t1, t2, t3;
  private StructuredDocument sd;
  private Snippet snippet;
  private User anyuser = TestFactory.createAnyUser("any");

  @BeforeEach
  public void setUp() throws Exception {
    f = new Folder();
    t1 = t2 = t3 = null;
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void dateTimeNotNullAfterCreation() {
    assertNotNull(f.getCreationDate());
    assertNotNull(f.getEditInfo().getCreationDateMillis());
  }

  @Test
  public void getGenerateCycleAttemptThrowsException() throws IllegalAddChildOperation {
    Folder f2 = TestFactory.createAFolder("1", anyuser);
    Folder f3 = TestFactory.createAFolder("1", anyuser);
    f2.addChild(f3, anyuser);
    assertThrows(IllegalAddChildOperation.class, () -> f3.addChild(f2, anyuser));
  }

  @Test
  public void copyFolder() throws InterruptedException, IllegalAddChildOperation {
    makeNestedFolders();
    t2.setDocTag("test t2 tags");
    Folder t2Copy = t2.copy();
    assertEquals(t2.getDocTag(), t2Copy.getDocTag());
    assertEquals(t2.getName(), t2Copy.getName());
    assertThat(t2Copy.getParents()).isEmpty();
  }

  @Test
  public void isChildOf() throws InterruptedException {
    makeNestedFolders();
    assertTrue(t2.isDescendantOf(t1));
    assertTrue(t3.isDescendantOf(t2));
    assertFalse(t1.isDescendantOf(t2));
    // cannot be child of itself
    assertFalse(t2.isDescendantOf(t2));
  }

  @Test
  public void moveToThrowsExceptionOnMoveToSelf()
      throws InterruptedException, IllegalAddChildOperation {
    makeNestedFolders();
    Folder parent = t2.getSingleParent();

    assertThrows(IllegalAddChildOperation.class, () -> t2.move(parent, t2, anyuser));
  }

  @Test
  public void moveToThrowsExceptionOnMoveToChildOfSelf()
      throws InterruptedException, IllegalAddChildOperation {
    makeNestedFolders();
    Folder parent = t2.getSingleParent();

    assertThrows(IllegalAddChildOperation.class, () -> t2.move(parent, t3, anyuser));
  }

  @Test
  public void moveToReturnsFalseIfTargetAlreadyHoldsRecord()
      throws InterruptedException, IllegalAddChildOperation {
    makeNestedFolders();
    Folder old = t3.getParent();
    assertTrue(t3.move(t3.getParent(), t1, anyuser)); // OK
    assertFalse(t3.move(old, t1, anyuser)); // not OK, is already in t1
    assertFalse(t3.move(null, null, null)); // no null args
  }

  @Test
  public void moveToReturnsFalseIfMovingSharedFoldersOutOfShared() {
    Folder workspaceRoot = new Folder();
    workspaceRoot.addType(RecordType.ROOT);
    Folder workspaceSubfolder = new Folder();

    Folder sharedFolder = new Folder();
    sharedFolder.setSystemFolder(true);
    sharedFolder.setName(SHARED_FOLDER_NAME);
    Folder sharedGroupFolder = new Folder();
    sharedGroupFolder.addType(RecordType.SHARED_GROUP_FOLDER_ROOT);
    Folder sharedGroupSubFolder = new Folder();
    sharedGroupSubFolder.addType(RecordType.SHARED_FOLDER);

    workspaceRoot.addChild(workspaceSubfolder, anyuser);
    workspaceRoot.addChild(sharedFolder, anyuser);
    sharedFolder.addChild(sharedGroupFolder, anyuser);
    sharedGroupFolder.addChild(sharedGroupSubFolder, anyuser);

    // shared folders of various types shouldn't be movable out of Workspace->Shared hierarchy
    assertFalse(sharedFolder.move(workspaceRoot, workspaceSubfolder, anyuser));
    assertFalse(sharedGroupFolder.move(sharedFolder, workspaceSubfolder, anyuser));
    assertFalse(sharedGroupSubFolder.move(sharedGroupFolder, workspaceSubfolder, anyuser));
  }

  @Test
  public void moveFromSharedFolderToNotebookAllowedWhenOnlyAnonymousAudienceIsShared() {
    Folder sharedFolder = TestFactory.createAFolder("shared", anyuser);
    sharedFolder.addType(RecordType.SHARED_FOLDER);
    Notebook notebook = TestFactory.createANotebook("notebook", anyuser);
    StructuredDocument document = TestFactory.createAnySD();
    document.setOwner(anyuser);
    sharedFolder.addChild(document, anyuser);

    ConstraintBasedPermission readPermission =
        new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ);
    sharedFolder
        .getSharingACL()
        .addACLElement(new ACLElement(RecordGroupSharing.ANONYMOUS_USER, readPermission));
    notebook
        .getSharingACL()
        .addACLElement(new ACLElement(RecordGroupSharing.ANONYMOUS_USER, readPermission));

    assertTrue(document.move(sharedFolder, notebook, anyuser));
  }

  @Test
  public void moveFromSharedFolderToNotebookBlockedWhenDestinationCoversOnlyPartOfAudience() {
    Folder sharedFolder = TestFactory.createAFolder("shared", anyuser);
    sharedFolder.addType(RecordType.SHARED_FOLDER);
    Notebook notebook = TestFactory.createANotebook("notebook", anyuser);
    StructuredDocument document = TestFactory.createAnySD();
    document.setOwner(anyuser);
    sharedFolder.addChild(document, anyuser);

    ConstraintBasedPermission readPermission =
        new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ);
    sharedFolder.getSharingACL().addACLElement(new ACLElement("alice", readPermission));
    sharedFolder.getSharingACL().addACLElement(new ACLElement("bob", readPermission));
    notebook.getSharingACL().addACLElement(new ACLElement("alice", readPermission));

    assertFalse(document.move(sharedFolder, notebook, anyuser));

    notebook.getSharingACL().addACLElement(new ACLElement("bob", readPermission));
    assertTrue(document.move(sharedFolder, notebook, anyuser));
  }

  @Test
  public void isRoot() throws IllegalAddChildOperation {
    Folder f = new Folder();
    assertFalse(f.isRootFolder());
    f.addType(RecordType.ROOT);
    assertTrue(f.isRootFolder());

    Folder newRoot = new Folder();
    newRoot.setOwner(anyuser);
    f.setOwner(anyuser);
    newRoot.addChild(f, anyuser);
    newRoot.addType(RecordType.ROOT);
    assertTrue(f.isRootFolder()); // must can be root for a user
    assertTrue(newRoot.isRootFolderForUser(anyuser));
    assertFalse(f.isRootFolderForUser(TestFactory.createAnyUser("another")));
  }

  @Test
  public void globalIdPrefix_RSPAC842() throws IllegalAddChildOperation {
    Folder anyFolder = TestFactory.createAFolder("any", anyuser);
    assertEquals(GlobalIdPrefix.FL, anyFolder.getGlobalIdPrefix());

    Folder mediaRoot = TestFactory.createAFolder("media", anyuser);
    mediaRoot.addType(RecordType.ROOT_MEDIA);
    mediaRoot.addChild(anyFolder, anyuser);
    assertEquals(GlobalIdPrefix.GF, anyFolder.getGlobalIdPrefix());
  }

  @Test
  public void isTemplateFolder() throws IllegalAddChildOperation {
    Folder folder = new Folder();
    assertFalse(folder.isTemplateFolder());
    folder.addType(RecordType.TEMPLATE);
    assertFalse(folder.isRootFolder());
    folder.setSystemFolder(true);
    assertFalse(folder.isRootFolder());
    folder.setName(Folder.TEMPLATE_MEDIA_FOLDER_NAME);
    // all must be set
    assertTrue(folder.isTemplateFolder());
    folder.setSystemFolder(false);
    assertFalse(folder.isRootFolder());
    assertFalse(folder.isApiInboxFolder());
  }

  @Test
  public void isSharedFolder() throws IllegalAddChildOperation {
    Folder folder = new Folder();
    assertFalse(folder.isSharedFolder());
    folder.addType(RecordType.SHARED_FOLDER);
    assertTrue(folder.isSharedFolder());
    folder.removeType(RecordType.SHARED_FOLDER);
    folder.addType(RecordType.SHARED_GROUP_FOLDER_ROOT);
    assertTrue(folder.isSharedFolder());
    folder.removeType(RecordType.SHARED_GROUP_FOLDER_ROOT);
    folder.addType(RecordType.INDIVIDUAL_SHARED_FOLDER_ROOT);
    assertTrue(folder.isSharedFolder());
    folder.removeType(RecordType.INDIVIDUAL_SHARED_FOLDER_ROOT);
    assertFalse(folder.isSharedFolder());
    assertFalse(folder.isApiInboxFolder());
  }

  @Test
  public void addToSharedFolder() throws IllegalAddChildOperation {
    Folder sharedFolder = new Folder();
    sharedFolder.setSystemFolder(true);
    sharedFolder.setName(SHARED_FOLDER_NAME);
    User user = TestFactory.createAnyUser("us");
    Folder root = TestFactory.createAFolder("root", user);
    root.addType(RecordType.ROOT);
    root.addChild(sharedFolder, user);
    Folder added = sharedFolder.addChild(TestFactory.createAnySD(), user).getFolder();
    assertEquals(added, sharedFolder);
  }

  @Test
  public void addToFolderTemplateACL() throws IllegalAddChildOperation {
    Folder templateFolder = TestFactory.createTemplateFolder(anyuser);
    Folder newFolder = TestFactory.createAFolder("any", anyuser);
    assertFalse(newFolder.getSharingACL().isPermitted(anyuser, PermissionType.DELETE));
    templateFolder.addChild(newFolder, anyuser);
    assertTrue(newFolder.getSharingACL().isPermitted(anyuser, PermissionType.DELETE));
  }

  @Test
  public void defaultACLPRopagationPolicyDoesNotPropagatesAnonymousShareAcl()
      throws InterruptedException {
    makeNestedFolders();
    Folder newRoot = TestFactory.createAFolder("newParent", anyuser);
    ConstraintPermissionResolver parser = new ConstraintPermissionResolver();
    ACLElement anonymousSharedElement =
        new ACLElement(RecordGroupSharing.ANONYMOUS_USER, parser.resolvePermission("RECORD:READ:"));
    newRoot.getSharingACL().addACLElement(anonymousSharedElement);
    newRoot.addChild(t1, anyuser);
    for (ACLElement el : t1.getSharingACL().getAclElements()) {
      assertFalse(el.getUserOrGrpUniqueName().equals(RecordGroupSharing.ANONYMOUS_USER));
    }
  }

  @Test
  public void defaultACLPropagationPolicyPropagatesACLsToDirectChild()
      throws InterruptedException, IllegalAddChildOperation {
    makeNestedFolders();
    Folder newRoot = TestFactory.createAFolder("newParent", anyuser);

    // test ADDITION
    ConstraintBasedPermission perm =
        new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.WRITE);
    newRoot.getSharingACL().addACLElement(anyuser, perm);
    assertTrue(newRoot.getSharingACL().isPermitted(anyuser, PermissionType.WRITE));
    newRoot.addChild(t1, anyuser);
    assertTrue(t1.getSharingACL().isPermitted(anyuser, PermissionType.WRITE));
    // FolderManager propagates to content; do it by hand so the removal below has an ACL to remove
    assertFalse(t2.getSharingACL().isPermitted(anyuser, PermissionType.WRITE));
    ACLPropagationPolicy.DEFAULT_POLICY.onAdd(t1, t2);

    // on REmoval - remove all ACLS inheriting from the old parent
    t1.removeChild(t2);
    assertTrue(t1.getSharingACL().isPermitted(anyuser, PermissionType.WRITE));
    assertFalse(t2.getSharingACL().isPermitted(anyuser, PermissionType.WRITE));
  }

  @Test
  public void removingFromOneParentKeepsAclFromTheOther()
      throws InterruptedException, IllegalAddChildOperation {
    makeNestedFolders();

    ConstraintBasedPermission perm =
        new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.WRITE);

    Folder newRoot1 = TestFactory.createAFolder("newParent1", anyuser);
    newRoot1.getSharingACL().addACLElement(anyuser, perm);
    newRoot1.addChild(t2, anyuser);

    User other = TestFactory.createAnyUser("other");
    Folder newRoot2 = TestFactory.createAFolder("newParent2", other);
    newRoot2.getSharingACL().addACLElement(other, perm);
    newRoot2.addChild(t2, other);

    // t2 has permissions from both newRoot and newRoot2
    assertEquals(2, t2.getSharingACL().getNumPermissions());
    assertTrue(t2.getSharingACL().isPermitted(other, PermissionType.WRITE));
    assertTrue(t2.getSharingACL().isPermitted(anyuser, PermissionType.WRITE));

    // now remove t2 from newparent2 - this should remove only newParent2's
    // permissions
    newRoot2.removeChild(t2);
    assertFalse(t2.getSharingACL().isPermitted(other, PermissionType.WRITE));
    assertTrue(t2.getSharingACL().isPermitted(anyuser, PermissionType.WRITE));
  }

  @Test
  public void getAllAncestors() throws InterruptedException, IllegalAddChildOperation {
    Folder root = makeNestedFolders();
    List<Folder> folders = sd.getAllAncestors();
    assertThat(folders).hasSize(3);
    Folder x = TestFactory.createAFolder("parent2", anyuser);
    x.addChild(sd, anyuser);
    assertThat(sd.getAllAncestors()).hasSize(4);

    // create artificial cycle in folder structure, ensure that getAllAncestors can handle it
    folders.get(0).doAdd(root, anyuser);
    assertThat(sd.getAllAncestors()).hasSize(4);
  }

  @Test
  public void getAncestorByType() throws InterruptedException, IllegalAddChildOperation {
    Folder root = makeNestedFolders();
    assertFalse(t3.hasAncestorOfType(RecordType.ROOT, true));
    root.addType(RecordType.ROOT);
    assertTrue(t3.hasAncestorOfType(RecordType.ROOT, true));
    t2.addType(RecordType.NOTEBOOK);
    assertTrue(t3.hasAncestorOfType(RecordType.NOTEBOOK, true));
    // include calling object in type comparison or not?
    assertTrue(t2.hasAncestorOfType(RecordType.NOTEBOOK, true));
    assertFalse(t2.hasAncestorOfType(RecordType.NOTEBOOK, false));
    // check top of hierarchy is handled OK
    assertFalse(root.hasAncestorOfType(RecordType.ROOT, false));
  }

  @Test
  public void isApiFolder() {
    Folder apiFolder = TestFactory.createAnAPiInboxFolder(TestFactory.createAnyUser("any"));
    assertTrue(apiFolder.isApiInboxFolder());
    apiFolder.removeType(RecordType.API_INBOX);
    assertFalse(apiFolder.isApiInboxFolder());
  }

  @Test
  public void isImportsFolder() {
    Folder importsFolder = TestFactory.createAnImportsFolder(TestFactory.createAnyUser("any"));
    assertTrue(importsFolder.isImportsFolder());
    importsFolder.removeType(RecordType.IMPORTS);
    assertFalse(importsFolder.isImportsFolder());
  }

  @Test
  @DisplayName("RSPAC-1543: children of Import folder have standard actions permitted ")
  public void addChildToImportFolder() {
    StructuredDocument child = TestFactory.createAnySD();
    User subject = child.getOwner();
    Folder importsFlder = TestFactory.createAnImportsFolder(subject);
    new DefaultPermissionFactory().setUpAclForIndividualInboxFolder(importsFlder, subject);
    importsFlder.addChild(child, subject);
    assertTrue(child.getSharingACL().isPermitted(subject, PermissionType.COPY));
    assertTrue(child.getSharingACL().isPermitted(subject, PermissionType.DELETE));
    assertTrue(child.getSharingACL().isPermitted(subject, PermissionType.RENAME));
  }

  @Test
  public void deletedDate() {
    // initially null - not deleted
    assertNull(f.getDeletedDate());

    // check date is now. more or less
    f.setRecordDeleted(true);
    Date stored = f.getDeletedDate();
    assertNotNull(stored);
    // assert deletion date is now
    assertEquals(
        DateUtils.ceiling(stored, Calendar.MINUTE), DateUtils.ceiling(new Date(), Calendar.MINUTE));

    // after undeleting, deletion date is now null again.
    f.setRecordDeleted(false);
    assertNull(f.getDeletedDate());
  }

  /** Makes a 3-deep nested folder structure with a record inside t1 /t2/t3/rtd */
  private Folder makeNestedFolders() throws InterruptedException {
    t1 = TestFactory.createAFolder("level1", anyuser);
    t2 = TestFactory.createAFolder("level2", anyuser);
    t3 = TestFactory.createAFolder("level3", anyuser);
    sd = TestFactory.createAnySD();
    sd.setOwner(anyuser);
    snippet = TestFactory.createAnySnippet(anyuser);
    try {
      t3.addChild(sd, anyuser);
      t3.addChild(snippet, anyuser);
      t2.addChild(t3, anyuser);
      t1.addChild(t2, anyuser);
    } catch (IllegalAddChildOperation e) {
      e.printStackTrace();
    }

    Thread.sleep(1);
    return t1;
  }
}
