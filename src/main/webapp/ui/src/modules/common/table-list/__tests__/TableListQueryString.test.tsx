import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Link,
  Outlet,
  RouterProvider,
} from "@tanstack/react-router";
import { act, render as renderElement, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NuqsTestingAdapter, type OnUrlUpdateFunction } from "nuqs/adapters/testing";
import { type ReactNode, useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryHistoryNuqsAdapter } from "@/__tests__/MemoryHistoryNuqsAdapter";
import { usePersistedTableList, useTableList } from "../hooks/useTableList";
import { type TableViewDescriptor, tableViewDescriptor } from "../persistence";
import { TableList } from "../TableList";
import { tableViewRouteOptions } from "../tableViewRoute";
import { config, records } from "./fixtures/tableList";

const recordsTable = tableViewDescriptor(config, "discard");
const firstTable = tableViewDescriptor(config, "discard", { parameterPrefix: "first", tableId: "first" });
const secondTable = tableViewDescriptor(config, "discard", { parameterPrefix: "second", tableId: "second" });
const storageKey = "rspace.tableList.records.view";
const searchLabel = "common:tableList.search.label";

function persistedView(
  overrides: Partial<Record<"search" | "where" | "columns" | "defaults" | "sort", string | null>>,
) {
  return JSON.stringify({ v: 1, search: null, where: null, columns: null, sort: null, ...overrides });
}

/** Client-row tables still use a query; each test gets its own client. */
const render = (ui: ReactNode) =>
  renderElement(<QueryClientProvider client={new QueryClient()}>{ui}</QueryClientProvider>);
const columns = (fields: readonly string[]) => JSON.stringify({ fields });
const stored = (key = storageKey) => {
  const value = window.localStorage.getItem(key);
  return value === null ? null : JSON.parse(value);
};

function PersistedTable({ table = recordsTable, label }: { table?: TableViewDescriptor<"discard">; label?: string }) {
  const list = usePersistedTableList({
    config,
    dataSource: { type: "client", rows: records },
    persistence: table,
  });
  const content = <TableList {...list.tableProps} />;
  return label ? <section aria-label={label}>{content}</section> : content;
}

function TwoTables() {
  return (
    <>
      <PersistedTable table={firstTable} label="First table" />
      <PersistedTable table={secondTable} label="Second table" />
    </>
  );
}

/** A memory router with a home page and a `/records` page that spreads the route helper. */
function renderRoute(
  url: string,
  {
    tables = [recordsTable],
    page = () => <PersistedTable />,
  }: { tables?: TableViewDescriptor[]; page?: () => ReactNode } = {},
) {
  const rootRoute = createRootRoute({
    component: () => (
      <MemoryHistoryNuqsAdapter>
        <Outlet />
      </MemoryHistoryNuqsAdapter>
    ),
  });
  const home = createRoute({
    getParentRoute: () => rootRoute,
    path: "/",
    component: () => <Link to={"/records" as never}>{"Open records"}</Link>,
  });
  const recordsRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/records",
    ...tableViewRouteOptions(tables),
    component: page,
  });
  const history = createMemoryHistory({ initialEntries: [url] });
  const router = createRouter({ routeTree: rootRoute.addChildren([home, recordsRoute]), history });
  render(<RouterProvider router={router as never} />);
  return { router, history, search: () => new URLSearchParams(history.location.search) };
}

afterEach(() => {
  vi.restoreAllMocks();
  window.localStorage.clear();
});

