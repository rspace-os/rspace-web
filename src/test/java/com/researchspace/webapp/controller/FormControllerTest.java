package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.dtos.NumberFieldDTO;
import com.researchspace.model.dtos.TextFieldDTO;
import com.researchspace.model.field.NumberFieldForm;
import com.researchspace.model.field.StringFieldForm;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.record.RSForm;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockServletContext;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

public class FormControllerTest extends SpringTransactionalTest {
  @Rule public MockitoRule rule = MockitoJUnit.rule();
  @Autowired RSFormController rsFormController;
  private final Model model = new ExtendedModelMap();
  static final Long ID = 1L;

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testSaveEditedTextField() {
    User u = createAndSaveUserIfNotExists("any");
    logoutAndLoginAs(u);
    RSForm form = createAnyForm(u);
    AjaxReturnObject<TextFieldForm> toInsert =
        rsFormController.createTextField(new TextFieldDTO<>("def", "name"), form.getId());

    AjaxReturnObject<TextFieldForm> rc =
        rsFormController.saveEditedTextField(
            new TextFieldDTO<>("name2", "defaultValue"), form.getFieldForms().get(0).getId());
    assertNotNull(rc.getData());
    assertNull(rc.getErrorMsg());

    // no name. should be  rejected
    AjaxReturnObject<TextFieldForm> rc2 =
        rsFormController.saveEditedTextField(
            new TextFieldDTO<>("", "defaultValue2"), form.getFieldForms().get(0).getId());
    assertNull(rc2.getData());
    assertNotNull(rc2.getErrorMsg());
    assertTrue(rc2.getErrorMsg().getErrorMessages().size() > 0);
  }

  @Test
  public void testSaveEditedNumber() {
    User u = createAndSaveUserIfNotExists("any");
    logoutAndLoginAs(u);
    RSForm form = createAnyForm(u);
    NumberFieldDTO nfdto = new NumberFieldDTO();
    nfdto.setName("any");
    nfdto.setMaxNumberValue("12");
    nfdto.setMinNumberValue("0");
    nfdto.setParentId(form.getId() + "");

    AjaxReturnObject<NumberFieldForm> rc = rsFormController.createNumberField(nfdto);
    nfdto.setParentId(rc.getData().getId() + "");
    rc = rsFormController.saveEditedNumber(nfdto);
    assertNotNull(rc.getData());
    assertNull(rc.getErrorMsg());
  }

  @Test
  public void testCreateNumberFieldWithMissingFields() {
    NumberFieldDTO nfdto = new NumberFieldDTO();
    AjaxReturnObject<NumberFieldForm> rc = rsFormController.createNumberField(nfdto);
    assertNull(rc.getData());
    assertTrue(rc.getErrorMsg().hasErrorMessages());
    assertEquals(1, rc.getErrorMsg().getErrorMessages().size());
  }

  @Test
  public void testCreateNumberFieldWithMaxLessThanMin() {
    String ANY_ID = "1";
    NumberFieldDTO nfdto = new NumberFieldDTO();
    nfdto.setName("any");
    nfdto.setMaxNumberValue("8");
    nfdto.setMinNumberValue("12");

    nfdto.setParentId(ANY_ID);
    AjaxReturnObject<NumberFieldForm> rc = rsFormController.createNumberField(nfdto);
    assertNull(rc.getData());
    assertTrue(rc.getErrorMsg().hasErrorMessages());
    assertEquals(1, rc.getErrorMsg().getErrorMessages().size());
  }

  @Test
  public void testSaveEditedStringField() {
    User u = createAndSaveUserIfNotExists("any");
    logoutAndLoginAs(u);
    RSForm form = createAnyForm(u);
    AjaxReturnObject<StringFieldForm> rc =
        rsFormController.createStringField("", "false", "any", form.getId(), false);
    rc = rsFormController.saveEditedStringField("def", "yes", "name", rc.getData().getId(), false);
    assertNotNull(rc.getData());
    assertNull(rc.getErrorMsg());

    // no name. should be rejected
    AjaxReturnObject<StringFieldForm> rc2 =
        rsFormController.saveEditedStringField("def", "yes", "", rc.getData().getId(), false);
    assertNull(rc2.getData());
    assertNotNull(rc2.getErrorMsg());
  }

  // rspac-2427 fix
  @Test
  public void cannotViewUnauthorisedFormViaEdit() throws Exception {
    try {
      User u1 = createInitAndLoginAnyUser();
      RSForm formU1 = createAnyPrivateForm(u1);
      RSpaceTestUtils.logout();
      User unauthorizedUser = createInitAndLoginAnyUser();
      rsFormController.setServletContext(new MockServletContext());
      assertExceptionThrown(
          () -> rsFormController.editForm(model, unauthorizedUser::getUsername, formU1.getId()),
          RecordAccessDeniedException.class);

    } finally {
      rsFormController.setServletContext(null);
    }
  }
}
