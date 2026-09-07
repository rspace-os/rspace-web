import { createMemoryHistory, createRootRoute, createRouter, RouterProvider } from "@tanstack/react-router";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NuqsAdapter } from "nuqs/adapters/tanstack-router";
import { NuqsTestingAdapter, type OnUrlUpdateFunction } from "nuqs/adapters/testing";
import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { SortRule } from "@/modules/common/collection/collectionConfig";
import { TableList } from "../../TableList";
import type { FilterState, TableListQueryStringOptions } from "../../tableListState";
import { config, emptyFilters, records, type TestRecord } from "../fixtures/tableListFixtures";

const originalLocation = `${window.location.pathname}${window.location.search}${window.location.hash}`;

function setWindowSearch(search: URLSearchParams | string) {
  window.history.replaceState({}, "", `/?${search.toString().replace(/^\?/, "")}`);
}

function persistedView(overrides: Partial<Record<"search" | "where" | "columns" | "sort", string | null>>) {
  return JSON.stringify({ v: 1, search: null, where: null, columns: null, sort: null, ...overrides });
}

function Harness({
  queryString,
  withSorting = false,
  collection = config,
}: {
  queryString?: true | TableListQueryStringOptions;
  withSorting?: boolean;
  collection?: typeof config;
} = {}) {
  const [filters, setFilters] = useState<FilterState<TestRecord>>(emptyFilters);
  const [columns, setColumns] = useState(config.defaultColumns);
  const [sorting, setSorting] = useState<readonly SortRule<TestRecord>[]>(config.defaultSort ?? []);
  return (
    <TableList
      queryString={queryString}
      config={collection}
      rows={records}
      getRowId={(row) => row.id}
      features={{
        filtering: { value: filters, onChange: setFilters },
        sorting: withSorting ? { value: sorting, onChange: setSorting } : false,
        pagination: false,
        columns: { value: columns, onChange: setColumns },
      }}
    />
  );
}

