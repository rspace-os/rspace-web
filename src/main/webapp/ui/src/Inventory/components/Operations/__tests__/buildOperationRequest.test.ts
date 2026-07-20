import { describe, expect, it } from "vitest";
import { buildOperationRequest } from "../buildOperationRequest";
import type { InventoryOperation } from "../operationsConfig";
import type {
  OperationInputs,
  OperationLinkField,
  OperationNewSample,
  OperationOrigin,
  OperationRequest,
} from "../types";

/** The created sample, asserting it exists - producing operations always create one. */
function newSampleOf(request: OperationRequest): OperationNewSample {
  if (!request.newSample) throw new Error("expected a new sample");
  return request.newSample;
}

const deriveOperation: InventoryOperation = {
  key: "derive",
  labelKey: "operations.derive.label",
  documentationStep: true,
  inputs: [],
  effect: {
    nameFrom: "sampleName",
    countFrom: "count",
    eachAmountFrom: "eachAmount",
    amountTakenFrom: "amountTaken",
    links: [{ relationType: "IsDerivedFrom", fieldNameKey: "operations.derive.linkFieldName" }],
  },
};

const origin: OperationOrigin = {
  id: 100,
  globalId: "SS100",
  name: "Origin A",
  quantity: { numericValue: 1, unitId: 3 },
};

const resolveLabel = (key: string, params?: Record<string, unknown>): string => {
  if (key === "operations.derive.linkFieldName") return `Is Derived From using process: ${String(params?.processName)}`;
  if (key === "operations.pool.linkFieldName") return `Pooled from: ${String(params?.originName)}`;
  return key;
};

const deriveValues: OperationInputs = {
  sampleName: "Derived material",
  processName: "PCR",
  count: 2,
  eachAmount: { numericValue: 0.5, unitId: 3 },
  amountTaken: { numericValue: 0.6, unitId: 3 },
};

describe("buildOperationRequest (Derive)", () => {
  const request = buildOperationRequest({
    operation: deriveOperation,
    values: deriveValues,
    origins: [origin],
    resolveLabel,
    templateId: null,
  });

  it("sets the origin to its absolute after-quantity", () => {
    expect(request.origins).toEqual([{ id: 100, amountTaken: { numericValue: 0.6, unitId: 3 } }]);
  });

  it("passes through a chosen templateId (any / from-origin-sample resolve to an id)", () => {
    const withTemplate = buildOperationRequest({
      operation: deriveOperation,
      values: deriveValues,
      origins: [origin],
      resolveLabel,
      templateId: 77,
    });
    expect(newSampleOf(withTemplate).templateId).toBe(77);
  });

  it("creates N subsamples, each with the each-item amount in the origin's unit", () => {
    expect(newSampleOf(request).subSamples).toHaveLength(2);
    for (const subSample of newSampleOf(request).subSamples) {
      expect(subSample.quantity).toEqual({ numericValue: 0.5, unitId: 3 });
    }
  });

  it("puts the IsDerivedFrom link (named with the process) on the sample only, never on the subsamples", () => {
    const expectedName = "Is Derived From using process: PCR";
    const sampleLink = newSampleOf(request).extraFields[0] as OperationLinkField;
    expect(sampleLink.name).toBe(expectedName);
    expect(sampleLink.link).toEqual({
      relationType: "IsDerivedFrom",
      targetGlobalId: "SS100",
      versionPin: null,
    });
    // The process links belong on the created sample, not on any subsample it creates.
    for (const subSample of newSampleOf(request).subSamples) {
      expect(subSample.extraFields).toEqual([]);
    }
  });

  it("sets the sample total quantity to N x each-amount", () => {
    expect(newSampleOf(request).quantity).toEqual({ numericValue: 1, unitId: 3 });
  });

  it("carries the operation key as operationType", () => {
    expect(request.operationType).toBe("derive");
  });

  it("adds the optional documentation link (IsDocumentedBy) to the sample only, not the subsamples", () => {
    const withDoc = buildOperationRequest({
      operation: deriveOperation,
      values: deriveValues,
      origins: [origin],
      resolveLabel,
      templateId: null,
      documentationLink: { fieldName: "Documented by", targetGlobalId: "SD42" },
    });
    const docOn = (fields: Array<{ type: string }>) =>
      fields.some((f) => (f as OperationLinkField).link?.relationType === "IsDocumentedBy");
    expect(docOn(newSampleOf(withDoc).extraFields)).toBe(true);
    // The documentation link, like the provenance link, stays on the sample and off the subsamples.
    for (const subSample of newSampleOf(withDoc).subSamples) {
      expect(subSample.extraFields).toEqual([]);
    }
  });
});

