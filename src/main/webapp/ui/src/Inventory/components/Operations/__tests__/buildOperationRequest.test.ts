import sharedCases from "@testresources/inventory/fieldNameUniquenessCases.json";
import { describe, expect, it } from "vitest";
import { buildFacadeRequest, withUniqueFieldNames } from "../buildOperationRequest";
import type { InventoryOperation } from "../operationsConfig";
import type { OperationExtraField, OperationInputs, OperationOrigin } from "../types";
import { operations } from "./testOperations";

/*
 * The wizard owns exactly two things here: the body it POSTs to an operation's endpoint, and the
 * field-name uniqueness rule the confirmation preview applies so it can show the names the server
 * will actually store. What the server builds is pinned server-side, by the per-operation tests.
 */

function operationNamed(key: string): InventoryOperation {
  const operation = operations.find((o) => o.key === key);
  if (!operation) throw new Error(`no operation ${key}`);
  return operation;
}

const millilitres = (numericValue: number) => ({ numericValue, unitId: 3 });

const origin = (id: number): OperationOrigin => ({
  id,
  globalId: `SS${id}`,
  name: `Origin ${id}`,
  quantity: millilitres(1),
});

const deriveValues: OperationInputs = {
  sampleName: "Derived material",
  processName: "PCR",
  count: 2,
  eachAmount: millilitres(0.5),
  amountTaken: millilitres(0.6),
};

describe("buildFacadeRequest, single-origin operations", () => {
  it("sends the operation's own values, one origin, the template and the documentation target", () => {
    const request = buildFacadeRequest({
      operation: operationNamed("derive"),
      values: deriveValues,
      origins: [origin(100)],
      templateId: 7,
      documentedByGlobalId: "SD42",
    });

    expect(request).toEqual({
      origin: { globalId: "SS100", amountTaken: millilitres(0.6) },
      sampleName: "Derived material",
      processName: "PCR",
      count: 2,
      eachAmount: millilitres(0.5),
      templateId: 7,
      documentedByGlobalId: "SD42",
    });
  });

  it("omits the template and the documentation target rather than sending null", () => {
    const request = buildFacadeRequest({
      operation: operationNamed("derive"),
      values: deriveValues,
      origins: [origin(100)],
      templateId: null,
      documentedByGlobalId: null,
    });

    expect(request).not.toHaveProperty("templateId");
    expect(request).not.toHaveProperty("documentedByGlobalId");
  });

  it("sends no amount for an operation that takes nothing from its origin", () => {
    // Passage links the new sample back but leaves the origin alone, and the endpoint refuses an
    // amount sent anyway.
    const request = buildFacadeRequest({
      operation: operationNamed("passage"),
      values: { sampleName: "HeLa p4", count: 1, eachAmount: millilitres(5) },
      origins: [origin(100)],
      templateId: null,
      documentedByGlobalId: null,
    });

    expect(request.origin).toEqual({ globalId: "SS100" });
  });

  it("sends no amount for Destroy, which takes whatever the origin holds", () => {
    const request = buildFacadeRequest({
      operation: operationNamed("destroy"),
      values: {},
      origins: [origin(100)],
      templateId: null,
      documentedByGlobalId: null,
    });

    expect(request).toEqual({ origin: { globalId: "SS100" } });
  });

  it("never sends the amount taken among the operation's own values", () => {
    // It belongs to the origin, and an operation body has no field of that name.
    const request = buildFacadeRequest({
      operation: operationNamed("aliquot"),
      values: {
        sampleName: "Aliquots",
        count: 3,
        eachAmount: millilitres(0.5),
        amountTaken: millilitres(1.5),
      },
      origins: [origin(100)],
      templateId: null,
      documentedByGlobalId: null,
    });

    expect(request).not.toHaveProperty("amountTaken");
    expect(request.origin?.amountTaken).toEqual(millilitres(1.5));
  });

  it("sends a single-origin operation's cryogenic values as its own fields", () => {
    const request = buildFacadeRequest({
      operation: operationNamed("cryopreserve"),
      values: {
        sampleName: "Frozen",
        count: 1,
        eachAmount: millilitres(1),
        amountTaken: millilitres(1),
        cryomedium: "10% DMSO",
        storageTemp: { numericValue: -80, unitId: 8 },
      },
      origins: [origin(100)],
      templateId: null,
      documentedByGlobalId: null,
    });

    expect(request.cryomedium).toBe("10% DMSO");
    expect(request.storageTemp).toEqual({ numericValue: -80, unitId: 8 });
  });
});

