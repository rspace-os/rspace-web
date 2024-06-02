package com.researchspace.webapp.controller;

import static com.researchspace.core.testutil.CoreTestUtils.assertExceptionThrown;
import static com.researchspace.testutils.RSpaceTestUtils.logoutCurrUserAndLoginAs;
import static com.researchspace.testutils.TestRunnerController.isJDK8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.Constants;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.AccessControl;
import com.researchspace.model.EditStatus;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dtos.FormSharingCommand;
import com.researchspace.model.dtos.TextFieldDTO;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.CopyIndependentFormAndFieldFormPolicy;
import com.researchspace.model.record.IconEntity;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.FormSearchCriteria;
import com.researchspace.service.IconImageManager;
import com.researchspace.session.UserSessionTracker;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.InputStream;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MvcResult;

/*
 * mixture of regular and mvc tests
 */
public class FormControllerAcceptanceMVCIT extends MVCTestBase {

  private @Autowired RSFormController formController;
  private @Autowired ImageController imageCtrller;
  private @Autowired IconImageManager iconImageManager;

  @Autowired
  public void setController(RSFormController controller) {
    this.formController = controller;
  }

  User other;

  /** Represents the 'user' user. */
  protected Principal mockOtherPrincipal = () -> other.getUsername();

  StructuredDocument docToShare;
  MockServletContext mockServletCtxt;

  Group grp;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    docToShare = null;
    other = null;
    grp = null;
    mockServletCtxt = new MockServletContext();
    mockServletCtxt.setAttribute(UserSessionTracker.USERS_KEY, anySessionTracker());
    formController.setServletContext(mockServletCtxt);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  @SuppressWarnings({"unchecked"})
  public void testListForms() throws Exception {
    docToShare = setUpLoginAsPIUserAndCreateADocument();
    // this is a basic document system form
    RSForm form = docToShare.getForm();
    PaginationCriteria<RSForm> pgCrit = PaginationCriteria.createDefaultForClass(RSForm.class);
    // these don't include system forms
    formController.listForms(modelTss, pgCrit, new FormSearchCriteria());
    List<RSForm> res = (List<RSForm>) modelTss.get("templates");
    final int originalFormCount = res.size();
    // this makes an unpublished, non-system form
    RSForm copy = form.copy(new CopyIndependentFormAndFieldFormPolicy());
    copy.setOwner(piUser);
    formMgr.save(copy, piUser);

    // basic list shows all forms, the copy is not a system form, so is
    // included in the count.
    formController.listForms(modelTss, pgCrit, new FormSearchCriteria());
    List<RSForm> res2 = (List<RSForm>) modelTss.get("templates");
    assertEquals(originalFormCount + 1, res2.size());

    // create menu should only see published forms, not 'new' ones
    formController.listFormsForCreateMenu(modelTss, pgCrit, 1L);
    List<RSForm> res3 = (List<RSForm>) modelTss.get("forms");
    final int menuCount = res3.size();

    // now lets publish the copy, and set in create menu, this should be visible in the create menu
    // now.
    copy.publish();
    formMgr.save(copy, piUser);

    formMgr.addFormToUserCreateMenu(piUser, copy.getId(), piUser);
    formController.listFormsForCreateMenu(modelTss, pgCrit, 1L);
    List<RSForm> res4 = (List<RSForm>) modelTss.get("forms");
    assertEquals(menuCount + 1, res4.size());
  }

  private static final String TEST_SEARCH_TERM = "test";