describe("buildOperationRequest (operation-specific fields)", () => {
  it("adds a text field and storage temperature when the effect declares them", () => {
    const cryo: InventoryOperation = {
      key: "cryopreserve",
      labelKey: "operations.cryopreserve.label",
      documentationStep: true,
      inputs: [],
      effect: {
        nameFrom: "sampleName",
        countFrom: "count",
        eachAmountFrom: "eachAmount",
        amountTakenFrom: "amountTaken",
        storageTempFrom: "storageTemp",
        links: [{ relationType: "IsDerivedFrom", fieldNameKey: "operations.cryopreserve.linkFieldName" }],
        textFields: [{ nameKey: "operations.cryopreserve.cryomediumField", contentFrom: "cryomedium" }],
      },
    };
    const values: OperationInputs = {
      sampleName: "Frozen aliquots",
      count: 1,
      eachAmount: { numericValue: 1, unitId: 3 },
      amountTaken: { numericValue: 0, unitId: 3 },
      cryomedium: "DMSO 10%",
      storageTemp: { numericValue: -196, unitId: 8 },
    };

    const request = buildOperationRequest({
      operation: cryo,
      values,
      origins: [origin],
      resolveLabel,
      templateId: null,
    });

    expect(newSampleOf(request).storageTempMin).toEqual({ numericValue: -196, unitId: 8 });
    expect(newSampleOf(request).storageTempMax).toEqual({ numericValue: -196, unitId: 8 });
    const textField = newSampleOf(request).extraFields.find((f) => f.type === "text");
    expect(textField).toMatchObject({ content: "DMSO 10%" });
    // text fields live on the sample only, not on the subsamples
    expect(newSampleOf(request).subSamples[0].extraFields.every((f) => f.type === "link")).toBe(true);
  });
});

// An operation with no amountTakenFrom (e.g. Passage) must NOT send an empty origins array - the
// backend rejects that. It sends the origin with a zero amount (a no-op decrement), so the origin
// is still linked and permission-checked but its quantity is unchanged. See adr/0002.
describe("buildOperationRequest (operation that does not decrement the origin)", () => {
  const passageOperation: InventoryOperation = {
    key: "passage",
    labelKey: "operations.passage.label",
    documentationStep: true,
    inputs: [],
    effect: {
      nameFrom: "sampleName",
      countFrom: "count",
      eachAmountFrom: "eachAmount",
      links: [{ relationType: "IsDerivedFrom", fieldNameKey: "operations.passage.linkFieldName" }],
    },
  };
  const request = buildOperationRequest({
    operation: passageOperation,
    values: { sampleName: "Culture P2", count: 1, eachAmount: { numericValue: 2, unitId: 3 } },
    origins: [origin],
    resolveLabel,
    templateId: null,
  });

  it("sends the origin with a zero amount taken (not an empty origins array)", () => {
    expect(request.origins).toEqual([{ id: 100, amountTaken: { numericValue: 0, unitId: 3 } }]);
  });
});

