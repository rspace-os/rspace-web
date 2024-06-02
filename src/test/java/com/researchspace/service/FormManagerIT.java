package com.researchspace.service;

import static com.researchspace.model.dtos.AbstractFormFieldDTO.MAX_NAME_LENGTH;
import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;
import static org.hamcrest.Matchers.containsString;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.User;
import com.researchspace.model.dtos.ChoiceFieldDTO;
import com.researchspace.model.dtos.ChoiceFieldDTOValidatorTest;
import com.researchspace.model.dtos.DateFieldDTO;
import com.researchspace.model.dtos.DateFieldDTOValidatorTest;
import com.researchspace.model.dtos.RadioFieldDTO;
import com.researchspace.model.dtos.RadioFieldDTOValidatorTest;
import com.researchspace.model.dtos.TextFieldDTO;
import com.researchspace.model.dtos.TextFieldDTOValidatorTest;
import com.researchspace.model.field.ChoiceFieldForm;
import com.researchspace.model.field.DateFieldForm;
import com.researchspace.model.field.RadioFieldForm;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.record.RSForm;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import javax.validation.ConstraintViolationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class FormManagerIT extends RealTransactionSpringTestBase {

  private @Autowired FormManager formMgr;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void checkFieldFormValidationRunsBeforePersistingField() throws Exception {

    User user = createInitAndLoginAnyUser();
    RSForm form = formMgr.create(user);
    formMgr.publish(form.getId(), true, null, user);

    ChoiceFieldDTO<ChoiceFieldForm> invalidChoice = ChoiceFieldDTOValidatorTest.createValid();
    invalidChoice.setChoiceValues(null);
    CoreTestUtils.assertExceptionThrown(
        () -> formMgr.createFieldForm(invalidChoice, form.getId(), user),
        ConstraintViolationException.class,
        containsString("choice options is a required field"));

    DateFieldDTO<DateFieldForm> invalidDate = DateFieldDTOValidatorTest.createValid();
    invalidDate.setDateFormat("");
    CoreTestUtils.assertExceptionThrown(
        () -> formMgr.createFieldForm(invalidDate, form.getId(), user),
        ConstraintViolationException.class,
        containsString("format is a required field"));

    RadioFieldDTO<RadioFieldForm> invalidRadio = RadioFieldDTOValidatorTest.createValid();
    invalidRadio.setRadioValues("   ");
    CoreTestUtils.assertExceptionThrown(
        () -> formMgr.createFieldForm(invalidRadio, form.getId(), user),
        ConstraintViolationException.class,
        containsString("radio options is a required field"));

    TextFieldDTO<TextFieldForm> invalidText = TextFieldDTOValidatorTest.createValid();
    invalidText.setName(randomAlphabetic(MAX_NAME_LENGTH + 1));
    CoreTestUtils.assertExceptionThrown(
        () -> formMgr.createFieldForm(invalidText, form.getId(), user),
        ConstraintViolationException.class,
        containsString("size must be between"));
  }
}