  @Test
  @SuppressWarnings("rawtypes")
  public void testSearchFormsBySearchTerm() throws Exception {

    docToShare = setUpLoginAsPIUserAndCreateADocument();
    RSForm form = docToShare.getForm();
    PaginationCriteria<RSForm> pgCrit = PaginationCriteria.createDefaultForClass(RSForm.class);
    FormSearchCriteria fsCrit = new FormSearchCriteria();
    fsCrit.setSearchTerm(TEST_SEARCH_TERM);

    /* getting initial number */
    formController.searchForms(modelTss, pgCrit, fsCrit);
    List res = (List) modelTss.get("templates");
    final int ORIGINAL_FORM_COUNT = res.size();

    /* adding form with test term in name */
    RSForm copy = form.copy(new CopyIndependentFormAndFieldFormPolicy());
    copy.setOwner(piUser);
    copy.setName("name " + TEST_SEARCH_TERM + " form");
    formMgr.save(copy, piUser);

    /* adding form with test term in tag */
    RSForm copy2 = form.copy(new CopyIndependentFormAndFieldFormPolicy());
    copy2.setOwner(piUser);
    copy2.setTags("tag " + TEST_SEARCH_TERM + " form");
    formMgr.save(copy2, piUser);

    /* should find both */
    formController.searchForms(modelTss, pgCrit, fsCrit);
    List res2 = (List) modelTss.get("templates");
    assertEquals(ORIGINAL_FORM_COUNT + 2, res2.size());

    /* adding form without test term in name */
    RSForm copy3 = form.copy(new CopyIndependentFormAndFieldFormPolicy());
    copy3.setOwner(piUser);
    copy3.setName("name form");
    formMgr.save(copy3, piUser);

    /* adding form without test term in tag */
    RSForm copy4 = form.copy(new CopyIndependentFormAndFieldFormPolicy());
    copy4.setOwner(piUser);
    copy4.setTags("tag form");
    formMgr.save(copy4, piUser);

    /* should not find forms without search term */
    formController.searchForms(modelTss, pgCrit, fsCrit);
    List res3 = (List) modelTss.get("templates");
    assertEquals(ORIGINAL_FORM_COUNT + 2, res3.size());

    /* clean up */
    formMgr.delete(copy.getId(), piUser);
    formMgr.delete(copy2.getId(), piUser);
    formMgr.delete(copy3.getId(), piUser);
    formMgr.delete(copy4.getId(), piUser);
    formController.searchForms(modelTss, pgCrit, fsCrit);
    List res4 = (List) modelTss.get("templates");
    assertEquals(ORIGINAL_FORM_COUNT, res4.size());
  }

  // rspac-2264
  @Test
  public void testaddToMenuPermissions() throws Exception {
    docToShare = setUpLoginAsPIUserAndCreateADocument();
    RSForm form = docToShare.getForm();

    final RSForm newForm =
        formMgr.copy(form.getId(), piUser, new CopyIndependentFormAndFieldFormPolicy());

    User maliciousMike = createInitAndLoginAnyUser();

    // assert both addTo and removeFrom menu
    assertExceptionThrown(
        () -> formController.toggleMenu(true, newForm.getId()), AuthorizationException.class);
    assertExceptionThrown(
        () -> formController.toggleMenu(false, newForm.getId()), AuthorizationException.class);
    logoutAndLoginAs(piUser);
    formController.toggleMenu(true, newForm.getId()); // ok
  }

