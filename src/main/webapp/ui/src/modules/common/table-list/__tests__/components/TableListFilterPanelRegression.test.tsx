import { render, screen } from "@testing-library/react";
import type { UserEvent } from "@testing-library/user-event";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { parseRsqlExpression } from "../../rsql/rsqlCodec";
import { TableList } from "../../TableList";
import type { FilterExpression, FilterState } from "../../tableListState";
import { config, records, type TestRecord } from "../fixtures/tableListFixtures";
import { chooseFilterField, chooseFilterOperator } from "./chooseFilterField";

function tableList(
  expression: FilterExpression<TestRecord> | null,
  onChange: (value: FilterState<TestRecord>) => void,
) {
  return (
    <TableList
      queryString={false}
      config={config}
      rows={records}
      getRowId={(row) => row.id}
      features={{
        filtering: { value: { search: "", expression }, onChange },
        sorting: false,
        pagination: false,
        columns: false,
      }}
    />
  );
}

async function openFilterPanel(user: UserEvent, hasFilters: boolean): Promise<void> {
  await user.click(
    screen.getByRole("button", {
      name: hasFilters ? "common:tableList.filters.applied" : "common:tableList.filters.noneApplied",
    }),
  );
}

describe("TableList filter panel regressions", () => {
  it("converts an entered local datetime to an instant when applying", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(tableList(null, onChange));

    await openFilterPanel(user, false);
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.addFilter" }));
    await chooseFilterField(user, "common:tableList.examples.fields.modified");
    await chooseFilterOperator(user, "common:tableList.filters.operators.equals");

    const value = screen.getByLabelText<HTMLInputElement>("common:tableList.filters.value");
    expect(value).toHaveAttribute("type", "datetime-local");
    await user.type(value, "2026-08-01T10:15");
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.applyFilters" }));

    expect(onChange).toHaveBeenCalledWith({
      search: "",
      expression: {
        kind: "comparison",
        field: "modifiedAt",
        operator: "equals",
        value: new Date("2026-08-01T10:15"),
      },
    });
  });

  it("shows and reapplies a restored datetime in local time without shifting the instant", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const instant = new Date("2026-08-01T14:15:30.250Z");
    const expression = parseRsqlExpression("modifiedAt==2026-08-01T14:15:30.250Z", config);
    if (!expression) throw new Error("Expected the saved datetime filter to parse");

    render(tableList(expression, onChange));
    await openFilterPanel(user, true);

    const value = screen.getByLabelText<HTMLInputElement>("common:tableList.filters.value");
    expect(value).toHaveAttribute("type", "datetime-local");
    expect(value).toHaveAttribute("step", "any");
    expect(new Date(value.value).toISOString()).toBe(instant.toISOString());

    await user.click(screen.getByRole("button", { name: "common:tableList.actions.applyFilters" }));
    expect(onChange).toHaveBeenCalledWith({ search: "", expression });
  });

  it.each([
    { name: "ISO string", instant: "2026-08-01T14:15:30.250Z", asString: true },
    { name: "DST overlap", instant: "2026-10-25T01:30:30.250Z", asString: false },
  ])("reapplies an unchanged $name operand without changing the instant", async ({ instant, asString }) => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const expression: FilterExpression<TestRecord> = {
      kind: "comparison",
      field: "modifiedAt",
      operator: "equals",
      value: asString ? instant : new Date(instant),
    };
    render(tableList(expression, onChange));
    await openFilterPanel(user, true);
    const value = screen.getByLabelText<HTMLInputElement>("common:tableList.filters.value");
    expect(new Date(value.value).getTime()).toBeGreaterThan(0);
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.applyFilters" }));
    expect(onChange).toHaveBeenCalledWith({
      search: "",
      expression: { ...expression, value: new Date(instant) },
    });
  });

  it("preserves a root OR group when Apply is selected unchanged", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const expression: FilterExpression<TestRecord> = {
      kind: "or",
      children: [
        { kind: "comparison", field: "title", operator: "equals", value: "Alpha" },
        { kind: "comparison", field: "title", operator: "equals", value: "Beta" },
      ],
    };
    render(tableList(expression, onChange));

    await openFilterPanel(user, true);
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.applyFilters" }));

    expect(onChange).toHaveBeenCalledWith({ search: "", expression });
  });

  it("preserves an OR group nested under AND when Apply is selected unchanged", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const expression: FilterExpression<TestRecord> = {
      kind: "and",
      children: [
        { kind: "comparison", field: "title", operator: "equals", value: "Alpha" },
        {
          kind: "or",
          children: [
            { kind: "comparison", field: "owner", operator: "equals", value: "Ada" },
            { kind: "comparison", field: "owner", operator: "equals", value: "Grace" },
          ],
        },
      ],
    };
    render(tableList(expression, onChange));

    await openFilterPanel(user, true);
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.applyFilters" }));

    expect(onChange).toHaveBeenCalledWith({ search: "", expression });
  });

  it("lets Clear All explicitly remove a preserved root group", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const expression: FilterExpression<TestRecord> = {
      kind: "or",
      children: [
        { kind: "comparison", field: "title", operator: "equals", value: "Alpha" },
        { kind: "comparison", field: "title", operator: "equals", value: "Beta" },
      ],
    };
    render(tableList(expression, onChange));

    await openFilterPanel(user, true);
    const clearAll = screen.getByRole("button", { name: "common:tableList.actions.clearAll" });
    expect(clearAll).toBeEnabled();
    await user.click(clearAll);

    expect(onChange).toHaveBeenCalledWith({ search: "", expression: null });
  });
});