describe("TableList URL view", () => {
  it("restores shared columns from memory history without changing the browser URL", async () => {
    const originalLocation = window.location.href;
    renderRoute(`/records?records.q=Ada&records.where=owner%3D%3DAda&records.columns=${columns(["title", "score"])}`);

    expect(await screen.findByRole("columnheader", { name: "tableListExamples:fields.title" })).toBeVisible();
    expect(screen.getByRole("textbox", { name: searchLabel })).toHaveValue("Ada");
    expect(screen.getByRole("button", { name: "common:tableList.filters.applied" })).toBeVisible();
    expect(screen.queryByRole("columnheader", { name: "tableListExamples:fields.owner" })).not.toBeInTheDocument();
    expect(window.location.href).toBe(originalLocation);
  });

  it("applies a later URL change to every feature without writing the previous view back", async () => {
    const { history } = renderRoute("/records");
    await screen.findByRole("columnheader", { name: "tableListExamples:fields.owner" });

    const view = {
      "records.q": "Ada",
      "records.where": "owner==Ada",
      "records.columns": columns(["title", "score"]),
      "records.sort": "-score",
    };
    act(() => history.push(`/records?${new URLSearchParams(view)}`));

    await waitFor(() => expect(screen.getByRole("textbox", { name: searchLabel })).toHaveValue("Ada"));
    expect(screen.getByRole("button", { name: "common:tableList.filters.applied" })).toBeVisible();
    expect(screen.queryByRole("columnheader", { name: "tableListExamples:fields.owner" })).not.toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "tableListExamples:fields.score" })).toHaveAttribute(
      "aria-sort",
      "descending",
    );
    await new Promise((resolve) => setTimeout(resolve, 200));
    expect(Object.fromEntries(new URLSearchParams(history.location.search))).toEqual(view);
  });

  it("invalidates a saved expression when a known runtime predicate is malformed", async () => {
    const runtimeField = {
      name: "customFields.SF104",
      labelKey: "customFields.SF104",
      label: "Hazard number",
      type: "number",
      list: {},
      form: false,
      capabilities: { sortable: false, filterOperators: ["greaterThanOrEqual"], supportsWildcards: false },
      origin: { kind: "runtimeField", groupLabelKey: "tableList.fieldGroups.customFields", namespace: "customFields" },
    } as const;
    const withKnownRuntimeField = {
      ...config,
      runtimeNamespaces: ["customFields"],
      fields: [...config.fields, runtimeField],
    } as unknown as typeof config;
    function Table() {
      const list = usePersistedTableList({
        config: withKnownRuntimeField,
        dataSource: { type: "client", rows: records },
        persistence: recordsTable,
      });
      return <TableList {...list.tableProps} />;
    }
    const params = new URLSearchParams({ "records.where": "owner==Ada;customFields.SF104=ge=not-a-number" });

    render(
      <NuqsTestingAdapter searchParams={params} hasMemory>
        <Table />
      </NuqsTestingAdapter>,
    );

    expect(await screen.findByRole("button", { name: "common:tableList.filters.noneApplied" })).toBeVisible();
  });

  it("emits changed filters and removes default state from the URL", async () => {
    const user = userEvent.setup();
    const onUrlUpdate = vi.fn<OnUrlUpdateFunction>();
    render(
      <NuqsTestingAdapter searchParams="?tab=mine" hasMemory onUrlUpdate={onUrlUpdate}>
        <PersistedTable />
      </NuqsTestingAdapter>,
    );

    await user.type(screen.getByRole("textbox", { name: searchLabel }), "Ada");

    await waitFor(() => expect(onUrlUpdate.mock.lastCall?.[0].searchParams.get("records.q")).toBe("Ada"));
    expect(onUrlUpdate.mock.lastCall?.[0].searchParams.get("tab")).toBe("mine");

    await user.click(screen.getByRole("button", { name: "common:tableList.search.clear" }));
    await waitFor(() => expect(onUrlUpdate.mock.lastCall?.[0].searchParams.has("records.q")).toBe(false));
  });

  it("keeps a header sort made from page 2 and returns to page 1", async () => {
    const user = userEvent.setup();
    const { search } = renderRoute("/records");
    await user.click(await screen.findByRole("button", { name: "common:tableList.actions.nextPage" }));
    expect(screen.getAllByRole("row")).toHaveLength(2);

    const titleHeader = screen.getByRole("columnheader", { name: "tableListExamples:fields.title" });
    await user.click(within(titleHeader).getByRole("button", { name: "common:tableList.actions.sortBy" }));

    await waitFor(() => expect(search().get("records.sort")).toBe("title"));
    expect(screen.getAllByRole("row")).toHaveLength(3);
    await waitFor(() => expect(stored()?.sort).toBe("title"));
  });

  it("keeps a toolbar search made from page 2", async () => {
    const user = userEvent.setup();
    const { search } = renderRoute("/records");
    await user.click(await screen.findByRole("button", { name: "common:tableList.actions.nextPage" }));

    await user.type(screen.getByRole("textbox", { name: searchLabel }), "Ada{Enter}");

    await waitFor(() => expect(search().get("records.q")).toBe("Ada"));
    expect(screen.getByRole("textbox", { name: searchLabel })).toHaveValue("Ada");
    await waitFor(() => expect(stored()?.search).toBe("Ada"));
  });

  it("never touches the URL or storage for a local table", async () => {
    const user = userEvent.setup();
    const onUrlUpdate = vi.fn<OnUrlUpdateFunction>();
    function LocalTable() {
      const list = useTableList({ config, dataSource: { type: "client", rows: records } });
      return <TableList {...list.tableProps} />;
    }
    render(
      <NuqsTestingAdapter hasMemory onUrlUpdate={onUrlUpdate}>
        <LocalTable />
      </NuqsTestingAdapter>,
    );

    await user.type(screen.getByRole("textbox", { name: searchLabel }), "Ada{Enter}");

    expect(screen.getByRole("textbox", { name: searchLabel })).toHaveValue("Ada");
    expect(onUrlUpdate).not.toHaveBeenCalled();
    expect(window.localStorage.length).toBe(0);
  });

  it("follows a persistence identity change on a mounted caller", async () => {
    function Switching() {
      const [table, setTable] = useState(firstTable);
      const list = usePersistedTableList({
        config,
        dataSource: { type: "client", rows: records },
        persistence: table,
      });
      return (
        <>
          <button type="button" onClick={() => setTable(secondTable)}>
            {"Switch"}
          </button>
          <TableList {...list.tableProps} />
        </>
      );
    }
    const user = userEvent.setup();
    render(
      <NuqsTestingAdapter searchParams="?first.q=Ada&second.q=Grace" hasMemory>
        <Switching />
      </NuqsTestingAdapter>,
    );
    expect(screen.getByRole("textbox", { name: searchLabel })).toHaveValue("Ada");

    await user.click(screen.getByRole("button", { name: "Switch" }));

    expect(screen.getByRole("textbox", { name: searchLabel })).toHaveValue("Grace");
  });
});