  @Test
  public void testPermissionsOnDBAccess() throws Exception {
    final int NUM_FORMS = 1000;
    final int NUM_WORLD_READABLE = 10;

    docToShare = setUpLoginAsPIUserAndCreateADocument();
    RSForm form = docToShare.getForm();
    RSForm[] copies = new RSForm[NUM_FORMS];

    // counting initial forms visible to 'user'
    PaginationCriteria<RSForm> pg = new PaginationCriteria<RSForm>(RSForm.class);
    FormSearchCriteria tsc = new FormSearchCriteria(PermissionType.READ);
    int userInitFormNumber = formMgr.searchForms(piUser, tsc, pg).getTotalHits().intValue();

    // publish some forms
    openTransaction();
    for (int i = 0; i < NUM_FORMS; i++) {
      copies[i] = form.copy(new CopyIndependentFormAndFieldFormPolicy());
      copies[i].setOwner(piUser);
      copies[i].publish();
      formMgr.save(copies[i], piUser);
      Thread.sleep(1);
    }
    commitTransaction();

    // make sure 'user' can see all the forms
    ISearchResults<RSForm> forms = formMgr.searchForms(piUser, tsc, pg);
    int userNewFormNumber = forms.getTotalHits().intValue();
    assertEquals(NUM_FORMS + userInitFormNumber, userNewFormNumber);
    for (RSForm resultForm : forms.getResults()) {
      assertFalse(resultForm.isInSubjectsMenu());
    }
    formMgr.addFormToUserCreateMenu(piUser, forms.getResults().get(0).getId(), piUser);
    ISearchResults<RSForm> forms2 = formMgr.searchForms(piUser, tsc, pg);
    assertTrue(forms2.getFirstResult().isInSubjectsMenu());

    // counting initial forms visible to 'other1'
    User other1 = createAndSaveUser("other1");
    int other1InitFormNumber =
        formMgr.getAllWithPermissions(other1, PermissionType.READ, true).getTotalHits().intValue();

    // now make 10 forms World readable:
    openTransaction();
    for (int i = 0; i < NUM_WORLD_READABLE; i++) {
      copies[i].setAccessControl(
          new AccessControl(PermissionType.WRITE, PermissionType.WRITE, PermissionType.WRITE));
      formMgr.save(copies[i], piUser);
    }
    commitTransaction();

    // and verify 'other1' can see the forms that were just made world
    // readable
    int other1NewFormNumber =
        formMgr.getAllWithPermissions(other1, PermissionType.READ, true).getTotalHits().intValue();
    assertEquals(NUM_WORLD_READABLE + other1InitFormNumber, other1NewFormNumber);
  }

