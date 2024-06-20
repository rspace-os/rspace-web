package com.researchspace.service;

import static com.researchspace.testutils.RSpaceTestUtils.logout;
import static com.researchspace.testutils.RSpaceTestUtils.logoutCurrUserAndLoginAs;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.dao.FormCreateMenuDao;
import com.researchspace.dao.FormUsageDao;
import com.researchspace.model.AccessControl;
import com.researchspace.model.User;
import com.researchspace.model.Version;
import com.researchspace.model.dtos.*;
import com.researchspace.model.field.*;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.*;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class FormManagerTest extends SpringTransactionalTest {

  private static final String USER1A = "user1a";
  private static final String USER1APWD = "user1234";

  private @Autowired FormManager formMgr;
  private @Autowired FormUsageDao formUsageDao;
  private @Autowired FormCreateMenuDao userMenuDao;

  User user = null;

  @Before
  public void setUp() throws Exception {
    user = createAndSaveUserIfNotExists(getRandomAlphabeticString("frmMgr"));
    initialiseContentWithEmptyContent(user);
    logoutAndLoginAs(user);
  }

  @After
  public void tearDown() throws Exception {
    logout();
  }

  @Test
  public void addRemoveFormFromCreateMenu() {
    final int initialCount = userMenuDao.getAll().size();
    RSForm form = formMgr.create(user);
    FormUserMenu menu = formMgr.addFormToUserCreateMenu(user, form.getId(), user);
    assertNotNull(menu);
    assertEquals(menu, userMenuDao.get(menu.getId()));
    assertEquals(initialCount + 1, userMenuDao.getAll().size());
    // unknown, can't be deleted
    assertFalse(formMgr.removeFormFromUserCreateMenu(user, 123456L, user));
    // known, can be deleted.
    assertTrue(formMgr.removeFormFromUserCreateMenu(user, form.getId(), user));
    assertEquals(initialCount, userMenuDao.getAll().size());
  }

  @Test
  public void createFormTest() {
    RSForm form = formMgr.create(user);
    assertNotNull(form.getId());
    assertEquals(user.getUsername(), form.getCreatedBy());

    // admin can create template
    User admin = userDao.getUserByUsername("admin");
    formMgr.create(admin);
  }

  @Test
  public void createFormTestRequiresAuthorization() {
    // check
    // regular user can create templates
    logoutCurrUserAndLoginAs(USER1A, USER1APWD);
    User user = userDao.getUserByUsername(USER1A);
    formMgr.create(user);
  }

  @Test(expected = AuthorizationException.class)
  public void publishFormTestRequiresAuthorization() {
    // check permissionsAreNeeded
    // need
    RSForm form = formMgr.create(user);
    // this user does not have rights to share the template.
    logoutCurrUserAndLoginAs(USER1A, USER1APWD);
    User u = userDao.getUserByUsername(USER1A);
    formMgr.publish(form.getId(), true, null, u);
  }

  @Test
  public void saveFormTest() throws Exception {
    RSForm form = formMgr.create(user);
    form.setDescription("new desc");
    Thread.sleep(2);
    formMgr.save(form, user);
    RSForm t1 = formMgr.get(form.getId(), user);
    assertEquals("new desc", t1.getDescription());
    assertTrue(t1.getModificationDateAsDate().after(form.getCreationDateAsDate()));
  }

  @Test
  public void createDateFieldFormTest() {
    RSForm form = formMgr.create(user);
    DateFieldDTO<DateFieldForm> dto = createAnyDTO("date");
    DateFieldForm dft = formMgr.createFieldForm(dto, form.getId(), user);
    assertNotNull(dft);
    assertEquals(form, dft.getForm());
    assertNotNull(dft.getId());
  }

  private DateFieldDTO<DateFieldForm> createAnyDTO(String name) {
    return new DateFieldDTO<DateFieldForm>("", "", "", "yy-mm-dd", name);
  }

  @Test
  public void updateDateFieldFormTest() throws Exception {
    RSForm form = formMgr.create(user);
    DateFieldDTO<DateFieldForm> dto = createAnyDTO("date");
    DateFieldDTO<DateFieldForm> dto2 = createAnyDTO("date2");
    DateFieldForm dft = formMgr.createFieldForm(dto, form.getId(), user);
    formMgr.updateFieldForm(dto2, dft.getId(), user);

    RSForm form2 = formMgr.get(form.getId(), user);
    assertEquals("date2", form2.getFieldForms().iterator().next().getName());
  }

  @Test(expected = AuthorizationException.class)
  public void updateFieldFormTestIsAuthorised() throws Exception {
    RSForm form = formMgr.create(user);
    DateFieldDTO<DateFieldForm> dto = createAnyDTO("date");
    DateFieldDTO<DateFieldForm> dto2 = createAnyDTO("date2");
    DateFieldForm dft = formMgr.createFieldForm(dto, form.getId(), user);
    logoutCurrUserAndLoginAs(USER1A, USER1APWD);
    User imposter = userDao.getUserByUsername(USER1A);
    formMgr.updateFieldForm(dto2, dft.getId(), imposter);
  }

  @Test
  public void createStringFieldFormTest() throws Exception {
    RSForm form = formMgr.create(user);
    StringFieldDTO<StringFieldForm> dto = createAnyStringDTO();
    StringFieldForm sft = formMgr.createFieldForm(dto, form.getId(), user);
    assertNotNull(sft.getId());
    assertNotNull(sft.getForm());
    assertEquals(FieldType.STRING, sft.getType());
    assertTrue(sft.isIfPassword());

    RSForm form2 = formMgr.get(form.getId(), null);
    assertEquals(1, form2.getNumActiveFields());
  }

  @Test
  public void updateStringFieldFormTest() throws Exception {
    RSForm form = formMgr.create(user);
    StringFieldDTO<StringFieldForm> dto = createAnyStringDTO();
    StringFieldDTO<StringFieldForm> dto2 = createAnyStringDTO();
    dto2.setName("newname");
    StringFieldForm sft = formMgr.createFieldForm(dto, form.getId(), user);
    formMgr.updateFieldForm(dto2, sft.getId(), user);

    RSForm form2 = formMgr.get(form.getId(), user);
    assertEquals("newname", form2.getFieldForms().iterator().next().getName());
  }

  @Test
  public void createNumberFieldFormTest() throws Exception {
    RSForm form = formMgr.create(user);
    NumberFieldDTO<NumberFieldForm> dto = createAnyNumberFieldDTO();
    NumberFieldForm sft = formMgr.createFieldForm(dto, form.getId(), user);
    assertNotNull(sft.getId());
    assertNotNull(sft.getForm());
    assertEquals(FieldType.NUMBER, sft.getType());
    RSForm form2 = formMgr.get(form.getId(), null);
    assertEquals(1, form2.getNumActiveFields());
  }

  @Test(expected = AuthorizationException.class)
  public void createFieldFormIsAuthorised() {
    RSForm form = formMgr.create(user);
    NumberFieldDTO<NumberFieldForm> dto = createAnyNumberFieldDTO();
    logoutCurrUserAndLoginAs(USER1A, USER1APWD);
    User imposter = userDao.getUserByUsername(USER1A);
    formMgr.createFieldForm(dto, form.getId(), imposter);
  }

  private NumberFieldDTO<NumberFieldForm> createAnyNumberFieldDTO() {
    NumberFieldDTO<NumberFieldForm> nfdto =
        new NumberFieldDTO<NumberFieldForm>(0 + "", 5 + "", "", "3", FieldType.NUMBER, "number");
    return nfdto;
  }

  @Test
  public void updateNumberFieldFormTest() throws Exception {
    RSForm form = formMgr.create(user);
    NumberFieldDTO<NumberFieldForm> dto = createAnyNumberFieldDTO();
    NumberFieldDTO<NumberFieldForm> dto2 = createAnyNumberFieldDTO();

    dto2.setName("newname");

    NumberFieldForm nft = formMgr.createFieldForm(dto, form.getId(), user);
    dto2.setId(nft.getId().toString());
    formMgr.updateFieldForm(dto2, Long.parseLong(dto2.getId()), user);

    RSForm t2 = formMgr.get(form.getId(), user);
    assertEquals("newname", t2.getFieldForms().iterator().next().getName());
  }

  @Test
  public void createTextFieldFormTest() throws Exception {
    RSForm form = formMgr.create(user);
    TextFieldDTO<TextFieldForm> dto = new TextFieldDTO<TextFieldForm>("Name", "Default");
    TextFieldForm sft = formMgr.createFieldForm(dto, form.getId(), user);
    assertNotNull(sft.getId());
    assertNotNull(sft.getForm());
    assertEquals(FieldType.TEXT, sft.getType());
    RSForm form2 = formMgr.get(form.getId(), user);
    assertEquals(1, form2.getNumActiveFields());
  }

  @Test
  public void updateTextFieldFormTest() throws Exception {
    RSForm form = formMgr.create(user);
    TextFieldDTO<TextFieldForm> dto = new TextFieldDTO<TextFieldForm>("Name", "Default");
    TextFieldForm sft = formMgr.createFieldForm(dto, form.getId(), user);

    TextFieldDTO<TextFieldForm> dto2 = new TextFieldDTO<TextFieldForm>("newname", "");
    TextFieldForm altered = formMgr.updateFieldForm(dto2, sft.getId(), user);

    RSForm form2 = formMgr.get(form.getId(), user);
    assertEquals("newname", form2.getFieldForms().iterator().next().getName());
    assertEquals("newname", altered.getName());
  }

  @Test
  public void createTimeFieldFormTest() throws Exception {
    RSForm form = formMgr.create(user);
    TimeFieldDTO<TimeFieldForm> valid = TimeFieldDTOValidatorTest.createValid();
    TimeFieldForm sft = formMgr.createFieldForm(valid, form.getId(), user);
    assertNotNull(sft.getId());
    assertNotNull(sft.getForm());
    assertEquals(FieldType.TIME, sft.getType());
    // java9 and onwards uses different Locale mechanism  - it's 'PM' in java8, 'pm' on Java 11
    // https://docs.oracle.com/javase/9/migrate/toc.htm#JSMIG-GUID-A20F2989-BFA9-482D-8618-6CBB4BAAE310
    assertEquals(valid.getDefaultValue().toLowerCase(), sft.getDefaultTimeAsString().toLowerCase());
    RSForm form2 = formMgr.get(form.getId(), user);
    assertEquals(1, form2.getNumActiveFields());
  }

  @Test
  public void updateTimeFieldFormTest() throws Exception {
    RSForm form = formMgr.create(user);
    TimeFieldDTO<TimeFieldForm> valid = TimeFieldDTOValidatorTest.createValid();
    TimeFieldDTO<TimeFieldForm> valid2 = TimeFieldDTOValidatorTest.createValid();
    valid2.setMaxValue("11:59 PM");
    TimeFieldForm sft = formMgr.createFieldForm(valid, form.getId(), user);

    TimeFieldForm altered = formMgr.updateFieldForm(valid2, sft.getId(), user);
    // java9 and onwards uses different Locale mechanism  - it's 'PM' in java8, 'pm' on Java 11
    // https://docs.oracle.com/javase/9/migrate/toc.htm#JSMIG-GUID-A20F2989-BFA9-482D-8618-6CBB4BAAE310
    assertEquals("11:59 PM", altered.getmaxTimeAsString().toUpperCase());
  }

  @Test
  public void createChoiceFieldFormTest() throws Exception {
    RSForm form = formMgr.create(user);
    ChoiceFieldDTO<ChoiceFieldForm> valid = ChoiceFieldDTOValidatorTest.createValid();
    formMgr.publish(form.getId(), true, null, user);
    ChoiceFieldForm sft = formMgr.createFieldForm(valid, form.getId(), user);
    assertNotNull(sft.getId());
    assertNotNull(sft.getForm());
    assertEquals(FieldType.CHOICE, sft.getType());
    assertEquals(valid.getChoiceValues(), sft.getChoiceOptions());
    RSForm t2 = formMgr.get(form.getId(), user);
    assertEquals(1, t2.getNumActiveFields());
  }

  @Test
  public void updateRadioFieldFormTest() throws Exception {
    RSForm form = formMgr.create(user);
    formMgr.publish(form.getId(), true, null, user);
    RadioFieldDTO<RadioFieldForm> valid = RadioFieldDTOValidatorTest.createValid();
    RadioFieldDTO<RadioFieldForm> valid2 = RadioFieldDTOValidatorTest.createValid();
    valid2.setRadioValues("x=z");
    RadioFieldForm sft = formMgr.createFieldForm(valid, form.getId(), user);
    formMgr.get(form.getId(), user).getVersion().getVersion();
    RadioFieldForm altered = formMgr.updateFieldForm(valid2, sft.getId(), user);
    assertEquals("x=z", altered.getRadioOption());
    formMgr.get(form.getId(), user);
  }

  @Test
  public void testCreateDocumentFromRadioField() throws Exception {
    // setup, creates an empty form
    RSForm form = formMgr.create(user);
    formMgr.publish(form.getId(), true, null, user);

    RadioFieldDTO<RadioFieldForm> rdto =
        new RadioFieldDTO<RadioFieldForm>("a=b&c=d", "b", "some name", false, false);
    formMgr.createFieldForm(rdto, form.getId(), user);

    // now create a document from the form:
    StructuredDocument sdoc =
        recordMgr.createNewStructuredDocument(user.getRootFolder().getId(), form.getId(), user);
    assertNotNull(sdoc);
    assertTrue(FieldType.RADIO.equals(sdoc.getFields().get(0).getType()));
  }

  @Test
  public void testCreateDocumentFromChoiceField() throws Exception {
    // setup, creates an empty form
    RSForm form = formMgr.create(user);
    formMgr.publish(form.getId(), true, null, user);

    ChoiceFieldDTO<ChoiceFieldForm> rdto = ChoiceFieldDTOValidatorTest.createValid();
    formMgr.createFieldForm(rdto, form.getId(), user);
    // now create a document from the form:
    StructuredDocument sdoc =
        recordMgr.createNewStructuredDocument(user.getRootFolder().getId(), form.getId(), user);
    assertNotNull(sdoc);
    assertTrue(FieldType.CHOICE.equals(sdoc.getFields().get(0).getType()));
  }

  @Test
  public void createRadioFieldFormTest() throws Exception {
    RSForm form = formMgr.create(user);
    RadioFieldDTO<RadioFieldForm> valid = RadioFieldDTOValidatorTest.createValid();
    RadioFieldForm sft = formMgr.createFieldForm(valid, form.getId(), user);
    assertNotNull(sft.getId());
    assertNotNull(sft.getForm());
    assertEquals(FieldType.RADIO, sft.getType());
    assertEquals(valid.getRadioValues(), sft.getRadioOption());
    RSForm t2 = formMgr.get(form.getId(), user);
    assertEquals(1, t2.getNumActiveFields());
  }

  @Test
  public void testMenuCreation() throws Exception {
    List<RSForm> items = formMgr.getDynamicMenuFormItems(user);
    RSForm[] forms = setUpDBWith5Forms(user);
    // no templates or documents to begin with, except Basic Document
    assertEquals(1, items.size());

    // create a single document, its form will now be in the list.
    formMgr.addFormToUserCreateMenu(
        user, formDao.getMostRecentVersionForForm(forms[1].getStableID()).getId(), user);

    StructuredDocument sd1 = TestFactory.createAnySD(forms[1]);
    sd1.setOwner(user);
    recordMgr.save(sd1, user);
    formUsageDao.save(new FormUsage(user, forms[1]));
    user = userDao.get(user.getId());

    items = formMgr.getDynamicMenuFormItems(user);
    assertEquals(1, items.size());
    assertEquals(forms[1], items.get(0));

    // create 1 doc of each template
    for (RSForm t : forms) {
      createNewDocumentAndSave(user, t);
      formMgr.addFormToUserCreateMenu(
          user, formDao.getMostRecentVersionForForm(t.getStableID()).getId(), user);
      Thread.sleep(5);
    }

    items = formMgr.getDynamicMenuFormItems(user);
    System.err.println("menu items, in order");
    for (RSForm t : items) {
      System.err.println("[" + t.getName() + "," + t.getId() + "]");
    }

    assertEquals(4, items.size());
    assertEquals(2, items.indexOf(forms[3]));

    // this has now been used twice and will be first
    assertEquals(0, items.indexOf(forms[1]));

    // now templates 2 will most common
    for (int i = 0; i < 5; i++) {
      createNewDocumentAndSave(user, forms[2]);
      Thread.sleep(1);
    }
    createNewDocumentAndSave(user, forms[0]);

    items = formMgr.getDynamicMenuFormItems(user);
    // never > 4 items
    assertEquals(4, items.size());

    // t2 is at top of list
    assertEquals(0, items.indexOf(forms[2]));

    // now create 4 documents each of t0-t3, then finally 1 of t4. t4 should appear
    // last
    // on the list as it is the last item
    for (int i = 0; i < 5; i++) {
      for (int j = 0; j < 4; j++) {
        createNewDocumentAndSave(user, forms[j]);

        Thread.sleep(1);
      }
    }
    createNewDocumentAndSave(user, forms[4]);

    items = formMgr.getDynamicMenuFormItems(user);
    assertEquals(4, items.size());

    // final ordering
    assertEquals(3, items.indexOf(forms[4]));
  }

  @Test
  public void testMenuCreationGetsLatestVersion() throws Exception {
    User any = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(any);
    Folder rootFolder = folderDao.getRootRecordForUser(any);
    logoutAndLoginAs(any);
    // maybe there already forms..get baseline count
    final int INITIAL_DISTINCT_FORM_COUNT = formDao.getAllDistinct().size();
    final int INITIAL_CURRENT_FORM_COUNT = formDao.getAllCurrentNormalForms().size();
    // but we'll add 5 more
    final int NUM_ACTIVE_FORMS = 5;
    RSForm[] forms = setUpDBWith5Forms(any);
    // create 5 documents with
    for (int i = 0; i < NUM_ACTIVE_FORMS; i++) {
      Thread.sleep(1);
      recordMgr.createNewStructuredDocument(rootFolder.getId(), forms[0].getId(), any);
    }
    Thread.sleep(1);
    // now edit and update new version
    RSForm toEdit = formMgr.getForEditing(forms[0].getId(), any, anySessionTracker());
    Thread.sleep(1);
    formMgr.updateVersion(toEdit.getId(), any);
    formMgr.addFormToUserCreateMenu(
        any, formDao.getMostRecentVersionForForm(forms[0].getStableID()).getId(), user);
    // now check that the menu has only one forms, form0;
    List<RSForm> inmenu = formMgr.getDynamicMenuFormItems(any);
    assertEquals(1, inmenu.size());
    assertEquals(new Version(1L), inmenu.get(0).getVersion());
    assertEquals(forms[0].getStableID(), inmenu.get(0).getStableID());
    // a new form was created for new version, so there are now 6 forms in total
    assertEquals(
        INITIAL_DISTINCT_FORM_COUNT + NUM_ACTIVE_FORMS + 1, formDao.getAllDistinct().size());
    // but only 5 active, the previus version is now 'old'
    assertEquals(
        INITIAL_CURRENT_FORM_COUNT + NUM_ACTIVE_FORMS, formDao.getAllCurrentNormalForms().size());
  }

  private void createNewDocumentAndSave(User u, RSForm t) throws Exception {
    StructuredDocument sd = TestFactory.createAnySD(t);
    sd.setOwner(u);
    recordMgr.save(sd, u);
    formUsageDao.save(new FormUsage(u, t));
  }

  RSForm[] setUpDBWith5Forms(User u) throws InterruptedException {

    RSForm[] templates = new RSForm[5];
    int indx = 0;
    for (String name : new String[] {"t0", "t1", "t2", "t3", "t4"}) {
      RSForm t = TestFactory.createAnyForm(name);
      t.setOwner(u);
      t.addFieldForm(TestFactory.createDateFieldForm());
      t.setPublishingState(FormState.PUBLISHED);
      Thread.sleep(1);
      t.getAccessControl().setWorldPermissionType(PermissionType.READ);
      formMgr.save(t, u);
      templates[indx++] = t;
    }
    return templates;
  }

  @Test
  public void updateChoiceFieldFormTest() throws Exception {

    RSForm t = formMgr.create(user);
    ChoiceFieldDTO<ChoiceFieldForm> valid = ChoiceFieldDTOValidatorTest.createValid();
    ChoiceFieldDTO<ChoiceFieldForm> valid2 = ChoiceFieldDTOValidatorTest.createValid();
    valid2.setChoiceValues("x=z");
    ChoiceFieldForm sft = formMgr.createFieldForm(valid, t.getId(), user);

    ChoiceFieldForm altered = formMgr.updateFieldForm(valid2, sft.getId(), user);
    assertEquals("x=z", altered.getChoiceOptions());
  }

  // doesnt matter exactly what is te template type,
  // should be the case for all field template types
  @Test
  public void testModificationTimeUpdated() throws Exception {

    RSForm form = formMgr.create(user);
    ChoiceFieldDTO<ChoiceFieldForm> valid = ChoiceFieldDTOValidatorTest.createValid();
    ChoiceFieldDTO<ChoiceFieldForm> valid2 = ChoiceFieldDTOValidatorTest.createValid();
    valid2.setChoiceValues("x=z");
    ChoiceFieldForm sft = formMgr.createFieldForm(valid, form.getId(), user);
    Thread.sleep(10); // slow down the db operation!
    Date currTime = new Date();
    assertTrue(currTime.after(new Date(sft.getModificationDate())));

    Thread.sleep(10); // slow down the db operation!
    ChoiceFieldForm altered = formMgr.updateFieldForm(valid2, sft.getId(), user);

    assertTrue(currTime.before(new Date(altered.getModificationDate())));
  }

  @Test()
  public void testDeleteForm() throws Exception {
    RSForm form = formMgr.create(user);
    assertTrue(form.isNewState());
    int b4 = formMgr.getAllCurrentNormalForms().size();
    ConstraintBasedPermission cbp =
        new ConstraintBasedPermission(PermissionDomain.FORM, PermissionType.DELETE);
    user.addPermission(cbp);
    // regular delete works, so long as has permission
    formMgr.delete(form.getId(), user);
    assertEquals(b4 - 1, formMgr.getAllCurrentNormalForms().size());

    // save again in published form
    form.setPublishingState(FormState.PUBLISHED);
    formMgr.save(form, user);
    // published can now be deleted
    formMgr.delete(form.getId(), user);
    assertEquals(b4 - 1, formMgr.getAllCurrentNormalForms().size());

    // but not if used to create a document:
    RSForm form2 = formMgr.save(form, user);
    StructuredDocument doc =
        recordMgr.createNewStructuredDocument(user.getRootFolder().getId(), form2.getId(), user);
    assertExceptionThrown(
        () -> formMgr.delete(form2.getId(), user), IllegalArgumentException.class);
    assertEquals(b4, formMgr.getAllCurrentNormalForms().size());
  }

  @Test
  public void testDeleteFormRequiresPermission() throws Exception {
    User user = createAndSaveUserWithNoPermissions(getRandomAlphabeticString("formPmns"));
    RSForm form = formMgr.create(user);
    assertTrue(form.isNewState());
    int b4 = formMgr.getAllCurrentNormalForms().size();
    ConstraintBasedPermission cbp =
        new ConstraintBasedPermission(PermissionDomain.FORM, PermissionType.DELETE);
    user.addPermission(cbp);
    userDao.save(user);
    permissionUtils.refreshCache();
    logoutAndLoginAs(user);

    // regular delete works, so long as has permission
    formMgr.delete(form.getId(), user);
    assertEquals(b4 - 1, formMgr.getAllCurrentNormalForms().size());

    // re-save
    formMgr.save(form, user);

    user.removePermission(cbp);
    userDao.save(user);
    permissionUtils.refreshCache();
    // now should gfail
    assertAuthorisationExceptionThrown(() -> formMgr.delete(form.getId(), user));
  }

  @Test
  public void testFieldOrderPersisted() throws Exception {
    RSForm form = formMgr.create(user);
    TimeFieldDTO<TimeFieldForm> valid = TimeFieldDTOValidatorTest.createValid();
    valid.setName("xxx");
    formMgr.createFieldForm(valid, form.getId(), user);
    Thread.sleep(1);

    valid.setName("xxx2");
    formMgr.createFieldForm(valid, form.getId(), user);

    RSForm retrieved = formMgr.get(form.getId(), user);
    assertEquals(2, retrieved.getNumActiveFields());
    // check is still the first.
    assertEquals("xxx", retrieved.getFieldForms().iterator().next().getName());
  }

  @Test
  public void testPublishingState() throws Exception {
    RSForm form = formMgr.create(user);
    AccessControl ORIGINAL_AC = form.getAccessControl();
    assertTrue(form.isNewState());
    formMgr.publish(form.getId(), true, null, user);

    RSForm t1 = formMgr.get(form.getId(), user);
    assertTrue(t1.isPublishedAndVisible());
    assertEquals(ORIGINAL_AC, form.getAccessControl());

    // unpublish
    formMgr.publish(t1.getId(), false, null, user);
    RSForm t2 = formMgr.get(form.getId(), user);
    assertTrue(t2.isPublishedAndHidden());
    assertEquals(ORIGINAL_AC, form.getAccessControl());

    // unpublish again, no change.
    formMgr.publish(t2.getId(), false, null, user);
    RSForm t3 = formMgr.get(form.getId(), user);
    assertTrue(t3.isPublishedAndHidden());
    assertEquals(ORIGINAL_AC, form.getAccessControl());

    // republish again, no change.
    formMgr.publish(t3.getId(), true, null, user);
    RSForm t4 = formMgr.get(form.getId(), user);
    assertTrue(t3.isPublishedAndVisible());
    assertEquals(ORIGINAL_AC, form.getAccessControl());

    // now unpublish, and control accessibility
    formMgr.publish(t3.getId(), false, null, user);
    FormSharingCommand tc = new FormSharingCommand();
    tc.setWorldOptions(Arrays.asList(new String[] {PermissionType.WRITE.toString()}));
    formMgr.publish(t3.getId(), true, tc, user);

    t4 = formMgr.get(form.getId(), user);
    assertFalse(ORIGINAL_AC.equals(t4.getAccessControl()));
    assertTrue(t4.getAccessControl().getWorldPermissionType().equals(PermissionType.WRITE));
  }

  @Test
  public void testRegulsrGetLazyLoadsFieldForms() throws Exception {
    RSForm form = formMgr.create(user);
    TimeFieldDTO<TimeFieldForm> valid = TimeFieldDTOValidatorTest.createValid();
    valid.setName("xxx");
    formMgr.createFieldForm(valid, form.getId(), user);
    flushDatabaseState();
    clearSessionAndEvictAll();

    RSForm t2 = formMgr.get(form.getId(), user);
    t2.getFieldForms().iterator().next().getName();
  }

  private StringFieldDTO<StringFieldForm> createAnyStringDTO() {
    StringFieldDTO<StringFieldForm> dto = new StringFieldDTO<StringFieldForm>();
    dto.setDefaultStringValue("Default");
    dto.setIfPassword("yes");
    dto.setName("name");
    return dto;
  }

  @Test
  public void deleteFieldFromFormTest() throws Exception {
    RSForm form = formMgr.create(user);
    DateFieldDTO<DateFieldForm> dto = createAnyDTO("date");
    formMgr.createFieldForm(dto, form.getId(), user);
    RSForm form2 = formMgr.get(form.getId(), user);

    FieldForm f = form2.getFieldForms().iterator().next();
    assertEquals(1, form2.getNumActiveFields());

    formMgr.deleteFieldFromForm(f.getId(), user);
    // test is deleted from DB and from object model.
    assertEquals(0, form2.getNumActiveFields());

    RSForm form3 = formMgr.get(form.getId(), user);
    assertEquals(0, form3.getNumActiveFields());
  }

  @Test
  public void testCopy() throws Exception {
    RSForm form = formMgr.create(user);
    form.setTags("template_tag");
    FieldForm anyFT = TestFactory.createDateFieldForm();
    form.addFieldForm(anyFT);
    form.publish();
    formMgr.save(form, user);
    RSForm copy = formMgr.copy(form.getId(), user, new TemporaryCopyLinkedToOriginalCopyPolicy());
    assertTrue(copy.getTags().equals(form.getTags()));
    assertTrue(copy.isTemporary());
    assertTrue(copy.getFieldForms().get(0).isTemporary());
    assertTrue(copy.isNewState());
    assertFalse(copy.isCurrent());
    assertEquals(new Version(0L), copy.getVersion());

    RSForm copy2 = formMgr.copy(form.getId(), user, new CopyIndependentFormAndFieldFormPolicy());
    assertTrue(copy2.getTags().equals(form.getTags()));
    assertFalse(copy2.isTemporary());
    assertFalse(copy2.getFieldForms().get(0).isTemporary());
    assertTrue(copy2.isNewState());
    assertTrue(copy2.isCurrent());
    assertEquals(new Version(0L), copy.getVersion());
  }

  @Test
  public void testUpdatePErmissions() throws Exception {
    RSForm form = formMgr.create(user);
    AccessControl ac = form.getAccessControl();
    // new template has default settings
    assertEquals(ac, new AccessControl());
    assertEquals(PermissionType.NONE, form.getAccessControl().getGroupPermissionType());

    FormSharingCommand config = new FormSharingCommand();
    config.setGroupOptions(Arrays.asList(new String[] {"READ"}));

    formMgr.updatePermissions(form.getId(), config, user);
    // assert is now updated.
    RSForm updated = formMgr.get(form.getId(), user);
    assertEquals(PermissionType.READ, updated.getAccessControl().getGroupPermissionType());
  }

  @Test
  public void testAbandonUpdate() throws Exception {
    // create a template with a date field
    RSForm form = formMgr.create(user);
    FieldForm anyFT = TestFactory.createTextFieldForm();
    form.addFieldForm(anyFT);
    form.publish();
    formMgr.save(form, user);
    Version b4 = form.getVersion();
    int ORIGINAL_FIELD_COUNT = form.getNumActiveFields();
    int ORIGINAL_TEMP_COUNT = formDao.getAllCurrentNormalForms().size();

    userDao.save(user);
    // this will be a temp copy, not the original
    RSForm toEdit = formMgr.getForEditing(form.getId(), user, anySessionTracker());
    assertTrue(toEdit.isTemporary()); // sanity check

    // edit text field to change name
    editTextFieldForm(toEdit, "newName");

    // now we'll abandon the update:
    formMgr.abandonUpdateForm(toEdit.getId(), user);

    // now load the original version
    RSForm original = formMgr.get(form.getId(), user);
    assertEquals(b4.getVersion().longValue(), original.getVersion().getVersion().longValue());
    assertEquals(ORIGINAL_FIELD_COUNT, original.getNumActiveFields());
    assertEquals(ORIGINAL_TEMP_COUNT, formMgr.getAllCurrentNormalForms().size());
    assertFalse(original.getFieldForms().get(0).getName().equals("XXX"));

    // check temporary template is deleted
    assertTemporaryFormDeleted(user, toEdit);
  }

  public void assertTemporaryFormDeleted(User u, RSForm toEdit) {
    boolean tempPresent = true;
    try {
      formMgr.get(toEdit.getId(), u);
    } catch (Exception e) {
      tempPresent = false;
    }
    assertFalse(tempPresent);
  }

  @Test
  public void testDocsFromPreviousVersionsLoadCorrectForm() throws Exception {
    RSForm form = formMgr.create(user);
    DateFieldForm anyFT = TestFactory.createDateFieldForm();
    form.addFieldForm(anyFT);
    form.publish();
    formMgr.save(form, user);
    Version b4 = form.getVersion();
    StructuredDocument sd = recordFactory.createStructuredDocument("a", user, form);
    recordMgr.save(sd, user);

    // this will be a temp copy, not the original
    RSForm toEdit = formMgr.getForEditing(form.getId(), user, anySessionTracker());

    // we'll add a new field, as if coming from the controller
    addANewField(toEdit);
    formMgr.updateVersion(toEdit.getId(), user);
    StructuredDocument sd2 = recordFactory.createStructuredDocument("a", user, form);
    recordMgr.save(sd2, user);

    StructuredDocument sdREloaded = (StructuredDocument) recordMgr.get(sd.getId());
    assertEquals(form, sdREloaded.getForm());
    assertEquals(b4, sdREloaded.getForm().getVersion());
  }

  @Test
  public void testUpdate() throws Exception {
    final int ORIG_NORMAL_FORM_COUNT = formMgr.getAllCurrentNormalForms().size();
    final int ORIG_FORM_COUNT = formDao.getAll().size();
    // create a template with a date field
    RSForm newForm = formMgr.create(user);
    FieldForm anyFT = TestFactory.createTextFieldForm();
    newForm.addFieldForm(anyFT);
    newForm.publish();
    formMgr.save(newForm, user);
    Version b4 = newForm.getVersion();

    // this will be a temp copy, not the original
    RSForm toEdit = formMgr.getForEditing(newForm.getId(), user, anySessionTracker());
    assertTrue(toEdit.isTemporary()); // sanity check

    // creates a new temp form with edited field
    final String NEWNAME = "newName";
    editTextFieldForm(toEdit, NEWNAME);

    // now we'll update:
    assertNotNull(formMgr.updateVersion(toEdit.getId(), user));

    // now load the new version
    RSForm newlyVersioned = formMgr.get(toEdit.getId(), user);
    assertFalse(newlyVersioned.isTemporary());
    assertEquals(b4.getVersion() + 1, newlyVersioned.getVersion().getVersion().longValue());
    assertEquals(2, newlyVersioned.getNumActiveFields());
    assertEquals(
        ORIG_NORMAL_FORM_COUNT + 1,
        formMgr.getAllCurrentNormalForms().size()); // still 1 current version
    assertEquals(ORIG_FORM_COUNT + 2, formDao.getAll().size()); // but 2 versions in DB

    RSForm oldVersion = formMgr.get(newForm.getId(), user);
    assertEquals(oldVersion, newlyVersioned.getPreviousVersion());
    assertTrue(oldVersion.getVersion().before(newlyVersioned.getVersion()));
    assertNull(oldVersion.getPreviousVersion());
    assertEquals(NEWNAME, newlyVersioned.getFieldForms().get(0).getName());
  }

  private void editTextFieldForm(RSForm toEdit, String newFieldName) {
    // we'll add a new field, as if coming from the controller
    addANewField(toEdit);

    TextFieldDTO<TextFieldForm> dftdto = new TextFieldDTO<TextFieldForm>(newFieldName, "");
    formMgr.updateFieldForm(dftdto, toEdit.getFieldForms().get(0).getId(), user);
  }

  public void addANewField(RSForm toEdit) {
    NumberFieldDTO<NumberFieldForm> nftDTO = new NumberFieldDTO<NumberFieldForm>();
    nftDTO.setMinNumberValue("1");
    nftDTO.setMaxNumberValue("10");
    nftDTO.setName("name");
    formMgr.createFieldForm(nftDTO, toEdit.getId(), user);
  }

  @Test
  public void systemFormCannotHavePublishingStateChanged() throws Exception {
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(user, "any");
    final RSForm systemForm = sd.getForm();
    assertTrue(systemForm.isSystemForm());
    // this will be a temp copy, not the original
    logoutAndLoginAsSysAdmin();
    RSForm toEdit = formMgr.getForEditing(systemForm.getId(), user, anySessionTracker());
    assertTrue(toEdit.isTemporary()); // sanity check

    // creates a new temp form with updated field
    addANewField(toEdit);
    // no update
    assertNull(formMgr.updateVersion(toEdit.getId(), user));
    // will throw exception if altered

    assertExceptionThrown(
        () -> formMgr.publish(systemForm.getId(), false, null, user),
        UnsupportedOperationException.class);
    // sanity check
    assertTrue(formMgr.get(systemForm.getId(), user).isPublishedAndVisible());
  }

  // RSPAC-1421
  @Test
  public void formDynamicMenuShowsBasicDocumentForNewUser() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    FormMenu menu = formMgr.generateFormMenu(anyUser);
    assertEquals(1, menu.getMenuToAdd().size());
    assertThat(menu.getMenuToAdd().get(0), is(formDao.getBasicDocumentForm()));
  }

  @Test
  public void reorderAndUpdate() throws Exception {
    // setup, creates an empty form
    RSForm form = formMgr.create(user);

    form = formMgr.publish(form.getId(), true, null, user);

    form = formMgr.getForEditing(form.getId(), user, anySessionTracker());
    assertTrue(form.isTemporary()); // sanity check
    StringFieldDTO<StringFieldForm> dto = createAnyStringDTO();
    dto.setName("n1");
    StringFieldForm sft1 = formMgr.createFieldForm(dto, form.getId(), user);
    dto.setName("n2");
    Thread.sleep(1);
    StringFieldForm sft2 = formMgr.createFieldForm(dto, form.getId(), user);
    dto.setName("n3");
    Thread.sleep(1);
    StringFieldForm sft3 = formMgr.createFieldForm(dto, form.getId(), user);
    // commit changes
    form = formMgr.updateVersion(form.getId(), user);
    // start editig again
    Long originalId = form.getId();
    form = formMgr.getForEditing(originalId, user, anySessionTracker());
    assertFieldsInOriginalOrder(sft1, sft2, sft3, form);
    FieldForm sft1a = form.getFieldForms().get(0);
    FieldForm sft2a = form.getFieldForms().get(1);
    FieldForm sft3a = form.getFieldForms().get(2);

    // now reorder
    formMgr.reorderFields(
        form.getId(), TransformerUtils.toList(sft2a.getId(), sft3a.getId(), sft1a.getId()), user);

    // now abandon update
    formMgr.abandonUpdateForm(form.getId(), user);
    flushDatabaseState();
    // field order should be original order
    RSForm original = formMgr.get(originalId, user);
    assertFieldsInOriginalOrder(sft1, sft2, sft3, original);
    // now start editing again, reorder, and update (commit) the changes
    form = formMgr.getForEditing(originalId, user, anySessionTracker());
    // sinc this gets a temp copy, the fields are copied too, so we need new ids.
    FieldForm sft1b = form.getFieldForms().get(0);
    FieldForm sft2b = form.getFieldForms().get(1);
    FieldForm sft3b = form.getFieldForms().get(2);
    // reorder 2-3-1
    formMgr.reorderFields(
        form.getId(), TransformerUtils.toList(sft2b.getId(), sft3b.getId(), sft1b.getId()), user);
    // now we'll update:
    assertNotNull(formMgr.updateVersion(form.getId(), user));

    // now load the new version
    RSForm newlyVersioned = formMgr.get(form.getId(), user);
    assertEquals(3, newlyVersioned.getFieldForms().size());
    assertEquals(sft2.getName(), newlyVersioned.getFieldForms().get(0).getName());
    assertEquals(sft3.getName(), newlyVersioned.getFieldForms().get(1).getName());
    assertEquals(sft1.getName(), newlyVersioned.getFieldForms().get(2).getName());
  }

  private void assertFieldsInOriginalOrder(
      StringFieldForm sft1, StringFieldForm sft2, StringFieldForm sft3, RSForm updated) {
    assertEquals(sft1.getName(), updated.getFieldForms().get(0).getName());
    assertEquals(sft2.getName(), updated.getFieldForms().get(1).getName());
    assertEquals(sft3.getName(), updated.getFieldForms().get(2).getName());
  }
}
