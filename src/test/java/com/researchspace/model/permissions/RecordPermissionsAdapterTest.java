package com.researchspace.model.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.researchspace.model.Group;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.TestFactory;
import java.util.EnumSet;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.support.SubjectThreadState;
import org.apache.shiro.util.ThreadState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class RecordPermissionsAdapterTest {
  private static ThreadState subjectThreadState;
  private BaseRecord doc;
  private RecordPermissionAdapter adapter;
  private User user;
  @Mock private Subject subject;
  private User pi1;
  private User recordOwner;
  private ConstraintPermissionResolver constraintParser;
  private Group g1Grp;

  @BeforeEach
  public void setUp() throws Exception {
    openMocks(this);
    subjectThreadState = new SubjectThreadState(subject);
    subjectThreadState.bind();
    user = TestFactory.createAnyUser("user");
    constraintParser = new ConstraintPermissionResolver();
    when(subject.getPrincipal()).thenReturn("pi1");
    pi1 = TestFactory.createAnyUserWithRole("pi1", "ROLE_PI");
    recordOwner = TestFactory.createAnyUserWithRole("other", "ROLE_USER");
    g1Grp = new Group("g1", pi1);
    g1Grp.addMember(pi1, RoleInGroup.PI);
    g1Grp.addMember(recordOwner, RoleInGroup.DEFAULT);
    doc = TestFactory.createAnySD();
    doc.setOwner(recordOwner);
    adapter = new RecordPermissionAdapter(doc);
    adapter.setAction(PermissionType.READ);
  }

  @AfterEach
  public void tearDown() throws Exception {
    // unbind the mock Subject so it cannot leak into later tests on the same thread
    subjectThreadState.clear();
  }

  // RSPAC-625
  @Test
  public void testPIUnreadableByOtherPIs() {
    User otherPI = TestFactory.createAnyUserWithRole("other", "ROLE_PI");
    Group grp = new Group("g1", pi1);
    grp.addMember(pi1, RoleInGroup.PI);
    grp.addMember(otherPI, RoleInGroup.DEFAULT);
    doc.setOwner(otherPI);
    ConstraintBasedPermission cbp = new ConstraintBasedPermission(PermissionDomain.RECORD);
    // read not allowed
    cbp.setActions(EnumSet.of(PermissionType.READ));
    cbp.setGroupConstraint(new GroupConstraint(grp.getUniqueName()));
    assertFalse(cbp.implies(adapter));
    // but share into group OK
    cbp.setActions(EnumSet.of(PermissionType.SHARE));
    adapter.setAction(PermissionType.SHARE);
    cbp.setGroupConstraint(new GroupConstraint(grp.getUniqueName()));
    assertTrue(cbp.implies(adapter));
  }

  @Test
  public void shouldHandleSpecialPropertiesForPiOfAGroupWhenRecordSharedWithPI() {
    doc.getSharingACL().addACLElement(pi1, constraintParser.resolvePermission("RECORD:READ:"));
    PropertyConstraint constraint = adapter.handleSpecialProperties("sharingACL");
    assertEquals("pi1", constraint.getValue());
  }

  @Test
  public void shouldHandleSpecialPropertiesForPiOfAGroupWhenRecordNotSharedWithPIOrWithPIGroup() {
    doc.getSharingACL()
        .addACLElement(recordOwner, constraintParser.resolvePermission("RECORD:READ:"));
    PropertyConstraint constraint = adapter.handleSpecialProperties("sharingACL");
    assertEquals("", constraint.getValue());
  }

  @Test
  public void shouldHandleSpecialPropertiesForPiOfAGroupWhenRecordSharedWithPIsGroup() {
    doc.getSharingACL().addACLElement(g1Grp, constraintParser.resolvePermission("RECORD:READ:"));
    PropertyConstraint constraint = adapter.handleSpecialProperties("sharingACL");
    assertEquals("pi1", constraint.getValue());
  }

  @Test
  public void testImpliesForm() {
    RSForm form = TestFactory.createAnyForm("t1");
    doc = TestFactory.createAnySD(form);
    adapter = new RecordPermissionAdapter(doc);
    adapter.setAction(PermissionType.CREATE);

    ConstraintBasedPermission cbp = createUserPermission();
    cbp.addPropertyConstraint(new PropertyConstraint("form", "*"));
    assertTrue(cbp.implies(adapter));

    cbp = createUserPermission();
    cbp.addPropertyConstraint(new PropertyConstraint("form", form.getName()));
    assertTrue(cbp.implies(adapter));

    cbp = createUserPermission();
    cbp.addPropertyConstraint(new PropertyConstraint("form", "Other"));
    assertFalse(cbp.implies(adapter));
  }

  @Test
  public void testImpliesOwner() {
    RSForm t = TestFactory.createAnyForm("t1");
    doc = TestFactory.createAnySD(t);
    doc.setOwner(user);
    adapter = new RecordPermissionAdapter(doc);
    adapter.setAction(PermissionType.READ);
    ConstraintBasedPermission userPermission = createUserPermission();
    userPermission.addPropertyConstraint(new PropertyConstraint("owner", user.getUsername()));
    userPermission.addPermissionType(PermissionType.READ);

    assertTrue(userPermission.implies(adapter));

    // change owner, can't use
    doc.setOwner(new User("other"));
    assertFalse(userPermission.implies(adapter));
  }

  @Test
  public void testImpliesGroup() {
    // create a record, set owner as user. user belongs to g1
    RSForm t = TestFactory.createAnyForm("t1");
    doc = TestFactory.createAnySD(t);
    doc.setOwner(user);
    Group group = new Group("g1", user);
    group.addMember(user, RoleInGroup.RS_LAB_ADMIN);

    // u2 is another user with permission to write records in group.
    ConstraintBasedPermission userPermission = createUserPermission();
    userPermission.setGroupConstraint(new GroupConstraint("g1"));
    User u2 = new User("u2");
    group.addMember(u2, RoleInGroup.RS_LAB_ADMIN);
    userPermission.setGroupConstraint(new GroupConstraint("g1"));
    userPermission.addPermissionType(PermissionType.WRITE);
    adapter = new RecordPermissionAdapter(doc);
    adapter.setAction(PermissionType.WRITE);
    assertTrue(userPermission.implies(adapter));

    group.addPermission(userPermission);
    assertTrue(u2.isPermitted(adapter, true));

    assertFalse(u2.isPermitted(adapter, false));
  }

  public ConstraintBasedPermission createUserPermission() {
    return new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.CREATE);
  }
}
