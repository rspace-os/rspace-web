import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory, createRootRoute, createRouter, RouterProvider } from "@tanstack/react-router";
import { act, render, renderHook, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { NuqsTestingAdapter, type OnUrlUpdateFunction } from "nuqs/adapters/testing";
import { type ComponentProps, Suspense, useState } from "react";
import * as v from "valibot";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createTestQueryClient, queryClientWrapper } from "@/__tests__/helpers/testQueryClient";
import { v2ListEnvelope } from "@/__tests__/helpers/v2ListEnvelope";
import { MemoryHistoryNuqsAdapter } from "@/__tests__/MemoryHistoryNuqsAdapter";
import { server } from "@/__tests__/mswServer";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { useApiV2RuntimeFields } from "../api-v2/queries";
import { useApiV2TableList, usePersistedApiV2TableList, withApiV2Status } from "../hooks/useApiV2TableList";
import { tableViewDescriptor } from "../persistence";
import { TableList } from "../TableList";
import { tableViewRouteOptions } from "../tableViewRoute";
import type { TableListProps } from "../types";
import { RuntimeFieldProjectionLimitError } from "../utils/errors";
import { metadataFixture } from "./fixtures/apiV2Metadata";
import { fakeCollectionResource } from "./fixtures/apiV2Resources";
import { hazardClassDefinition } from "./fixtures/tableList";

const resource = fakeCollectionResource("instruments");

const documentSchema = v.object({ id: v.number(), name: v.string(), target: v.optional(v.string()) });
type Instrument = {
  id: number;
  name: string;
  target?: string;
  customFields?: Record<string, string | number | readonly string[] | null>;
};

const config = {
  slug: "instruments",
  idField: "id" as const,
  labels: { singularKey: "tableListExamples:record", pluralKey: "tableListExamples:records" } as const,
  useAsTitle: "name" as const,
  defaultColumns: ["name" as const],
  fields: [
    {
      name: "id" as const,
      labelKey: "tableListExamples:fields.id" as const,
      type: "number" as const,
      list: false as const,
    },
    { name: "name" as const, labelKey: "tableListExamples:fields.title" as const, type: "text" as const },
    { name: "target" as const, labelKey: "tableListExamples:fields.owner" as const, type: "text" as const },
  ],
};

const metadata = metadataFixture<Instrument>({
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
});

const instrumentsTable = tableViewDescriptor(config, "issue");
const storageKey = "rspace.tableList.instruments.view";

function Harness({
  scope = 1,
  tableOptions,
}: {
  scope?: number;
  tableOptions?: { features?: { filtering?: boolean; sorting?: boolean; pagination?: boolean; columns?: boolean } };
}) {
  const table = usePersistedApiV2TableList({
    persistence: instrumentsTable,
    resource: resource,
    config,
    documentSchema,
    metadata,
    request: { authScope: scope },
    table: tableOptions,
  });
  harness.table = table;
  return <TableList {...table.tableProps} />;
}
const harness: {
  table?: { setFilters: (filters: never) => void; setPage: (page: { pageIndex: number; pageSize: number }) => void };
} = {};
function Providers({ children, searchParams = "" }: { children: React.ReactNode; searchParams?: string }) {
  const [client] = useState(createTestQueryClient);
  return (
    <QueryClientProvider client={client}>
      <NuqsTestingAdapter hasMemory searchParams={searchParams}>
        <Suspense fallback={<p>{"Loading metadata"}</p>}>{children}</Suspense>
      </NuqsTestingAdapter>
    </QueryClientProvider>
  );
}
/** The persisted harness on a memory router whose root route spreads the route helper. */
function renderHarness(searchParams = "", props: ComponentProps<typeof Harness> = {}) {
  const history = createMemoryHistory({ initialEntries: [searchParams ? `/?${searchParams}` : "/"] });
  const rootRoute = createRootRoute({
    ...tableViewRouteOptions([instrumentsTable]),
    component: () => (
      <MemoryHistoryNuqsAdapter>
        <Harness {...props} />
      </MemoryHistoryNuqsAdapter>
    ),
  });
  const router = createRouter({ routeTree: rootRoute, history });
  render(
    <Suspense fallback={<p>{"Loading metadata"}</p>}>
      <RouterProvider router={router} />
    </Suspense>,
    { wrapper: queryClientWrapper() },
  );
  return { history };
}

