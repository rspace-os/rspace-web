import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { NuqsTestingAdapter } from "nuqs/adapters/testing";
import { Suspense } from "react";
import * as v from "valibot";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import type { CollectionConfig } from "@/modules/common/collection/collectionConfig";
import type { ApiV2CollectionMetadata } from "../../../adapters/apiV2/apiV2CollectionMetadata";
import type { ApiV2CollectionFetchOptions } from "../../../adapters/apiV2/createApiV2CollectionFetcher";
import { useApiV2TableList } from "../../../adapters/apiV2/useApiV2TableList";
import { TableList } from "../../../TableList";
import type { FilterExpression } from "../../../tableListState";

type RecordRow = { id: string; title: string; extra?: string };
const config: CollectionConfig<RecordRow> = {
  slug: "records",
  idField: "id",
  useAsTitle: "title",
  labels: { singularKey: "record", pluralKey: "records" },
  defaultColumns: ["title"],
  fields: [
    { name: "id", type: "text", labelKey: "id", list: false },
    { name: "title", type: "text", labelKey: "title" },
    { name: "extra", type: "text", labelKey: "extra", list: false },
  ],
};
const documentSchema = v.object({ id: v.string(), title: v.string(), extra: v.optional(v.string()) });
const metadata: ApiV2CollectionMetadata<RecordRow> = {
  resourceName: "records",
  fields: ["id", "title", "extra"],
  sorting: { fields: ["title"], default: [], maximumFields: 5 },
  filtering: {
    selectors: { title: { operators: ["=contains=", "=="], wildcards: false } },
    limits: {
      maximumComparisons: 50,
      maximumLikeComparisons: 10,
      maximumNesting: 10,
      maximumArguments: 100,
      maximumWhereLength: 4096,
    },
  },
  pagination: { defaultLimit: 2, maximumLimit: 100 },
};
function page(title: string) {
  return {
    docs: [{ id: "1", title }],
    totalDocs: 1,
    limit: 2,
    page: 1,
    pagingCounter: 1,
    totalPages: 1,
    hasPrevPage: false,
    hasNextPage: false,
    prevPage: null,
    nextPage: null,
  };
}
function Harness({
  request,
  expression = null,
  collectionConfig = config,
}: {
  request?: ApiV2CollectionFetchOptions<RecordRow>;
  collectionConfig?: CollectionConfig<RecordRow>;
  expression?: FilterExpression<RecordRow> | null;
}) {
  const table = useApiV2TableList({
    resourceName: "records",
    config: collectionConfig,
    documentSchema,
    metadata,
    request,
    table: { queryString: false, initialState: { filters: { search: "", expression } } },
    query: { retry: false, staleTime: Infinity, keepPreviousData: true },
  });
  return <TableList {...table.tableProps} />;
}

