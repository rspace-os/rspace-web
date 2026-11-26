package com.researchspace.service.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

  @ParameterizedTest
  @CsvSource({
    "IsCitedBy, Cites",
    "Cites, IsCitedBy",
    "IsSupplementTo, IsSupplementedBy",
    "IsSupplementedBy, IsSupplementTo",
    "IsContinuedBy, Continues",
    "Continues, IsContinuedBy",
    "IsDescribedBy, Describes",
    "Describes, IsDescribedBy",
    "HasMetadata, IsMetadataFor",
    "IsMetadataFor, HasMetadata",
    "HasVersion, IsVersionOf",
    "IsVersionOf, HasVersion",
    "IsNewVersionOf, IsPreviousVersionOf",
    "IsPreviousVersionOf, IsNewVersionOf",
    "IsPartOf, HasPart",
    "HasPart, IsPartOf",
    "IsReferencedBy, References",
    "References, IsReferencedBy",
    "IsDocumentedBy, Documents",
    "Documents, IsDocumentedBy",
    "IsCompiledBy, Compiles",
    "Compiles, IsCompiledBy",
    "IsVariantFormOf, IsOriginalFormOf",
    "IsOriginalFormOf, IsVariantFormOf",
    "IsReviewedBy, Reviews",
    "Reviews, IsReviewedBy",
    "IsDerivedFrom, IsSourceOf",
    "IsSourceOf, IsDerivedFrom",
    "IsRequiredBy, Requires",
    "Requires, IsRequiredBy",
    "IsObsoletedBy, Obsoletes",
    "Obsoletes, IsObsoletedBy",
    "IsIdenticalTo, IsIdenticalTo"
  })
  void inverseInDataCite43MatchesEachDataCite43Pair(String relation, String inverse) {
    assertEquals(inverse, DataCiteRelationType.inverseInDataCite43(relation));
  }

  @Test
  void inverseInDataCite43FallsBackToIsReferencedByOutsideThatSchemaVersion() {
    for (String relation :
        List.of(
            "IsPublishedIn",
            "IsCollectedBy",
            "Collects",
            "IsTranslationOf",
            "HasTranslation",
            "IsCalibratedBy",
            "Calibrates",
            "MadeUpRelation")) {
      assertEquals("IsReferencedBy", DataCiteRelationType.inverseInDataCite43(relation), relation);
    }
    assertEquals("IsReferencedBy", DataCiteRelationType.inverseInDataCite43(null));
  }

  @Test
  void everyOtherRelationInvertsBackToItself() {
    Set<String> fallbacks =
        Set.of(
            "IsPublishedIn",
            "IsCollectedBy",
            "Collects",
            "IsTranslationOf",
            "HasTranslation",
            "IsCalibratedBy",
            "Calibrates");
    for (String relation : DataCiteRelationType.allValues()) {
      if (!fallbacks.contains(relation)) {
        assertEquals(
            relation,
            DataCiteRelationType.inverseInDataCite43(
                DataCiteRelationType.inverseInDataCite43(relation)),
            relation);
      }
    }
  }
}