  /**
   * Tests several permutations of form read/write access within groups.
   *
   * @throws Exception
   */
  @Test
  public void testFormReadWriteAccess() throws Exception {
    docToShare = setUpLoginAsPIUserAndCreateADocument();
    RSForm form = docToShare.getForm();
    User pi1 = createAndSaveUser("pi1", Constants.PI_ROLE);
    User admin1 = createAndSaveUser("grpAdmn1");
    User user_1 = createAndSaveUser("grpUsr1");
    final User imposter = createAndSaveUser(getRandomAlphabeticString("imposter"));
    initUsers(pi1, admin1, user_1, imposter);

    // now we'll test that a regular user has create form permission
    final RSForm anyform = formMgr.create(user_1);

    createGroupForUsers(piUser, pi1.getUsername(), admin1.getUsername(), user_1, admin1, pi1);
    // current logged in is 'user_1', who created the form and therefore has
    // publish permission + edit pernmissio.
    logoutAndLoginAs(user_1);
    assertPublishIsAuthorized(anyform, user_1);
    assertEquals(
        "Success",
        formController.rename(anyform.getId(), "newname", new MockPrincipal(user_1)).getData());
    // .. and edit permission
    RSForm openedForm = formMgr.getForEditing(anyform.getId(), user_1, anySessionTracker());
    assertEquals(EditStatus.EDIT_MODE, openedForm.getEditStatus());
    // but, imposter can't do the following:
    logoutAndLoginAs(imposter);
    assertAuthorisationExceptionThrown(
        () -> formController.rename(anyform.getId(), "hacked", new MockPrincipal(imposter)));

    assertEquals("newname", formMgr.get(anyform.getId(), user_1).getName());

    // ---------------- PI logged in
    // now switch to the group PI - they  have publish rights on a
    // form created within the group
    logoutAndLoginAs(pi1);
    assertPublishIsAuthorized(anyform, pi1);

    // and it can open for viewing and editing
    RSForm openedForm2 = formMgr.getForEditing(anyform.getId(), pi1, anySessionTracker());
    assertEquals(EditStatus.EDIT_MODE, openedForm2.getEditStatus());

    // now we'll create a new form for use by the group.
    RSForm grpForm = formMgr.create(pi1);
    // and publish with default sharing - i.e., private
    formMgr.publish(grpForm.getId(), true, null, pi1);
    // can view/edit
    assertHasEditPermission(grpForm, pi1);
    formMgr.save(grpForm, pi1);

    // ---------------- group admin logged in
    // now switch to the group admin - they can view edit any form in
    // the group, even if private
    logoutAndLoginAs(admin1);
    assertHasEditPermission(grpForm, admin1);
    formMgr.save(grpForm, admin1);

    // ---------------- group member logged in
    // now switch to a group member - they cannot view edit any form in
    // the group
    // that has not been shared with the group.
    logoutAndLoginAs(user_1);
    assertAccessDenied(grpForm, user_1);

    // ---------------- PI logged in
    // now switch to the group PI - he'll make the template group-readable
    logoutAndLoginAs(pi1);
    FormSharingCommand tsc = new FormSharingCommand(grpForm);
    tsc.setGroupOptions(Arrays.asList(new String[] {"READ"}));
    formMgr.updatePermissions(grpForm.getId(), tsc, pi1);

    // ---------------- group member logged in
    // now switch to a group member - they can now view the shared form
    logoutCurrUserAndLoginAs(user_1.getUsername(), TESTPASSWD);
    assertHasReadPermission(grpForm, user_1);
    // ... and use it to create a document
    user_1 = userMgr.get(user_1.getId()); // update
    recordMgr.createNewStructuredDocument(user_1.getRootFolder().getId(), grpForm.getId(), user_1);
    // but user cannot edit
    assertHasNOEditPermission(grpForm, user_1);
    // or publish
    assertPublishNotAuthorized(grpForm, user_1);

    // ---------------- PI logged in
    // now switch to the group PI - he'll make the form group-WRITEABLE
    logoutCurrUserAndLoginAs(pi1.getUsername(), TESTPASSWD);
    FormSharingCommand tsc2 = new FormSharingCommand(grpForm);
    tsc2.setGroupOptions(Arrays.asList(new String[] {"WRITE"}));
    formMgr.updatePermissions(grpForm.getId(), tsc2, pi1);

    // ---------------- group member logged in
    // now switch to a group member - they can now EDIT the shared form
    logoutCurrUserAndLoginAs(user_1.getUsername(), TESTPASSWD);
    assertHasEditPermission(grpForm, user_1);
    // but still can't publish
    assertPublishNotAuthorized(grpForm, user_1);

    // now switch to a user who is the solitary member of another group
    // he should NOT be able to see the new form as it still does not have
    // world-read permissions...
    User USER_IN_OTHER_GROUP = createAndSaveUser("OUTSIDEGROUP", Constants.PI_ROLE);
    initUser(USER_IN_OTHER_GROUP);
    createGroupForUsers(piUser, USER_IN_OTHER_GROUP.getUsername(), null, USER_IN_OTHER_GROUP);
    // ---------------- OUTSIDE_GROUP member logged in
    logoutAndLoginAs(USER_IN_OTHER_GROUP);
    assertHasNOEditPermission(grpForm, USER_IN_OTHER_GROUP);
    assertAccessDenied(grpForm, USER_IN_OTHER_GROUP);
    // and the outside user certainly can't publish
    assertPublishNotAuthorized(grpForm, USER_IN_OTHER_GROUP);

    // now PI will login and make the template world-readable:
    // It is now Group Read/WRiteable and World readable.
    logoutAndLoginAs(pi1);
    // ---------------- PI member logged in
    FormSharingCommand tsc3 = new FormSharingCommand(grpForm);
    tsc3.setWorldOptions(Arrays.asList(new String[] {"READ"}));
    formMgr.updatePermissions(grpForm.getId(), tsc3, pi1);

    // now switch to a user who is not a group member - they should now
    // be able to see the new template as it is world-read permissions...
    logoutAndLoginAs(USER_IN_OTHER_GROUP);
    assertHasReadPermission(grpForm, USER_IN_OTHER_GROUP);

    // but they still can't control publishing
    assertPublishNotAuthorized(grpForm, USER_IN_OTHER_GROUP);
  }

  private void assertAccessDenied(RSForm grpForm, User user) throws Exception {
    RSForm t = formMgr.getForEditing(grpForm.getId(), user, anySessionTracker());
    assertTrue(t.getEditStatus().equals(EditStatus.ACCESS_DENIED));
  }

  private void assertHasEditPermission(RSForm grpForm, User user) throws Exception {
    RSForm t = formMgr.getForEditing(grpForm.getId(), user, anySessionTracker());
    assertTrue(t.getEditStatus().isEditable());
  }

