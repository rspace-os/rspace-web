import "@/__tests__/__mocks__/useOauthToken";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw/http";
import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";
import { captureRequests } from "@/__tests__/mswRequestCapture";
import { server } from "@/__tests__/mswServer";
import { useFilestoreLinkedDocumentsQuery } from "../queries";
import { Filestore, type GalleryFile, RemoteFile } from "../useGalleryListing";

const ENDPOINT = "/api/v1/gallery/filestores/:filestoreId/linkedDocuments";

function filestore(filesystemType = "S3"): Filestore {
  return new Filestore({
    id: 42,
    name: "My Filestore",
    filesystemId: 10,
    filesystemName: "My Filesystem",
    filesystemType,
    canWrite: true,
    ownerName: "gallery:unknownOwner",
  });
}

function remoteFile({
  folder = false,
  path = [filestore()],
  remotePath = "/XRD/run.dat",
}: {
  folder?: boolean;
  path?: ReadonlyArray<GalleryFile>;
  remotePath?: string;
} = {}): RemoteFile {
  return new RemoteFile({
    nfsId: null,
    name: remotePath.split("/").at(-1) ?? "",
    folder,
    fileSize: 1024,
    modificationDate: undefined,
    path,
    logicPath: `42:${remotePath}`,
    token: "",
    ownerName: "gallery:unknownOwner",
  });
}

function renderQuery(file: RemoteFile) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderHook(() => useFilestoreLinkedDocumentsQuery(file), {
    wrapper: ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    ),
  });
}

describe("useFilestoreLinkedDocumentsQuery", () => {
  it("asks the filestores API for the item with the bearer token and the raw remote path", async () => {
    // spaces, "+", "#" and "%" must survive the round trip as the remotePath parameter
    const requests = captureRequests("get", ENDPOINT, () => HttpResponse.json([]));

    const { result } = renderQuery(remoteFile({ remotePath: "/XRD/run 1+2#50%.dat" }));

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(requests).toHaveLength(1);
    const url = new URL(requests[0].url);
    expect(url.pathname).toBe("/api/v1/gallery/filestores/42/linkedDocuments");
    expect(url.searchParams.get("remotePath")).toBe("/XRD/run 1+2#50%.dat");
    expect(requests[0].headers.get("Authorization")).toBe("Bearer token");
  });

  it("splits readable rows from owner-only private placeholders", async () => {
    server.use(
      http.get(ENDPOINT, () =>
        HttpResponse.json([
          { id: 11, globalId: "SD11", name: "Doc one", ownerFullName: "Ada Lovelace" },
          { ownerFullName: "Grace Hopper" },
          { ownerFullName: "Grace Hopper" },
          { id: 12, ownerFullName: "Grace Hopper" },
          {}, // a placeholder with no owner name is still a private document
        ]),
      ),
    );

    const { result } = renderQuery(remoteFile());

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.documents).toEqual([
      expect.objectContaining({ id: 11, globalId: "SD11", name: "Doc one", permalinkHref: "/globalId/SD11" }),
    ]);
    expect(result.current.data?.privateByOwner).toEqual([
      { ownerFullName: "Grace Hopper", count: 3 },
      { ownerFullName: "", count: 1 },
    ]);
  });

  it("lists documents only: rows of another type are Inventory items and are dropped", async () => {
    server.use(
      http.get(ENDPOINT, () =>
        HttpResponse.json([
          { id: 11, globalId: "SD11", name: "Doc one", ownerFullName: "Ada Lovelace", type: "DOCUMENT" },
          { id: 7, globalId: "SA7", name: "Buffer A", ownerFullName: "Ada Lovelace", type: "SAMPLE" },
          { ownerFullName: "Grace Hopper", type: "INSTRUMENT" },
          { ownerFullName: "Grace Hopper", type: "DOCUMENT" },
        ]),
      ),
    );

    const { result } = renderQuery(remoteFile());

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.documents.map((d) => d.globalId)).toEqual(["SD11"]);
    expect(result.current.data?.privateByOwner).toEqual([{ ownerFullName: "Grace Hopper", count: 1 }]);
  });

  it.each([
    ["a folder", remoteFile({ folder: true, remotePath: "/XRD" })],
    ["an item in an iRODS filestore", remoteFile({ path: [filestore("IRODS")] })],
  ])("queries for %s", async (_label, file) => {
    const requests = captureRequests("get", ENDPOINT, () => HttpResponse.json([]));

    const { result } = renderQuery(file);

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(requests).toHaveLength(1);
    expect(new URL(requests[0].url).searchParams.get("remotePath")).toBe(file.remotePath);
  });

  it("asks again when another item of the same filestore is selected", async () => {
    const requests = captureRequests("get", ENDPOINT, () => HttpResponse.json([]));
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { result, rerender } = renderHook(({ file }) => useFilestoreLinkedDocumentsQuery(file), {
      initialProps: { file: remoteFile({ remotePath: "/XRD/a.dat" }) },
      wrapper: ({ children }: { children: ReactNode }) => (
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      ),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    rerender({ file: remoteFile({ remotePath: "/XRD/b.dat" }) });

    await waitFor(() => expect(requests).toHaveLength(2));
    expect(requests.map((r) => new URL(r.url).searchParams.get("remotePath"))).toEqual(["/XRD/a.dat", "/XRD/b.dat"]);
  });

  it("stays idle for a remote file whose path holds no filestore", async () => {
    // No MSW handler is registered: a request would fail the strict unhandled-request check.
    const { result } = renderQuery(remoteFile({ folder: true, path: [] }));

    await waitFor(() => expect(result.current.fetchStatus).toBe("idle"));
    expect(result.current.isError).toBe(false);
    expect(result.current.data).toBeUndefined();
  });
});
