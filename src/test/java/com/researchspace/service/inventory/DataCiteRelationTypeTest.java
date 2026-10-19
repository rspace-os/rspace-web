package com.researchspace.service.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DataCiteRelationTypeTest {

  @Test
  void isValidAcceptsCanonicalDataCiteValue() {
    assertTrue(DataCiteRelationType.isValid("IsCitedBy"));
    assertTrue(DataCiteRelationType.isValid("References"));
    assertTrue(DataCiteRelationType.isValid("IsPartOf"));
  }

  @Test
  void isValidRejectsUnknownValue() {
    assertFalse(DataCiteRelationType.isValid("MadeUpRelation"));
    assertFalse(DataCiteRelationType.isValid(""));
    assertFalse(DataCiteRelationType.isValid(null));
  }

  @Test
  void isValidIsCaseSensitive() {
    assertFalse(DataCiteRelationType.isValid("iscitedby"));
    assertFalse(DataCiteRelationType.isValid("ISCITEDBY"));
  }

  @Test
  void vocabularyIncludesIsCalibratedByForPidinst() {
    assertTrue(DataCiteRelationType.isValid("IsCalibratedBy"));
    assertTrue(DataCiteRelationType.isValid("Calibrates"));
  }

  @Test
  void vocabularyExposesAllValues() {
    assertThat(DataCiteRelationType.allValues()).contains("IsCitedBy");
    assertThat(DataCiteRelationType.allValues()).contains("HasPart");
    assertEquals(
        DataCiteRelationType.allValues().size(),
        DataCiteRelationType.allValues().stream().distinct().count(),
        "vocabulary must have no duplicate entries");
  }
}
