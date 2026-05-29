/**
 * Controlled vocabulary of DataCite Metadata Schema 4.7 relationType values, plus the
 * PIDINST-aligned IsCalibratedBy / Calibrates pair. Kept in lockstep with the backend
 * com.researchspace.service.inventory.DataCiteRelationType.
 */
export const DATACITE_RELATION_TYPES = [
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
  "Calibrates",
] as const;

export type DataCiteRelationType = (typeof DATACITE_RELATION_TYPES)[number];

const RELATION_TYPE_SET: ReadonlySet<string> = new Set(DATACITE_RELATION_TYPES);

export function isValidDataCiteRelationType(
  value: string,
): value is DataCiteRelationType {
  return value !== "" && RELATION_TYPE_SET.has(value);
}
