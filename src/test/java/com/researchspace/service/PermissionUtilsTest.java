package com.researchspace.service;

import static org.junit.Assert.*;

import com.researchspace.auth.PermissionUtils;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.Group;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.permissions.*;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import java.util.*;
import org.apache.shiro.authz.Permission;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PermissionUtilsTest {

  private IPermissionUtils permissionUtils;
  private PermissionUtilsTSS permissionUtilsTSS;
  private User user;

  @Before
  public void setUp() throws Exception {
    permissionUtils = new PermissionUtils();
    user = TestFactory.createAnyUser("user");
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testFindBy() {
    ConstraintBasedPermission toFind =
        new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.WRITE);
    Long EXPECTED_ID = 1L;
    toFind.setIdConstraint(new IdConstraint(EXPECTED_ID));

    Set<Permission> perms = new HashSet<Permission>();
    perms.add(toFind);

    assertEquals(
        toFind,
        permissionUtils.findBy(perms, PermissionDomain.RECORD, new IdConstraint(EXPECTED_ID)));
    // returns null if not found
    Long OTHER_ID = 2l;
    assertNull(permissionUtils.findBy(perms, PermissionDomain.RECORD, new IdConstraint(OTHER_ID)));
  }

  @Test
  public void testFindByReadWriteGetsWritePreferentially() {
    ConstraintBasedPermission write =
        new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.WRITE);
    ConstraintBasedPermission read =
        new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ);
    Long EXPECTED_ID = 1L;
    write.setIdConstraint(new IdConstraint(EXPECTED_ID));
    read.setIdConstraint(new IdConstraint(EXPECTED_ID));

    Set<Permission> perms = new HashSet<Permission>();
    perms.add(read);
    perms.add(write);
    // read was 1st, so is first found
    assertEquals(
        read,
        permissionUtils.findBy(perms, PermissionDomain.RECORD, new IdConstraint(EXPECTED_ID)));

    // explicitly prefer write
    assertEquals(
        write,
        permissionUtils.findBy(
            perms,
            PermissionDomain.RECORD,
            new IdConstraint(EXPECTED_ID),
            TransformerUtils.toList(PermissionType.WRITE, PermissionType.READ)));

    // explicitly prefer non-existent, fallsback to read
    assertEquals(
        read,
        permissionUtils.findBy(
            perms,
            PermissionDomain.RECORD,
            new IdConstraint(EXPECTED_ID),
            TransformerUtils.toList(PermissionType.CREATE, PermissionType.READ)));

    // return null if neither exist
    assertNull(
        permissionUtils.findBy(
            perms,
            PermissionDomain.RECORD,
            new IdConstraint(EXPECTED_ID),
            TransformerUtils.toList(PermissionType.CREATE, PermissionType.CREATE_FOLDER)));

    // explicitly prefer read
    assertEquals(
        read,
        permissionUtils.findBy(
            perms,
            PermissionDomain.RECORD,
            new IdConstraint(EXPECTED_ID),
            TransformerUtils.toList(PermissionType.READ, PermissionType.WRITE)));

    // returns null if not found
    Long OTHER_ID = 2l;
    assertNull(permissionUtils.findBy(perms, PermissionDomain.RECORD, new IdConstraint(OTHER_ID)));
  }

  class PermissionUtilsTSS extends PermissionUtils {
    boolean refreshed = false;
    boolean isPermitted = false;
    String uname;

    public void refreshCache() {
      refreshed = true;
    }

    @Override
    protected String getSubjectUserName() {
      return uname;
    }

    @Override
    protected boolean checkPermissions(AbstractEntityPermissionAdapter adapter, User u) {
      return isPermitted;
    }
  }

  @Test
  public void testNotifyCacheRefresh() {
    permissionUtilsTSS = new PermissionUtilsTSS();
    permissionUtils = permissionUtilsTSS;

    User a = TestFactory.createAnyUser("A");
    a.addRole(Role.PI_ROLE);
    User b = TestFactory.createAnyUser("B");
    Group g = TestFactory.createAnyGroup(a, new User[] {b});

    // simulate a loggedin
    permissionUtilsTSS.uname = a.getUsername();
    permissionUtils.notifyUserOrGroupToRefreshCache(g);

    // now simulate b logged in; refreshCache should be called
    permissionUtilsTSS.uname = b.getUsername();
    permissionUtils.refreshCacheIfNotified();

    assertTrue(permissionUtilsTSS.refreshed);

    // but will not be called twice without a separate notification
    permissionUtilsTSS.refreshed = false; // reset
    permissionUtils.refreshCacheIfNotified();
    assertFalse(permissionUtilsTSS.refreshed); // notificatin has been cancelled
  }

  @Test
  public void testFilterISearchResultsOfTPermissionType() {
    permissionUtilsTSS = new PermissionUtilsTSS();
    permissionUtils = permissionUtilsTSS;

    permissionUtilsTSS.isPermitted = true;
    List<RSForm> templates = createListOfForms();
    ISearchResults<RSForm> srchResults =
        new SearchResultsImpl<RSForm>(templates, 0, (long) templates.size());
    // filters in place
    permissionUtils.filter(srchResults, PermissionType.READ, user);
    assertEquals(2, srchResults.getResults().size());

    permissionUtilsTSS.isPermitted = false;
    // filters in place
    permissionUtils.filter(srchResults, PermissionType.READ, user);
    assertEquals(0, srchResults.getResults().size());
  }

  List<RSForm> createListOfForms() {
    RSForm anyTemplate1 = TestFactory.createAnyForm("temp1");
    RSForm anyTemplate2 = TestFactory.createAnyForm("temp2");
    // arrays.asList does not allow remove operations!
    List<RSForm> templates =
        new ArrayList<RSForm>(Arrays.asList(new RSForm[] {anyTemplate1, anyTemplate2}));
    return templates;
  }

  @Test
  public void testFilterCollectionOfTPermissionType() {
    permissionUtilsTSS = new PermissionUtilsTSS();
    permissionUtils = permissionUtilsTSS;
    List<RSForm> templates = createListOfForms();

    permissionUtilsTSS.isPermitted = true;
    // filters in place
    permissionUtils.filter(templates, PermissionType.READ, user);
    assertEquals(2, templates.size());

    permissionUtilsTSS.isPermitted = false;
    // filters in place
    permissionUtils.filter(templates, PermissionType.READ, user);
    assertEquals(0, templates.size());
  }

  @Test
  public void testIsPermitted() {
    permissionUtilsTSS = new PermissionUtilsTSS();
    permissionUtils = permissionUtilsTSS;
    RSForm anyTemplate = TestFactory.createAnyForm("temp1");
    permissionUtilsTSS.isPermitted = true;
    assertTrue(permissionUtils.isPermitted(anyTemplate, PermissionType.READ, user));

    permissionUtilsTSS.isPermitted = false;
    assertFalse(permissionUtils.isPermitted(anyTemplate, PermissionType.READ, user));
    permissionUtilsTSS.isPermitted = true;
    // if T is null, return false
    assertFalse(permissionUtils.isPermitted(null, PermissionType.READ, user));
  }

  @Test
  public void testBaseRecordWithPublicLinkIsPermitted() {
    permissionUtils = new PermissionUtils();
    StructuredDocument structuredDocument = TestFactory.createAnySD();
    ConstraintBasedPermission cbp =
        new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ);
    ACLElement publicShared = new ACLElement(RecordGroupSharing.ANONYMOUS_USER, cbp);
    structuredDocument.getSharingACL().addACLElement(publicShared);
    assertTrue(permissionUtils.isPermitted(structuredDocument, PermissionType.READ, user));
  }
}