describe("API V2 table request recovery and source isolation", () => {
  it("keeps filter controls mounted after a limit error and recovers when cleared", async () => {
    const user = userEvent.setup();
    const queryClient = new QueryClient();
    let requests = 0;
    server.use(
      http.get("/api/v2/records", () => {
        requests += 1;
        return HttpResponse.json(page("First"));
      }),
    );
    const expression: FilterExpression<RecordRow> = {
      kind: "and",
      children: Array.from({ length: 10 }, (_, index) => ({
        kind: "comparison",
        field: "title",
        operator: "contains",
        value: `item${index}`,
      })),
    };
    render(
      <QueryClientProvider client={queryClient}>
        <NuqsTestingAdapter hasMemory>
          <Suspense fallback="Loading metadata">
            <Harness expression={expression} />
          </Suspense>
        </NuqsTestingAdapter>
      </QueryClientProvider>,
    );
    await screen.findByRole("cell", { name: "First" });
    await user.click(screen.getByRole("button", { name: "common:tableList.filters.applied" }));
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.addFilter" }));
    await user.type(screen.getAllByRole("textbox", { name: "common:tableList.filters.value" })[10], "extra");
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.applyFilters" }));

    expect(await screen.findByText("common:tableList.error.title")).toBeVisible();
    expect(requests).toBe(1);
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.resetToDefaults" }));
    expect(await screen.findByRole("cell", { name: "First" })).toBeVisible();
    expect(requests).toBe(2);
  });

  it("reuses fixed-projection rows when an ordinary column is hidden", async () => {
    const user = userEvent.setup();
    const queryClient = new QueryClient();
    const projections: string[] = [];
    const fixedColumns: CollectionConfig<RecordRow> = {
      ...config,
      defaultColumns: ["title", "extra"],
      fields: config.fields.map((field) => (field.name === "extra" ? { ...field, list: {} } : field)),
    };
    server.use(
      http.get("/api/v2/records", ({ request }) => {
        projections.push(new URL(request.url).searchParams.get("fields[records]") ?? "");
        return HttpResponse.json({ ...page("First"), docs: [{ id: "1", title: "First", extra: "details" }] });
      }),
    );
    render(
      <QueryClientProvider client={queryClient}>
        <NuqsTestingAdapter hasMemory>
          <Suspense fallback="Loading metadata">
            <Harness collectionConfig={fixedColumns} request={{ projection: { fixed: ["title", "extra"] } }} />
          </Suspense>
        </NuqsTestingAdapter>
      </QueryClientProvider>,
    );
    await screen.findByRole("cell", { name: "details" });

    await user.click(screen.getByRole("button", { name: "common:tableList.toolbar.columns" }));
    await user.click(screen.getAllByRole("button", { name: "common:tableList.actions.hideColumn" })[1]);

    expect(screen.queryByRole("cell", { name: "details" })).not.toBeInTheDocument();
    expect(screen.getByRole("cell", { name: "First" })).toBeVisible();
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
    expect(projections).toEqual(["id,title,extra"]);
  });

  it("fetches new projection dependencies when the configuration changes", async () => {
    const queryClient = new QueryClient();
    const projections: string[] = [];
    server.use(
      http.get("/api/v2/records", ({ request }) => {
        const projection = new URL(request.url).searchParams.get("fields[records]") ?? "";
        projections.push(projection);
        return HttpResponse.json({
          ...page("First"),
          docs: [{ id: "1", title: "First", ...(projection.includes("extra") ? { extra: "details" } : {}) }],
        });
      }),
    );
    const withDependency: CollectionConfig<RecordRow> = {
      ...config,
      fields: config.fields.map((field) =>
        field.name === "title"
          ? {
              ...field,
              list: { dependencies: ["extra"], renderCell: ({ row }) => `${row.title} ${row.extra ?? ""}`.trim() },
            }
          : field,
      ),
    };
    const { rerender } = render(
      <QueryClientProvider client={queryClient}>
        <NuqsTestingAdapter hasMemory>
          <Suspense fallback="Loading metadata">
            <Harness />
          </Suspense>
        </NuqsTestingAdapter>
      </QueryClientProvider>,
    );
    await screen.findByRole("cell", { name: "First" });
    rerender(
      <QueryClientProvider client={queryClient}>
        <NuqsTestingAdapter hasMemory>
          <Suspense fallback="Loading metadata">
            <Harness collectionConfig={withDependency} />
          </Suspense>
        </NuqsTestingAdapter>
      </QueryClientProvider>,
    );
    expect(await screen.findByRole("cell", { name: "First details" })).toBeVisible();
    expect(projections).toEqual(["id,title", "id,title,extra"]);
  });

  it.each(["endpoint", "token", "authScope"] as const)(
    "does not reuse rows or placeholders after the %s changes",
    async (changed) => {
      const queryClient = new QueryClient();
      let release: () => void = () => {};
      const responseReady = new Promise<void>((resolve) => {
        release = resolve;
      });
      server.use(
        http.get("/api/v2/records", async ({ request }) => {
          const second = request.headers.get("Authorization") === "Bearer second";
          if (second) await responseReady;
          return HttpResponse.json(page(second ? "Second" : "First"));
        }),
        http.get("/api/v2/other-records", async () => {
          await responseReady;
          return HttpResponse.json(page("Second"));
        }),
      );
      const first = { endpoint: "/api/v2/records", token: "first" };
      const second = {
        endpoint: changed === "endpoint" ? "/api/v2/other-records" : first.endpoint,
        token: changed === "authScope" ? () => "second" : changed === "token" ? "second" : "first",
        authScope: changed === "authScope" ? "second" : undefined,
      };
      // A getter's identity is represented by the explicit auth scope.
      const initialRequest = changed === "authScope" ? { ...first, token: () => "first", authScope: "first" } : first;
      const { rerender } = render(
        <QueryClientProvider client={queryClient}>
          <NuqsTestingAdapter hasMemory>
            <Suspense fallback="Loading metadata">
              <Harness request={initialRequest} />
            </Suspense>
          </NuqsTestingAdapter>
        </QueryClientProvider>,
      );
      await screen.findByRole("cell", { name: "First" });
      rerender(
        <QueryClientProvider client={queryClient}>
          <NuqsTestingAdapter hasMemory>
            <Suspense fallback="Loading metadata">
              <Harness request={second} />
            </Suspense>
          </NuqsTestingAdapter>
        </QueryClientProvider>,
      );
      expect(screen.queryByRole("cell", { name: "First" })).not.toBeInTheDocument();
      release();
      expect(await screen.findByRole("cell", { name: "Second" })).toBeVisible();
    },
  );
});
