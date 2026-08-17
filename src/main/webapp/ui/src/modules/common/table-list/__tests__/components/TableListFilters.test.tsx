import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { TableList } from "../../TableList";
import { config, emptyFilters, records } from "../fixtures/tableListFixtures";

type StatusRecord = { id: string; title: string; status: string };

const statusConfig = resolveCollectionConfig<StatusRecord>({
  slug: "statuses",
  idField: "id",
  labels: { singularKey: "tableList.examples.record", pluralKey: "tableList.examples.records" },
  useAsTitle: "title",
  defaultColumns: ["title", "status"],
  fields: [
    { name: "id", labelKey: "tableList.examples.fields.id", type: "text", list: false },
    { name: "title", labelKey: "tableList.examples.fields.title", type: "text" },
    {
      name: "status",
      labelKey: "tableList.examples.fields.status",
      type: "select",
      options: ["Draft", "Published"],
    },
  ],
});

const statusRecords: readonly StatusRecord[] = [
  { id: "1", title: "First", status: "Draft" },
  { id: "2", title: "Second", status: "Published" },
];

describe("TableList filters", () => {
  it("does not commit draft changes until Apply filters is selected", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <TableList
        queryString={false}
        config={config}
        rows={records}
        getRowId={(row) => row.id}
        features={{
          filtering: { value: emptyFilters, onChange },
          sorting: false,
          pagination: false,
          columns: false,
        }}
      />,
    );

    await user.click(screen.getByRole("button", { name: "common:tableList.filters.noneApplied" }));
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.addFilter" }));
    await user.type(screen.getByRole("textbox", { name: "common:tableList.filters.value" }), "Alpha");
    expect(onChange).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.applyFilters" }));
    expect(onChange).toHaveBeenCalledWith({
      search: "",
      expression: { kind: "comparison", field: "title", operator: "contains", value: "Alpha" },
    });
  });

  it("applies immediately when the filters are cleared", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <TableList
        queryString={false}
        config={config}
        rows={records}
        getRowId={(row) => row.id}
        features={{
          filtering: {
            value: { search: "", expression: { kind: "comparison", field: "title", operator: "contains", value: "A" } },
            onChange,
          },
          sorting: false,
          pagination: false,
          columns: false,
        }}
      />,
    );

    await user.click(screen.getByRole("button", { name: "common:tableList.filters.applied" }));
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.clearAll" }));

    expect(onChange).toHaveBeenCalledWith({ search: "", expression: null });
  });

  it("discards draft changes when the panel is closed", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <TableList
        queryString={false}
        config={config}
        rows={records}
        getRowId={(row) => row.id}
        features={{
          filtering: { value: emptyFilters, onChange },
          sorting: false,
          pagination: false,
          columns: false,
        }}
      />,
    );

    await user.click(screen.getByRole("button", { name: "common:tableList.filters.noneApplied" }));
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.addFilter" }));
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.closeFilters" }));
    expect(onChange).not.toHaveBeenCalled();
  });

  it("creates arbitrary text values as removable chips", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const { container } = render(
      <TableList
        queryString={false}
        config={config}
        rows={records}
        getRowId={(row) => row.id}
        features={{
          filtering: { value: emptyFilters, onChange },
          sorting: false,
          pagination: false,
          columns: false,
        }}
      />,
    );

    await user.click(screen.getByRole("button", { name: "common:tableList.filters.noneApplied" }));
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.addFilter" }));
    await user.selectOptions(screen.getByRole("combobox", { name: "common:tableList.filters.operator" }), "in");
    const values = screen.getByRole("combobox", { name: "common:tableList.filters.value" });
    await user.type(values, "Alpha, Inc.{Enter}");
    await user.type(values, "Beta{Enter}");
    await user.tab();

    const selectedValues = within(screen.getByRole("listitem"));
    expect(selectedValues.getByText("Alpha, Inc.")).toBeVisible();
    expect(selectedValues.getByText("Beta")).toBeVisible();
    await expectAccessible(container);
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.applyFilters" }));

    expect(onChange).toHaveBeenCalledWith({
      search: "",
      expression: { kind: "comparison", field: "title", operator: "in", value: ["Alpha, Inc.", "Beta"] },
    });
  });

  it("selects multiple values from a field's constant options", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <TableList
        queryString={false}
        config={statusConfig}
        rows={statusRecords}
        getRowId={(row) => row.id}
        features={{
          filtering: { value: { search: "", expression: null }, onChange },
          sorting: false,
          pagination: false,
          columns: false,
        }}
      />,
    );

    await user.click(screen.getByRole("button", { name: "common:tableList.filters.noneApplied" }));
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.addFilter" }));
    await user.selectOptions(screen.getByRole("combobox", { name: "common:tableList.filters.field" }), "status");
    await user.selectOptions(screen.getByRole("combobox", { name: "common:tableList.filters.operator" }), "notIn");
    const values = screen.getByRole("combobox", { name: "common:tableList.filters.value" });
    await user.click(values);
    await user.click(await screen.findByRole("option", { name: "Draft" }));
    await user.click(values);
    await user.click(await screen.findByRole("option", { name: "Published" }));
    await user.keyboard("{Escape}");
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.applyFilters" }));

    expect(onChange).toHaveBeenCalledWith({
      search: "",
      expression: { kind: "comparison", field: "status", operator: "notIn", value: ["Draft", "Published"] },
    });
  });

  it("selects an equals value from a field's constant options", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <TableList
        queryString={false}
        config={statusConfig}
        rows={statusRecords}
        getRowId={(row) => row.id}
        features={{
          filtering: { value: { search: "", expression: null }, onChange },
          sorting: false,
          pagination: false,
          columns: false,
        }}
      />,
    );

    await user.click(screen.getByRole("button", { name: "common:tableList.filters.noneApplied" }));
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.addFilter" }));
    await user.selectOptions(screen.getByRole("combobox", { name: "common:tableList.filters.field" }), "status");
    await user.selectOptions(screen.getByRole("combobox", { name: "common:tableList.filters.operator" }), "equals");
    await user.selectOptions(screen.getByRole("combobox", { name: "common:tableList.filters.value" }), "Draft");
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.applyFilters" }));

    expect(onChange).toHaveBeenCalledWith({
      search: "",
      expression: { kind: "comparison", field: "status", operator: "equals", value: "Draft" },
    });
  });

  it("does not offer list operators for datetime fields", async () => {
    const user = userEvent.setup();
    render(
      <TableList
        queryString={false}
        config={config}
        rows={records}
        getRowId={(row) => row.id}
        features={{
          filtering: { value: emptyFilters, onChange: vi.fn() },
          sorting: false,
          pagination: false,
          columns: false,
        }}
      />,
    );

    await user.click(screen.getByRole("button", { name: "common:tableList.filters.noneApplied" }));
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.addFilter" }));
    await user.selectOptions(screen.getByRole("combobox", { name: "common:tableList.filters.field" }), "modifiedAt");

    const operator = within(screen.getByRole("combobox", { name: "common:tableList.filters.operator" }));
    expect(operator.queryByRole("option", { name: "common:tableList.filters.operators.in" })).not.toBeInTheDocument();
    expect(
      operator.queryByRole("option", { name: "common:tableList.filters.operators.notIn" }),
    ).not.toBeInTheDocument();
  });
});
