import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor, cleanup } from "@testing-library/react";

const { mockAxiosGet } = vi.hoisted(() => ({ mockAxiosGet: vi.fn() }));

vi.mock("@/common/axios", () => ({
  default: { get: mockAxiosGet },
}));

import useReferencingInventoryItems from "../useReferencingInventoryItems";

describe("useReferencingInventoryItems", () => {
  beforeEach(() => {
    mockAxiosGet.mockReset();
  });
  afterEach(cleanup);

  it("requests the generic referencingItems endpoint with the record's global id", async () => {
    mockAxiosGet.mockResolvedValue({ data: { referencingItems: [] } });

    const { result } = renderHook(() => useReferencingInventoryItems("GL5"));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(mockAxiosGet).toHaveBeenCalledWith(
      "/workspace/getReferencingInventoryItems/GL5",
    );
  });

  it("maps referencing items into rows with a linkable inventory record", async () => {
    mockAxiosGet.mockResolvedValue({
      data: {
        referencingItems: [
          {
            sourceGlobalId: "SA1",
            sourceName: "My sample",
            sourceType: "SAMPLE",
            relationType: "IsPartOf",
            versionPin: null,
            modifiedAt: null,
          },
        ],
      },
    });

    const { result } = renderHook(() => useReferencingInventoryItems("GL5"));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.items).toHaveLength(1);
    const row = result.current.items[0];
    expect(row.globalId).toBe("SA1");
    expect(row.name).toBe("My sample");
    expect(row.relationType).toBe("IsPartOf");
    expect(row.linkableRecord.iconName).toBe("sample");
    expect(row.linkableRecord.permalinkURL).toBe("/globalId/SA1");
  });

  it("renders a row whose relationType is missing rather than dropping it", async () => {
    // the relation column is nullable on the server; the legacy panel renders
    // such rows with a blank relation and this hook must agree
    mockAxiosGet.mockResolvedValue({
      data: {
        referencingItems: [
          {
            sourceGlobalId: "SA2",
            sourceName: "No relation",
            sourceType: "SAMPLE",
            versionPin: null,
            modifiedAt: null,
          },
        ],
      },
    });

    const { result } = renderHook(() => useReferencingInventoryItems("GL5"));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.items).toHaveLength(1);
    expect(result.current.items[0].globalId).toBe("SA2");
    expect(result.current.items[0].relationType).toBe("");
  });

  it("does not call the endpoint and reports empty when given a null global id", async () => {
    const { result } = renderHook(() => useReferencingInventoryItems(null));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(mockAxiosGet).not.toHaveBeenCalled();
    expect(result.current.items).toHaveLength(0);
  });

  it("surfaces an error message when the request fails", async () => {
    mockAxiosGet.mockRejectedValue(new Error("boom"));

    const { result } = renderHook(() => useReferencingInventoryItems("GL5"));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.errorMessage).toMatch(/related inventory items/i);
    expect(result.current.items).toHaveLength(0);
  });
});
