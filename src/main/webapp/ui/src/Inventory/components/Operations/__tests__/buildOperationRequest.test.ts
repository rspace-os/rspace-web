import sharedCases from "@testresources/inventory/fieldNameUniquenessCases.json";
import { describe, expect, it } from "vitest";
import { buildOperationInputsRequest, withUniqueFieldNames } from "../buildOperationRequest";
import type { InventoryOperation } from "../operationsConfig";
import type { OperationExtraField, OperationInputs, OperationOrigin } from "../types";
import { UNSET_UNIT } from "../types";
import { operations } from "./testOperations";

/*
 * These tests used to drive a TS buildOperationRequest, the wizard's own model of the sample the
 * server builds. That function had no production caller: since the server started building the
 * sample itself it was a second implementation that could drift from the real one while both suites
 * stayed green, so it was deleted. What the server builds is pinned server-side by
 * InventoryOperationRequestBuilderTest and InventoryOperationsInputsShapeMVCIT.
 *
 * What remains here is the part the wizard still owns: the request it POSTs, and the field-name
 * uniqueness rule the confirmation preview applies so it can show the names the server will store.
 */

/** One of the real definitions, since the inputs shape sends exactly the inputs a definition declares. */
function real(key: string): InventoryOperation {
  const operation = operations.find((o) => o.key === key);
  if (!operation) throw new Error(`no configured operation ${key}`);
  return operation;
}

const origin: OperationOrigin = {
  id: 100,
  globalId: "SS100",
  name: "Origin A",
  quantity: { numericValue: 1, unitId: 3 },
};

const deriveValues: OperationInputs = {
  sampleName: "Derived material",
  processName: "PCR",
  count: 2,
  eachAmount: { numericValue: 0.5, unitId: 3 },
  amountTaken: { numericValue: 0.6, unitId: 3 },
};

describe("buildOperationInputsRequest (the inputs shape)", () => {
  it("sends the declared inputs by key, the origins' amounts, the template and the documentation target", () => {
    const request = buildOperationInputsRequest({
      operation: real("derive"),
      values: { ...deriveValues, undeclared: "x" },
      origins: [origin],
      templateId: 77,
      documentedByGlobalId: "SD42",
    });
    expect(request).toEqual({
      operationType: "derive",
      origins: [{ id: 100, amountMode: "explicit", amountTaken: { numericValue: 0.6, unitId: 3 } }],
      inputs: {
        processName: "PCR",
        sampleName: "Derived material",
        count: 2,
        eachAmount: { numericValue: 0.5, unitId: 3 },
      },
      templateId: 77,
      documentedByGlobalId: "SD42",
    });
  });

  it("sends Destroy as a whole-origin claim with no inputs, leaving the disposed date to the server", () => {
    const request = buildOperationInputsRequest({
      operation: real("destroy"),
      values: {},
      origins: [{ id: 100, globalId: "SS100", name: "Vial A", quantity: { numericValue: 2, unitId: 3 } }],
      templateId: null,
      documentedByGlobalId: null,
    });
    expect(request).toEqual({
      operationType: "destroy",
      origins: [{ id: 100, amountMode: "all", amountTaken: { numericValue: 2, unitId: 3 } }],
      inputs: {},
      templateId: null,
      documentedByGlobalId: null,
    });
  });

  it("never sends fields for the origin itself: the server builds those from the definition", () => {
    const request = buildOperationInputsRequest({
      operation: real("destroy"),
      values: { disposedDate: "2026-09-12" },
      origins: [{ id: 100, globalId: "SS100", name: "Vial A", quantity: { numericValue: 2, unitId: 3 } }],
      templateId: null,
      documentedByGlobalId: null,
    });
    expect(Object.keys(request.origins[0]).sort()).toEqual(["amountMode", "amountTaken", "id"]);
  });
});

