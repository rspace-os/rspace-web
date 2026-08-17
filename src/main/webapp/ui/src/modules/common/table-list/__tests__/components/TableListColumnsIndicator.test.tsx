import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
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

describe("TableList columns indicator", () => {
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
