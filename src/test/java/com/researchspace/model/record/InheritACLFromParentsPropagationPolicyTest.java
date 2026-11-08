package com.researchspace.model.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.researchspace.Constants;
import com.researchspace.model.Group;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.permissions.ACLElement;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.permissions.RecordSharingACL;
import com.researchspace.model.permissions.RecordSharingAclTestMother;
import com.researchspace.testutils.TestFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

public class InheritACLFromParentsPropagationPolicyTest {
  private InheritACLFromParentsPropagationPolicy inheritACLFromParentsPropagationPolicy;
  @Mock private BaseRecord parent;
  @Mock private BaseRecord child;
  @Captor private ArgumentCaptor<List<ACLElement>> aclCaptor;
  private RecordSharingACL parentACLWithoutAnonymous =
      RecordSharingAclTestMother.createRecordSharingAclAllSamePermission(
          new String[] {"el1", "el2", "el3"}, "RECORD:CREATE:");
  private RecordSharingACL parentACLWithAnonymous =
      RecordSharingAclTestMother.createRecordSharingAclAllSamePermission(
          new String[] {"el1", "el2", "el3", RecordGroupSharing.ANONYMOUS_USER}, "RECORD:CREATE:");
  private RecordSharingACL childACLWithAnonymous =
      RecordSharingAclTestMother.createRecordSharingAclAllSamePermission(
          new String[] {"el1", "el2", "el3", RecordGroupSharing.ANONYMOUS_USER}, "RECORD:CREATE:");

  @BeforeEach
  public void setUp() {
    openMocks(this);
    when(parent.getSharingACL()).thenReturn(parentACLWithoutAnonymous);
    when(child.getSharingACL())
        .thenReturn(
            RecordSharingAclTestMother.createRecordSharingAclAllSamePermission(
                new String[] {"el1", "el2", "el3", "el4"}, "RECORD:CREATE:"));
    inheritACLFromParentsPropagationPolicy = new InheritACLFromParentsPropagationPolicy();
  }

  @Test
  public void shouldRemovePermissionsInCommonWithParent() {
    when(child.removeACLs(aclCaptor.capture())).thenReturn(child);
    inheritACLFromParentsPropagationPolicy.onRemove(parent, child);
    List<ACLElement> removedFromChild = aclCaptor.getValue();
    assertTrue(removedFromChild.equals(parentACLWithoutAnonymous.getAclElements()));
  }

  @Test
  public void shouldNotRemoveAnonymousUserPermissionsInCommonWithParent() {
    when(parent.getSharingACL()).thenReturn(parentACLWithAnonymous);
    when(child.getSharingACL()).thenReturn(childACLWithAnonymous);
    when(child.removeACLs(aclCaptor.capture())).thenReturn(child);
    inheritACLFromParentsPropagationPolicy.onRemove(parent, child);
    List<ACLElement> removedFromChild = aclCaptor.getValue();
    assertTrue(removedFromChild.equals(parentACLWithoutAnonymous.getAclElements()));
  }

  @Test
  public void notebookEntriesDoNotInheritPiAndLabAdminPermissions() {
    User owner = TestFactory.createAnyUserWithRole("owner", Constants.PI_ROLE);
    Notebook notebook = TestFactory.createANotebook("nb", owner);
    ConstraintBasedPermission read =
        new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ);
    Group group = TestFactory.createAnyGroup(owner);
    ACLElement groupAcl = new ACLElement(group.getUniqueName(), read);
    ACLElement piAcl =
        ACLElement.createRoleRestrictedGroupACL(
            group, read, RoleInGroup.PI, RoleInGroup.RS_LAB_ADMIN);
    notebook.getSharingACL().addACLElement(groupAcl);
    notebook.getSharingACL().addACLElement(piAcl);
    StructuredDocument entry = TestFactory.createAnySD();
    Folder folder = TestFactory.createAFolder("folder", owner);
    folder.getSharingACL().addACLElement(piAcl);
    StructuredDocument doc = TestFactory.createAnySD();

    inheritACLFromParentsPropagationPolicy.onAddDescendant(notebook, entry);
    inheritACLFromParentsPropagationPolicy.onAddDescendant(folder, doc);

    assertThat(entry.getSharingACL().getAclElements()).contains(groupAcl).doesNotContain(piAcl);
    assertThat(doc.getSharingACL().getAclElements()).contains(piAcl);
  }

  @Test
  public void notFromFoldersPolicyOnlyRemovesAclsFromDocuments() {
    User owner = TestFactory.createAnyUser("owner");
    ACLElement shared =
        new ACLElement(
            "grp", new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ));
    Folder parentFolder = TestFactory.createAFolder("parent", owner);
    Folder subfolder = TestFactory.createAFolder("sub", owner);
    StructuredDocument doc = TestFactory.createAnySD();
    for (BaseRecord record : List.of(parentFolder, subfolder, doc)) {
      record.getSharingACL().addACLElement(shared);
    }
    ACLPropagationPolicy policy = new InheritACLNotFromFoldersFromParentsPropagationPolicy();

    assertThat(policy.aclsRemovedFromDescendants(parentFolder, subfolder)).isEmpty();
    assertThat(policy.aclsRemovedFromDescendants(parentFolder, doc)).contains(shared);
  }
}