  private void assertHasNOEditPermission(RSForm grpForm, User user) throws Exception {
    RSForm t = formMgr.getForEditing(grpForm.getId(), user, anySessionTracker());
    assertFalse(t.getEditStatus().isEditable());
  }

  private void assertHasReadPermission(RSForm grpForm, User user) throws Exception {
    RSForm form = formMgr.getForEditing(grpForm.getId(), user, anySessionTracker());
    assertTrue(form.getEditStatus().equals(EditStatus.CANNOT_EDIT_NO_PERMISSION));
  }

  private void assertPublishNotAuthorized(RSForm form, User u) throws Exception {
    assertAuthorisationExceptionThrown(
        () -> {
          formMgr.publish(form.getId(), false, null, u);
        });
  }

  private void assertPublishIsAuthorized(RSForm form, User u) {
    try {
      formMgr.publish(form.getId(), false, null, u);
    } catch (AuthorizationException ae) {
      fail(" Authorisation failed for user " + SecurityUtils.getSubject().getPrincipal());
    }
  }

  @Test
  public void deleteFieldTest() throws Exception {
    User u1 = createAndSaveUser(getRandomAlphabeticString("any"));
    initUser(u1);
    logoutAndLoginAs(u1);
    mockPrincipal = new MockPrincipal(u1.getUsername());

    formController.createForm(model);
    RSForm newForm = getFormFromModel();

    formController.editForm(model, mockPrincipal, newForm.getId());
    RSForm tempform = getFormFromModel();

    // create 2 fields in forms, these are added into real form as it is new and unpublished
    formController.createTextField(new TextFieldDTO<>("f1", "f1"), tempform.getId());
    formController.createTextField(new TextFieldDTO<>("f2", "f2"), tempform.getId());
    formController.saveForm(tempform.getId(), mockPrincipal);
    FormSharingCommand publishCmd = new FormSharingCommand(newForm);
    formMgr.publish(newForm.getId(), true, publishCmd, u1);
    // now create doc from this form:

    Long rootId = folderMgr.getRootFolderForUser(u1).getId();
    StructuredDocument created = recordMgr.createNewStructuredDocument(rootId, newForm.getId(), u1);
    // need to get loaded form in order to get ID
    formController.editForm(model, mockPrincipal, newForm.getId());
    tempform = getFormFromModel();
    int NUMFIELDS = tempform.getFieldForms().size();
    AjaxReturnObject<String> aro =
        formController.deleteFieldbyId(tempform.getFieldForms().iterator().next().getId());
    formController.saveForm(tempform.getId(), mockPrincipal);
    assertNotNull(aro.getData());

    formController.editForm(model, mockPrincipal, newForm.getId());
    RSForm form2 = getFormFromModel();
    // assertFieldTempalte was deleted - RSPAC-550.
    assertEquals(NUMFIELDS - 1, form2.getFieldForms().size());
    AjaxReturnObject<Long> abandonedResult =
        formController.abandonUpdateForm(tempform.getId(), mockPrincipal);
    assertNotNull(abandonedResult);
    formController.editForm(model, mockPrincipal, newForm.getId());
    tempform = getFormFromModel();
    // now we're back to the original state.
    assertEquals(NUMFIELDS, tempform.getFieldForms().size());

    // now we'll add 2 fields, save&delete a field and save it
    tempform = getFormFromModel();
    // create 2 fields in forms
    formController.createTextField(new TextFieldDTO<>("f1", "f1"), tempform.getId());
    formController.createTextField(new TextFieldDTO<>("f2", "f2"), tempform.getId());
    Long currVersionId = formController.updateForm(tempform.getId(), mockPrincipal).getData();

    // create a doc with the current form id, assert has 4 fields:
    created = recordMgr.createNewStructuredDocument(rootId, currVersionId, u1);
    assertEquals(4, created.getFieldCount());
    // now we'll delete a field and persist this
    formController.editForm(model, mockPrincipal, currVersionId);
    tempform = getFormFromModel();
    aro = formController.deleteFieldbyId(tempform.getFieldForms().iterator().next().getId());
    formController.saveForm(tempform.getId(), mockPrincipal);
    currVersionId = formController.updateForm(tempform.getId(), mockPrincipal).getData();

    // now reload doc -s hould have 4 fields OK
    created = recordMgr.get(created.getId()).asStrucDoc();
    assertEquals(4, created.getFieldCount());
    // now create new doc with form that has had fieldform deleted, should be 2 fields
    created = recordMgr.createNewStructuredDocument(rootId, currVersionId, u1);
    assertEquals(3, created.getFieldCount());
  }

