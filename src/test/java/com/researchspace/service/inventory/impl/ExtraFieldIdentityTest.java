package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.field.ExtraFieldIdentity;
import com.researchspace.model.inventory.field.ExtraFieldIdentity.Definition;
import org.junit.jupiter.api.Test;

class ExtraFieldIdentityTest {

  private static Definition roundTrip(String name, FieldType type) {
    return ExtraFieldIdentity.decode(ExtraFieldIdentity.encode(name, type));
  }

  @Test
  void survivesCharactersTheQueryGrammarWouldOtherwiseTreatAsSyntax() {
    for (String name :
        new String[] {
          "Room",
          "Room, floor",
          "a=b",
          "it's",
          "50% full",
          "a;b",
          "(x)",
          "a b",
          "naïve",
          "温度",
          "a/b\\c",
          "x".repeat(129)
        }) {
      assertEquals(new Definition(name, FieldType.TEXT), roundTrip(name, FieldType.TEXT));
    }
  }

  @Test
  void keepsOneNameOfTwoTypesApart() {
    String asText = ExtraFieldIdentity.encode("V", FieldType.TEXT);
    String asNumber = ExtraFieldIdentity.encode("V", FieldType.NUMBER);

    assertNotEquals(asText, asNumber);
    assertEquals(FieldType.TEXT, ExtraFieldIdentity.decode(asText).type());
    assertEquals(FieldType.NUMBER, ExtraFieldIdentity.decode(asNumber).type());
  }

  @Test
  void distinguishesNamesThatDifferOnlyByCase() {
    assertNotEquals(
        ExtraFieldIdentity.encode("room", FieldType.TEXT),
        ExtraFieldIdentity.encode("Room", FieldType.TEXT));
  }

  @Test
  void refusesAnIdItDidNotIssue() {
    for (String id :
        new String[] {
          null, "", "XF", "SF104", "XFt", "XFx4142", "XFt41424", "XFtZZ", "customFields.SF1"
        }) {
      assertNull(ExtraFieldIdentity.decode(id), id);
    }
  }

  @Test
  void refusesMalformedUtf8InsteadOfReplacingIt() {
    assertNull(ExtraFieldIdentity.decode("XFt80"));
  }
}
