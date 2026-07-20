import { describe, expect, it } from "vitest";
import { buildOperationRequest } from "../buildOperationRequest";
import type { InventoryOperation } from "../operationsConfig";
import type { OperationInputs, OperationLinkField, OperationOrigin } from "../types";

const deriveOperation: InventoryOperation = {
  key: "derive",
  labelKey: "operations.derive.label",
  minSelected: 1,
  maxSelected: 1,
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
  quantity: { numericValue: 1, unitId: 3 },
};

const resolveLabel = (key: string, params?: Record<string, unknown>): string =>
  key === "operations.derive.linkFieldName" ? `Is Derived From using process: ${String(params?.processName)}` : key;

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
    origin,
    resolveLabel,
    templateId: null,
  });

  it("sets the origin to its absolute after-quantity", () => {
    expect(request.origins).toEqual([{ id: 100, globalId: "SS100", amountTaken: { numericValue: 0.6, unitId: 3 } }]);
  });

  it("names the sample from the input; templateId is null when no template is chosen", () => {
    expect(request.newSample.name).toBe("Derived material");
    expect(request.newSample.templateId).toBeNull();
  });

  it("passes through a chosen templateId (any / from-origin-sample resolve to an id)", () => {
    const withTemplate = buildOperationRequest({
      operation: deriveOperation,
      values: deriveValues,
      origin,
      resolveLabel,
      templateId: 77,
    });
    expect(withTemplate.newSample.templateId).toBe(77);
  });

  it("creates N subsamples, each with the each-item amount in the origin's unit", () => {
    expect(request.newSample.subSamples).toHaveLength(2);
    for (const subSample of request.newSample.subSamples) {
      expect(subSample.quantity).toEqual({ numericValue: 0.5, unitId: 3 });
    }
  });

  it("puts the IsDerivedFrom link (named with the process) on the sample only, never on the subsamples", () => {
    const expectedName = "Is Derived From using process: PCR";
    const sampleLink = request.newSample.extraFields[0] as OperationLinkField;
    expect(sampleLink.name).toBe(expectedName);
    expect(sampleLink.link).toEqual({
      relationType: "IsDerivedFrom",
      targetGlobalId: "SS100",
      versionPin: null,
    });
    // The process links belong on the created sample, not on any subsample it creates.
    for (const subSample of request.newSample.subSamples) {
      expect(subSample.extraFields).toEqual([]);
    }
  });

  it("sets the sample total quantity to N x each-amount", () => {
    expect(request.newSample.quantity).toEqual({ numericValue: 1, unitId: 3 });
  });

  it("carries the operation key as operationType", () => {
    expect(request.operationType).toBe("derive");
  });

  it("adds the optional documentation link (IsDocumentedBy) to the sample only, not the subsamples", () => {
    const withDoc = buildOperationRequest({
      operation: deriveOperation,
      values: deriveValues,
      origin,
      resolveLabel,
      templateId: null,
      documentationLink: { fieldName: "Documented by", targetGlobalId: "SD42" },
    });
    const docOn = (fields: Array<{ type: string }>) =>
      fields.some((f) => (f as OperationLinkField).link?.relationType === "IsDocumentedBy");
    expect(docOn(withDoc.newSample.extraFields)).toBe(true);
    // The documentation link, like the provenance link, stays on the sample and off the subsamples.
    for (const subSample of withDoc.newSample.subSamples) {
      expect(subSample.extraFields).toEqual([]);
    }
  });
});

describe("buildOperationRequest (operation-specific fields)", () => {
  it("adds a text field and storage temperature when the effect declares them", () => {
    const cryo: InventoryOperation = {
      key: "cryopreserve",
      labelKey: "operations.cryopreserve.label",
      minSelected: 1,
      maxSelected: 1,
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

    const request = buildOperationRequest({ operation: cryo, values, origin, resolveLabel, templateId: null });

    expect(request.newSample.storageTempMin).toEqual({ numericValue: -196, unitId: 8 });
    expect(request.newSample.storageTempMax).toEqual({ numericValue: -196, unitId: 8 });
    const textField = request.newSample.extraFields.find((f) => f.type === "text");
    expect(textField).toMatchObject({ content: "DMSO 10%" });
    // text fields live on the sample only, not on the subsamples
    expect(request.newSample.subSamples[0].extraFields.every((f) => f.type === "link")).toBe(true);
  });
});

// An operation with no amountTakenFrom (e.g. Passage) must NOT send an empty origins array - the
// backend rejects that. It sends the origin with a zero amount (a no-op decrement), so the origin
// is still linked and permission-checked but its quantity is unchanged. See adr/0002.
describe("buildOperationRequest (operation that does not decrement the origin)", () => {
  const passageOperation: InventoryOperation = {
    key: "passage",
    labelKey: "operations.passage.label",
    minSelected: 1,
    maxSelected: 1,
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
    origin,
    resolveLabel,
    templateId: null,
  });

  it("sends the origin with a zero amount taken (not an empty origins array)", () => {
    expect(request.origins).toEqual([{ id: 100, globalId: "SS100", amountTaken: { numericValue: 0, unitId: 3 } }]);
  });
});
