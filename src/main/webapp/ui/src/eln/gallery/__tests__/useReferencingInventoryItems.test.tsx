import { cleanup, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { mockAxiosGet } = vi.hoisted(() => ({ mockAxiosGet: vi.fn() }));

vi.mock("@/common/axios", () => ({
  default: { get: mockAxiosGet },
}));

import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import useReferencingInventoryItems from "../useReferencingInventoryItems";

type Row = Record<string, unknown>;

/**
 * The hook fans out to two endpoints for a gallery file: the links (referencing) endpoint and the
 * attachments endpoint. Route each mocked response by URL so a test can supply link rows and
 * attachment rows independently.
 */
function mockEndpoints({ links = [], attachments = [] }: { links?: Array<Row>; attachments?: Array<Row> }) {
  mockAxiosGet.mockImplementation((url: string) => {
    if (url.includes("getAttachingInventoryItems")) {
      return Promise.resolve({ data: { referencingItems: attachments } });
    }
    return Promise.resolve({ data: { referencingItems: links } });
  });
}

describe("useReferencingInventoryItems", () => {
  beforeEach(() => {
    mockAxiosGet.mockReset();
  });
  afterEach(cleanup);

  it("requests the links endpoint with the record's global id", async () => {
    mockEndpoints({});

    const { result } = renderHook(() => useReferencingInventoryItems("GL5"));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(mockAxiosGet).toHaveBeenCalledWith("/workspace/getReferencingInventoryItems/GL5");
  });

  it("also requests the attachments endpoint for a gallery-file target", async () => {
    mockEndpoints({});

    const { result } = renderHook(() => useReferencingInventoryItems("GL5"));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(mockAxiosGet).toHaveBeenCalledWith("/workspace/getAttachingInventoryItems/GL5");
  });

  it("does not request the attachments endpoint for a non-gallery target", async () => {
    // only gallery files can be attached; firing the attachments endpoint for an ELN/inventory
    // target would 404
    mockEndpoints({});

    const { result } = renderHook(() => useReferencingInventoryItems("SD123"));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(mockAxiosGet).toHaveBeenCalledWith("/workspace/getReferencingInventoryItems/SD123");
    expect(mockAxiosGet).not.toHaveBeenCalledWith(expect.stringContaining("getAttachingInventoryItems"));
  });

  it("url-encodes the global id in the request path", async () => {
    // a global id is normally path-safe, but encoding guards against any
    // future id format that introduces characters that would otherwise alter
    // the request path
    mockEndpoints({});

    const { result } = renderHook(() => useReferencingInventoryItems("SA1 2#3"));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(mockAxiosGet).toHaveBeenCalledWith("/workspace/getReferencingInventoryItems/SA1%202%233");
  });

  it("maps link items into rows with a linkable inventory record", async () => {
    mockEndpoints({
      links: [
        {
          sourceGlobalId: "SA1",
          sourceName: "My sample",
          sourceType: "SAMPLE",
          relationType: "IsPartOf",
          versionPin: null,
          modifiedAt: null,
        },
      ],
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

  it("merges attachment rows and labels them Attachment", async () => {
    // attachments arrive from a different endpoint and carry no DataCite relation type; the hook
    // marks their Relation column so they read distinctly from links in the shared grid
    mockEndpoints({
      links: [
        {
          sourceGlobalId: "SA1",
          sourceName: "Linked sample",
          sourceType: "SAMPLE",
          relationType: "IsPartOf",
        },
      ],
      attachments: [
        {
          sourceGlobalId: "IC7",
          sourceName: "Box A",
          sourceType: "CONTAINER",
          relationType: null,
        },
      ],
    });

    const { result } = renderHook(() => useReferencingInventoryItems("GL5"));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.items).toHaveLength(2);
    const attachmentRow = result.current.items.find((i) => i.globalId === "IC7");
    expect(attachmentRow?.relationType).toContain("attachment");
    const linkRow = result.current.items.find((i) => i.globalId === "SA1");
    expect(linkRow?.relationType).toBe("IsPartOf");
  });

  it("keeps one row per attachment connection without dedup", async () => {
    // the same item attaching the same gallery file twice yields two rows, matching the backend
    mockEndpoints({
      attachments: [
        { sourceGlobalId: "SA1", sourceName: "My sample", sourceType: "SAMPLE", relationType: null },
        { sourceGlobalId: "SA1", sourceName: "My sample", sourceType: "SAMPLE", relationType: null },
      ],
    });

    const { result } = renderHook(() => useReferencingInventoryItems("GL5"));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.items).toHaveLength(2);
  });

  it("renders a link row whose relationType is missing rather than dropping it", async () => {
    // the relation column is nullable on the server; the legacy panel renders
    // such rows with a blank relation and this hook must agree
    mockEndpoints({
      links: [
        {
          sourceGlobalId: "SA2",
          sourceName: "No relation",
          sourceType: "SAMPLE",
          versionPin: null,
          modifiedAt: null,
        },
      ],
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

  it("surfaces an error message when the links request fails", async () => {
    const restoreConsole = silenceConsole(["error"], [/.*/]);
    mockAxiosGet.mockRejectedValue(new Error("boom"));

    const { result } = renderHook(() => useReferencingInventoryItems("GL5"));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.errorMessage).toContain("gallery:referencingInventoryItems.loadFailed");
    expect(result.current.items).toHaveLength(0);
    restoreConsole();
  });

  it("renders links when only the attachments endpoint fails", async () => {
    // a failing attachments lookup is a supplementary back-reference; it must not blank the links
    // that loaded fine, so the section degrades to links-only with no error
    const restoreConsole = silenceConsole(["error"], [/.*/]);
    mockAxiosGet.mockImplementation((url: string) => {
      if (url.includes("getAttachingInventoryItems")) {
        return Promise.reject(new Error("attachments 500"));
      }
      return Promise.resolve({
        data: {
          referencingItems: [
            { sourceGlobalId: "SA1", sourceName: "Linked sample", sourceType: "SAMPLE", relationType: "IsPartOf" },
          ],
        },
      });
    });

    const { result } = renderHook(() => useReferencingInventoryItems("GL5"));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.errorMessage).toBeNull();
    expect(result.current.items).toHaveLength(1);
    expect(result.current.items[0].globalId).toBe("SA1");
    restoreConsole();
  });

  it("drops a row missing a required field rather than failing the whole payload", async () => {
    // the two endpoints' payloads are merged; one malformed row must not blank the grid
    mockEndpoints({
      links: [
        { sourceGlobalId: "SA1", sourceName: "Good row", sourceType: "SAMPLE", relationType: "IsPartOf" },
        { sourceGlobalId: "SA2", sourceType: "SAMPLE", relationType: "IsPartOf" }, // missing sourceName
      ],
    });

    const { result } = renderHook(() => useReferencingInventoryItems("GL5"));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.items).toHaveLength(1);
    expect(result.current.items[0].globalId).toBe("SA1");
  });

  it("ignores a stale response after the global id changes", async () => {
    // clicking between gallery files must not let an earlier in-flight response overwrite the
    // current target's rows
    const deferred: Array<(v: unknown) => void> = [];
    mockAxiosGet.mockImplementation(
      () =>
        new Promise((resolve) => {
          deferred.push(resolve);
        }),
    );

    const initialProps: { id: string } = { id: "SD1" };
    const { result, rerender } = renderHook(({ id }: { id: string }) => useReferencingInventoryItems(id), {
      initialProps,
    });
    // SD1 and SD2 are non-gallery, so each fires exactly one (links) request
    await waitFor(() => expect(deferred).toHaveLength(1));
    rerender({ id: "SD2" });
    await waitFor(() => expect(deferred).toHaveLength(2));

    // resolve the current target (SD2) first, then the stale SD1 request
    deferred[1]({
      data: { referencingItems: [{ sourceGlobalId: "SA2", sourceName: "current", sourceType: "SAMPLE" }] },
    });
    deferred[0]({ data: { referencingItems: [{ sourceGlobalId: "SA1", sourceName: "stale", sourceType: "SAMPLE" }] } });

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.items).toHaveLength(1);
    expect(result.current.items[0].globalId).toBe("SA2");
  });

  it("clears a stale error when the global id becomes null", async () => {
    // a failure for one record must not leave its error showing once the hook
    // is pointed at no record at all (the early-return path)
    const restoreConsole = silenceConsole(["error"], [/.*/]);
    mockAxiosGet.mockRejectedValue(new Error("boom"));

    const initialProps: { id: string | null } = { id: "GL5" };
    const { result, rerender } = renderHook(({ id }: { id: string | null }) => useReferencingInventoryItems(id), {
      initialProps,
    });

    await waitFor(() => expect(result.current.errorMessage).not.toBeNull());

    rerender({ id: null });

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.errorMessage).toBeNull();
    expect(result.current.items).toHaveLength(0);
    restoreConsole();
  });
});