describe("buildOperationInputsRequest (amount modes, multi-origin)", () => {
  const poolOp = real("pool");
  const values: OperationInputs = {
    sampleName: "Pool",
    count: 1,
    eachAmount: { numericValue: 2, unitId: 3 },
    amountTaken: { numericValue: 1, unitId: 3 },
  };
  const origins: Array<OperationOrigin> = [
    { id: 1, globalId: "SS1", name: "Vial A", quantity: { numericValue: 5, unitId: 3 } },
    { id: 2, globalId: "SS2", name: "Vial B", quantity: { numericValue: 8, unitId: 3 } },
    { id: 3, globalId: "SS3", name: "Vial C", quantity: { numericValue: 4, unitId: 3 } },
  ];
  const build = (extra: Partial<Parameters<typeof buildOperationInputsRequest>[0]>) =>
    buildOperationInputsRequest({
      operation: poolOp,
      values,
      origins,
      templateId: null,
      documentedByGlobalId: null,
      ...extra,
    });

  it("'take all' empties every origin (each takes its own full current quantity)", () => {
    expect(build({ amountMode: "all" }).origins).toEqual([
      { id: 1, amountMode: "all", amountTaken: { numericValue: 5, unitId: 3 } },
      { id: 2, amountMode: "all", amountTaken: { numericValue: 8, unitId: 3 } },
      { id: 3, amountMode: "all", amountTaken: { numericValue: 4, unitId: 3 } },
    ]);
  });

  it("marks 'take all' origins as a whole-origin claim", () => {
    // amountTaken alone cannot tell the backend whether 5 was typed by the user or read off the
    // origin, and the backend's shape rules key off the mode, so it has to travel with the amount.
    expect(build({ amountMode: "all" }).origins.map((o) => o.amountMode)).toEqual(["all", "all", "all"]);
  });

  it("marks 'per subsample' origins explicit: those amounts are user-entered, not snapshots", () => {
    const request = build({
      amountMode: "perSubsample",
      perSubsampleAmounts: { SS1: { numericValue: 2, unitId: 3 } },
    });
    expect(request.origins.map((o) => o.amountMode)).toEqual(["explicit", "explicit", "explicit"]);
  });

  it("'per subsample' takes each origin's chosen amount, defaulting a missing one to zero", () => {
    const request = build({
      amountMode: "perSubsample",
      perSubsampleAmounts: { SS1: { numericValue: 2, unitId: 3 }, SS2: { numericValue: 4, unitId: 3 } },
    });
    expect(request.origins).toEqual([
      { id: 1, amountMode: "explicit", amountTaken: { numericValue: 2, unitId: 3 } },
      { id: 2, amountMode: "explicit", amountTaken: { numericValue: 4, unitId: 3 } },
      // SS3 has no chosen amount, so it takes a zero (no-op) decrement in its own unit.
      { id: 3, amountMode: "explicit", amountTaken: { numericValue: 0, unitId: 3 } },
    ]);
  });

  it("'same' (the default) takes the one shared amount from every origin", () => {
    expect(build({ amountMode: "same" }).origins).toEqual([
      { id: 1, amountMode: "explicit", amountTaken: { numericValue: 1, unitId: 3 } },
      { id: 2, amountMode: "explicit", amountTaken: { numericValue: 1, unitId: 3 } },
      { id: 3, amountMode: "explicit", amountTaken: { numericValue: 1, unitId: 3 } },
    ]);
  });
});

describe("buildOperationInputsRequest (remaining origin-update branches)", () => {
  const passageValues: OperationInputs = {
    sampleName: "Culture P2",
    count: 1,
    eachAmount: { numericValue: 1, unitId: 3 },
  };

  it("Passage (no amountTakenFrom) sends an explicit zero decrement in the origin's own unit", () => {
    // The origin still travels so it is linked and permission-checked; the backend treats a 0
    // decrement as a no-op (SubSampleApiManagerImpl returns early). It is "explicit", not a
    // whole-origin claim.
    const request = buildOperationInputsRequest({
      operation: real("passage"),
      values: passageValues,
      origins: [origin],
      templateId: null,
      documentedByGlobalId: null,
    });
    expect(request.origins).toEqual([{ id: 100, amountMode: "explicit", amountTaken: { numericValue: 0, unitId: 3 } }]);
  });

  it("Destroy on an origin with no quantity claims the whole (empty) origin with an unset unit", () => {
    // Destroy declares no each-amount either, so there is no unit to borrow: the fallback is the
    // unset marker.
    const request = buildOperationInputsRequest({
      operation: real("destroy"),
      values: {},
      origins: [{ id: 100, globalId: "SS100", name: "Vial A", quantity: null }],
      templateId: null,
      documentedByGlobalId: null,
    });
    expect(request.origins).toEqual([
      { id: 100, amountMode: "all", amountTaken: { numericValue: 0, unitId: UNSET_UNIT } },
    ]);
  });

  it("never sends a computed value's `into` key: the server computes Passage's number itself", () => {
    // A stale wizard state (or a remembered bundle from an older version) could carry passageNumber
    // in values; only the DECLARED inputs travel, so it must not.
    const request = buildOperationInputsRequest({
      operation: real("passage"),
      values: { ...passageValues, passageNumber: 3 },
      origins: [origin],
      templateId: null,
      documentedByGlobalId: null,
    });
    expect(request.inputs).not.toHaveProperty("passageNumber");
    expect(request.inputs).toEqual({ sampleName: "Culture P2", count: 1, eachAmount: { numericValue: 1, unitId: 3 } });
  });

  it("drops a declared input the user never touched rather than sending an undefined entry", () => {
    // Cryopreserve's cryomedium is optional; absent from values it must be absent from the request
    // too (an `undefined` would serialise to nothing anyway, but the key must not be claimed).
    const request = buildOperationInputsRequest({
      operation: real("cryopreserve"),
      values: {
        sampleName: "Frozen",
        count: 1,
        eachAmount: { numericValue: 1, unitId: 3 },
        amountTaken: { numericValue: 1, unitId: 3 },
        storageTemp: { numericValue: -80, unitId: 8 },
      },
      origins: [origin],
      templateId: null,
      documentedByGlobalId: null,
    });
    expect("cryomedium" in request.inputs).toBe(false);
    expect(Object.keys(request.inputs).sort()).toEqual(["count", "eachAmount", "sampleName", "storageTemp"]);
  });

  it("a single-origin operation handed amountMode 'all' still sends the typed amount, explicitly", () => {
    const request = buildOperationInputsRequest({
      operation: real("derive"),
      values: deriveValues,
      origins: [origin],
      templateId: null,
      documentedByGlobalId: null,
      amountMode: "all",
    });
    expect(request.origins).toEqual([
      { id: 100, amountMode: "explicit", amountTaken: { numericValue: 0.6, unitId: 3 } },
    ]);
  });
});

/**
 * The rule is implemented twice, once per language, and these cases are the only thing tying the two
 * together: the same file is asserted from InventoryOperationRequestBuilderTest. Without it, changing
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
