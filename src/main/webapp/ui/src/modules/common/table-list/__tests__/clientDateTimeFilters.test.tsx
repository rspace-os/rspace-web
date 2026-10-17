import { render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { parseRsqlExpression, serializeRsqlExpression } from "../rsql/rsqlCodec";
import { TableList } from "../TableList";
import type { FilterExpression, FilterState } from "../tableListState";
import { config, records, type TestRecord } from "./fixtures/tableListFixtures";

type DateTimeCase = {
  name: string;
  operator: "equals" | "greaterThan";
  value: string | readonly string[];
  expectedIds: readonly string[];
};

const cases: readonly DateTimeCase[] = [
  {
    name: "equality",
    operator: "equals",
    value: "2026-08-02T10:00:00Z",
    expectedIds: ["3"],
  },
  {
    name: "ordering",
    operator: "greaterThan",
    value: "2026-08-01T10:00:00Z",
    expectedIds: ["2", "3"],
  },
];

function table(filters: FilterState<TestRecord>) {
  return (
    <TableList
      queryString={false}
      config={config}
      rows={records}
      getRowId={(row) => row.id}
      clientSide
      renderRows={(rows) => (
        <ol aria-label="Filtered record IDs">
          {rows.map((row) => (
            <li key={row.id}>{row.id}</li>
          ))}
        </ol>
      )}
      features={{
        filtering: { value: filters, onChange: vi.fn() },
        sorting: false,
        pagination: false,
        columns: false,
      }}
    />
  );
}

function visibleIds(): readonly string[] {
  return within(screen.getByRole("list", { name: "Filtered record IDs" }))
    .getAllByRole("listitem")
    .map((item) => item.textContent ?? "");
}

describe("client-side date-time filters", () => {
  it.each(cases)("keeps $name filtering stable when the URL restores a Date", ({ operator, value, expectedIds }) => {
    const initialExpression: FilterExpression<TestRecord> = {
      kind: "comparison",
      field: "modifiedAt",
      operator,
      value,
    };
    const serialized = serializeRsqlExpression(initialExpression);
    const restoredExpression = parseRsqlExpression(serialized, config);

    expect(restoredExpression).not.toBeNull();
    if (restoredExpression?.kind !== "comparison") {
      throw new Error("Expected a restored date-time comparison");
    }
    const restoredValues = Array.isArray(restoredExpression.value)
      ? restoredExpression.value
      : [restoredExpression.value];
    expect(restoredValues.every((item) => item instanceof Date)).toBe(true);

    const initialFilters: FilterState<TestRecord> = { search: "", expression: initialExpression };
    const restoredFilters: FilterState<TestRecord> = { search: "", expression: restoredExpression };
    const { rerender } = render(table(initialFilters));

    expect(visibleIds()).toEqual(expectedIds);

    rerender(table(restoredFilters));

    expect(visibleIds()).toEqual(expectedIds);
  });
});
