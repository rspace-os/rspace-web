package com.researchspace.model.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.model.field.FieldType;
import com.researchspace.model.field.NumberFieldForm;
import com.researchspace.model.field.TimeFieldForm;
import org.junit.jupiter.api.Test;

/** The form field editor sends every setting on each save, so an empty setting clears it. */
class LegacyFieldDTOClearingTest {

  @Test
  void emptyNumberSettingsClearStoredOnes() {
    NumberFieldForm field =
        new NumberFieldDTO<>("1", "10", "2", "5", FieldType.NUMBER, "n").createFieldForm();
    assertEquals(1d, field.getMinNumberValue());

    new NumberFieldDTO<>("", "", "", "", FieldType.NUMBER, "n").copyValuesIntoFieldForm(field);

    assertNull(field.getMinNumberValue());
    assertNull(field.getMaxNumberValue());
    assertNull(field.getDecimalPlaces());
    assertNull(field.getDefaultNumberValue());
  }

  @Test
  void emptyTimeSettingsClearStoredOnes() {
    TimeFieldForm field =
        new TimeFieldDTO<>("10:00", "09:00", "11:00", "HH:mm", "t").createFieldForm();
    assertEquals("09:00", field.getMinTimeAsString());

    new TimeFieldDTO<>("", "", "", "HH:mm", "t").copyValuesIntoFieldForm(field);

    assertEquals("", field.getDefaultTimeAsString());
    assertEquals("", field.getMinTimeAsString());
    assertEquals("", field.getmaxTimeAsString());
  }
}
