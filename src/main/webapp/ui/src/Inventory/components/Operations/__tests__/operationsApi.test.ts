import { renderHook } from "@testing-library/react";
import type React from "react";
import { beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { createRealI18nWrapper } from "@/__tests__/helpers/realI18n";
import common from "@/modules/common/i18n/locales/en-US/common.json";
import inventory from "@/modules/common/i18n/locales/en-US/inventory.json";
import type { InventoryOperation } from "../operations";
import { operations } from "../operations";
import { performOperation, placeSubSamples, sampleNameAvailable, useDescribeOperationError } from "../operationsApi";

function operationNamed(key: string): InventoryOperation {
  const operation = operations.find((o) => o.key === key);
  if (!operation) throw new Error(`no operation ${key}`);
  return operation;
}

const query = vi.fn((_resource: string, _params: URLSearchParams) =>
  Promise.resolve({ data: { valid: true } as { valid?: boolean; message?: string } }),
);
const post = vi.fn((_resource: string, _body: unknown) => Promise.resolve({ data: null as unknown }));
const bulk = vi.fn((_records: unknown, _operationType: string, _rollbackOnError: boolean) =>
  Promise.resolve({ data: { errorCount: 0, successCount: 1, results: [] } as unknown }),
);
vi.mock("@/common/InvApiService", () => ({
  default: {
    query: (resource: string, params: URLSearchParams) => query(resource, params),
    post: (resource: string, body: unknown) => post(resource, body),
    bulk: (records: unknown, operationType: string, rollbackOnError: boolean) =>
      bulk(records, operationType, rollbackOnError),
  },
}));

describe("sampleNameAvailable", () => {
  beforeEach(() => query.mockClear());

  it("reports a free name as available via the exact validateNameForNewSample endpoint", async () => {
    query.mockResolvedValueOnce({ data: { valid: true } });
    expect(await sampleNameAvailable("Blood dna")).toBe(true);
    const [resource, params] = query.mock.calls[0];
    expect(resource).toBe("samples/validateNameForNewSample");
    expect(params.get("name")).toBe("Blood dna");
  });

  it("reports an already-taken name as unavailable", async () => {
    query.mockResolvedValueOnce({ data: { valid: false, message: "There is already a sample named Blood dna" } });
    expect(await sampleNameAvailable("Blood dna")).toBe(false);
  });

  it("treats a blank name as available without querying", async () => {
    expect(await sampleNameAvailable("   ")).toBe(true);
    expect(query).not.toHaveBeenCalled();
  });

  it("degrades to available if the check fails (dedup is a nicety, never a blocker)", async () => {
    query.mockRejectedValueOnce(new Error("network"));
    expect(await sampleNameAvailable("Blood dna")).toBe(true);
  });
});

describe("performOperation", () => {
  beforeEach(() => post.mockClear());

  it("posts to the operation's own endpoint", async () => {
    post.mockResolvedValueOnce({ data: { sample: null } });
    const body = { origin: { globalId: "SS100" } };

    await performOperation(operationNamed("cryopreserve"), body);

    expect(post.mock.calls[0]).toEqual(["operations/cryopreserve", body]);
  });

  it("unwraps the created sample from the response envelope", async () => {
    const created = { id: 7, globalId: "SA7", name: "Derived" };
    post.mockResolvedValueOnce({ data: { sample: created } });

    expect(await performOperation(operationNamed("derive"), {})).toEqual(created);
  });

  it("reports no sample for a terminal operation, whatever shape the empty body takes", async () => {
    for (const empty of [{ sample: null }, null, ""]) {
      post.mockResolvedValueOnce({ data: empty });
      expect(await performOperation(operationNamed("destroy"), {})).toBeNull();
    }
  });
});

describe("useDescribeOperationError", () => {
  const operation = {
    key: "aliquot",
    inputs: [{ key: "sampleName", type: "text", labelKey: "operations.fields.sampleName" }],
    effect: { links: [] },
  } as unknown as InventoryOperation;
  let InEnglish: React.ComponentType<{ children: React.ReactNode }>;
  beforeAll(async () => {
    InEnglish = await createRealI18nWrapper({ resources: { common, inventory }, defaultNS: "common" });
  });
  const describeError = (error: unknown, op: InventoryOperation = operation): Array<string> =>
    renderHook(() => useDescribeOperationError(), { wrapper: InEnglish }).result.current(error, op);
  const rejectedWith = (first: string) => ({ response: { data: { message: "Errors detected: 1", errors: [first] } } });

  it("words an error on a declared input with the input's label instead of its bare key", () => {
    expect(describeError(rejectedWith("sampleName: Required by this operation."))).toEqual([
      "New sample name: Required by this operation.",
    ]);
  });

  it("strips a dotted origin path but keeps which origin it was, through the catalog", () => {
    expect(
      describeError(rejectedWith("origins[0].amountTaken: Cannot take more from an origin than it currently holds")),
    ).toEqual(["Cannot take more from an origin than it currently holds (origin 1)"]);
  });

  it("leaves a leading word that is not one of the operation's inputs alone", () => {
    expect(describeError(rejectedWith("Warning: stock is low"))).toEqual(["Warning: stock is low"]);
  });

  it("falls back to the response message when there is no field-scoped error", () => {
    const conflict = { response: { data: { message: "The subsample's quantity changed", errors: [""] } } };
    expect(describeError(conflict)).toEqual(["The subsample's quantity changed"]);
  });

  it("strips a dotted `inputs.<key>` path to the bare reason, without the input's label", () => {
    const withCount = {
      ...operation,
      inputs: [...operation.inputs, { key: "count", type: "integer", labelKey: "operations.fields.count" }],
    } as unknown as InventoryOperation;
    expect(describeError(rejectedWith("inputs.count: must be at most 100"), withCount)).toEqual([
      "must be at most 100",
    ]);
  });

  it("returns every rejection separately, each worded for its own field or origin", () => {
    const twoErrors = {
      response: {
        data: {
          message: "Errors detected: 2",
          errors: ["origins[0].amountTaken: Too much", "sampleName: Required by this operation."],
        },
      },
    };
    expect(describeError(twoErrors)).toEqual(["Too much (origin 1)", "New sample name: Required by this operation."]);
  });

  it("reports a network failure (no response at all) by the error's own message", () => {
    expect(describeError(new Error("Network Error"))).toEqual(["Network Error"]);
  });

  it("reports a 404 by its message body, like any response without a field-scoped error", () => {
    const notFound = { response: { status: 404, data: { message: "Origin SS100 was not found", errors: [""] } } };
    expect(describeError(notFound)).toEqual(["Origin SS100 was not found"]);
  });

  it("uses the catalog's failure message when there is nothing to describe at all", () => {
    expect(describeError(undefined)).toEqual(["The operation could not be completed"]);
  });
});

describe("placeSubSamples", () => {
  const records = [{ id: 11, type: "SUBSAMPLE" as const, globalId: "SS11", parentContainers: [{ id: 5 }] }];

  it("moves the records in one all-or-nothing bulk request", async () => {
    await placeSubSamples(records);
    expect(bulk).toHaveBeenCalledWith(records, "MOVE", true);
  });

  it("rejects with each record's reasons when the server refuses any of the moves", async () => {
    bulk.mockResolvedValueOnce({
      data: {
        errorCount: 1,
        successCount: 0,
        results: [{ error: { errors: ["Location is already taken"] } }, { record: { globalId: "SS12" } }],
      },
    });
    await expect(placeSubSamples(records)).rejects.toMatchObject({ reasons: ["Location is already taken"] });
  });
});
