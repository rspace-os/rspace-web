package com.researchspace.service.inventory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
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

  private static final Map<String, String> INVERSES_IN_DATACITE_43 = new HashMap<>();

  static {
    String[][] pairs = {
      {"IsCitedBy", "Cites"},
      {"IsSupplementTo", "IsSupplementedBy"},
      {"IsContinuedBy", "Continues"},
      {"IsDescribedBy", "Describes"},
      {"HasMetadata", "IsMetadataFor"},
      {"HasVersion", "IsVersionOf"},
      {"IsNewVersionOf", "IsPreviousVersionOf"},
      {"IsPartOf", "HasPart"},
      {"IsReferencedBy", "References"},
      {"IsDocumentedBy", "Documents"},
      {"IsCompiledBy", "Compiles"},
      {"IsVariantFormOf", "IsOriginalFormOf"},
      {"IsReviewedBy", "Reviews"},
      {"IsDerivedFrom", "IsSourceOf"},
      {"IsRequiredBy", "Requires"},
      {"IsObsoletedBy", "Obsoletes"}
    };
    for (String[] pair : pairs) {
      INVERSES_IN_DATACITE_43.put(pair[0], pair[1]);
      INVERSES_IN_DATACITE_43.put(pair[1], pair[0]);
    }
    INVERSES_IN_DATACITE_43.put("IsIdenticalTo", "IsIdenticalTo");
  }

  private DataCiteRelationType() {}

  public static boolean isValid(String value) {
    return value != null && VALUES.contains(value);
  }

  public static Set<String> allValues() {
    return VALUES;
  }

  /**
   * The relation seen from the other end, as DataCite 4.3 names it. {@code IsReferencedBy} for a
   * relation without an inverse, for one whose inverse that schema version does not list, and for
   * anything unknown: the metadata sidecar declares that version (ADR 0016).
   */
  public static String inverseInDataCite43(String relationType) {
    return INVERSES_IN_DATACITE_43.getOrDefault(relationType, "IsReferencedBy");
  }
}
