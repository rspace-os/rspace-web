import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor, cleanup } from "@testing-library/react";

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
      data: { globalId: "SD5", name: "my doc", type: "DOCUMENT", deleted: true },
    });

    const { result } = renderHook(() => useLinkTargetSummary("SD5"));

    await waitFor(() => expect(result.current).not.toBeNull());
    expect(mockGet).toHaveBeenCalledWith("linkTargets/SD5/summary");
    expect(result.current?.deleted).toBe(true);
    expect(result.current?.name).toBe("my doc");
  });

  it("degrades to null on any resolution failure", async () => {
    // missing record, no permission and network failure all look the same to
    // the card: no pill, Open kept, exactly as before the summary existed
    mockGet.mockRejectedValue(new Error("404"));

    const { result } = renderHook(() => useLinkTargetSummary("SD404"));

    await waitFor(() => expect(mockGet).toHaveBeenCalled());
    expect(result.current).toBeNull();
  });
});