describe("TableList query string sharing", () => {
  beforeEach(() => {
    window.history.replaceState({}, "", originalLocation);
    window.localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    window.localStorage.clear();
    window.history.replaceState({}, "", originalLocation);
  });

  it("restores shared columns through the TanStack Router adapter", async () => {
    const rootRoute = createRootRoute({
      component: () => (
        <NuqsAdapter>
          <Harness />
        </NuqsAdapter>
      ),
    });
    const router = createRouter({
      routeTree: rootRoute,
      history: createMemoryHistory({
        initialEntries: [
          "/?records.q=Ada&records.where=owner%3D%3DAda&records.columns={%22fields%22:[%22title%22,%22score%22]}",
        ],
      }),
    });
    setWindowSearch(
      "records.q=Ada&records.where=owner%3D%3DAda&records.columns={%22fields%22:[%22title%22,%22score%22]}",
    );
    render(<RouterProvider router={router as never} />);

    expect(await screen.findByRole("columnheader", { name: /common:tableList.examples.fields.title/ })).toBeVisible();
    await waitFor(() =>
      expect(screen.getByRole("textbox", { name: "common:tableList.search.label" })).toHaveValue("Ada"),
    );
    expect(screen.getByRole("button", { name: "common:tableList.filters.applied" })).toBeVisible();
    expect(
      screen.queryByRole("columnheader", { name: /common:tableList.examples.fields.owner/ }),
    ).not.toBeInTheDocument();
  });

  it("drops only the per-actor field a saved view can no longer resolve", async () => {
    const withCustomFields = { ...config, runtimeNamespaces: ["customFields"] };
    const params = new URLSearchParams({
      "records.q": "Ada",
      "records.where": 'owner==Ada;customFields.SF999=="BSL-2"',
      "records.columns": JSON.stringify({ fields: ["title", "customFields.SF999"] }),
    });
    setWindowSearch(params);
    render(
      <NuqsTestingAdapter searchParams={params} hasMemory>
        <Harness collection={withCustomFields} />
      </NuqsTestingAdapter>,
    );

    expect(await screen.findByRole("textbox", { name: "common:tableList.search.label" })).toHaveValue("Ada");
    expect(screen.getByRole("button", { name: "common:tableList.filters.applied" })).toBeVisible();
    expect(await screen.findByRole("columnheader", { name: /common:tableList.examples.fields.title/ })).toBeVisible();
  });

  it("invalidates a saved expression when a known runtime predicate is malformed", async () => {
    const runtimeField = {
      name: "customFields.SF104",
      labelKey: "customFields.SF104",
      label: "Hazard number",
      type: "number",
      list: {},
      form: false,
      capabilities: {
        sortable: false,
        filterOperators: ["greaterThanOrEqual"],
        supportsWildcards: false,
      },
      origin: {
        kind: "runtimeField",
        groupLabelKey: "tableList.fieldGroups.customFields",
        stableId: "SF104",
        namespace: "customFields",
        viaLabel: "",
      },
    } as const;
    const withKnownRuntimeField = {
      ...config,
      runtimeNamespaces: ["customFields"],
      fields: [...config.fields, runtimeField],
    } as unknown as typeof config;
    const params = new URLSearchParams({
      "records.where": "owner==Ada;customFields.SF104=ge=not-a-number",
    });
    setWindowSearch(params);

    render(
      <NuqsTestingAdapter searchParams={params} hasMemory>
        <Harness collection={withKnownRuntimeField} />
      </NuqsTestingAdapter>,
    );

    expect(await screen.findByRole("button", { name: "common:tableList.filters.noneApplied" })).toBeVisible();
  });

  it("emits changed filters and removes default state from the URL", async () => {
    const user = userEvent.setup();
    const onUrlUpdate = vi.fn<OnUrlUpdateFunction>();
    render(
      <NuqsTestingAdapter searchParams="?tab=mine" hasMemory onUrlUpdate={onUrlUpdate}>
        <Harness />
      </NuqsTestingAdapter>,
    );
    const search = screen.getByRole("textbox", { name: "common:tableList.search.label" });

    await user.type(search, "Ada");

    await waitFor(() => {
      expect(onUrlUpdate.mock.lastCall?.[0].searchParams.get("records.q")).toBe("Ada");
    });
    expect(onUrlUpdate.mock.lastCall?.[0].searchParams.get("tab")).toBe("mine");
    expect(onUrlUpdate.mock.lastCall?.[0].searchParams.has("records.filters")).toBe(false);

    await user.click(screen.getByRole("button", { name: "common:tableList.search.clear" }));
    await waitFor(() => expect(onUrlUpdate.mock.lastCall?.[0].searchParams.has("records.q")).toBe(false));
  });

  it("migrates legacy filter state on the next change", async () => {
    const user = userEvent.setup();
    const onUrlUpdate = vi.fn<OnUrlUpdateFunction>();
    const filters: FilterState<TestRecord> = {
      search: "Ada",
      expression: { kind: "comparison", field: "owner", operator: "equals", value: "Ada" },
    };
    const params = new URLSearchParams({ "records.filters": JSON.stringify(filters) });
    setWindowSearch(params);
    render(
      <NuqsTestingAdapter searchParams={params} hasMemory onUrlUpdate={onUrlUpdate}>
        <Harness />
      </NuqsTestingAdapter>,
    );
    const search = await screen.findByRole("textbox", { name: "common:tableList.search.label" });
    expect(search).toHaveValue("Ada");

    await user.type(search, "!");

    await waitFor(() => expect(onUrlUpdate.mock.lastCall?.[0].searchParams.get("records.q")).toBe("Ada!"));
    expect(onUrlUpdate.mock.lastCall?.[0].searchParams.get("records.where")).toBe("owner==Ada");
    expect(onUrlUpdate.mock.lastCall?.[0].searchParams.has("records.filters")).toBe(false);
  });

  it("emits changed columns and removes them after a reset", async () => {
    const user = userEvent.setup();
    const onUrlUpdate = vi.fn<OnUrlUpdateFunction>();
    render(
      <NuqsTestingAdapter hasMemory onUrlUpdate={onUrlUpdate}>
        <Harness />
      </NuqsTestingAdapter>,
    );

    await user.click(screen.getByRole("button", { name: "common:tableList.toolbar.columns" }));
    await user.click(screen.getAllByRole("button", { name: "common:tableList.actions.hideColumn" })[0]);

    await waitFor(() => {
      const serialized = onUrlUpdate.mock.lastCall?.[0].searchParams.get("records.columns");
      expect(serialized && JSON.parse(serialized)).toEqual({
        fields: ["owner", "score", "enabled", "modifiedAt"],
      });
    });
    await waitFor(() => expect(window.localStorage.getItem("rspace.tableList.records.view")).not.toBeNull());

    await user.click(screen.getByRole("button", { name: "common:tableList.actions.resetColumns" }));
    await waitFor(() => expect(onUrlUpdate.mock.lastCall?.[0].searchParams.has("records.columns")).toBe(false));
    await waitFor(() => expect(window.localStorage.getItem("rspace.tableList.records.view")).toBeNull());
  });

  it("restores a persisted view without overwriting it during initialization", async () => {
    window.localStorage.setItem(
      "rspace.tableList.records.view",
      persistedView({
        search: "Ada",
        where: "owner==Ada",
        columns: JSON.stringify({ fields: ["title", "score"] }),
        sort: "title",
      }),
    );

    render(
      <NuqsTestingAdapter hasMemory>
        <Harness />
      </NuqsTestingAdapter>,
    );

    expect(await screen.findByRole("textbox", { name: "common:tableList.search.label" })).toHaveValue("Ada");
    expect(screen.getByRole("button", { name: "common:tableList.filters.applied" })).toBeVisible();
    expect(screen.getByRole("columnheader", { name: /common:tableList.examples.fields.score/ })).toBeVisible();
    expect(
      screen.queryByRole("columnheader", { name: /common:tableList.examples.fields.owner/ }),
    ).not.toBeInTheDocument();
    await waitFor(() =>
      expect(JSON.parse(window.localStorage.getItem("rspace.tableList.records.view") as string)).toMatchObject({
        search: "Ada",
        where: "owner==Ada",
        sort: null,
      }),
    );
  });

  it("removes a malformed persisted view", async () => {
    window.localStorage.setItem("rspace.tableList.records.view", "not-json");

    render(
      <NuqsTestingAdapter hasMemory>
        <Harness />
      </NuqsTestingAdapter>,
    );

    await waitFor(() => expect(window.localStorage.getItem("rspace.tableList.records.view")).toBeNull());
    expect(screen.getByRole("textbox", { name: "common:tableList.search.label" })).toHaveValue("");
  });

  it("lets raw empty and invalid URL state override storage and writes the canonical result", async () => {
    window.localStorage.setItem("rspace.tableList.records.view", persistedView({ search: "Ada" }));
    const params = new URLSearchParams({ "records.q": "", "records.where": "not valid rsql" });
    setWindowSearch(params);

    render(
      <NuqsTestingAdapter searchParams={params} hasMemory>
        <Harness />
      </NuqsTestingAdapter>,
    );

    expect(screen.getByRole("textbox", { name: "common:tableList.search.label" })).toHaveValue("");
    await waitFor(() => expect(window.localStorage.getItem("rspace.tableList.records.view")).toBeNull());
  });

  it("synchronizes sorting with the URL and persisted view", async () => {
    const user = userEvent.setup();
    const onUrlUpdate = vi.fn<OnUrlUpdateFunction>();
    render(
      <NuqsTestingAdapter hasMemory onUrlUpdate={onUrlUpdate}>
        <Harness withSorting />
      </NuqsTestingAdapter>,
    );
    const titleHeader = screen.getByRole("columnheader", { name: /common:tableList.examples.fields.title/ });

    await user.click(within(titleHeader).getByRole("button", { name: "common:tableList.actions.sortBy" }));

    await waitFor(() => expect(onUrlUpdate.mock.lastCall?.[0].searchParams.get("records.sort")).toBe("title"));
    await waitFor(() =>
      expect(JSON.parse(window.localStorage.getItem("rspace.tableList.records.view") as string).sort).toBe("title"),
    );
  });

  it("ignores storage failures", async () => {
    const fail = () => {
      throw new Error("storage unavailable");
    };
    const storage = {
      clear: vi.fn(),
      getItem: vi.fn(fail),
      key: vi.fn(() => null),
      length: 0,
      removeItem: vi.fn(fail),
      setItem: vi.fn(fail),
    } satisfies Storage;
    vi.spyOn(window, "localStorage", "get").mockReturnValue(storage);
    const user = userEvent.setup();

    render(
      <NuqsTestingAdapter hasMemory>
        <Harness />
      </NuqsTestingAdapter>,
    );
    const search = screen.getByRole("textbox", { name: "common:tableList.search.label" });
    await user.type(search, "Ada");

    expect(search).toHaveValue("Ada");
    expect(storage.getItem).toHaveBeenCalled();
    await waitFor(() => expect(storage.setItem).toHaveBeenCalled());
  });

  it("isolates persisted views by tableId when tables share a config", async () => {
    window.localStorage.setItem("rspace.tableList.first.view", persistedView({ search: "Ada" }));
    window.localStorage.setItem("rspace.tableList.second.view", persistedView({ search: "Grace" }));

    const first = render(
      <NuqsTestingAdapter hasMemory>
        <Harness queryString={{ parameterPrefix: "first", tableId: "first" }} />
      </NuqsTestingAdapter>,
    );
    const second = render(
      <NuqsTestingAdapter hasMemory>
        <Harness queryString={{ parameterPrefix: "second", tableId: "second" }} />
      </NuqsTestingAdapter>,
    );

    expect(await within(first.container).findByRole("textbox", { name: "common:tableList.search.label" })).toHaveValue(
      "Ada",
    );
    expect(await within(second.container).findByRole("textbox", { name: "common:tableList.search.label" })).toHaveValue(
      "Grace",
    );
  });
});
