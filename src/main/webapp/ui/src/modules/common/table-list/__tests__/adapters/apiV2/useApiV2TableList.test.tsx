import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory, createRootRoute, createRouter, RouterProvider } from "@tanstack/react-router";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { NuqsTestingAdapter } from "nuqs/adapters/testing";
import { Suspense, useState } from "react";
import * as v from "valibot";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryHistoryNuqsAdapter } from "@/__tests__/MemoryHistoryNuqsAdapter";
import { server } from "@/__tests__/mswServer";
import type { ApiV2CollectionMetadata } from "../../../adapters/apiV2/apiV2CollectionMetadata";
import type { RuntimeFieldDefinition } from "../../../adapters/apiV2/runtimeFieldCatalog";
import { useApiV2RuntimeFields } from "../../../adapters/apiV2/useApiV2RuntimeFields";
import { useApiV2TableList } from "../../../adapters/apiV2/useApiV2TableList";
import { TableList } from "../../../TableList";

type Instrument = {
  id: number;
  name: string;
  customFields?: Record<string, string | number | readonly string[] | null>;
};

const documentSchema = v.object({ id: v.number(), name: v.string() });

const config = {
  slug: "instruments",
  idField: "id" as const,
  labels: { singularKey: "instrument", pluralKey: "instruments" },
  useAsTitle: "name" as const,
  defaultColumns: ["name" as const],
  fields: [
    { name: "id" as const, labelKey: "id", type: "number" as const, list: false as const },
    { name: "name" as const, labelKey: "name", type: "text" as const },
  ],
};

const metadata: ApiV2CollectionMetadata<Instrument> = {
  resourceName: "instruments",
  fields: ["id", "name"],
  sorting: { fields: ["name"], default: [{ field: "name", direction: "asc" }], maximumFields: 5 },
  filtering: {
    selectors: { name: { operators: ["==", "=contains="], wildcards: true, fieldType: "text" } },
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
      catalog: "/api/v2/instruments/custom-fields",
      responseField: "customFields",
      filterable: true,
      columnSelectable: true,
      sortable: false,
      maximumProjections: 2,
      catalogDefaultLimit: 50,
      catalogMaximumLimit: 200,
      catalogMaximumIds: 50,
      via: "",
      viaResource: "",
    },
  ],
  pagination: { defaultLimit: 20, maximumLimit: 100 },
};

function definition(overrides: Partial<RuntimeFieldDefinition> = {}): RuntimeFieldDefinition {
  return {
    id: "SF104",
    selector: "customFields.SF104",
    label: "Hazard class",
    type: "text",
    jsonType: "string",
    operators: ["==", "!=", "=in=", "=out=", "=contains=", "=like=", "=exists="],
    supportsWildcards: false,
    columnSelectable: true,
    sortable: false,
    source: { id: "IT9", label: "Cell line template" },
    options: [],
    ...overrides,
  };
}

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
function collectionHandler(requests: string[]) {
  return http.get("/api/v2/instruments", ({ request }) => {
    requests.push(new URL(request.url).searchParams.get("where") ?? "");
    return HttpResponse.json({
      docs: [{ id: 1, name: "Centrifuge" }],
      totalDocs: 1,
      page: 1,
      totalPages: 1,
      pagingCounter: 1,
      limit: 20,
      hasNextPage: false,
      hasPrevPage: false,
      nextPage: null,
      prevPage: null,
    });
  });
}
const catalog = (fields = [definition()]) => HttpResponse.json({ fields, hasMore: false, page: 1, limit: 50 });
const where = "customFields.SF104==hazardous";
const params = (rule: string) => new URLSearchParams({ "instruments.where": rule }).toString();
afterEach(() => window.localStorage.clear());

