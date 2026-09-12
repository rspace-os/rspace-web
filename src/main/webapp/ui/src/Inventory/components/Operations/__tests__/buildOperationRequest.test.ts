import { describe, expect, it } from "vitest";
import { buildOperationInputsRequest, withUniqueFieldNames } from "../buildOperationRequest";
import type { InventoryOperation } from "../operationsConfig";
import type { OperationExtraField, OperationInputs, OperationOrigin } from "../types";
import { operations } from "./testOperations";

/*
 * These tests used to drive a TS buildOperationRequest, the wizard's own model of the sample the
 * server builds. That function had no production caller: since the server started building the
 * sample itself it was a second implementation that could drift from the real one while both suites
 * stayed green, so it was deleted (parallel review). What the server builds is pinned server-side by
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
      // an undeclared key never travels; the amount taken belongs to the origin element (M3)
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
    // Destroy declares an originFields entry (the disposed date). The wizard used to map it onto
    // every origin update and the request builder then dropped it, so it was built on each request
    // and thrown away (parallel review). An origin update carries only id, mode and amount.
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

  it("marks 'take all' origins as a whole-origin claim so the backend can compare-and-swap them", () => {
    // amountTaken alone cannot tell the backend whether 5 was typed by the user or read off the
    // origin. Only the second case may be rejected as stale, so the mode has to travel with it.
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

describe("withUniqueFieldNames", () => {
  // The confirmation preview applies this so it shows the names the server will actually store;
  // the server applies the same rule in InventoryOperationRequestBuilder.withUniqueFieldNames.
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
    // Two distinct subsamples may share a name ("Aliquot"), which produced two fields called
    // "Pooled from: Aliquot"; the endpoint rejects duplicates, so a valid Pool always failed at
    // Perform. Every member is suffixed, not just the later ones, so the names stay symmetrical.
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