  @Test
  public void testFormTag() throws Exception {
    docToShare = setUpLoginAsPIUserAndCreateADocument();
    Boolean ok = formController.tagForm(docToShare.getForm().getId(), "template_tag").getData();
    assertTrue(ok);
    // too short
    assertNotNull(formController.tagForm(docToShare.getForm().getId(), "t").getError());
  }

  @Test
  public void testSaveIconImg() throws Exception {
    docToShare = setUpLoginAsPIUserAndCreateADocument();
    formController.editForm(model, mockPrincipal, docToShare.getForm().getId());
    RSForm form = getFormFromModel();
    AjaxReturnObject<Long> fid = formController.updateForm(form.getId(), mockPrincipal);

    InputStream is = RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("Picture1.png");
    byte[] rawBytes = IOUtils.toByteArray(is);
    // this is too big, should be thumbnailed
    assertEquals(72169, rawBytes.length);
    MockMultipartFile mf =
        new MockMultipartFile("file", "Picture1.png", "multipart/form-data", rawBytes);

    AjaxReturnObject<Long> savedIcon = formController.saveImage(mf, fid.getData(), mockPrincipal);
    assertTrue(savedIcon.getData() != null);
    MockHttpServletResponse response = new MockHttpServletResponse();
    imageCtrller.getIconImage(savedIcon.getData(), response); // if failed throw out
    IconEntity ie = iconImageManager.getIconEntity(savedIcon.getData());
    assertEquals(64, ie.getWidth());
    assertEquals(39, ie.getHeight());
    assertEquals("png", ie.getImgType());

    assertEquals(isJDK8() ? 1377 : 1410, ie.getIconImage().length);
    assertEquals(form.getId(), ie.getParentId());

    RSForm savedform = formMgr.get(form.getId(), piUser); // exception
    // assertEquals(savedIcon.getData(), savedform.getIconId());
  }

  private RSForm getFormFromModel() {
    return (RSForm) modelTss.get("template");
  }

  final String formUrlBase = "/workspace/editor/form/";

  @Test
  public void createFormMvcCreateEditFields() throws Exception {
    User u = createInitAndLoginAnyUser();
    RSForm form = getNewForm(u);
    assertNotNull(form.getId());
    MvcResult dateresult =
        mockMvc
            .perform(
                post(formUrlBase + "ajax/createDateField")
                    .principal(u::getUsername)
                    .param("recordId", form.getId() + "")
                    .param("name", "datefield")
                    .param("dateFormat", "yyyy-MM-dd")
                    .param("minDate", "2017-06-29"))
            .andReturn();
    assertEquals("datefield", getJsonPathValue(dateresult, "$.data.name"));
    String id = getJsonPathValue(dateresult, "$.data.id").toString();

    MvcResult dateresult2 =
        mockMvc
            .perform(
                post(formUrlBase + "ajax/saveEditedDateField")
                    .principal(u::getUsername)
                    .param("fieldId", id)
                    .param("name", "datefield2")
                    .param("dateFormat", "yyyy-MM-dd")
                    .param("minDate", "2017-06-29"))
            .andReturn();
    assertEquals("datefield2", getJsonPathValue(dateresult2, "$.data.name"));

    RSpaceTestUtils.logout();
  }

  private RSForm getNewForm(User u) throws Exception {
    MvcResult result =
        mockMvc
            .perform(post(formUrlBase).principal(u::getUsername))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    RSForm form = (RSForm) result.getModelAndView().getModelMap().get("template");
    return form;
  }
}
