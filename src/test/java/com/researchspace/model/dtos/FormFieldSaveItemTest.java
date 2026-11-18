package com.researchspace.model.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.field.NumberFieldForm;
import com.researchspace.model.field.RadioFieldForm;
import java.util.List;
import org.junit.jupiter.api.Test;

public class FormFieldSaveItemTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void sourceIsReadAsTheLegacyDtoForTheType() throws Exception {
    FormFieldSaveItem item =
        mapper.readValue(
            """
            {"id": 7, "type": "Radio", "fieldKey": "k",
             "displayRule": {"sourceFieldKey": "s", "operator": "IS_ANY_OF", "values": ["Yes"]},
             "source": {"name": "GMO?", "radioValues": "fieldChoices=Yes&fieldChoices=No",
                        "showAsPickList": true, "mandatory": true}}
            """,
            FormFieldSaveItem.class);

    assertEquals(7L, item.getId());
    assertEquals("k", item.getFieldKey());
    assertEquals(new FieldDisplayRuleDTO("s", "IS_ANY_OF", List.of("Yes")), item.getDisplayRule());
    RadioFieldDTO<?> radio = assertInstanceOf(RadioFieldDTO.class, item.getSource());
    assertTrue(radio.isShowAsPickList());
    assertTrue(radio.isMandatory());
    LegacyFormFieldDTOs.normalise(radio);
    RadioFieldForm field = radio.createFieldForm();
    assertEquals("GMO?", field.getName());
    assertEquals(List.of("Yes", "No"), field.getRadioOptionAsList());
  }

  @Test
  public void everyLegacyDtoRoundTripsThroughJson() throws Exception {
    NumberFieldForm number = new NumberFieldForm("n");
    number.setMinNumberValue(1.5);
    number.setDecimalPlaces((byte) 2);
    number.setMandatory(true);
    FormFieldSaveItem written =
        new FormFieldSaveItem(
            null, "Number", null, null, LegacyFormFieldDTOs.fromFieldForm(number));

    FormFieldSaveItem read =
        mapper.readValue(mapper.writeValueAsString(written), FormFieldSaveItem.class);

    NumberFieldForm copy = (NumberFieldForm) read.getSource().createFieldForm();
    assertEquals(1.5, copy.getMinNumberValue());
    assertEquals((byte) 2, copy.getDecimalPlaces());
    assertTrue(copy.isMandatory());
  }

  @Test
  public void unknownTypeLeavesNoSource() throws Exception {
    FormFieldSaveItem item =
        mapper.readValue("{\"type\": \"Hologram\", \"source\": {}}", FormFieldSaveItem.class);
    assertNull(item.getSource());
    assertNull(item.getId());
  }

  @Test
  public void nonNumericIdIsRejected() {
    assertThrows(
        JsonProcessingException.class,
        () -> mapper.readValue("{\"id\": \"abc\", \"type\": \"Text\"}", FormFieldSaveItem.class));
  }

  @Test
  public void ruleDtoRejectsAnUnknownOperatorOrMissingValues() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new FieldDisplayRuleDTO("k", "SOMETIMES", List.of()).toRule());
    assertThrows(
        IllegalArgumentException.class,
        () -> new FieldDisplayRuleDTO("k", "IS_ANY_OF", List.of()).toRule());
    assertEquals(
        "{\"sourceFieldKey\":\"k\",\"operator\":\"HAS_VALUE\"}",
        new FieldDisplayRuleDTO("k", "HAS_VALUE", null).toRule().toJson());
  }
}
