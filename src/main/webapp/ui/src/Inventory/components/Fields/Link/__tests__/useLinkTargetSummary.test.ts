import { cleanup, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { mockGet, mockQuery } = vi.hoisted(() => ({ mockGet: vi.fn(), mockQuery: vi.fn() }));

vi.mock("@/common/InvApiService", () => ({
  default: { get: mockGet, query: mockQuery },
}));

import useLinkTargetSummary from "../useLinkTargetSummary";

describe("useLinkTargetSummary", () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockQuery.mockReset();
  });
  afterEach(cleanup);

  it("resolves the target's current state from the lazy summary endpoint", async () => {
    mockGet.mockResolvedValue({
      data: {
        globalId: "SD5",
        name: "my doc",
        type: "DOCUMENT",
        deleted: true,
        readable: true,
      },
    });

    const { result } = renderHook(() => useLinkTargetSummary("SD5"));

    await waitFor(() => expect(result.current).not.toBeNull());
    expect(mockGet).toHaveBeenCalledWith("linkTargets/SD5/summary");
    expect(result.current?.deleted).toBe(true);
    expect(result.current?.name).toBe("my doc");
    expect(result.current?.readable).toBe(true);
  });

  it("degrades to null on any resolution failure", async () => {
    // network failure and malformed ids look the same to the card: no pill,
    // Open kept, exactly as before the summary existed. (Missing and
    // unreadable records are not failures: they resolve to a redacted
    // readable:false summary.)
    mockGet.mockRejectedValue(new Error("404"));

    const { result } = renderHook(() => useLinkTargetSummary("SD404"));

    await waitFor(() => expect(mockGet).toHaveBeenCalled());
    expect(result.current).toBeNull();
  });

  it("asks the filestore form of the endpoint for a filestore item, with the path encoded", async () => {
    mockQuery.mockResolvedValue({
      data: { globalId: null, name: "My S3", type: "FILESTORE_ITEM", deleted: false, readable: true },
    });

    const { result } = renderHook(() =>
      useLinkTargetSummary("", { filestoreId: 12, remotePath: "/XRD/run 1+2#final.dat" }),
    );

    await waitFor(() => expect(result.current).not.toBeNull());
    expect(mockQuery).toHaveBeenCalledTimes(1);
    const [resource, params] = mockQuery.mock.calls[0] as [string, URLSearchParams];
    expect(resource).toBe("linkTargets/filestores/12/summary");
    expect(params.toString()).toBe("remotePath=%2FXRD%2Frun+1%2B2%23final.dat");
    expect(mockGet).not.toHaveBeenCalled();
    expect(result.current?.name).toBe("My S3");
  });

  it("asks once when the caller rebuilds an equal filestore target on every render", async () => {
    mockQuery.mockResolvedValue({
      data: { globalId: null, name: "My S3", type: "FILESTORE_ITEM", deleted: false, readable: true },
    });

    const { result, rerender } = renderHook(() => useLinkTargetSummary("", { filestoreId: 12, remotePath: "/a" }));
    await waitFor(() => expect(result.current).not.toBeNull());
    rerender();

    expect(result.current?.name).toBe("My S3");
    expect(mockQuery).toHaveBeenCalledTimes(1);
  });

  it("asks nothing when the link has neither kind of target", () => {
    const { result } = renderHook(() => useLinkTargetSummary("", null));

    expect(result.current).toBeNull();
    expect(mockGet).not.toHaveBeenCalled();
    expect(mockQuery).not.toHaveBeenCalled();
  });
});
