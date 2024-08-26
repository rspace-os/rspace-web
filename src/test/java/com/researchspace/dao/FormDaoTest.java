package com.researchspace.dao;

import static com.researchspace.model.PaginationCriteria.createDefaultForClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.Constants;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SortOrder;
import com.researchspace.model.AccessControl;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.Version;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.ConstraintPermissionResolver;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.FormState;
import com.researchspace.model.record.FormUserMenu;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.model.record.TemporaryCopyLinkedToOriginalCopyPolicy;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.FormSearchCriteria;
import com.researchspace.testutils.RSpaceTestUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.shiro.authz.Permission;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.springframework.beans.factory.annotation.Autowired;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class FormDaoTest extends BaseDaoTestCase {

  private @Autowired FormDao dao;
  private @Autowired FormCreateMenuDao menuDao;
  private @Autowired GroupDao groupdao;
  private RecordFactory recordFactory;

  private RSForm[] forms = null;
  private User user;
  private ConstraintPermissionResolver parser;

  @Before
  public void setUp() throws InterruptedException {
    parser = new ConstraintPermissionResolver();
    user = createAndSaveUserIfNotExists("auser");
    initialiseContentWithEmptyContent(user);
    logoutAndLoginAs(user);
    setUpDBWith4Forms(user);
    recordFactory = new RecordFactory();
  }

  @After
  public void tearDown() throws Exception {
    RSpaceTestUtils.logout();
    super.tearDown();
  }

  @Test
  @SuppressWarnings("unchecked")
  // rspac-235
  public void testFindOldestFormByName() throws InterruptedException {
    // create 5 with same name
    setUpDBWithNForms(user, 5, new String[] {"aa", "aa", "aa", "aa", "aa"});
    RSForm oldestForm = dao.findOldestFormByName("aa");
    Long oldestCreationDate = oldestForm.getCreationDate();
    assertNotNull(oldestCreationDate);

    // get all forms called 'a'
    List<RSForm> allFormsCalledAA =
        sessionFactory
            .getCurrentSession()
            .createQuery("from RSForm where name = :name")
            .setParameter("name", "aa")
            .list();

    assertEquals(5, allFormsCalledAA.size());
    for (RSForm aForm : allFormsCalledAA) {
      Long aFormCreationDate = aForm.getCreationDate();
      assertNotNull(aFormCreationDate);
      assertTrue(
          String.format(
              "found an older one: %s/%d < %s/%d",
              aForm.getName(), aFormCreationDate, oldestForm.getName(), oldestCreationDate),
          aFormCreationDate >= oldestCreationDate);
    }
  }

  @Test
  public void testGetMostRecentVersionForStableId() throws InterruptedException {
    Thread.sleep(5);
    RSForm toCopy = forms[0];
    final int B4_count = dao.getAll().size();
    RSForm copy = toCopy.copy(new TemporaryCopyLinkedToOriginalCopyPolicy());
    copy.makeCurrentVersion(toCopy);

    dao.save(copy);

    List<RSForm> ts2 = dao.getAll();
    assertEquals(B4_count + 1, ts2.size());
    assertEquals(
        new Version(1L), dao.getMostRecentVersionForForm(toCopy.getStableID()).getVersion());
  }

  PaginationCriteria<RSForm> getAllPgCrit() {
    PaginationCriteria<RSForm> pg = PaginationCriteria.createDefaultForClass(RSForm.class);
    pg.setGetAllResults();
    return pg;
  }

  @Test
  public void testSearchByCreateMenu() {
    flushDatabaseState();
    FormSearchCriteria searchCrit = new FormSearchCriteria(PermissionType.READ);
    searchCrit.setPublishedOnly(true);
    searchCrit.setIncludeSystemForm(true);

    dao.getAllFormsByPermission(user, searchCrit, getAllPgCrit());

    // only default forms are in create menu yet.
    searchCrit.setInUserMenu(true);
    int totalInMenuInitialCount = countReadableForms(user, searchCrit, getAllPgCrit());
    assertTrue(totalInMenuInitialCount > 0); // a default form is available

    // now set 1 as wanted in create menu
    menuDao.save(new FormUserMenu(user, forms[0]));
    flushDatabaseState();
    assertEquals(totalInMenuInitialCount + 1, countReadableForms(user, searchCrit, getAllPgCrit()));
    // if the same form gets added twice, no problem, duplicates are ignored.
    menuDao.save(new FormUserMenu(user, forms[0]));
    flushDatabaseState();
    // still only 1 form.
    assertEquals(totalInMenuInitialCount + 1, countReadableForms(user, searchCrit, getAllPgCrit()));
    // we still get all results, setting this to false means we don't care if it's
    // in menu or not.
    searchCrit.setInUserMenu(false);
    assertEquals(totalInMenuInitialCount + 6, countReadableForms(user, searchCrit, getAllPgCrit()));
  }

  private int countReadableForms(
      User user2, FormSearchCriteria searchCrit, PaginationCriteria<RSForm> allPgCrit) {
    return dao.getAllFormsByPermission(user2, searchCrit, allPgCrit).getTotalHits().intValue();
  }

  @Test
  public void queryWithoutTrailingAnd() {
    ConstraintBasedPermission cbp = parser.resolvePermission("FORM:READ");
    User noFormUser = createAndSaveUserWithNoPermissions("any");
    FormSearchCriteria sc = new FormSearchCriteria();
    sc.setRequestedAction(PermissionType.READ);
    sc.setIncludeSystemForm(true);
    sc.setPublishedOnly(true);
    sc.setInUserMenu(true);
    noFormUser.addPermission(cbp);

    flushDatabaseState();
    assertEquals(0, countReadableForms(noFormUser, sc, getAllPgCrit()));
  }

  @Test
  public void userOnlyOrSearchAll() throws InterruptedException {
    ConstraintBasedPermission cbp = parser.resolvePermission("FORM:READ");
    User ownerOfFourForms = createAndSaveUserWithNoPermissions("any");
    ownerOfFourForms.addPermission(cbp);
    setUpDBWith4Forms(ownerOfFourForms);
    User otherOwnerOf1Form = createAndSaveUserWithNoPermissions("other");
    otherOwnerOf1Form.addPermission(cbp);

    setUpDBWithNForms(otherOwnerOf1Form, 1, new String[] {"other"});
    flushDatabaseState();
    FormSearchCriteria fsc = new FormSearchCriteria();
    // ignore all other factors
    fsc.setIncludeSystemForm(false);
    fsc.setInUserMenu(false);
    fsc.setPublishedOnly(false);

    // CASE 1
    fsc.setUserFormsOnly(true);
    fsc.setSearchTerm("t1"); //
    // users have READ:ALL permission on all forms but are restricted by default to
    // items they own
    assertEquals(1, countReadableForms(ownerOfFourForms, fsc, getAllPgCrit()));
    // no search match
    assertEquals(0, countReadableForms(otherOwnerOf1Form, fsc, getAllPgCrit()));
    fsc.setSearchTerm("other");
    assertEquals(0, countReadableForms(ownerOfFourForms, fsc, getAllPgCrit()));
    // no search match
    assertEquals(1, countReadableForms(otherOwnerOf1Form, fsc, getAllPgCrit()));

    // CASE 2
    fsc.setUserFormsOnly(true);
    fsc.setSearchTerm("");
    // users have READ:ALL permission on all forms but are restricted by default to
    // items they own
    assertEquals(4, countReadableForms(ownerOfFourForms, fsc, getAllPgCrit()));
    assertEquals(1, countReadableForms(otherOwnerOf1Form, fsc, getAllPgCrit()));

    // CASE 3
    fsc.setUserFormsOnly(false);
    fsc.setSearchTerm("t1");
    // 'user' in setup + 'ownerOfFourForms'
    assertEquals(2, countReadableForms(ownerOfFourForms, fsc, getAllPgCrit()));
    assertEquals(2, countReadableForms(otherOwnerOf1Form, fsc, getAllPgCrit()));

    // CASE 4
    fsc.setUserFormsOnly(false);
    fsc.setSearchTerm("");
    // will get all forms: 5 created in test, plus existing ones
    assertEquals(11, countReadableForms(ownerOfFourForms, fsc, getAllPgCrit()));
    assertEquals(11, countReadableForms(otherOwnerOf1Form, fsc, getAllPgCrit()));
  }

  @Test
  public void testByPermission() throws InterruptedException {
    User user = createAndSaveUserWithNoPermissions("any");

    Thread.sleep(5);
    RSForm CAN_ACCESS = forms[0];

    // test by Id
    ConstraintBasedPermission cbp =
        parser.resolvePermission("FORM:READ:id=" + CAN_ACCESS.getId() + "," + forms[1].getId());
    user.addPermission(cbp);
    flushDatabaseState();

    user.removePermission(cbp);

    ConstraintBasedPermission GROUP_READ =
        parser.resolvePermission("FORM:READ:property_group=true");
    user.addPermission(GROUP_READ);
    ISearchResults<RSForm> results2 = getPublishedForms(user, PermissionType.READ, true);
    assertEquals(0, results2.getTotalHits().longValue());

    // now set templates[0] as group readable, will get 1 result
    forms[0].setAccessControl(
        new AccessControl(PermissionType.WRITE, PermissionType.READ, PermissionType.NONE));
    dao.save(forms[0]);
    flushDatabaseState();
    ISearchResults<RSForm> results3 = getPublishedForms(user, PermissionType.READ, true);
    assertEquals(1, results3.getTotalHits().longValue());

    // but can't edit
    ISearchResults<RSForm> groupEditable = getPublishedForms(user, PermissionType.WRITE, true);
    assertEquals(0, groupEditable.getTotalHits().longValue());

    // set 2 templates world Readable
    for (int i = 0; i < 2; i++) {
      forms[i].getAccessControl().setWorldPermissionType(PermissionType.READ);
      dao.save(forms[i]);
    }
    flushDatabaseState();
    // and test this
    ConstraintBasedPermission GLOBALREAD =
        parser.resolvePermission("FORM:READ:property_global=true");
    user.addPermission(GLOBALREAD);
    ISearchResults<RSForm> worldReadable = getPublishedForms(user, PermissionType.READ, true);
    // 2 world-readable + standard basic document created in setup
    assertEquals(5, worldReadable.getTotalHits().longValue());

    clearPermissions(user);
    // check we now can't access anything
    assertTrue(getPublishedForms(user, PermissionType.READ, true).getResults().isEmpty());

    // test access by simple property permission
    ConstraintBasedPermission simpleProperty =
        parser.resolvePermission("FORM:READ:property_name=t3");
    user.addPermission(simpleProperty);
    assertEquals(1, getPublishedForms(user, PermissionType.READ, true).getTotalHits().longValue());

    // test access by simple owner permission
    ConstraintBasedPermission OWNER_PERMISSION =
        parser.resolvePermission("FORM:READ:property_owner=${self}");
    user.addPermission(OWNER_PERMISSION);
    assertEquals(1, getPublishedForms(user, PermissionType.READ, true).getTotalHits().longValue());

    clearPermissions(user);
    ConstraintBasedPermission NOT_OWNER =
        parser.resolvePermission("FORM:READ:property_owner=OTHER");
    user.addPermission(NOT_OWNER);
    assertEquals(0, getPublishedForms(user, PermissionType.READ, true).getTotalHits().longValue());
    // check that permissions are OR'd together
    user.addPermission(OWNER_PERMISSION);
    assertEquals(0, getPublishedForms(user, PermissionType.READ, true).getTotalHits().longValue());

    clearPermissions(user);

    User other = createAndSaveUserIfNotExists("other", Constants.PI_ROLE);

    Group g = createGroup(other);

    ConstraintBasedPermission GROUP =
        parser.resolvePermission("FORM:READ:group=" + g.getUniqueName());
    other.addPermission(GROUP);
    flushDatabaseState();
    assertEquals(7, getPublishedForms(other, PermissionType.READ, true).getTotalHits().longValue());

    // set 1 form to other user, should only access 3 now
    User notInGrp = createAndSaveUserIfNotExists("notingroup");
    forms[0].setOwner(notInGrp);
    dao.save(forms[0]);
    flushDatabaseState();

    // now add permission to view by other id as well as by group -check are OR'd
    // together:
    ConstraintBasedPermission BY_ID = parser.resolvePermission("FORM:READ:id=" + forms[0].getId());
    other.addPermission(BY_ID);
    assertEquals(7, getPublishedForms(other, PermissionType.READ, true).getTotalHits().longValue());

    clearPermissions(user);
    // check can handle no properties
    parser.resolvePermission("FORM:READ");
    other.addPermission(BY_ID);
    assertEquals(7, getPublishedForms(other, PermissionType.READ, true).getTotalHits().longValue());

    // make form no longer current
    forms[0].setCurrent(false);

    dao.save(forms[0]);
    flushDatabaseState();
    assertEquals(6, getPublishedForms(other, PermissionType.READ, true).getTotalHits().longValue());

    // lets unpublish a template
    forms[1].unpublish();

    dao.save(forms[1]);
    flushDatabaseState();
    assertEquals(5, getPublishedForms(other, PermissionType.READ, true).getTotalHits().longValue());
    assertEquals(
        6, getPublishedForms(other, PermissionType.READ, false).getTotalHits().longValue());
  }

  @Test
  public void hasUserPublishedFormsUserInOtherRecords() {
    User u1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("u1"));
    User u2 = createAndSaveUserIfNotExists(getRandomAlphabeticString("u2"));
    initialiseContentWithEmptyContent(u1);
    initialiseContentWithEmptyContent(u2);
    logoutAndLoginAs(u1);
    RSForm basicForm = recordFactory.createBasicDocumentForm(u1);
    basicForm.publish();
    basicForm.setAccessControl(
        new AccessControl(PermissionType.WRITE, PermissionType.READ, PermissionType.READ));
    formDao.save(basicForm);

    assertFalse(formDao.hasUserPublishedFormsUsedinOtherRecords(u1));
    Record record1 = recordFactory.createStructuredDocument("doc1", u1, basicForm);
    recordDao.save(record1);
    // still false for records owned by the creator of the form
    assertFalse(formDao.hasUserPublishedFormsUsedinOtherRecords(u1));

    // now login as other user and create a document based on that form....
    logoutAndLoginAs(u2);
    Record record2 = recordFactory.createStructuredDocument("doc1", u2, basicForm);
    recordDao.save(record2);
    // now, the form is used
    logoutAndLoginAs(u1);
    assertTrue(formDao.hasUserPublishedFormsUsedinOtherRecords(u1));
  }

  @Test
  public void testSearchBySystemForm() {
    RSForm basicForm = new RecordFactory().createBasicDocumentForm(user);
    formDao.save(basicForm);

    flushDatabaseState();
    // the above factory method creates the basic document form - a single text
    // field
    RSForm basic = formDao.getBasicDocumentForm();
    assertNotNull(basic);
    basicForm.setSystemForm(false);
    formDao.save(basicForm);

    // this only retrieves a basic document which is a system form
    RSForm basic2 = formDao.getBasicDocumentForm();
    assertNotNull(basic2);

    // now reset as system form
    basicForm.setSystemForm(true);
    formDao.save(basicForm);

    // include systemForms gets basic document...
    FormSearchCriteria fsc = new FormSearchCriteria();

    // includes system and non-system forms
    fsc.setIncludeSystemForm(true);
    fsc.setPublishedOnly(false);

    PaginationCriteria<RSForm> pc = PaginationCriteria.createDefaultForClass(RSForm.class);
    ISearchResults<RSForm> forms = formDao.getAllFormsByPermission(user, fsc, pc);
    int expected = 8; // 3 default + 4 created in setup + 1 created in this test

    assertEquals(expected, forms.getTotalHits().longValue());
    // not including it in search criteria does not retrieve it.
    fsc.setIncludeSystemForm(false);
    ISearchResults<RSForm> forms2 = formDao.getAllFormsByPermission(user, fsc, pc);
    assertEquals(expected - 1, forms2.getTotalHits().longValue());
  }

  @Test
  public void testByPermissionWithSearchCriteria() throws InterruptedException {
    User user = createAndSaveUserWithNoPermissions("any");
    flushDatabaseState();
    Thread.sleep(5);
    FormSearchCriteria sc = new FormSearchCriteria();
    sc.setSearchTerm("t1");
    sc.setPublishedOnly(true);
    ConstraintBasedPermission ALL = parser.resolvePermission("FORM:READ");
    user.addPermission(ALL);
    assertEquals(
        1,
        dao.getAllFormsByPermission(user, sc, createDefaultForClass(RSForm.class))
            .getTotalHits()
            .longValue());

    sc.setSearchTerm("t"); // will match all except 'BasicDocument'
    ISearchResults<RSForm> allFormsByPermission =
        dao.getAllFormsByPermission(user, sc, createDefaultForClass(RSForm.class));
    assertEquals(5, allFormsByPermission.getTotalHits().longValue());

    // now paginate with only 2 records per page
    PaginationCriteria<RSForm> pc = new PaginationCriteria<>(RSForm.class);
    pc.setPageNumber(0L);
    pc.setResultsPerPage(2);
    pc.setSortOrder(SortOrder.DESC);
    pc.setOrderBy("name");
    assertEquals(2, dao.getAllFormsByPermission(user, sc, pc).getHits().longValue());
  }

  @Test
  public void testTransferFormOwnership() throws InterruptedException {
    User originalOwner = createAndSaveRandomUser();
    setUpDBWith4Forms(originalOwner);

    User newOwner = createAndSaveRandomUser();

    List<Long> originalUserForms = getFormIdsOwnedByUser(originalOwner);
    List<Long> newOwnerForms = getFormIdsOwnedByUser(newOwner);

    // original owner has 4 forms, new owner has none
    assertEquals(4, originalUserForms.size());
    assertEquals(0, newOwnerForms.size());

    dao.transferOwnershipOfForms(originalOwner, newOwner, originalUserForms);
    flush();

    List<Long> originalOwnerFormsPostTransfer = getFormIdsOwnedByUser(originalOwner);
    List<Long> newOwnerFormsPostTransfer = getFormIdsOwnedByUser(newOwner);
    // new owners forms match those which used to be owned by the original user
    assertEquals(originalUserForms, newOwnerFormsPostTransfer);
    // original owner has no forms
    assertEquals(0, originalOwnerFormsPostTransfer.size());
  }

  private List<Long> getFormIdsOwnedByUser(User owner) {
    return formDao.getAll().stream()
        .filter(form -> form.getOwner().equals(owner))
        .map(RSForm::getId)
        .collect(Collectors.toList());
  }

  @Test
  public void testGetFormsUsedByOtherUsers() {
    User u1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("u1"));
    User u2 = createAndSaveUserIfNotExists(getRandomAlphabeticString("u2"));
    initialiseContentWithEmptyContent(u1);
    initialiseContentWithEmptyContent(u2);
    logoutAndLoginAs(u1);

    // create and publish form as u1
    RSForm u1Form = recordFactory.createBasicDocumentForm(u1);
    u1Form.publish();
    u1Form.setAccessControl(
        new AccessControl(PermissionType.WRITE, PermissionType.READ, PermissionType.READ));
    formDao.save(u1Form);

    // create doc from form as u1
    Record u1Record = recordFactory.createStructuredDocument("doc", u1, u1Form);
    recordDao.save(u1Record);
    // no forms returned as the doc was created by the form creator
    assertTrue(formDao.getFormsUsedByOtherUsers(u1).isEmpty());

    // login and create doc as u2, based on u1 form
    logoutAndLoginAs(u2);
    Record u2Record = recordFactory.createStructuredDocument("doc", u2, u1Form);
    recordDao.save(u2Record);

    // u1 now has 1 form used by other users
    List<RSForm> u1FormsUsedByOthers = formDao.getFormsUsedByOtherUsers(u1);
    assertEquals(1, u1FormsUsedByOthers.size());
    assertEquals(u1Form.getId(), u1FormsUsedByOthers.get(0).getId());
  }

  private Group createGroup(User pi) {
    // test Group permissions
    Group g = new Group("anygroup", pi);
    g.addMember(pi, RoleInGroup.PI);
    g.addMember(user, RoleInGroup.DEFAULT);
    groupdao.save(g);
    return g;
  }

  private void clearPermissions(User user2) {
    Set<Permission> toDelete = new HashSet<>();
    toDelete.addAll(user2.getAllPermissions(true, true));
    for (Permission toDel : toDelete) {
      user2.removePermission(toDel);
    }
    userDao.save(user2);
    flushDatabaseState();
  }

  private void setUpDBWith4Forms(User owner) throws InterruptedException {
    setUpDBWithNForms(owner, 4, new String[] {"t0", "t1", "t2", "t3"});
  }

  // creates n published forms ow
  private void setUpDBWithNForms(User owner, int n, String[] names) throws InterruptedException {
    forms = new RSForm[n];
    int indx = 0;
    for (String name : names) {
      Thread.sleep(5); // ensure all forms have unique equals
      RSForm form = TestFactory.createAnyForm(name);
      form.setPublishingState(FormState.PUBLISHED);
      form.setOwner(owner);
      form.setCreatedBy(owner.getUsername());
      dao.save(form);
      forms[indx++] = form;
    }
  }

  ISearchResults<RSForm> getPublishedForms(User user, PermissionType type, boolean published) {
    PaginationCriteria<RSForm> pg = PaginationCriteria.createDefaultForClass(RSForm.class);
    pg.setGetAllResults();
    FormSearchCriteria fsc = new FormSearchCriteria(type);
    fsc.setPublishedOnly(published);
    fsc.setIncludeSystemForm(true);
    return dao.getAllFormsByPermission(user, fsc, pg);
  }
}
