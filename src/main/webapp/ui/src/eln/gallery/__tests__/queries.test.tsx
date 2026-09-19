import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";

import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { server } from "@/__tests__/mswServer";
import { useReferencingInventoryItemsQuery } from "../queries";

type Row = Record<string, unknown>;
const LINKS_ENDPOINT = "/workspace/getReferencingInventoryItems/:globalId";
const ATTACHMENTS_ENDPOINT = "/workspace/getAttachingInventoryItems/:globalId";

function createQueryWrapper(queryClient: QueryClient) {
  return function QueryWrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

function renderReferencingItems(globalId: string | null, queryClient = createQueryClient()) {
  return renderHook(() => useReferencingInventoryItemsQuery({ globalId }), {
    wrapper: createQueryWrapper(queryClient),
  });
}

/**
 * The hook fans out to two endpoints for a gallery file: the links (referencing) endpoint and the
 * attachments endpoint. Route each mocked response by URL so a test can supply link rows and
 * attachment rows independently.
 */
function mockEndpoints({
  links = [],
  attachments = [],
  linksResponse,
  attachmentsResponse,
}: {
  links?: Array<Row>;
  attachments?: Array<Row>;
  linksResponse?: () => Response;
  attachmentsResponse?: () => Response;
} = {}): Request[] {
  const requests: Request[] = [];
  server.use(
    http.get(LINKS_ENDPOINT, ({ request }) => {
      requests.push(request.clone());
      return linksResponse?.() ?? HttpResponse.json({ referencingItems: links });
    }),
    http.get(ATTACHMENTS_ENDPOINT, ({ request }) => {
      requests.push(request.clone());
      return attachmentsResponse?.() ?? HttpResponse.json({ referencingItems: attachments });
    }),
  );
  return requests;
}

function requestedPaths(requests: ReadonlyArray<Request>): Array<string> {
  return requests.map((request) => new URL(request.url).pathname);
}

describe("useReferencingInventoryItemsQuery", () => {
  it("requests the links endpoint with the record's global id", async () => {
    const requests = mockEndpoints();

    const { result } = renderReferencingItems("GL5");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(requestedPaths(requests)).toContain("/workspace/getReferencingInventoryItems/GL5");
  });

  it("also requests the attachments endpoint for a gallery-file target", async () => {
    const requests = mockEndpoints();

    const { result } = renderReferencingItems("GL5");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(requestedPaths(requests)).toContain("/workspace/getAttachingInventoryItems/GL5");
  });

  it("reuses a completed request when the other responsive info panel mounts", async () => {
    const requests = mockEndpoints();
    const queryClient = createQueryClient();

    const firstPanel = renderReferencingItems("GL5", queryClient);
    await waitFor(() => expect(firstPanel.result.current.isSuccess).toBe(true));

    const secondPanel = renderReferencingItems("GL5", queryClient);
    await waitFor(() => expect(secondPanel.result.current.isSuccess).toBe(true));

    expect(requests).toHaveLength(2);
  });

  it("does not request the attachments endpoint for a non-gallery target", async () => {
    const requests = mockEndpoints();

    const { result } = renderReferencingItems("SD123");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(requestedPaths(requests)).toEqual(["/workspace/getReferencingInventoryItems/SD123"]);
  });

  it("url-encodes the global id in the request path", async () => {
    const requests = mockEndpoints();

    const { result } = renderReferencingItems("SA1 2#3");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(requestedPaths(requests)).toContain("/workspace/getReferencingInventoryItems/SA1%202%233");
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

    const { result } = renderReferencingItems("GL5");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    const row = result.current.data?.[0];
    expect(row).toBeDefined();
    if (!row) throw new Error("Expected a referencing item");
    expect(row.globalId).toBe("SA1");
    expect(row.name).toBe("My sample");
    expect(row.relationType).toBe("IsPartOf");
    expect(row.linkableRecord.iconName).toBe("sample");
    expect(row.linkableRecord.permalinkURL).toBe("/globalId/SA1");
  });

  it("merges attachment rows and labels them Attachment", async () => {
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

    const { result } = renderReferencingItems("GL5");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(2);
    const attachmentRow = result.current.data?.find((item) => item.globalId === "IC7");
    expect(attachmentRow?.isAttachment).toBe(true);
    const linkRow = result.current.data?.find((item) => item.globalId === "SA1");
    expect(linkRow?.relationType).toBe("IsPartOf");
  });

  it("keeps one row per attachment connection without dedup", async () => {
    mockEndpoints({
      attachments: [
        { sourceGlobalId: "SA1", sourceName: "My sample", sourceType: "SAMPLE", relationType: null },
        { sourceGlobalId: "SA1", sourceName: "My sample", sourceType: "SAMPLE", relationType: null },
      ],
    });

    const { result } = renderReferencingItems("GL5");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(2);
  });

  it("renders a link row whose relationType is missing rather than dropping it", async () => {
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

    const { result } = renderReferencingItems("GL5");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data?.[0]?.globalId).toBe("SA2");
    expect(result.current.data?.[0]?.relationType).toBe("");
  });

  it("does not call the endpoint when given a null global id", () => {
    const requests = mockEndpoints();

    const { result } = renderReferencingItems(null);

    expect(result.current.fetchStatus).toBe("idle");
    expect(requests).toHaveLength(0);
    expect(result.current.data).toBeUndefined();
  });

  it("reports an error when the links request fails", async () => {
    mockEndpoints({
      linksResponse: () => HttpResponse.json(null, { status: 500 }),
    });

    const { result } = renderReferencingItems("GL5");

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.data).toBeUndefined();
  });

  it("renders links when only the attachments endpoint fails", async () => {
    // a failing attachments lookup is a supplementary back-reference; it must not blank the links
    // that loaded fine, so the section degrades to links-only with no error
    const restoreConsole = silenceConsole(["error"], [/.*/]);
    mockEndpoints({
      links: [{ sourceGlobalId: "SA1", sourceName: "Linked sample", sourceType: "SAMPLE", relationType: "IsPartOf" }],
      attachmentsResponse: () => HttpResponse.json(null, { status: 500 }),
    });

    const { result } = renderReferencingItems("GL5");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data?.[0]?.globalId).toBe("SA1");
    restoreConsole();
  });

  it("drops a row missing a required field rather than failing the whole payload", async () => {
    mockEndpoints({
      links: [
        { sourceGlobalId: "SA1", sourceName: "Good row", sourceType: "SAMPLE", relationType: "IsPartOf" },
        { sourceGlobalId: "SA2", sourceType: "SAMPLE", relationType: "IsPartOf" }, // missing sourceName
      ],
    });

    const { result } = renderReferencingItems("GL5");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data?.[0]?.globalId).toBe("SA1");
  });

  it("does not display a response for the previous global id", async () => {
    // clicking between gallery files must not let an earlier in-flight response overwrite the
    // current target's rows
    const deferred: Array<(rows: Array<Row>) => void> = [];
    server.use(
      http.get(
        LINKS_ENDPOINT,
        () =>
          new Promise<Response>((resolve) => {
            deferred.push((rows) => resolve(HttpResponse.json({ referencingItems: rows })));
          }),
      ),
    );

    const initialProps: { id: string } = { id: "SD1" };
    const { result, rerender } = renderHook(
      ({ id }: { id: string }) => useReferencingInventoryItemsQuery({ globalId: id }),
      {
        initialProps,
        wrapper: createQueryWrapper(createQueryClient()),
      },
    );
    // SD1 and SD2 are non-gallery, so each fires exactly one (links) request
    await waitFor(() => expect(deferred).toHaveLength(1));
    rerender({ id: "SD2" });
    await waitFor(() => expect(deferred).toHaveLength(2));

    // resolve the current target (SD2) first, then the stale SD1 request
    deferred[1]([{ sourceGlobalId: "SA2", sourceName: "current", sourceType: "SAMPLE" }]);
    deferred[0]([{ sourceGlobalId: "SA1", sourceName: "stale", sourceType: "SAMPLE" }]);

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data?.[0]?.globalId).toBe("SA2");
  });

  it("clears a stale error when the global id becomes null", async () => {
    // a failure for one record must not leave its error showing once the hook
    // is disabled by being pointed at no record at all
    mockEndpoints({
      linksResponse: () => HttpResponse.json(null, { status: 500 }),
    });

    const initialProps: { id: string | null } = { id: "GL5" };
    const { result, rerender } = renderHook(
      ({ id }: { id: string | null }) => useReferencingInventoryItemsQuery({ globalId: id }),
      {
        initialProps,
        wrapper: createQueryWrapper(createQueryClient()),
      },
    );

    await waitFor(() => expect(result.current.isError).toBe(true));

    rerender({ id: null });

    await waitFor(() => expect(result.current.fetchStatus).toBe("idle"));
    expect(result.current.isError).toBe(false);
    expect(result.current.data).toBeUndefined();
  });
});
function createQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}