describe("buildFacadeRequest, Pool", () => {
  const pool = operationNamed("pool");
  const poolValues: OperationInputs = {
    sampleName: "Pooled",
    count: 1,
    eachAmount: millilitres(2),
    amountTaken: millilitres(0.5),
  };
  const poolOrigins = [origin(100), origin(200)];

  it("copies the one shared amount onto every origin in `same` mode", () => {
    const request = buildFacadeRequest({
      operation: pool,
      values: poolValues,
      origins: poolOrigins,
      templateId: null,
      documentedByGlobalId: null,
      amountMode: "same",
    });

    expect(request.origins).toEqual([
      { globalId: "SS100", amountTaken: millilitres(0.5) },
      { globalId: "SS200", amountTaken: millilitres(0.5) },
    ]);
    expect(request).not.toHaveProperty("takeAll");
  });

  it("sends each origin its own amount in `perSubsample` mode", () => {
    const request = buildFacadeRequest({
      operation: pool,
      values: poolValues,
      origins: poolOrigins,
      templateId: null,
      documentedByGlobalId: null,
      amountMode: "perSubsample",
      perSubsampleAmounts: { SS100: millilitres(0.2), SS200: millilitres(0.9) },
    });

    expect(request.origins).toEqual([
      { globalId: "SS100", amountTaken: millilitres(0.2) },
      { globalId: "SS200", amountTaken: millilitres(0.9) },
    ]);
  });

  it("asks the server to take everything in `all` mode, sending no amounts", () => {
    // The server reads each origin's live quantity at processing time, so the client never has to
    // guess what "everything" was.
    const request = buildFacadeRequest({
      operation: pool,
      values: poolValues,
      origins: poolOrigins,
      templateId: null,
      documentedByGlobalId: null,
      amountMode: "all",
    });

    expect(request.takeAll).toBe(true);
    expect(request.origins).toEqual([{ globalId: "SS100" }, { globalId: "SS200" }]);
  });

  it("sends an origin no amount when `perSubsample` mode recorded none for it", () => {
    const request = buildFacadeRequest({
      operation: pool,
      values: poolValues,
      origins: poolOrigins,
      templateId: null,
      documentedByGlobalId: null,
      amountMode: "perSubsample",
      perSubsampleAmounts: { SS100: millilitres(0.2) },
    });

    expect(request.origins?.[1]).toEqual({ globalId: "SS200" });
  });

  it("ignores a stale `all` carried by a single-origin operation's remembered bundle", () => {
    // The wizard restores a stored bundle's amountMode whatever the operation, and Aliquot offers
    // no modes; honouring it would empty the origin while the summary showed the typed amount.
    const request = buildFacadeRequest({
      operation: operationNamed("aliquot"),
      values: {
        sampleName: "Aliquots",
        count: 1,
        eachAmount: millilitres(0.5),
        amountTaken: millilitres(0.5),
      },
      origins: [origin(100)],
      templateId: null,
      documentedByGlobalId: null,
      amountMode: "all",
    });

    expect(request.origin).toEqual({ globalId: "SS100", amountTaken: millilitres(0.5) });
    expect(request).not.toHaveProperty("takeAll");
  });
});

/**
 * The rule is implemented twice, once per language, and these cases are the only thing tying the two
 * together: the same file is asserted from FieldNameUniquenessParityTest. Without it, changing
 * the suffix format on one side left the preview promising names the server would not store, with
 * both suites green.
 */
describe("withUniqueFieldNames, against the cases the Java implementation is held to", () => {
  it.each(sharedCases.cases)("$description", ({ fields, expected }) => {
    const asExtraFields: Array<OperationExtraField> = fields.map((field) =>
      field.type === "link"
        ? {
            name: field.name,
            type: "link",
            newFieldRequest: true,
            operationFieldKey: "operations.pool.linkFieldName",
            link: { relationType: "HasPart", targetGlobalId: field.targetGlobalId ?? "", versionPin: null },
          }
        : {
            name: field.name,
            type: "text",
            newFieldRequest: true,
            operationFieldKey: "operations.passage.numberField",
            content: "",
          },
    );
    expect(withUniqueFieldNames(asExtraFields).map((f) => f.name)).toEqual(expected);
  });
});

describe("withUniqueFieldNames", () => {
  const link = (name: string, targetGlobalId: string): OperationExtraField => ({
    name,
    type: "link",
    newFieldRequest: true,
    operationFieldKey: "operations.pool.linkFieldName",
    link: { relationType: "HasPart", targetGlobalId, versionPin: null },
  });

  it("leaves distinct names untouched", () => {
    const fields = [link("Pooled from: Vial A", "SS1"), link("Pooled from: Vial B", "SS2")];
    expect(withUniqueFieldNames(fields).map((f) => f.name)).toEqual(["Pooled from: Vial A", "Pooled from: Vial B"]);
  });

  it("suffixes every member of a colliding group with the global id it targets", () => {
    const fields = [link("Pooled from: Aliquot", "SS1"), link("Pooled from: Aliquot", "SS2")];
    expect(withUniqueFieldNames(fields).map((f) => f.name)).toEqual([
      "Pooled from: Aliquot (SS1)",
      "Pooled from: Aliquot (SS2)",
    ]);
  });

  it("treats names differing only by case or surrounding space as duplicates, as the backend does", () => {
    // InventoryFieldNameUniquenessValidator compares trimmed and case-insensitively.
    const fields = [link("Pooled from: Aliquot", "SS1"), link("  pooled FROM: aliquot ", "SS2")];
    const names = withUniqueFieldNames(fields).map((f) => f.name);
    expect(names[0]).toBe("Pooled from: Aliquot (SS1)");
    expect(names[1]).toBe("  pooled FROM: aliquot  (SS2)");
  });

  it("falls back to an ordinal when a suffixed name collides in turn", () => {
    const fields = [link("Pooled from: Aliquot", "SS1"), link("Pooled from: Aliquot", "SS1")];
    expect(withUniqueFieldNames(fields).map((f) => f.name)).toEqual([
      "Pooled from: Aliquot (SS1)",
      "Pooled from: Aliquot (SS1) (2)",
    ]);
  });
});
