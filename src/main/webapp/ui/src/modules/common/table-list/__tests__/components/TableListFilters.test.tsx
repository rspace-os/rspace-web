import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { queryKeys } from "@/modules/common/hooks/auth";
import type { RuntimeFieldDefinition } from "../../adapters/apiV2/runtimeFieldCatalog";
import { TableList } from "../../TableList";
import { config, emptyFilters, records } from "../fixtures/tableListFixtures";
import { chooseFilterField, chooseFilterOperator, chooseFilterValue } from "./chooseFilterField";

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
  it("returns the complete loaded definition when reusing a runtime field", async () => {
    const user = userEvent.setup();
    const onSelectRuntimeField = vi.fn();
    const definition: RuntimeFieldDefinition = {
      id: "SF104",
      selector: "customFields.SF104",
      label: "Hazard class",
      type: "radio",
      jsonType: "string",
      operators: ["==", "!=", "=in=", "=out=", "=exists="],
      supportsWildcards: true,
      columnSelectable: true,
      sortable: false,
      source: { id: "IT9", label: "Cell line template" },
      options: ["BSL-1", "BSL-2"],
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
      fields: [
        ...config.fields,
        {
          ...config.fields[1],
          name: "customFields.SF104",
          labelKey: definition.selector,
          label: definition.label,
          form: false,
          capabilities: {
            sortable: false,
            filterOperators: ["equals", "notEquals", "in", "notIn", "exists"],
            supportsWildcards: true,
          },
          origin: {
            kind: "runtimeField",
            groupLabelKey: "tableList.fieldGroups.customFields",
            sourceLabel: definition.source.label,
            stableId: definition.id,
            namespace: "customFields",
            viaLabel: "",
          },
        },
      ],
    } as unknown as typeof config;
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    queryClient.setQueryData(queryKeys.oauthToken(true), "test-token");
    render(
      <QueryClientProvider client={queryClient}>
        <TableList
          queryString={false}
          config={runtimeConfig}
          rows={records}
          getRowId={(row) => row.id}
          runtimeFieldDefinitions={[
            { namespace: "customFields", definitions: [definition] },
            { namespace: "extraFields", definitions: [] },
          ]}
          onSelectRuntimeField={onSelectRuntimeField}
          features={{
            filtering: { value: emptyFilters, onChange: vi.fn() },
            sorting: false,
            pagination: false,
            columns: false,
          }}
        />
      </QueryClientProvider>,
    );

    await user.click(screen.getByRole("button", { name: "common:tableList.filters.noneApplied" }));
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.addFilter" }));
    await chooseFilterField(user, "common:tableList.filters.customField.option");
    await user.click(screen.getByRole("combobox", { name: /common:tableList.filters.customField.searchLabel/ }));
    await user.click(await screen.findByRole("option", { name: /Hazard class/ }));

    expect(onSelectRuntimeField).toHaveBeenCalledWith("customFields", definition);
  });

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

  it("uses user-entered chips without suggesting values for a free-text in filter", async () => {
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
    await chooseFilterOperator(user, "common:tableList.filters.operators.in");
    const values = screen.getByRole("combobox", { name: "common:tableList.filters.value" });
    await user.click(values);
    expect(screen.queryByRole("option")).not.toBeInTheDocument();
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

  it("makes every filter row use the list's shared column tracks", async () => {
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
          columns: { value: config.defaultColumns, onChange: vi.fn() },
        }}
      />,
    );

    await user.click(screen.getByRole("button", { name: "common:tableList.filters.noneApplied" }));
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.addFilter" }));

    const rules = screen.getByRole("list", { name: "common:tableList.filters.title" });
    expect(rules.className).toContain("sm:grid-cols-");
    expect(within(rules).getByRole("listitem")).toHaveClass("sm:grid-cols-subgrid");
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
    await chooseFilterField(user, /status/i);
    await chooseFilterOperator(user, "common:tableList.filters.operators.notIn");
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
    await chooseFilterField(user, /status/i);
    await chooseFilterOperator(user, "common:tableList.filters.operators.equals");
    await chooseFilterValue(user, "Draft");
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
    await chooseFilterField(user, /fields\.modified/);

    await user.click(screen.getByRole("combobox", { name: "common:tableList.filters.operator" }));
    expect(await screen.findByRole("option", { name: "common:tableList.filters.operators.equals" })).toBeVisible();
    expect(screen.queryByRole("option", { name: "common:tableList.filters.operators.in" })).not.toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "common:tableList.filters.operators.notIn" })).not.toBeInTheDocument();
  });
});
