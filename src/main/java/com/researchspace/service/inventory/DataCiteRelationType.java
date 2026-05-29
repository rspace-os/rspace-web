package com.researchspace.service.inventory;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Controlled vocabulary for inventory link relation types, mirroring DataCite Metadata Schema 4.7
 * relationType values plus PIDINST-aligned IsCalibratedBy/Calibrates.
 */
public final class DataCiteRelationType {

  private static final Set<String> VALUES =
      Collections.unmodifiableSet(
          new LinkedHashSet<>(
              Arrays.asList(
                  "IsCitedBy",
                  "Cites",
                  "IsSupplementTo",
                  "IsSupplementedBy",
                  "IsContinuedBy",
                  "Continues",
                  "IsDescribedBy",
                  "Describes",
                  "HasMetadata",
                  "IsMetadataFor",
                  "HasVersion",
                  "IsVersionOf",
                  "IsNewVersionOf",
                  "IsPreviousVersionOf",
                  "IsPartOf",
                  "HasPart",
                  "IsPublishedIn",
                  "IsReferencedBy",
                  "References",
                  "IsDocumentedBy",
                  "Documents",
                  "IsCompiledBy",
                  "Compiles",
                  "IsVariantFormOf",
                  "IsOriginalFormOf",
                  "IsIdenticalTo",
                  "IsReviewedBy",
                  "Reviews",
                  "IsDerivedFrom",
                  "IsSourceOf",
                  "IsRequiredBy",
                  "Requires",
                  "IsObsoletedBy",
                  "Obsoletes",
                  "IsCollectedBy",
                  "Collects",
                  "IsTranslationOf",
                  "HasTranslation",
                  "IsCalibratedBy",
                  "Calibrates")));

  private DataCiteRelationType() {}

  public static boolean isValid(String value) {
    return value != null && VALUES.contains(value);
  }

  public static Set<String> allValues() {
    return VALUES;
  }
}
