package com.researchspace.service;

import static com.researchspace.model.dtos.AbstractFormFieldDTO.MAX_NAME_LENGTH;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FormManagerIT extends RealTransactionSpringTestBase {

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void checkFieldFormValidationRunsBeforePersistingField() throws Exception {

    User user = createInitAndLoginAnyUser();
    RSForm form = formMgr.create(user);
    formMgr.publish(form.getId(), true, null, user);
    Long formId = form.getId();

    ChoiceFieldDTO<ChoiceFieldForm> invalidChoice = ChoiceFieldDTOValidatorTest.createValid();
    invalidChoice.setChoiceValues(null);
    assertTrue(
        assertThrows(
                ConstraintViolationException.class,
                () -> formMgr.createFieldForm(invalidChoice, formId, user))
            .getMessage()
            .contains("choice options is a required field"),
        assertThrows(
                ConstraintViolationException.class,
                () -> formMgr.createFieldForm(invalidChoice, formId, user))
            .getMessage());

    DateFieldDTO<DateFieldForm> invalidDate = DateFieldDTOValidatorTest.createValid();
    invalidDate.setDateFormat("");
    assertTrue(
        assertThrows(
                ConstraintViolationException.class,
                () -> formMgr.createFieldForm(invalidDate, formId, user))
            .getMessage()
            .contains("format is a required field"),
        assertThrows(
                ConstraintViolationException.class,
                () -> formMgr.createFieldForm(invalidDate, formId, user))
            .getMessage());

    RadioFieldDTO<RadioFieldForm> invalidRadio = RadioFieldDTOValidatorTest.createValid();
    invalidRadio.setRadioValues("   ");
    assertTrue(
        assertThrows(
                ConstraintViolationException.class,
                () -> formMgr.createFieldForm(invalidRadio, formId, user))
            .getMessage()
            .contains("radio options is a required field"),
        assertThrows(
                ConstraintViolationException.class,
                () -> formMgr.createFieldForm(invalidRadio, formId, user))
            .getMessage());

    TextFieldDTO<TextFieldForm> invalidText = TextFieldDTOValidatorTest.createValid();
    invalidText.setName(randomAlphabetic(MAX_NAME_LENGTH + 1));
    assertTrue(
        assertThrows(
                ConstraintViolationException.class,
                () -> formMgr.createFieldForm(invalidText, formId, user))
            .getMessage()
            .contains("size must be between"),
        assertThrows(
                ConstraintViolationException.class,
                () -> formMgr.createFieldForm(invalidText, formId, user))
            .getMessage());
  }
}