// Pool (adr/0007) is multi-origin: it decrements every selected origin by the same shared amount and
// links the new sample back to each with a HasPart link.
describe("buildOperationRequest (Pool - multi-origin)", () => {
  const poolOperation: InventoryOperation = {
    key: "pool",
    labelKey: "operations.pool.label",
    requiresMultiple: true,
    documentationStep: true,
    inputs: [],
    effect: {
      nameFrom: "sampleName",
      countFrom: "count",
      eachAmountFrom: "eachAmount",
      amountTakenFrom: "amountTaken",
      links: [{ relationType: "HasPart", fieldNameKey: "operations.pool.linkFieldName" }],
    },
  };
  const origins: Array<OperationOrigin> = [
    { id: 1, globalId: "SS1", name: "Vial A", quantity: { numericValue: 5, unitId: 3 } },
    { id: 2, globalId: "SS2", name: "Vial B", quantity: { numericValue: 5, unitId: 3 } },
    { id: 3, globalId: "SS3", name: "Vial C", quantity: { numericValue: 5, unitId: 3 } },
  ];
  const request = buildOperationRequest({
    operation: poolOperation,
    values: {
      sampleName: "Pool",
      count: 1,
      eachAmount: { numericValue: 2, unitId: 3 },
      amountTaken: { numericValue: 1, unitId: 3 },
    },
    origins,
    resolveLabel,
    templateId: null,
  });

  it("reduces every origin by the same shared amount taken", () => {
    expect(request.origins).toEqual([
      { id: 1, amountTaken: { numericValue: 1, unitId: 3 } },
      { id: 2, amountTaken: { numericValue: 1, unitId: 3 } },
      { id: 3, amountTaken: { numericValue: 1, unitId: 3 } },
    ]);
  });

  it("puts one HasPart link back to each pooled origin on the new sample", () => {
    const links = newSampleOf(request).extraFields.filter((f): f is OperationLinkField => f.type === "link");
    expect(links.map((l) => l.link.targetGlobalId)).toEqual(["SS1", "SS2", "SS3"]);
    expect(links.every((l) => l.link.relationType === "HasPart")).toBe(true);
  });

  it("gives each pooled link a distinct name including the origin subsample name", () => {
    const links = newSampleOf(request).extraFields.filter((f): f is OperationLinkField => f.type === "link");
    const names = links.map((l) => l.name);
    expect(names).toEqual(["Pooled from: Vial A", "Pooled from: Vial B", "Pooled from: Vial C"]);
    // Distinct names: a record cannot hold two fields with the same name.
    expect(new Set(names).size).toBe(names.length);
  });
});

// Destroy (adr/0008) is a terminal operation: noOutput (no new sample), it empties the origin (takes
// its full current quantity) and adds a custom field to the origin itself (the disposed date).
describe("buildOperationRequest (Destroy - terminal, no output)", () => {
  const destroyOperation: InventoryOperation = {
    key: "destroy",
    labelKey: "operations.destroy.label",
    noOutput: true,
    documentationStep: false,
    steps: ["confirm"],
    inputs: [],
    effect: {
      emptiesOrigin: true,
      links: [],
      originFields: [{ nameKey: "operations.destroy.disposedField", contentFrom: "disposedDate", type: "text" }],
    },
  };
  const request = buildOperationRequest({
    operation: destroyOperation,
    // The disposed date is a computed value applied before the request is built (here supplied directly).
    values: { disposedDate: "2026-07-20" },
    origins: [{ id: 100, globalId: "SS100", name: "Vial A", quantity: { numericValue: 2, unitId: 3 } }],
    resolveLabel,
    templateId: null,
  });

  it("creates no new sample", () => {
    expect(request.newSample).toBeNull();
  });

  it("empties the origin (its full current quantity) and adds the disposed field to it", () => {
    expect(request.origins).toEqual([
      {
        id: 100,
        amountTaken: { numericValue: 2, unitId: 3 },
        extraFields: [
          {
            name: "operations.destroy.disposedField",
            type: "text",
            newFieldRequest: true,
            content: "2026-07-20",
          },
        ],
      },
    ]);
  });
});