describe("TableList route storage", () => {
  it("restores a stored view on enter, then follows Back and Forward within the route", async () => {
    window.localStorage.setItem(
      storageKey,
      persistedView({ search: "Ada", columns: columns(["title", "score"]), defaults: columns(config.defaultColumns) }),
    );
    const { history, search } = renderRoute("/records");

    expect(await screen.findByRole("textbox", { name: searchLabel })).toHaveValue("Ada");
    expect(search().get("records.q")).toBe("Ada");
    expect(screen.queryByRole("columnheader", { name: "tableListExamples:fields.owner" })).not.toBeInTheDocument();

    act(() => history.push("/records?records.q=Grace"));
    await waitFor(() => expect(screen.getByRole("textbox", { name: searchLabel })).toHaveValue("Grace"));
    await waitFor(() => expect(stored()).toMatchObject({ search: "Grace", columns: null }));

    act(() => history.back());
    await waitFor(() => expect(screen.getByRole("textbox", { name: searchLabel })).toHaveValue("Ada"));
    act(() => history.forward());
    await waitFor(() => expect(screen.getByRole("textbox", { name: searchLabel })).toHaveValue("Grace"));
  });

  it("clears the last filter and resets columns from a restored view to an empty URL and storage", async () => {
    const user = userEvent.setup();
    window.localStorage.setItem(
      storageKey,
      persistedView({ search: "Ada", columns: columns(["title", "score"]), defaults: columns(config.defaultColumns) }),
    );
    const { search } = renderRoute("/records");
    expect(await screen.findByRole("textbox", { name: searchLabel })).toHaveValue("Ada");

    await user.click(screen.getByRole("button", { name: "common:tableList.search.clear" }));
    await user.click(screen.getByRole("button", { name: "common:tableList.columns.customised" }));
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.resetColumns" }));

    await waitFor(() => expect([...search().keys()]).toEqual([]));
    await waitFor(() => expect(stored()).toBeNull());
    expect(screen.getByRole("textbox", { name: searchLabel })).toHaveValue("");
    expect(screen.getByRole("columnheader", { name: "tableListExamples:fields.owner" })).toBeVisible();
  });

  it("does not restore on a hover preload, then restores on the navigation that follows", async () => {
    const user = userEvent.setup();
    window.localStorage.setItem(storageKey, persistedView({ search: "Ada" }));
    const { router, history } = renderRoute("/");
    const link = await screen.findByRole("link", { name: "Open records" });

    await act(() => router.preloadRoute({ to: "/records" } as never));
    expect(history.location.pathname).toBe("/");
    expect(stored()).toMatchObject({ search: "Ada" });

    await user.click(link);
    expect(await screen.findByRole("textbox", { name: searchLabel })).toHaveValue("Ada");
    expect(new URLSearchParams(history.location.search).get("records.q")).toBe("Ada");
  });

  it("restores two tables in one redirect, keeping an unrelated key, the fragment and history state", async () => {
    window.localStorage.setItem("rspace.tableList.first.view", persistedView({ search: "Ada" }));
    window.localStorage.setItem("rspace.tableList.second.view", persistedView({ search: "Grace" }));
    const { history } = renderRoute("/", { tables: [firstTable, secondTable], page: TwoTables });
    await screen.findByRole("link", { name: "Open records" });
    const replace = vi.spyOn(history, "replace");

    act(() => history.push("/records?tab=mine#notes", { from: "test" } as never));
    const search = (table: string) =>
      within(screen.getByRole("region", { name: table })).getByRole("textbox", { name: searchLabel });

    await waitFor(() => expect(search("First table")).toHaveValue("Ada"));
    expect(search("Second table")).toHaveValue("Grace");
    expect(replace).toHaveBeenCalledOnce();
    expect(history.location.hash).toBe("#notes");
    expect(history.location.state).toMatchObject({ from: "test" });
    expect(Object.fromEntries(new URLSearchParams(history.location.search))).toEqual({
      tab: "mine",
      "first.q": "Ada",
      "second.q": "Grace",
    });
  });

  it("keeps table B's parameters and an unrelated key when table A changes", async () => {
    const user = userEvent.setup();
    const { search } = renderRoute("/records?tab=mine&second.q=Grace", {
      tables: [firstTable, secondTable],
      page: TwoTables,
    });
    const firstSearch = within(await screen.findByRole("region", { name: "First table" })).getByRole("textbox", {
      name: searchLabel,
    });

    await user.type(firstSearch, "Ada{Enter}");

    await waitFor(() => expect(search().get("first.q")).toBe("Ada"));
    expect(search().get("second.q")).toBe("Grace");
    expect(search().get("tab")).toBe("mine");
    expect(stored("rspace.tableList.second.view")).toMatchObject({ search: "Grace" });
  });

  // Router JSON-decodes `[1,2]` to an array, which nuqs reads as its first value: storage keeps what the table shows.
  it.each([
    ["true", "true"],
    ["123", "123"],
    ["[1,2]", "1"],
  ])(
    "round-trips the shared search term %s, shown as %s, and a column selection through storage",
    async (shared, term) => {
      const visible = columns(["title", "score"]);
      const { history, search } = renderRoute(
        `/records?${new URLSearchParams({ "records.q": shared, "records.columns": visible })}`,
      );
      expect(await screen.findByRole("textbox", { name: searchLabel })).toHaveValue(term);
      await waitFor(() => expect(stored()).toMatchObject({ search: term, columns: visible }));

      act(() => history.push("/"));
      await screen.findByRole("link", { name: "Open records" });
      act(() => history.push("/records"));

      expect(await screen.findByRole("textbox", { name: searchLabel })).toHaveValue(term);
      expect(search().get("records.q")).toBe(term);
      expect(screen.queryByRole("columnheader", { name: "tableListExamples:fields.owner" })).not.toBeInTheDocument();
    },
  );

  it("stores the first value of a repeated key", async () => {
    renderRoute("/records?records.q=first&records.q=second");

    expect(await screen.findByRole("textbox", { name: searchLabel })).toHaveValue("first");
    await waitFor(() => expect(stored()).toMatchObject({ search: "first" }));
  });

  it("removes the stored view for unparseable URL parameters", async () => {
    window.localStorage.setItem(storageKey, persistedView({ search: "Ada" }));
    renderRoute("/records?records.where=not%20valid%20rsql");

    await screen.findByRole("textbox", { name: searchLabel });
    await waitFor(() => expect(stored()).toBeNull());
  });

  it("removes an all-empty stored view without redirecting", async () => {
    window.localStorage.setItem(storageKey, persistedView({ search: "" }));
    const { history } = renderRoute("/records");

    expect(await screen.findByRole("textbox", { name: searchLabel })).toHaveValue("");
    expect(history.location.search).toBe("");
    expect(stored()).toBeNull();
  });

  it("does not redirect for an empty URL value, removes the stored view and keeps the value", async () => {
    window.localStorage.setItem(storageKey, persistedView({ search: "Ada" }));
    const { history } = renderRoute("/records?records.q=");

    expect(await screen.findByRole("textbox", { name: searchLabel })).toHaveValue("");
    await waitFor(() => expect(stored()).toBeNull());
    expect(history.location.search).toBe("?records.q=");
  });

  it("neither restores nor keeps a stored view whose filter is not RSQL", async () => {
    window.localStorage.setItem(storageKey, persistedView({ search: "Ada", where: "not valid rsql" }));
    const { history } = renderRoute("/records");

    expect(await screen.findByRole("textbox", { name: searchLabel })).toHaveValue("");
    expect(history.location.search).toBe("");
    expect(stored()).toBeNull();
  });

  it("restores the default columns for a stored selection that is not a column list, keeping the other fields", async () => {
    window.localStorage.setItem(
      storageKey,
      persistedView({ search: "Ada", columns: "not-columns", defaults: columns(config.defaultColumns) }),
    );
    const { search } = renderRoute("/records");

    expect(await screen.findByRole("textbox", { name: searchLabel })).toHaveValue("Ada");
    expect(screen.getByRole("columnheader", { name: "tableListExamples:fields.owner" })).toBeVisible();
    expect(search().has("records.columns")).toBe(false);
  });

  it("removes malformed storage on enter and shows the defaults", async () => {
    window.localStorage.setItem(storageKey, "not-json");
    renderRoute("/records");

    expect(await screen.findByRole("textbox", { name: searchLabel })).toHaveValue("");
    expect(stored()).toBeNull();
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
    renderRoute("/records");

    const search = await screen.findByRole("textbox", { name: searchLabel });
    await user.type(search, "Ada{Enter}");

    expect(search).toHaveValue("Ada");
    expect(storage.getItem).toHaveBeenCalled();
    await waitFor(() => expect(storage.setItem).toHaveBeenCalled());
  });
});
