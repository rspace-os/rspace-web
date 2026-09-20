import { beforeEach, describe, expect, it, vi } from "vitest";
import { describeOperationError, performOperation, sampleNameAvailable } from "../operationsApi";
import type { InventoryOperation } from "../operationsConfig";
import { operations } from "./testOperations";

function operationNamed(key: string): InventoryOperation {
  const operation = operations.find((o) => o.key === key);
  if (!operation) throw new Error(`no operation ${key}`);
  return operation;
}

const query = vi.fn((_resource: string, _params: URLSearchParams) =>
  Promise.resolve({ data: { valid: true } as { valid?: boolean; message?: string } }),
);
const post = vi.fn((_resource: string, _body: unknown) => Promise.resolve({ data: null as unknown }));
vi.mock("@/common/InvApiService", () => ({
  default: {
    query: (resource: string, params: URLSearchParams) => query(resource, params),
    post: (resource: string, body: unknown) => post(resource, body),
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

describe("describeOperationError", () => {
  const operation = {
    key: "aliquot",
    inputs: [{ key: "sampleName", type: "text", labelKey: "operations.fields.sampleName" }],
    effect: { links: [] },
  } as unknown as InventoryOperation;
  const resolveLabel = (key: string, params?: Record<string, unknown>): string => {
    if (key === "operations.fields.sampleName") return "Sample name";
    if (key === "operations.wizard.fieldReason") return `${String(params?.label)}: ${String(params?.reason)}`;
    if (key === "operations.wizard.originIndex") return `${String(params?.reason)} [ORIGIN ${String(params?.index)}]`;
    return key;
  };
  const rejectedWith = (first: string) => ({ response: { data: { message: "Errors detected: 1", errors: [first] } } });

  it("words an error on a declared input with the input's label instead of its bare key", () => {
    expect(
      describeOperationError(
        rejectedWith("sampleName: Required by this operation."),
        operation,
        resolveLabel,
        "failed",
      ),
    ).toBe("Sample name: Required by this operation.");
  });

  it("strips a dotted origin path but keeps which origin it was, through the catalog", () => {
    expect(
      describeOperationError(
        rejectedWith("origins[0].amountTaken: Cannot take more from an origin than it currently holds"),
        operation,
        resolveLabel,
        "failed",
      ),
    ).toBe("Cannot take more from an origin than it currently holds [ORIGIN 1]");
  });

  it("leaves a leading word that is not one of the operation's inputs alone", () => {
    expect(describeOperationError(rejectedWith("Warning: stock is low"), operation, resolveLabel, "failed")).toBe(
      "Warning: stock is low",
    );
  });

  it("falls back to the response message when there is no field-scoped error", () => {
    const conflict = { response: { data: { message: "The subsample's quantity changed", errors: [""] } } };
    expect(describeOperationError(conflict, operation, resolveLabel, "failed")).toBe(
      "The subsample's quantity changed",
    );
  });

  it("strips a dotted `inputs.<key>` path to the bare reason, without the input's label", () => {
    const withCount = {
      ...operation,
      inputs: [...operation.inputs, { key: "count", type: "integer", labelKey: "operations.fields.count" }],
    } as unknown as InventoryOperation;
    const labels = (key: string, params?: Record<string, unknown>): string =>
      key === "operations.fields.count" ? "Number of subsamples" : resolveLabel(key, params);
    expect(describeOperationError(rejectedWith("inputs.count: must be at most 100"), withCount, labels, "failed")).toBe(
      "must be at most 100",
    );
  });

  it("reports a network failure (no response at all) by the error's own message", () => {
    expect(describeOperationError(new Error("Network Error"), operation, resolveLabel, "failed")).toBe("Network Error");
  });

  it("reports a 404 by its message body, like any response without a field-scoped error", () => {
    const notFound = { response: { status: 404, data: { message: "Origin SS100 was not found", errors: [""] } } };
    expect(describeOperationError(notFound, operation, resolveLabel, "failed")).toBe("Origin SS100 was not found");
  });

  it("uses the fallback when there is nothing to describe at all", () => {
    expect(describeOperationError(undefined, operation, resolveLabel, "failed")).toBe("failed");
  });
});
