import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { OAUTH_TOKEN } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import type { RuntimeFieldDefinition } from "../api-v2/runtimeFieldCatalog";
import { TableListControlPanel } from "../components/TableListControlPanel";
import { TableList } from "../TableList";
import type { TableListFeatures } from "../types";
import { config, emptyFilters, records, type TestRecord } from "./fixtures/tableList";

function tableWithFeatures(features: TableListFeatures<TestRecord>) {
  return <TableList config={config} rows={records} getRowId={(row) => row.id} features={features} />;
}

function renderWithColumns(columns: readonly (typeof config)["defaultColumns"][number][]) {
  return render(
    tableWithFeatures({
      filtering: { value: emptyFilters, onChange: vi.fn() },
      sorting: false,
      pagination: false,
      columns: { value: columns, onChange: vi.fn() },
    }),
  );
}

describe("TableList control panel", () => {
  it("does not leave an empty panel bar when the active feature is disabled", () => {
    const { container } = render(
      <TableListControlPanel
        controlPanelId="table-list-control-panel"
        activePanel="filters"
        config={config}
        features={{ filtering: false, sorting: false, pagination: false, columns: false }}
        onClose={vi.fn()}
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it("searches custom and extra fields together and preserves the selected namespace", async () => {
    const user = userEvent.setup();
    const onColumnsChange = vi.fn();
    const onSelectRuntimeField = vi.fn();
    const extraDefinition: RuntimeFieldDefinition = {
      id: "XFt436162696e6574",
      selector: "extraFields.XFt436162696e6574",
      label: "Cabinet",
      type: "text",
      jsonType: "string",
      operators: ["==", "=contains="],
      supportsWildcards: false,
      columnSelectable: true,
      sortable: false,
      source: { id: "", label: "" },
      options: [],
    };
    const runtimeConfig = {
      ...config,
      runtimeNamespaces: ["customFields", "extraFields"],
      runtimeSources: [
        {
          namespace: "customFields",
          catalog: "/api/v2/instruments/fields/customFields",
          maximumLimit: 200,
          filterable: true,
          columnSelectable: true,
        },
        {
          namespace: "extraFields",
          catalog: "/api/v2/instruments/fields/extraFields",
          maximumLimit: 200,
          filterable: true,
          columnSelectable: true,
        },
      ],
    } as unknown as typeof config;
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const searches: URLSearchParams[] = [];
    server.use(
      http.get("/api/v2/instruments/fields", ({ request }) => {
        searches.push(new URL(request.url).searchParams);
        expect(request.headers.get("Authorization")).toBe(`Bearer ${OAUTH_TOKEN}`);
        return HttpResponse.json({
          catalogs: {
            customFields: { fields: [], hasMore: false, page: 1, limit: 20 },
            extraFields: { fields: [extraDefinition], hasMore: false, page: 1, limit: 20 },
          },
        });
      }),
    );
    render(
      <QueryClientProvider client={queryClient}>
        <TableList
          config={runtimeConfig}
          rows={records}
          getRowId={(row) => row.id}
          runtimeFieldDefinitions={[]}
          onSelectRuntimeField={onSelectRuntimeField}
          features={{
            filtering: false,
            sorting: false,
            pagination: false,
            columns: { value: config.defaultColumns, onChange: onColumnsChange },
          }}
        />
      </QueryClientProvider>,
    );

    await user.click(screen.getByRole("button", { name: "common:tableList.toolbar.columns" }));
    await user.type(screen.getByRole("combobox", { name: "common:tableList.filters.customField.option" }), "cab");
    await user.click(
      await screen.findByRole("option", { name: "Cabinetcommon:tableList.filters.customField.types.text" }),
    );

    expect(onSelectRuntimeField).toHaveBeenCalledWith("extraFields", extraDefinition);
    expect(searches).toHaveLength(1);
    expect(searches[0].getAll("namespace")).toEqual(["customFields", "extraFields"]);
    expect(searches[0].get("q")).toBe("cab");
    expect(onColumnsChange).toHaveBeenCalledWith([...config.defaultColumns, "extraFields.XFt436162696e6574"]);
  });

  it("keeps an open toolbar section open when its button is selected again", async () => {
    const user = userEvent.setup();
    render(
      tableWithFeatures({
        filtering: { value: emptyFilters, onChange: vi.fn() },
        sorting: { value: config.defaultSort ?? [], onChange: vi.fn() },
        pagination: false,
        columns: { value: config.defaultColumns, onChange: vi.fn() },
      }),
    );

    const buttons = [
      screen.getByRole("button", { name: "common:tableList.filters.noneApplied" }),
      screen.getByRole("button", { name: "common:tableList.sorting.applied" }),
      screen.getByRole("button", { name: "common:tableList.toolbar.columns" }),
    ];
    for (const button of buttons) {
      await user.click(button);
      await user.click(button);
      expect(button).toHaveAttribute("aria-expanded", "true");
    }
  });

  it.each<{
    label: string;
    columns: readonly (typeof config)["defaultColumns"][number][];
    buttonName: string;
    count?: string;
    reset?: "absent" | "visible";
  }>([
    {
      label: "leaves the columns button unannotated while the default columns are shown",
      columns: config.defaultColumns,
      buttonName: "common:tableList.toolbar.columns",
      reset: "absent",
    },
    {
      label: "counts the visible columns once they differ from the default",
      columns: ["title", "owner"],
      buttonName: "common:tableList.columns.customised",
      count: "2",
      reset: "visible",
    },
    {
      label: "flags a reordering of the default columns",
      columns: ["owner", "title", "score", "enabled", "modifiedAt"],
      buttonName: "common:tableList.columns.customised",
    },
  ])("$label", ({ columns, buttonName, count, reset }) => {
    renderWithColumns(columns);

    const button = screen.getByRole("button", { name: buttonName });
    expect(button).toBeInTheDocument();
    if (count) expect(button).toMatchTextContent(count);
    const resetButton = screen.queryByRole("button", { name: "common:tableList.actions.resetToDefaults" });
    if (reset === "absent") expect(resetButton).not.toBeInTheDocument();
    if (reset === "visible") expect(resetButton).toBeVisible();
  });

  it("shows the reset action for changed filters or sorting when columns are disabled", () => {
    const { rerender } = render(
      tableWithFeatures({
        filtering: { value: { search: "Ada", expression: null }, onChange: vi.fn() },
        sorting: false,
        pagination: false,
        columns: false,
      }),
    );

    expect(screen.getByRole("button", { name: "common:tableList.actions.resetToDefaults" })).toBeVisible();

    rerender(
      tableWithFeatures({
        filtering: false,
        sorting: { value: [{ field: "title", direction: "asc" }], onChange: vi.fn() },
        pagination: false,
        columns: false,
      }),
    );

    expect(screen.getByRole("button", { name: "common:tableList.actions.resetToDefaults" })).toBeVisible();
  });

  it("resets every enabled view feature and returns to the first page", async () => {
    const user = userEvent.setup();
    const onFiltersChange = vi.fn();
    const onSortingChange = vi.fn();
    const onColumnsChange = vi.fn();
    const onPageChange = vi.fn();
    render(
      tableWithFeatures({
        filtering: {
          value: {
            search: "Ada",
            expression: { kind: "comparison", field: "owner", operator: "equals", value: "Ada" },
          },
          onChange: onFiltersChange,
        },
        sorting: { value: [{ field: "title", direction: "asc" }], onChange: onSortingChange },
        pagination: { value: { pageIndex: 2, pageSize: 2 }, rowCount: records.length, onChange: onPageChange },
        columns: { value: ["title", "score"], onChange: onColumnsChange },
      }),
    );

    await user.click(screen.getByRole("button", { name: "common:tableList.actions.resetToDefaults" }));

    expect(onFiltersChange).toHaveBeenCalledWith(emptyFilters);
    expect(onSortingChange).toHaveBeenCalledWith(config.defaultSort);
    expect(onColumnsChange).toHaveBeenCalledWith(config.defaultColumns);
    expect(onPageChange).toHaveBeenCalledWith({ pageIndex: 0, pageSize: 2 });
  });
});
