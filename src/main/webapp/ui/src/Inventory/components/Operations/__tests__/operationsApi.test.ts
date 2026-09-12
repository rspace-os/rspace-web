import { beforeEach, describe, expect, it, vi } from "vitest";
import { describeOperationError, performOperation, sampleNameAvailable } from "../operationsApi";
import type { InventoryOperation } from "../operationsConfig";
import type { OperationInputsRequest } from "../types";

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

  it("normalises a terminal operation's empty response body to null", async () => {
    // A terminal operation (Destroy) creates no sample and returns an empty body, which Axios surfaces
    // as "" rather than null; performOperation must still resolve to null (its declared return type).
    post.mockResolvedValueOnce({ data: "" });
    expect(await performOperation({} as OperationInputsRequest)).toBeNull();
  });

  it("returns the created sample for a producing operation", async () => {
    const created = { id: 7, globalId: "SA7", name: "Derived" };
    post.mockResolvedValueOnce({ data: created });
    expect(await performOperation({} as OperationInputsRequest)).toEqual(created);
  });
});

// The inputs shape names a rejected input by its bare key ("sampleName: ..."), never a dotted path
// (DevDocs/adr/0007, M4), so the wizard swaps the key for the label it shows.
describe("describeOperationError", () => {
  const operation = {
    key: "aliquot",
    inputs: [{ key: "sampleName", type: "text", labelKey: "operations.fields.sampleName" }],
    effect: { links: [] },
  } as unknown as InventoryOperation;
  const resolveLabel = (key: string): string => (key === "operations.fields.sampleName" ? "Sample name" : key);
  const rejectedWith = (first: string) => ({ response: { data: { message: "Errors detected: 1", errors: [first] } } });

  it("words an error on a declared input with the input's label instead of its bare key", () => {
    expect(
      describeOperationError(
        rejectedWith("sampleName: This operation requires [sampleName]."),
        operation,
        resolveLabel,
        "failed",
      ),
    ).toBe("Sample name: This operation requires [sampleName].");
  });

  it("strips a dotted origin path as before", () => {
    expect(
      describeOperationError(
        rejectedWith("origins[0].amountTaken: Cannot take more from an origin than it currently holds"),
        operation,
        resolveLabel,
        "failed",
      ),
    ).toBe("Cannot take more from an origin than it currently holds");
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
});
