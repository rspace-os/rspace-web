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
});
