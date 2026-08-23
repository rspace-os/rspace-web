import { render, screen } from "@testing-library/react";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { TableList } from "../../TableList";
import { config, emptyFilters, records } from "../fixtures/tableListFixtures";

const disabledFeatures = { filtering: false, sorting: false, pagination: false, columns: false } as const;

describe("TableList states", () => {
  it("renders loading, empty, and error states in card-only lists", () => {
    const commonProps = {
      queryString: false as const,
      config,
      rows: [],
      getRowId: (row: (typeof records)[number]) => row.id,
      presentations: { table: false as const, cards: "all" as const },
      features: disabledFeatures,
    };
    const { rerender } = render(<TableList {...commonProps} status="loading" />);
    expect(screen.getByRole("region", { name: "common:tableList.cardView" })).toHaveAttribute("aria-busy", "true");

    rerender(<TableList {...commonProps} />);
    expect(screen.getByText("common:tableList.empty.title")).toBeVisible();

    rerender(<TableList {...commonProps} status="error" error={new Error("Expected card failure")} />);
    expect(screen.getByText("common:tableList.error.title")).toBeVisible();
    expect(screen.getByText("Expected card failure")).toBeVisible();
  });

  it("renders loading, empty, and error states", () => {
    const { rerender } = render(
      <TableList
        queryString={false}
        config={config}
        rows={[]}
        getRowId={(row) => row.id}
        status="loading"
        features={disabledFeatures}
      />,
    );
    expect(screen.getByRole("table")).toHaveAttribute("aria-busy", "true");

    rerender(
      <TableList
        queryString={false}
        config={config}
        rows={[]}
        getRowId={(row) => row.id}
        features={disabledFeatures}
      />,
    );
    expect(screen.getByText("common:tableList.empty.title")).toBeVisible();

    rerender(
      <TableList
        queryString={false}
        config={config}
        rows={[]}
        getRowId={(row) => row.id}
        status="error"
        error={new Error("Expected failure")}
        features={disabledFeatures}
      />,
    );
    expect(screen.getByText("common:tableList.error.title")).toBeVisible();
    expect(screen.getByText("Expected failure")).toBeVisible();
  });

  it("hides independently disabled features and passes accessibility checks", async () => {
    const { container } = render(
      <TableList
        queryString={false}
        config={config}
        rows={records}
        getRowId={(row) => row.id}
        features={disabledFeatures}
      />,
    );
    expect(screen.queryByRole("searchbox")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "common:tableList.actions.nextPage" })).not.toBeInTheDocument();
    await expectAccessible(container);
  });

  it("keeps search available for an empty searchable collection", () => {
    render(
      <TableList
        queryString={false}
        config={config}
        rows={[]}
        getRowId={(row) => row.id}
        features={{
          filtering: { value: emptyFilters, onChange: vi.fn() },
          sorting: false,
          pagination: false,
          columns: false,
        }}
      />,
    );

    expect(screen.getByRole("textbox", { name: "common:tableList.search.label" })).toBeVisible();
  });

  it("reserves ten rows for the empty state unless disabled", () => {
    const { rerender } = render(
      <TableList
        queryString={false}
        config={config}
        rows={[]}
        getRowId={(row) => row.id}
        features={disabledFeatures}
      />,
    );
    expect(screen.getByRole("cell")).toHaveClass("h-90");

    rerender(
      <TableList
        queryString={false}
        config={config}
        rows={[]}
        getRowId={(row) => row.id}
        features={disabledFeatures}
        reserveEmptyRows={false}
      />,
    );
    expect(screen.getByRole("cell")).toHaveClass("h-40");
  });

  it("shows a non-destructive refresh status while preserving rows", () => {
    function Harness() {
      const [filters, setFilters] = useState(emptyFilters);
      return (
        <TableList
          queryString={false}
          config={config}
          rows={records}
          getRowId={(row) => row.id}
          status="refreshing"
          features={{
            filtering: { value: filters, onChange: setFilters },
            sorting: false,
            pagination: false,
            columns: false,
          }}
        />
      );
    }
    render(<Harness />);
    expect(screen.getByRole("status")).toHaveTextContent("common:tableList.refreshing");
    expect(screen.getByRole("cell", { name: "Alpha" })).toBeVisible();
  });
});
