import { renderHook } from "@testing-library/react";
import { HttpResponse, http } from "msw/http";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import useFolders from "../useFolders";

vi.mock("../../auth/useOauthToken", () => ({
  default: () => ({ getToken: () => Promise.resolve("test-token") }),
}));

describe("useFolders getFolderTree", () => {
  it("reports whether each folder is a system folder", async () => {
    server.use(
      http.get("/api/v1/folders/tree/7", () =>
        HttpResponse.json({
          totalHits: 1,
          pageNumber: 0,
          records: [{ id: 15, globalId: "GF15", name: "PdfDocuments", type: "FOLDER", systemFolder: true }],
        }),
      ),
    );
    const { result } = renderHook(() => useFolders());

    const tree = await result.current.getFolderTree({ id: 7 });

    expect(tree.records).toEqual([
      { id: 15, globalId: "GF15", name: "PdfDocuments", type: "FOLDER", systemFolder: true },
    ]);
  });
});
