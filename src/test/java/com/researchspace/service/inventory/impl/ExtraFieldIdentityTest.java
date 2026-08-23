package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.dao.ExtraFieldDao.ExtraFieldRow;
import com.researchspace.model.field.FieldType;
import org.junit.jupiter.api.Test;

class ExtraFieldIdentityTest {

  private static ExtraFieldRow roundTrip(String name, FieldType type) {
    return ExtraFieldRuntimeManagerImpl.decode(
        ExtraFieldRuntimeManagerImpl.encode(new ExtraFieldRow(name, type)));
  }

  @Test
  void recoversTheNameAndTypeFromTheId() {
    assertEquals(new ExtraFieldRow("Room", FieldType.TEXT), roundTrip("Room", FieldType.TEXT));
  }

  @Test
  void survivesCharactersTheQueryGrammarWouldOtherwiseTreatAsSyntax() {
    for (String name :
        new String[] {
          "Room, floor", "a=b", "it's", "50% full", "a;b", "(x)", "a b", "naïve", "温度", "a/b\\c"
        }) {
      assertEquals(new ExtraFieldRow(name, FieldType.TEXT), roundTrip(name, FieldType.TEXT));
    }
  }

  @Test
  void keepsOneNameOfTwoTypesApart() {
    String asText = ExtraFieldRuntimeManagerImpl.encode(new ExtraFieldRow("V", FieldType.TEXT));
    String asNumber = ExtraFieldRuntimeManagerImpl.encode(new ExtraFieldRow("V", FieldType.NUMBER));

    assertNotEquals(asText, asNumber);
    assertEquals(FieldType.TEXT, ExtraFieldRuntimeManagerImpl.decode(asText).type());
    assertEquals(FieldType.NUMBER, ExtraFieldRuntimeManagerImpl.decode(asNumber).type());
  }

  @Test
  void distinguishesNamesThatDifferOnlyByCase() {
    assertNotEquals(
        ExtraFieldRuntimeManagerImpl.encode(new ExtraFieldRow("room", FieldType.TEXT)),
        ExtraFieldRuntimeManagerImpl.encode(new ExtraFieldRow("Room", FieldType.TEXT)));
  }

  @Test
  void refusesAnIdItDidNotIssue() {
    for (String id :
        new String[] {
          null, "", "XF", "SF104", "XFt", "XFx4142", "XFt41424", "XFtZZ", "customFields.SF1"
        }) {
      assertNull(ExtraFieldRuntimeManagerImpl.decode(id), id);
    }
  }

  @Test
  void refusesANameTooLongToCarryInAQuery() {
    String longName = "x".repeat(ExtraFieldRuntimeManagerImpl.MAX_NAME_LENGTH + 1);

    assertNull(roundTrip(longName, FieldType.TEXT));
  }
}
