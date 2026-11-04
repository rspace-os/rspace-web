import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory, createRootRoute, createRouter, RouterProvider } from "@tanstack/react-router";
import { act, render, renderHook, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { NuqsTestingAdapter } from "nuqs/adapters/testing";
import { type ComponentProps, Suspense, useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { v2ListEnvelope } from "@/__tests__/helpers/v2ListEnvelope";
import { MemoryHistoryNuqsAdapter } from "@/__tests__/MemoryHistoryNuqsAdapter";
import { server } from "@/__tests__/mswServer";
import type { ApiV2CollectionMetadata } from "../api-v2/metadata";
import { useApiV2RuntimeFields } from "../api-v2/queries";
import { useApiV2TableList } from "../hooks/apiV2TableList";
import { TableList } from "../TableList";
import { tableListDocumentSchema as documentSchema } from "./fixtures/schema";
import { definition } from "./fixtures/tableList";

type Instrument = {
  id: number;
  name: string;
  target?: string;
  customFields?: Record<string, string | number | readonly string[] | null>;
};

const config = {
  slug: "instruments",
  idField: "id" as const,
  labels: { singularKey: "instrument", pluralKey: "instruments" },
  useAsTitle: "name" as const,
  defaultColumns: ["name" as const],
  fields: [
    { name: "id" as const, labelKey: "id", type: "number" as const, list: false as const },
    { name: "name" as const, labelKey: "name", type: "text" as const },
    { name: "target" as const, labelKey: "target", type: "text" as const },
  ],
};

const metadata: ApiV2CollectionMetadata<Instrument> = {
  resourceName: "instruments",
  fields: ["id", "name", "target"],
  sorting: { fields: ["name"], default: [{ field: "name", direction: "asc" }], maximumFields: 5 },
  filtering: {
    selectors: {
      name: { operators: ["==", "=contains="], wildcards: true, fieldType: "text" },
      target: {
        operators: ["==", "=in="],
        wildcards: false,
        fieldType: "text",
        picker: { resource: "instruments", identity: "globalId", globalIdPrefix: "IN" },
      },
    },
    limits: {
      maximumComparisons: 50,
      maximumLikeComparisons: 10,
      maximumNesting: 10,
      maximumArguments: 100,
      maximumWhereLength: 4096,
    },
  },
  runtimeFields: [
    {
      namespace: "customFields",
      catalog: "/api/v2/instruments/fields/customFields",
      responseField: "customFields",
      filterable: true,
      columnSelectable: true,
      sortable: false,
      maximumProjections: 2,
      catalogDefaultLimit: 50,
      catalogMaximumLimit: 200,
      catalogMaximumIds: 50,
      via: "",
    },
  ],
  pagination: { defaultLimit: 20, maximumLimit: 100 },
};

function Harness({
  scope = 1,
  tableOptions,
}: {
  scope?: number;
  tableOptions?: { features?: { filtering?: boolean; sorting?: boolean; pagination?: boolean; columns?: boolean } };
}) {
  const table = useApiV2TableList({
    resourceName: "instruments",
    config,
    documentSchema,
    metadata,
    request: { token: "test-token", authScope: scope },
    table: tableOptions,
  });
  return <TableList {...table.tableProps} />;
}
function Providers({ children, searchParams = "" }: { children: React.ReactNode; searchParams?: string }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={client}>
      <NuqsTestingAdapter hasMemory searchParams={searchParams}>
        <Suspense fallback={<p>{"Loading metadata"}</p>}>{children}</Suspense>
      </NuqsTestingAdapter>
    </QueryClientProvider>
  );
}
function renderHarness(searchParams = "", props: ComponentProps<typeof Harness> = {}) {
  return render(
    <Providers searchParams={searchParams}>
      <Harness {...props} />
    </Providers>,
  );
}

function collectionHandler(requests: string[]) {
  return http.get("/api/v2/instruments", ({ request }) => {
    requests.push(new URL(request.url).searchParams.get("where") ?? "");
    return HttpResponse.json({
      ...v2ListEnvelope([{ id: 1, name: "Centrifuge" }], new URL(request.url).searchParams.get("totals") === "true"),
      limit: 20,
    });
  });
}
const catalog = (fields = [definition()]) => HttpResponse.json({ fields, hasMore: false, page: 1, limit: 50 });
const where = "customFields.SF104==hazardous";
const invalidTarget = "target==XX43";
const params = (rule: string) => new URLSearchParams({ "instruments.where": rule }).toString();
afterEach(() => window.localStorage.clear());

describe("API v2 optional totals", () => {
  it("caches one total per filter/sort, pages without totals, and refreshes totals after mutations", async () => {
    const requests: URLSearchParams[] = [];
    let total = 21;
    server.use(
      http.get("/api/v2/instruments", ({ request }) => {
        const params = new URL(request.url).searchParams;
        requests.push(params);
        const page = Number(params.get("page") ?? 1);
        return HttpResponse.json({
          ...v2ListEnvelope([{ id: page, name: `Instrument ${page}` }], params.get("totals") === "true"),
          ...(params.get("totals") === "true" ? { totalDocs: total, totalPages: 2 } : {}),
          page,
          limit: 20,
          hasNextPage: page === 1,
          nextPage: page === 1 ? 2 : null,
        });
      }),
    );
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { result } = renderHook(
      () =>
        useApiV2TableList({
          resourceName: "instruments",
          config,
          documentSchema,
          metadata,
          table: { queryString: false },
        }),
      {
        wrapper: ({ children }) => (
          <QueryClientProvider client={client}>
            <Suspense fallback={null}>{children}</Suspense>
          </QueryClientProvider>
        ),
      },
    );
    const countRequests = () => requests.filter((params) => params.get("totals") === "true");
    await waitFor(() => expect(result.current?.tableProps.features.pagination).toMatchObject({ rowCount: 21 }));
    act(() => result.current.setPage({ pageIndex: 1, pageSize: 20 }));
    await waitFor(() => expect(result.current.tableProps.rows[0]?.name).toBe("Instrument 2"));
    expect(requests.filter((params) => params.get("page") === "2").every((params) => !params.has("totals"))).toBe(true);
    expect(countRequests()).toHaveLength(1);
    expect(result.current.tableProps.features.pagination).toMatchObject({ hasNextPage: false, rowCount: 21 });
    act(() => result.current.setSorting([{ field: "name", direction: "desc" }]));
    await waitFor(() => expect(countRequests()).toHaveLength(2));
    act(() =>
      result.current.setFilters({
        search: "",
        expression: { kind: "comparison", field: "name", operator: "equals", value: "Centrifuge" },
      }),
    );
    await waitFor(() => expect(countRequests()).toHaveLength(3));
    total = 22;
    await act(async () => {
      await client.invalidateQueries({ queryKey: ["api-v2", "instruments"] });
    });
    await waitFor(() => expect(result.current.tableProps.features.pagination).toMatchObject({ rowCount: 22 }));
    expect(countRequests()).toHaveLength(4);
    expect(countRequests().at(-1)?.get("where")).toBe("name==Centrifuge");
    expect(countRequests().at(-1)?.get("sort")).toBe("-name");
  });

  it("shows an unknown total while counting and disables next from the last slice", async () => {
    const user = userEvent.setup();
    const { promise, resolve } = Promise.withResolvers<void>();
    server.use(
      http.get("/api/v2/instruments", async ({ request }) => {
        const params = new URL(request.url).searchParams;
        if (params.get("totals") === "true") await promise;
        const page = Number(params.get("page") ?? 1);
        return HttpResponse.json({
          ...v2ListEnvelope([{ id: page, name: `Instrument ${page}` }], params.get("totals") === "true"),
          ...(params.get("totals") === "true" ? { totalDocs: 21, totalPages: 2 } : {}),
          page,
          limit: 20,
          hasNextPage: page === 1,
          nextPage: page === 1 ? 2 : null,
        });
      }),
    );
    renderHarness();
    await screen.findByRole("cell", { name: "Instrument 1" });
    expect(screen.getByText("common:tableList.resultsUnknown")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.nextPage" }));
    await screen.findByRole("cell", { name: "Instrument 2" });
    expect(screen.getByRole("button", { name: "common:tableList.actions.nextPage" })).toBeDisabled();
    resolve();
    await screen.findByText("common:tableList.results");
  });
});

describe("API v2 saved views", () => {
  it("keeps the adapter stable across rerenders after changing caller scope", async () => {
    server.use(collectionHandler([]));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { result, rerender } = renderHook(
      ({ scope }) =>
        useApiV2TableList({
          resourceName: "instruments",
          config,
          documentSchema,
          metadata,
          request: { token: "test-token", authScope: scope },
          table: { queryString: false },
        }),
      {
        initialProps: { scope: 1 },
        wrapper: ({ children }) => (
          <QueryClientProvider client={client}>
            <Suspense fallback={null}>{children}</Suspense>
          </QueryClientProvider>
        ),
      },
    );
    await waitFor(() => expect(result.current?.tableProps.rows).toHaveLength(1));
    rerender({ scope: 2 });
    await waitFor(() => expect(result.current.tableProps.status).toBe("idle"));
    const adapter = result.current.adapter;
    rerender({ scope: 2 });
    expect(result.current.adapter).toBe(adapter);
  });

  it("blocks rehydration when a catalogue changes to an unapproved URL", async () => {
    const user = userEvent.setup();
    const catalogRequests = vi.fn();
    server.use(
      http.get("/api/v2/replacement-fields", () => {
        catalogRequests();
        return catalog([]);
      }),
    );
    function SelectionHarness() {
      const [selected, setSelected] = useState(false);
      const [replacement, setReplacement] = useState(false);
      const fields = useApiV2RuntimeFields({
        resourceName: "instruments",
        selectors: selected ? ["customFields.SF104"] : [],
        metadata: {
          ...metadata,
          runtimeFields: metadata.runtimeFields?.map((namespace) => ({
            ...namespace,
            catalog: replacement ? "/api/v2/replacement-fields" : namespace.catalog,
          })),
        },
        request: { authScope: 1, token: "token" },
      });
      return (
        <>
          <button
            type="button"
            onClick={() => {
              fields.selectRuntimeField("customFields", definition());
              setSelected(true);
            }}
          >
            {"Select definition"}
          </button>
          <button type="button" onClick={() => setReplacement(true)}>
            {"Change catalogue"}
          </button>
          <output>{fields.error ? "catalog error" : fields.missing.join(",")}</output>
        </>
      );
    }
    render(
      <Providers>
        <SelectionHarness />
      </Providers>,
    );
    await user.click(await screen.findByRole("button", { name: "Select definition" }));
    expect(screen.getByRole("status")).toBeEmptyDOMElement();
    await user.click(screen.getByRole("button", { name: "Change catalogue" }));
    await waitFor(() => expect(screen.getByRole("status")).toMatchTextContent("catalog error"));
    expect(catalogRequests).not.toHaveBeenCalled();
  });

  it("does not erase stored rules while navigation hydrates an unavailable field", async () => {
    const requests: string[] = [];
    const missing = "customFields.SF999==missing";
    const requestedMissing = vi.fn();
    const { promise: pending, resolve: finish } = Promise.withResolvers<void>();
    server.use(
      collectionHandler(requests),
      http.get("/api/v2/instruments/fields/customFields", async ({ request }) => {
        if (new URL(request.url).searchParams.get("ids") === "SF999") {
          requestedMissing();
          await pending;
          return catalog([]);
        }
        return catalog();
      }),
    );
    const history = createMemoryHistory({ initialEntries: [`/?${params(where)}`] });
    const rootRoute = createRootRoute({
      component: () => (
        <MemoryHistoryNuqsAdapter>
          <Harness />
        </MemoryHistoryNuqsAdapter>
      ),
    });
    const router = createRouter({ routeTree: rootRoute, history });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <Suspense fallback={null}>
          <RouterProvider router={router} />
        </Suspense>
      </QueryClientProvider>,
    );
    await screen.findByRole("cell", { name: "Centrifuge" });
    const stored = window.localStorage.getItem("rspace.tableList.instruments.view");
    expect(stored).toContain(where);
    await act(async () => {
      history.push(`/?${params(missing)}`);
    });
    await waitFor(() => expect(requestedMissing).toHaveBeenCalledOnce());
    expect(window.localStorage.getItem("rspace.tableList.instruments.view")).toBe(stored);
    finish();
    expect(await screen.findByRole("alert")).toMatchTextContent(missing);
    expect(window.localStorage.getItem("rspace.tableList.instruments.view")).toBe(stored);
    expect(new URLSearchParams(history.location.search).get("instruments.where")).toBe(missing);
    expect(requests).toEqual([where, where]);
  });

  it("supports disabled persistence without a Nuqs provider", async () => {
    const requests: string[] = [];
    server.use(collectionHandler(requests));
    function Unpersisted() {
      const table = useApiV2TableList({
        resourceName: "instruments",
        config,
        documentSchema,
        metadata,
        request: { token: "test-token", authScope: 1 },
        table: { queryString: false },
      });
      return <TableList {...table.tableProps} />;
    }
    const client = new QueryClient();
    render(
      <QueryClientProvider client={client}>
        <Suspense fallback={null}>
          <Unpersisted />
        </Suspense>
      </QueryClientProvider>,
    );
    await screen.findByRole("cell", { name: "Centrifuge" });
    expect(requests).toEqual(["", ""]);
  });

  it("fetches when a saved sort is disabled for the table", async () => {
    const requests: string[] = [];
    server.use(collectionHandler(requests));
    const searchParams = new URLSearchParams({ "instruments.sort": "-name" }).toString();
    render(
      <Providers searchParams={searchParams}>
        <Harness tableOptions={{ features: { sorting: false } }} />
      </Providers>,
    );
    await screen.findByRole("cell", { name: "Centrifuge" });
    expect(requests).toEqual(["", ""]);
  });

  it("hydrates new fields on back and forward navigation without broad requests", async () => {
    const requests: string[] = [];
    const second = "customFields.SF108==cold";
    server.use(
      collectionHandler(requests),
      http.get("/api/v2/instruments/fields/customFields", ({ request }) => {
        const id = new URL(request.url).searchParams.get("ids") ?? "";
        return catalog([definition({ id, selector: `customFields.${id}` })]);
      }),
    );
    const history = createMemoryHistory({ initialEntries: [`/?${params(where)}`, `/?${params(second)}`] });
    const rootRoute = createRootRoute({
      component: () => (
        <MemoryHistoryNuqsAdapter>
          <Harness />
        </MemoryHistoryNuqsAdapter>
      ),
    });
    const router = createRouter({ routeTree: rootRoute, history });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <Suspense fallback={null}>
          <RouterProvider router={router} />
        </Suspense>
      </QueryClientProvider>,
    );
    await waitFor(() => expect(requests).toContain(second));
    await act(async () => {
      history.back();
    });
    await waitFor(() => expect(requests).toContain(where));
    await act(async () => {
      history.forward();
    });
    await waitFor(() => expect(requests.at(-1)).toBe(second));
    expect(requests.every((request) => request === where || request === second)).toBe(true);
  });

  it("does not reuse runtime definitions or rows after changing caller", async () => {
    const user = userEvent.setup();
    const requests: string[] = [];
    let fieldRequests = 0;
    server.use(
      collectionHandler(requests),
      http.get("/api/v2/instruments/fields/customFields", () => {
        fieldRequests += 1;
        return catalog(fieldRequests === 1 ? [definition()] : []);
      }),
    );
    function SwitchCaller() {
      const [scope, setScope] = useState(1);
      return (
        <>
          <button type="button" onClick={() => setScope(2)}>
            {"Switch caller"}
          </button>
          <Harness scope={scope} />
        </>
      );
    }
    render(
      <Providers searchParams={params(where)}>
        <SwitchCaller />
      </Providers>,
    );
    await screen.findByRole("cell", { name: "Centrifuge" });
    await user.click(screen.getByRole("button", { name: "Switch caller" }));
    await screen.findByRole("alert");
    expect(screen.queryByRole("cell", { name: "Centrifuge" })).not.toBeInTheDocument();
    expect(fieldRequests).toBe(2);
    expect(requests).toEqual([where, where]);
  });

  it("never broadens a valid local-storage filter during URL restoration", async () => {
    const requests: string[] = [];
    window.localStorage.setItem(
      "rspace.tableList.instruments.view",
      JSON.stringify({ v: 1, search: null, where, columns: null, sort: null }),
    );
    server.use(
      collectionHandler(requests),
      http.get("/api/v2/instruments/fields/customFields", () => catalog()),
    );
    renderHarness();
    await screen.findByRole("cell", { name: "Centrifuge" });
    expect(requests.length).toBeGreaterThan(0);
    expect(requests.every((request) => request === where)).toBe(true);
  });

  it("waits for runtime definitions before requesting the filtered collection", async () => {
    const requests: string[] = [];
    const { promise: pending, resolve: finish } = Promise.withResolvers<void>();
    const requested = vi.fn();
    server.use(
      collectionHandler(requests),
      http.get("/api/v2/instruments/fields/customFields", async () => {
        requested();
        await pending;
        return catalog();
      }),
    );
    renderHarness(params(where));
    await waitFor(() => expect(requested).toHaveBeenCalledOnce());
    expect(requests).toEqual([]);
    finish();
    await screen.findByRole("cell", { name: "Centrifuge" });
    expect(requests).toEqual([where, where]);
  });

  it("preserves a missing saved field and only fetches after explicit reset", async () => {
    const user = userEvent.setup();
    const requests: string[] = [];
    server.use(
      collectionHandler(requests),
      http.get("/api/v2/instruments/fields/customFields", () => catalog([])),
    );
    renderHarness(params(where));
    expect(await screen.findByRole("alert")).toMatchTextContent(where);
    expect(requests).toEqual([]);
    await user.click(screen.getByRole("button", { name: "common:tableList.savedView.reset" }));
    await screen.findByRole("cell", { name: "Centrifuge" });
    expect(requests).toEqual(["", ""]);
  });

  it("preserves an invalid URL picker identity and only fetches after explicit reset", async () => {
    const user = userEvent.setup();
    const requests: string[] = [];
    server.use(collectionHandler(requests));
    renderHarness(params(invalidTarget));
    expect(await screen.findByRole("alert")).toMatchTextContent(invalidTarget);
    expect(requests).toEqual([]);
    await user.click(screen.getByRole("button", { name: "common:tableList.savedView.reset" }));
    await screen.findByRole("cell", { name: "Centrifuge" });
    expect(requests).toEqual(["", ""]);
  });

  it("preserves an invalid local-storage picker list and only fetches after explicit reset", async () => {
    const user = userEvent.setup();
    const requests: string[] = [];
    const invalidList = "target=in=(IN42,XX43)";
    const stored = JSON.stringify({ v: 1, search: null, where: invalidList, columns: null, sort: null });
    window.localStorage.setItem("rspace.tableList.instruments.view", stored);
    server.use(collectionHandler(requests));
    renderHarness();
    expect(await screen.findByRole("alert")).toMatchTextContent(invalidList);
    expect(requests).toEqual([]);
    expect(window.localStorage.getItem("rspace.tableList.instruments.view")).toBe(stored);
    await user.click(screen.getByRole("button", { name: "common:tableList.savedView.reset" }));
    await screen.findByRole("cell", { name: "Centrifuge" });
    expect(window.localStorage.getItem("rspace.tableList.instruments.view")).toBeNull();
    expect(requests).toEqual(["", ""]);
  });

  it("retries a failed catalog without losing the saved rule", async () => {
    const user = userEvent.setup();
    const requests: string[] = [];
    let fail = true;
    server.use(
      collectionHandler(requests),
      http.get("/api/v2/instruments/fields/customFields", () =>
        fail ? new HttpResponse(null, { status: 503 }) : catalog(),
      ),
    );
    renderHarness(params(where));
    expect(await screen.findByRole("alert")).toMatchTextContent(where);
    expect(requests).toEqual([]);
    fail = false;
    await user.click(screen.getByRole("button", { name: "common:tableList.savedView.retry" }));
    await screen.findByRole("cell", { name: "Centrifuge" });
    expect(requests).toEqual([where, where]);
  });

  it("preserves malformed stored views and blocks collection requests", async () => {
    const requests: string[] = [];
    window.localStorage.setItem("rspace.tableList.instruments.view", "{broken");
    server.use(collectionHandler(requests));
    renderHarness();
    await screen.findByRole("alert");
    expect(requests).toEqual([]);
    expect(window.localStorage.getItem("rspace.tableList.instruments.view")).toBe("{broken");
  });
});
