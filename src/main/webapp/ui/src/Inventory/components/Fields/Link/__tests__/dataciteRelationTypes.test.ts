import { describe, expect, it } from "vitest";
import {
  DATACITE_RELATION_TYPES,
  isValidDataCiteRelationType,
} from "../dataciteRelationTypes";

describe("dataciteRelationTypes", () => {
  it("accepts canonical DataCite values", () => {
    expect(isValidDataCiteRelationType("IsCitedBy")).toBe(true);
    expect(isValidDataCiteRelationType("References")).toBe(true);
    expect(isValidDataCiteRelationType("IsPartOf")).toBe(true);
  });

  it("rejects unknown values", () => {
    expect(isValidDataCiteRelationType("MadeUpRelation")).toBe(false);
    expect(isValidDataCiteRelationType("")).toBe(false);
  });

  it("rejects case variants", () => {
    expect(isValidDataCiteRelationType("iscitedby")).toBe(false);
    expect(isValidDataCiteRelationType("ISCITEDBY")).toBe(false);
  });

  it("includes PIDINST IsCalibratedBy and Calibrates", () => {
    expect(isValidDataCiteRelationType("IsCalibratedBy")).toBe(true);
    expect(isValidDataCiteRelationType("Calibrates")).toBe(true);
  });

  it("contains no duplicate entries", () => {
    expect(DATACITE_RELATION_TYPES.length).toBe(
      new Set(DATACITE_RELATION_TYPES).size,
    );
  });

  it("matches backend vocabulary list", () => {
    const expectedFromBackend = [
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
    ];
    expect([...DATACITE_RELATION_TYPES]).toEqual(expectedFromBackend);
  });
});
