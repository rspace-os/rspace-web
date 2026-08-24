import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { queryKeys } from "@/modules/common/hooks/auth";
import type { RuntimeFieldDefinition } from "../../adapters/apiV2/runtimeFieldCatalog";
import { TableList } from "../../TableList";
import type { TableListFeatures } from "../../tableListState";
import { config, emptyFilters, records, type TestRecord } from "../fixtures/tableListFixtures";

function tableWithFeatures(features: TableListFeatures<TestRecord>) {
  return (
    <TableList queryString={false} config={config} rows={records} getRowId={(row) => row.id} features={features} />
  );
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
  it("searches custom and extra fields together and preserves the selected namespace", async () => {
    const user = userEvent.setup();
    const onColumnsChange = vi.fn();
    const onSelectRuntimeField = vi.fn();
    const definition: RuntimeFieldDefinition = {
      id: "SF104",
      selector: "customFields.SF104",
      label: "Hazard class",
      type: "text",
      jsonType: "string",
      operators: ["==", "=contains="],
      supportsWildcards: false,
      columnSelectable: true,
      sortable: false,
      source: { id: "IT9", label: "Cell line template" },
      options: [],
    };
    const extraDefinition: RuntimeFieldDefinition = {
      ...definition,
      id: "XFt436162696e6574",
      selector: "extraFields.XFt436162696e6574",
      label: "Cabinet",
      source: { id: "", label: "" },
    };
    const runtimeConfig = {
      ...config,
      runtimeNamespaces: ["customFields", "extraFields"],
      runtimeSources: [
        {
          namespace: "customFields",
          viaLabel: "",
          catalog: "/api/v2/instruments/fields/customFields",
          maximumLimit: 200,
          filterable: true,
          columnSelectable: true,
        },
        {
          namespace: "extraFields",
          viaLabel: "",
          catalog: "/api/v2/instruments/fields/extraFields",
          maximumLimit: 200,
          filterable: true,
          columnSelectable: true,
        },
      ],
    } as unknown as typeof config;
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    queryClient.setQueryData(queryKeys.oauthToken(true), "test-token");
    server.use(
      http.get("/api/v2/instruments/fields/customFields", ({ request }) => {
        expect(new URL(request.url).searchParams.get("search")).toBe("cab");
        expect(request.headers.get("Authorization")).toBe("Bearer test-token");
        return HttpResponse.json({ fields: [], hasMore: false, page: 1, limit: 20 });
      }),
      http.get("/api/v2/instruments/fields/extraFields", ({ request }) => {
        expect(new URL(request.url).searchParams.get("search")).toBe("cab");
        expect(request.headers.get("Authorization")).toBe("Bearer test-token");
        return HttpResponse.json({ fields: [extraDefinition], hasMore: false, page: 1, limit: 20 });
      }),
    );
    render(
      <QueryClientProvider client={queryClient}>
        <TableList
          queryString={false}
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
    await user.click(await screen.findByRole("option", { name: /Cabinet/ }));

    expect(onSelectRuntimeField).toHaveBeenCalledWith("extraFields", extraDefinition);
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

  it("leaves the columns button unannotated while the default columns are shown", () => {
    renderWithColumns(config.defaultColumns);

    expect(screen.getByRole("button", { name: "common:tableList.toolbar.columns" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "common:tableList.actions.resetToDefaults" })).not.toBeInTheDocument();
  });

  it("counts the visible columns once they differ from the default", () => {
    renderWithColumns(["title", "owner"]);

    const button = screen.getByRole("button", { name: "common:tableList.columns.customised" });
    expect(button).toHaveTextContent("2");
    expect(screen.getByRole("button", { name: "common:tableList.actions.resetToDefaults" })).toBeVisible();
  });

  it("flags a reordering of the default columns", () => {
    renderWithColumns(["owner", "title", "score", "enabled", "modifiedAt"]);

    expect(screen.getByRole("button", { name: "common:tableList.columns.customised" })).toBeInTheDocument();
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

    const reset = screen.getByRole("button", { name: "common:tableList.actions.resetToDefaults" });
    expect(reset).not.toHaveTextContent("common:tableList.actions.resetToDefaults");

    await user.click(reset);

    expect(onFiltersChange).toHaveBeenCalledWith(emptyFilters);
    expect(onSortingChange).toHaveBeenCalledWith(config.defaultSort);
    expect(onColumnsChange).toHaveBeenCalledWith(config.defaultColumns);
    expect(onPageChange).toHaveBeenCalledWith({ pageIndex: 0, pageSize: 2 });
  });
});