describe("API v2 saved views", () => {
  it("rehydrates a selected definition when its catalogue changes", async () => {
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
          <output>{fields.missing.join(",")}</output>
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
    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent("customFields.SF104"));
    expect(catalogRequests).toHaveBeenCalledOnce();
  });

  it("does not erase stored rules while navigation hydrates an unavailable field", async () => {
    const requests: string[] = [];
    const missing = "customFields.SF999==missing";
    const requestedMissing = vi.fn();
    let finish: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      finish = resolve;
    });
    server.use(
      collectionHandler(requests),
      http.get("/api/v2/instruments/custom-fields", async ({ request }) => {
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
    finish?.();
    expect(await screen.findByRole("alert")).toHaveTextContent(missing);
    expect(window.localStorage.getItem("rspace.tableList.instruments.view")).toBe(stored);
    expect(new URLSearchParams(history.location.search).get("instruments.where")).toBe(missing);
    expect(requests).toEqual([where]);
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
    expect(requests).toEqual([""]);
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
    expect(requests).toEqual([""]);
  });

  it("hydrates new fields on back and forward navigation without broad requests", async () => {
    const requests: string[] = [];
    const second = "customFields.SF108==cold";
    server.use(
      collectionHandler(requests),
      http.get("/api/v2/instruments/custom-fields", ({ request }) => {
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
      http.get("/api/v2/instruments/custom-fields", () => {
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
    expect(requests).toEqual([where]);
  });

  it("never broadens a valid local-storage filter during URL restoration", async () => {
    const requests: string[] = [];
    window.localStorage.setItem(
      "rspace.tableList.instruments.view",
      JSON.stringify({ v: 1, search: null, where, columns: null, sort: null }),
    );
    server.use(
      collectionHandler(requests),
      http.get("/api/v2/instruments/custom-fields", () => catalog()),
    );
    render(
      <Providers>
        <Harness />
      </Providers>,
    );
    await screen.findByRole("cell", { name: "Centrifuge" });
    expect(requests.length).toBeGreaterThan(0);
    expect(requests.every((request) => request === where)).toBe(true);
  });

  it("waits for runtime definitions before requesting the filtered collection", async () => {
    const requests: string[] = [];
    let finish: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      finish = resolve;
    });
    const requested = vi.fn();
    server.use(
      collectionHandler(requests),
      http.get("/api/v2/instruments/custom-fields", async () => {
        requested();
        await pending;
        return catalog();
      }),
    );
    render(
      <Providers searchParams={params(where)}>
        <Harness />
      </Providers>,
    );
    await waitFor(() => expect(requested).toHaveBeenCalledOnce());
    expect(requests).toEqual([]);
    finish?.();
    await screen.findByRole("cell", { name: "Centrifuge" });
    expect(requests).toEqual([where]);
  });

  it("preserves a missing saved field and only fetches after explicit reset", async () => {
    const user = userEvent.setup();
    const requests: string[] = [];
    server.use(
      collectionHandler(requests),
      http.get("/api/v2/instruments/custom-fields", () => catalog([])),
    );
    render(
      <Providers searchParams={params(where)}>
        <Harness />
      </Providers>,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(where);
    expect(requests).toEqual([]);
    await user.click(screen.getByRole("button", { name: "common:tableList.savedView.reset" }));
    await screen.findByRole("cell", { name: "Centrifuge" });
    expect(requests).toEqual([""]);
  });

  it("retries a failed catalog without losing the saved rule", async () => {
    const user = userEvent.setup();
    const requests: string[] = [];
    let fail = true;
    server.use(
      collectionHandler(requests),
      http.get("/api/v2/instruments/custom-fields", () => (fail ? new HttpResponse(null, { status: 503 }) : catalog())),
    );
    render(
      <Providers searchParams={params(where)}>
        <Harness />
      </Providers>,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(where);
    expect(requests).toEqual([]);
    fail = false;
    await user.click(screen.getByRole("button", { name: "common:tableList.savedView.retry" }));
    await screen.findByRole("cell", { name: "Centrifuge" });
    expect(requests).toEqual([where]);
  });

  it("preserves malformed stored views and blocks collection requests", async () => {
    const requests: string[] = [];
    window.localStorage.setItem("rspace.tableList.instruments.view", "{broken");
    server.use(collectionHandler(requests));
    render(
      <Providers>
        <Harness />
      </Providers>,
    );
    await screen.findByRole("alert");
    expect(requests).toEqual([]);
    expect(window.localStorage.getItem("rspace.tableList.instruments.view")).toBe("{broken");
  });
});
