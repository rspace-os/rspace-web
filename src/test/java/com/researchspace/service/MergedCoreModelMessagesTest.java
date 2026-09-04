package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.model.GroupType;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.LocalizedIllegalArgumentException;
import com.researchspace.model.preference.Preference;
import org.junit.jupiter.api.Test;

class MergedCoreModelMessagesTest {

  private final MessageSourceUtils messages = new MessageSourceUtils(new JsonMessageSource());

  @Test
  void allDynamicModelMessageKeysResolve() {
    for (GroupType type : GroupType.values()) {
      assertDoesNotThrow(() -> messages.getMessage(type.getLabelKey()));
    }
    for (RoleInGroup role : RoleInGroup.values()) {
      assertDoesNotThrow(() -> messages.getMessage(role.getLabelKey()));
    }
    for (MessageType type : MessageType.values()) {
      assertDoesNotThrow(() -> messages.getMessage(type.getLabelKey()));
      if (type.getMoreInfoKey() != null) {
        assertDoesNotThrow(() -> messages.getMessage(type.getMoreInfoKey()));
      }
    }
    for (Preference preference : Preference.values()) {
      assertDoesNotThrow(() -> messages.getMessage(preference.getDisplayMessageKey()));
    }
  }

  @Test
  void resolvesCodedModelValidationErrorsWithArguments() {
    ErrorList errors = new ErrorList();
    errors.addErrorMsgCode("validation.fieldData.numberAboveMaximum", "11", 10);

    messages.resolve(errors);

    assertEquals("Data [11] greater than maximum [10]", errors.getErrorMessages().get(0));
  }

  @Test
  void resolvesLocalizedIllegalArguments() {
    ErrorList validationErrors = new ErrorList();
    validationErrors.addErrorMsgCode("validation.inventoryField.invalidNumber", "abc");

    LocalizedIllegalArgumentException fieldException =
        new LocalizedIllegalArgumentException(
            "validation.inventoryField.invalidForFieldType", validationErrors, "abc", "Number");

    assertEquals(
        "[abc] is invalid for field type Number: Invalid number: abc",
        messages.getMessage(fieldException));
  }
}
