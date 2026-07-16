import { beforeEach, describe, expect, it, vi } from "vitest";
import { sampleNameAvailable } from "../operationsApi";

const query = vi.fn((_resource: string, _params: URLSearchParams) =>
  Promise.resolve({ data: { valid: true } as { valid?: boolean; message?: string } }),
);
vi.mock("@/common/InvApiService", () => ({
  default: {
    query: (resource: string, params: URLSearchParams) => query(resource, params),
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