/** Answers collection requests with one instrument and returns the `where` of each, in order. */
function recordCollectionRequests(): string[] {
  const requests: string[] = [];
  server.use(
    http.get("/api/v2/instruments", ({ request }) => {
      requests.push(new URL(request.url).searchParams.get("where") ?? "");
      return HttpResponse.json({
        ...v2ListEnvelope([{ id: 1, name: "Centrifuge" }], new URL(request.url).searchParams.get("totals") === "true"),
        limit: 20,
      });
    }),
  );
  return requests;
}
const catalog = (fields = [hazardClassDefinition]) => HttpResponse.json({ fields, hasMore: false, page: 1, limit: 50 });
const where = "customFields.SF104==hazardous";
const invalidTarget = "target==XX43";
const params = (rule: string) => new URLSearchParams({ "instruments.where": rule }).toString();
afterEach(() => window.localStorage.clear());

describe("API v2 row validation", () => {
  it("reports an error instead of rows when validateRows rejects the page", async () => {
    recordCollectionRequests();
    const QueryWrapper = queryClientWrapper();
    const { result } = renderHook(
      () =>
        useApiV2TableList({
          resource,
          config,
          documentSchema,
          metadata,
          request: {
            validateRows: () => {
              throw new Error("Invalid row interval");
            },
          },
        }),
      {
        wrapper: ({ children }) => (
          <QueryWrapper>
            <Suspense fallback={null}>{children}</Suspense>
          </QueryWrapper>
        ),
      },
    );

    await waitFor(() => expect(result.current?.tableProps.status).toBe("error"));
    expect(result.current.tableProps.error).toEqual(new Error("Invalid row interval"));
    expect(result.current.tableProps.rows).toEqual([]);
  });
});

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
          ...(params.get("totals") === "true" ? { totalDocs: total } : {}),
          page,
          limit: 20,
          hasNextPage: page === 1,
        });
      }),
    );
    const client = createTestQueryClient();
    const QueryWrapper = queryClientWrapper(client);
    const { result } = renderHook(
      () =>
        useApiV2TableList({
          resource: resource,
          config,
          documentSchema,
          metadata,
        }),
      {
        wrapper: ({ children }) => (
          <QueryWrapper>
            <Suspense fallback={null}>{children}</Suspense>
          </QueryWrapper>
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
      await client.invalidateQueries({ queryKey: resource.list.queryKey() });
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
          ...(params.get("totals") === "true" ? { totalDocs: 21 } : {}),
          page,
          limit: 20,
          hasNextPage: page === 1,
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
  it("ignores a catalogue that moves to an unpublished URL without requesting it", async () => {
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
        resource: resource,
        selectors: selected ? ["customFields.SF104"] : [],
        metadata: {
          ...metadata,
          runtimeFields: metadata.runtimeFields?.map((namespace) => ({
            ...namespace,
            catalog: replacement ? "/api/v2/replacement-fields" : namespace.catalog,
          })),
        },
        request: { authScope: 1 },
      });
      return (
        <>
          <button
            type="button"
            onClick={() => {
              fields.selectRuntimeField("customFields", hazardClassDefinition);
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
    await waitFor(() => expect(screen.getByRole("status")).toBeEmptyDOMElement());
    expect(catalogRequests).not.toHaveBeenCalled();
  });

  it("keeps the stored rule in step with the URL while navigation hydrates an unavailable field", async () => {
    const requests = recordCollectionRequests();
    const missing = "customFields.SF999==missing";
    const requestedMissing = vi.fn();
    const { promise: pending, resolve: finish } = Promise.withResolvers<void>();
    server.use(
      http.get("/api/v2/instruments/fields/customFields", async ({ request }) => {
        if (new URL(request.url).searchParams.get("ids") === "SF999") {
          requestedMissing();
          await pending;
          return catalog([]);
        }
        return catalog();
      }),
    );
    const { history } = renderHarness(params(where));
    await screen.findByRole("cell", { name: "Centrifuge" });
    expect(window.localStorage.getItem(storageKey)).toContain(where);
    await act(async () => {
      history.push(`/?${params(missing)}`);
    });
    await waitFor(() => expect(requestedMissing).toHaveBeenCalledOnce());
    finish();
    expect(await screen.findByRole("alert")).toMatchTextContent(missing);
    // The route saves a syntactically valid URL; the table never writes or requests the blocked view.
    expect(window.localStorage.getItem(storageKey)).toContain("SF999");
    expect(new URLSearchParams(history.location.search).get("instruments.where")).toBe(missing);
    expect(requests).toEqual([where, where]);
  });

  it("supports disabled persistence without a Nuqs provider", async () => {
    const requests = recordCollectionRequests();
    function Unpersisted() {
      const table = useApiV2TableList({
        resource: resource,
        config,
        documentSchema,
        metadata,
        request: { authScope: 1 },
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
    const requests = recordCollectionRequests();
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
    const requests = recordCollectionRequests();
    const second = "customFields.SF108==cold";
    server.use(
      http.get("/api/v2/instruments/fields/customFields", ({ request }) => {
        const id = new URL(request.url).searchParams.get("ids") ?? "";
        return catalog([{ ...hazardClassDefinition, id, selector: `customFields.${id}` }]);
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
    render(
      <Suspense fallback={null}>
        <RouterProvider router={router} />
      </Suspense>,
      { wrapper: queryClientWrapper() },
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

  it("never broadens a valid local-storage filter during URL restoration", async () => {
    const requests = recordCollectionRequests();
    window.localStorage.setItem(storageKey, JSON.stringify({ v: 1, search: null, where, columns: null, sort: null }));
    server.use(http.get("/api/v2/instruments/fields/customFields", () => catalog()));
    renderHarness();
    await screen.findByRole("cell", { name: "Centrifuge" });
    expect(requests.length).toBeGreaterThan(0);
    expect(requests.every((request) => request === where)).toBe(true);
  });

  it("waits for runtime definitions before requesting the filtered collection", async () => {
    const requests = recordCollectionRequests();
    const { promise: pending, resolve: finish } = Promise.withResolvers<void>();
    const requested = vi.fn();
    server.use(
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

  const invalidList = "target=in=(IN42,XX43)";
  it.each<{ label: string; searchParams: string; rule: string; stored?: string; emptyCatalog?: boolean }>([
    { label: "a missing saved field", searchParams: params(where), rule: where, emptyCatalog: true },
    { label: "an invalid URL picker identity", searchParams: params(invalidTarget), rule: invalidTarget },
    {
      label: "an invalid local-storage picker list",
      searchParams: "",
      rule: invalidList,
      stored: JSON.stringify({ v: 1, search: null, where: invalidList, columns: null, sort: null }),
    },
  ])("preserves $label and only fetches after explicit reset", async ({ searchParams, rule, stored, emptyCatalog }) => {
    const user = userEvent.setup();
    const requests = recordCollectionRequests();
    if (stored) window.localStorage.setItem(storageKey, stored);
    if (emptyCatalog) server.use(http.get("/api/v2/instruments/fields/customFields", () => catalog([])));
    renderHarness(searchParams);
    expect(await screen.findByRole("alert")).toMatchTextContent(rule);
    expect(requests).toEqual([]);
    if (stored) expect(window.localStorage.getItem(storageKey)).toBe(stored);
    await user.click(screen.getByRole("button", { name: "common:tableList.savedView.reset" }));
    await screen.findByRole("cell", { name: "Centrifuge" });
    if (stored) expect(window.localStorage.getItem(storageKey)).toBeNull();
    expect(requests).toEqual(["", ""]);
  });

  it("retries a failed catalog without losing the saved rule", async () => {
    const user = userEvent.setup();
    const requests = recordCollectionRequests();
    let fail = true;
    server.use(
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

  it("shows a URL view over a filter limit as invalid instead of throwing", async () => {
    const user = userEvent.setup();
    const requests = recordCollectionRequests();
    const tooManyPatterns = Array.from({ length: 11 }, (_, index) => `name=contains=a${index}`).join(";");
    renderHarness(params(tooManyPatterns));
    expect(await screen.findByRole("alert")).toMatchTextContent(tooManyPatterns);
    expect(requests).toEqual([]);
    await user.click(screen.getByRole("button", { name: "common:tableList.savedView.reset" }));
    await screen.findByRole("cell", { name: "Centrifuge" });
    expect(requests).toEqual(["", ""]);
  });

  it("preserves malformed stored views and blocks collection requests", async () => {
    const requests = recordCollectionRequests();
    window.localStorage.setItem(storageKey, "{broken");
    renderHarness();
    await screen.findByRole("alert");
    expect(requests).toEqual([]);
    expect(window.localStorage.getItem(storageKey)).toBe("{broken");
  });

  it("shows a stored view whose filter is not RSQL as an issue instead of restoring it", async () => {
    const requests = recordCollectionRequests();
    const invalid = JSON.stringify({ v: 1, search: "Ada", where: "not valid rsql", columns: null, sort: null });
    window.localStorage.setItem(storageKey, invalid);
    const { history } = renderHarness();
    await screen.findByRole("alert");
    expect(history.location.search).toBe("");
    expect(requests).toEqual([]);
    expect(window.localStorage.getItem(storageKey)).toBe(invalid);
  });

  it("shows the defaults after a shared URL replaces malformed storage and its last filter is cleared", async () => {
    const user = userEvent.setup();
    const requests = recordCollectionRequests();
    window.localStorage.setItem(storageKey, "{broken");
    renderHarness(params("name==Centrifuge"));
    await screen.findByRole("cell", { name: "Centrifuge" });

    await user.click(screen.getByRole("button", { name: "common:tableList.filters.applied" }));
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.clearAll" }));

    await waitFor(() => expect(requests.at(-1)).toBe(""));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(window.localStorage.getItem(storageKey)).toBeNull();
  });

  it("restores a stored runtime-field view saved before a new default column, merging the new default", async () => {
    const requests: string[] = [];
    window.localStorage.setItem(
      storageKey,
      JSON.stringify({
        v: 1,
        search: null,
        where,
        columns: JSON.stringify({ fields: ["target"] }),
        defaults: JSON.stringify({ fields: [] }),
        sort: null,
      }),
    );
    server.use(
      http.get("/api/v2/instruments", ({ request }) => {
        requests.push(new URL(request.url).searchParams.get("where") ?? "");
        const totals = new URL(request.url).searchParams.get("totals") === "true";
        return HttpResponse.json({
          ...v2ListEnvelope([{ id: 1, name: "Centrifuge", target: "Shelf" }], totals),
          limit: 20,
        });
      }),
      http.get("/api/v2/instruments/fields/customFields", () => catalog()),
    );

    const { history } = renderHarness();
    await screen.findByRole("cell", { name: "Centrifuge" });
    const search = new URLSearchParams(history.location.search);
    expect(search.get("instruments.where")).toBe(where);
    expect(JSON.parse(search.get("instruments.columns") ?? "")).toEqual({ fields: ["name", "target"] });
    expect(screen.getByRole("columnheader", { name: "tableListExamples:fields.owner" })).toBeVisible();
    expect(requests.every((request) => request === where)).toBe(true);
  });
});

it("shows the custom-column limit for an edit over it, without requesting or throwing", async () => {
  const requests = recordCollectionRequests();
  const { result } = renderHook(() => useApiV2TableList({ resource, config, documentSchema, metadata }), {
    wrapper: queryClientWrapper(),
  });
  await waitFor(() => expect(result.current.tableProps.rows[0]?.name).toBe("Centrifuge"));
  const definitions = ["SF104", "SF105", "SF106"].map((id) => ({
    ...hazardClassDefinition,
    id,
    selector: `customFields.${id}`,
  }));
  act(() => {
    for (const definition of definitions) result.current.tableProps.runtimeFields?.onSelect("customFields", definition);
  });
  const requested = requests.length;
  act(() => result.current.setVisibleFields(["name", ...definitions.map(({ selector }) => selector as never)]));
  expect(result.current.tableProps.status).toBe("error");
  expect(result.current.tableProps.error).toBeInstanceOf(RuntimeFieldProjectionLimitError);
  expect(requests).toHaveLength(requested);
  expect(result.current.tableProps.persistenceIdentity).toBeUndefined();
});

it("keeps the last valid URL and the toolbar when a persisted edit exceeds a limit", async () => {
  const requests = recordCollectionRequests();
  const onUrlUpdate = vi.fn<OnUrlUpdateFunction>();
  const captured: { setFilters?: (filters: unknown) => void } = {};
  function EditHarness() {
    const table = usePersistedApiV2TableList({
      persistence: instrumentsTable,
      resource,
      config,
      documentSchema,
      metadata,
      request: { authScope: 1 },
    });
    captured.setFilters = table.setFilters as (filters: unknown) => void;
    return <TableList {...table.tableProps} />;
  }
  const valid = "name=contains=a0";
  render(
    <NuqsTestingAdapter hasMemory searchParams={params(valid)} onUrlUpdate={onUrlUpdate}>
      <Suspense fallback={null}>
        <EditHarness />
      </Suspense>
    </NuqsTestingAdapter>,
    { wrapper: queryClientWrapper() },
  );
  await screen.findByRole("cell", { name: "Centrifuge" });
  expect(requests).toEqual([valid, valid]);
  const overLimit = Array.from({ length: 11 }, (_, index) => ({
    kind: "comparison",
    field: "name",
    operator: "contains",
    value: `a${index}`,
  }));
  act(() => captured.setFilters?.({ search: "", expression: { kind: "and", children: overLimit } }));
  expect(await screen.findByText("common:tableList.error.title")).toBeVisible();
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  expect(requests).toEqual([valid, valid]);
  for (const [update] of onUrlUpdate.mock.calls) expect(update.searchParams.get("instruments.where")).toBe(valid);
  act(() =>
    captured.setFilters?.({
      search: "",
      expression: { kind: "comparison", field: "name", operator: "contains", value: "b" },
    }),
  );
  await waitFor(() => expect(requests).toContain("name=contains=b"));
  expect(onUrlUpdate.mock.lastCall?.[0].searchParams.get("instruments.where")).toBe("name=contains=b");
});

const overLimitFilters = {
  search: "",
  expression: {
    kind: "and" as const,
    children: Array.from({ length: 11 }, (_, index) => ({
      kind: "comparison" as const,
      field: "name",
      operator: "contains" as const,
      value: `a${index}`,
    })),
  },
};

describe("a rejected edit", () => {
  const reject = () => act(() => harness.table?.setFilters(overLimitFilters as never));

  it("stays rejected when the same handler also returns to the first page", async () => {
    recordCollectionRequests();
    const { history } = renderHarness(params("name=contains=a0"));
    await screen.findByRole("cell", { name: "Centrifuge" });

    act(() => {
      harness.table?.setFilters(overLimitFilters as never);
      harness.table?.setPage({ pageIndex: 0, pageSize: 20 });
    });

    expect(await screen.findByText("common:tableList.error.title")).toBeVisible();
    expect(new URLSearchParams(history.location.search).get("instruments.where")).toBe("name=contains=a0");
  });

  it("is dropped by Back and Forward", async () => {
    const requests = recordCollectionRequests();
    const { history } = renderHarness(params("name=contains=a0"));
    await screen.findByRole("cell", { name: "Centrifuge" });
    await act(async () => {
      history.push(`/?${params("name=contains=b")}`);
    });
    await waitFor(() => expect(requests).toContain("name=contains=b"));

    reject();
    expect(await screen.findByText("common:tableList.error.title")).toBeVisible();
    await act(async () => {
      history.back();
    });
    expect(await screen.findByRole("cell", { name: "Centrifuge" })).toBeVisible();
    expect(screen.queryByText("common:tableList.error.title")).not.toBeInTheDocument();
    await act(async () => {
      history.forward();
    });
    expect(await screen.findByRole("cell", { name: "Centrifuge" })).toBeVisible();
    expect(screen.queryByText("common:tableList.error.title")).not.toBeInTheDocument();
  });

  it("does not come back after navigating to a blocked view and back", async () => {
    const requests = recordCollectionRequests();
    const { history } = renderHarness(params("name=contains=a0"));
    await screen.findByRole("cell", { name: "Centrifuge" });
    reject();
    expect(await screen.findByText("common:tableList.error.title")).toBeVisible();

    await act(async () => {
      history.push(`/?${params(invalidTarget)}`);
    });
    expect(await screen.findByRole("alert")).toMatchTextContent(invalidTarget);
    await act(async () => {
      history.back();
    });

    expect(await screen.findByRole("cell", { name: "Centrifuge" })).toBeVisible();
    expect(screen.queryByText("common:tableList.error.title")).not.toBeInTheDocument();
    expect(requests.every((request) => request === "name=contains=a0")).toBe(true);
  });
});

it("does not validate a previous page against newly selected columns", async () => {
  const started = Promise.withResolvers<void>();
  const continueRequest = Promise.withResolvers<void>();
  server.use(
    http.get("/api/v2/instruments", async ({ request }) => {
      const params = new URL(request.url).searchParams;
      const hasTarget = (params.get("fields[instruments]") ?? "").split(",").includes("target");
      if (hasTarget) {
        started.resolve();
        await continueRequest.promise;
      }
      return HttpResponse.json({
        ...v2ListEnvelope(
          [{ id: 1, name: "Centrifuge", ...(hasTarget ? { target: "Shelf" } : {}) }],
          params.get("totals") === "true",
        ),
        limit: 20,
      });
    }),
  );
  const { result } = renderHook(
    () =>
      useApiV2TableList({
        resource,
        config,
        documentSchema,
        metadata: { ...metadata, runtimeFields: [] },
        query: { keepPreviousData: true },
      }),
    { wrapper: queryClientWrapper() },
  );
  try {
    await waitFor(() => expect(result.current.tableProps.rows[0]?.name).toBe("Centrifuge"));
    act(() => result.current.setVisibleFields(["name", "target"]));
    await started.promise;
    expect(result.current.tableProps.status).not.toBe("error");
    continueRequest.resolve();
    await waitFor(() => expect(result.current.tableProps.rows[0]?.target).toBe("Shelf"));
  } finally {
    continueRequest.resolve();
  }
});

describe("API v2 table status", () => {
  const props: TableListProps<Instrument> = {
    config: resolveCollectionConfig(config),
    rows: [],
    getRowId: (row) => String(row.id),
    features: {
      filtering: { value: { search: "", expression: null }, onChange: () => undefined },
      sorting: { value: [], onChange: () => undefined },
      columns: { value: ["name"], onChange: () => undefined },
      pagination: { value: { pageIndex: 0, pageSize: 20 }, rowCount: 21, onChange: () => undefined },
    },
  };
  const requestError = new Error("Over the comparison limit");
  const restoredViewIssue = { kind: "invalid" as const, encoded: "name==x", remove: () => undefined };

  it("keeps a request error under loading, and replaces every feature while runtime fields are pending", () => {
    const status = withApiV2Status(props, { requestError, pending: true, restoredViewIssue });
    expect(status).toMatchObject({ status: "loading", error: requestError });
    expect(status.features).toEqual({ filtering: false, columns: false, sorting: false, pagination: false });
    expect(status.restoredViewIssue).toBeUndefined();
  });

  it("keeps a request error under a restored-view issue", () => {
    expect(withApiV2Status(props, { requestError, pending: false, restoredViewIssue })).toMatchObject({
      status: "error",
      error: requestError,
      restoredViewIssue,
      features: props.features,
    });
  });
});
