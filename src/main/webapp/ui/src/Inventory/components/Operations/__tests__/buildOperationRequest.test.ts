import sharedCases from "@testresources/inventory/fieldNameUniquenessCases.json";
import { describe, expect, it } from "vitest";
import { buildFacadeRequest, withUniqueFieldNames } from "../buildOperationRequest";
import type { InventoryOperation } from "../operations";
import type { OperationExtraField, OperationInputs, OperationOrigin } from "../types";
import { operations } from "./testOperations";

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

/** Every case but the first leaves the template and the documentation target unset. */
const build = (args: Omit<Parameters<typeof buildFacadeRequest>[0], "templateId" | "documentedByGlobalId">) =>
  buildFacadeRequest({ templateId: null, documentedByGlobalId: null, ...args });

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

  it("sends only the operation's declared inputs, not everything the wizard is holding", () => {
    const request = build({
      operation: operationNamed("derive"),
      values: { ...deriveValues, cryomedium: "DMSO", storageTemp: { numericValue: -80, unitId: 8 } },
      origins: [origin(100)],
    });

    expect(request).not.toHaveProperty("cryomedium");
    expect(request).not.toHaveProperty("storageTemp");
    expect(request).toHaveProperty("processName", "PCR");
  });

  it("omits the template and the documentation target rather than sending null", () => {
    const request = build({
      operation: operationNamed("derive"),
      values: deriveValues,
      origins: [origin(100)],
    });

    expect(request).not.toHaveProperty("templateId");
    expect(request).not.toHaveProperty("documentedByGlobalId");
  });

  it("sends no amount for an operation that takes nothing from its origin", () => {
    const request = build({
      operation: operationNamed("passage"),
      values: { sampleName: "HeLa p4", count: 1, eachAmount: millilitres(5) },
      origins: [origin(100)],
    });

    expect(request.origin).toEqual({ globalId: "SS100" });
  });

  it("sends no amount for Destroy, which takes whatever the origin holds", () => {
    const request = build({
      operation: operationNamed("destroy"),
      values: {},
      origins: [origin(100)],
    });

    expect(request).toEqual({ origin: { globalId: "SS100" } });
  });

  it("never sends the amount taken among the operation's own values", () => {
    const request = build({
      operation: operationNamed("aliquot"),
      values: {
        sampleName: "Aliquots",
        count: 3,
        eachAmount: millilitres(0.5),
        amountTaken: millilitres(1.5),
      },
      origins: [origin(100)],
    });

    expect(request).not.toHaveProperty("amountTaken");
    expect(request.origin?.amountTaken).toEqual(millilitres(1.5));
  });

  it("sends a single-origin operation's cryogenic values as its own fields", () => {
    const request = build({
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
    const request = build({
      operation: pool,
      values: poolValues,
      origins: poolOrigins,
      amountMode: "same",
    });

    expect(request.origins).toEqual([
      { globalId: "SS100", amountTaken: millilitres(0.5) },
      { globalId: "SS200", amountTaken: millilitres(0.5) },
    ]);
    expect(request).not.toHaveProperty("takeAll");
  });

  it("sends each origin its own amount in `perSubsample` mode", () => {
    const request = build({
      operation: pool,
      values: poolValues,
      origins: poolOrigins,
      amountMode: "perSubsample",
      perSubsampleAmounts: { SS100: millilitres(0.2), SS200: millilitres(0.9) },
    });

    expect(request.origins).toEqual([
      { globalId: "SS100", amountTaken: millilitres(0.2) },
      { globalId: "SS200", amountTaken: millilitres(0.9) },
    ]);
  });

  it("asks the server to take everything in `all` mode, sending no amounts", () => {
    const request = build({
      operation: pool,
      values: poolValues,
      origins: poolOrigins,
      amountMode: "all",
    });

    expect(request.takeAll).toBe(true);
    expect(request.origins).toEqual([{ globalId: "SS100" }, { globalId: "SS200" }]);
  });

  it("sends an origin no amount when `perSubsample` mode recorded none for it", () => {
    const request = build({
      operation: pool,
      values: poolValues,
      origins: poolOrigins,
      amountMode: "perSubsample",
      perSubsampleAmounts: { SS100: millilitres(0.2) },
    });

    expect(request.origins?.[1]).toEqual({ globalId: "SS200" });
  });

  it("ignores a stale `all` carried by a single-origin operation's remembered bundle", () => {
    // The wizard restores a stored bundle's amountMode whatever the operation, and Aliquot offers
    // no modes; honouring it would empty the origin while the summary showed the typed amount.
    const request = build({
      operation: operationNamed("aliquot"),
      values: {
        sampleName: "Aliquots",
        count: 1,
        eachAmount: millilitres(0.5),
        amountTaken: millilitres(0.5),
      },
      origins: [origin(100)],
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
