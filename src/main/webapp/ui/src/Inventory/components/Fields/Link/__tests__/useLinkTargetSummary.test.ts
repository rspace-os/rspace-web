import { cleanup, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { mockGet } = vi.hoisted(() => ({ mockGet: vi.fn() }));

vi.mock("@/common/InvApiService", () => ({
  default: { get: mockGet },
}));

import useLinkTargetSummary from "../useLinkTargetSummary";

describe("useLinkTargetSummary", () => {
  beforeEach(() => {
    mockGet.mockReset();
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
    mockGet.mockResolvedValue({
      data: { globalId: null, name: "My S3", type: "FILESTORE_ITEM", deleted: false, readable: true },
    });

    const { result } = renderHook(() =>
      useLinkTargetSummary("", { filestoreId: 12, remotePath: "/XRD/run 1+2#final.dat" }),
    );

    await waitFor(() => expect(result.current).not.toBeNull());
    expect(mockGet).toHaveBeenCalledTimes(1);
    expect(mockGet).toHaveBeenCalledWith(
      "linkTargets/filestores/12/summary?remotePath=%2FXRD%2Frun%201%2B2%23final.dat",
    );
    expect(result.current?.name).toBe("My S3");
  });

  it("asks nothing when the link has neither kind of target", () => {
    const { result } = renderHook(() => useLinkTargetSummary("", null));

    expect(result.current).toBeNull();
    expect(mockGet).not.toHaveBeenCalled();
  });
});
