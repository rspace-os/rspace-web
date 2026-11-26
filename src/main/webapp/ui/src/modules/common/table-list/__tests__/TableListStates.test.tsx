import { onlineManager } from "@tanstack/react-query";
import { act, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { TableList } from "../TableList";
import { LocalizedTableListError } from "../utils/errors";
import { config, emptyFilters, records } from "./fixtures/tableList";

const disabledFeatures = { filtering: false, sorting: false, pagination: false, columns: false } as const;

describe("TableList states", () => {
  it("explains offline remote requests and clears the notice on reconnect without hiding cached rows", () => {
    const props = {
      config,
      rows: records,
      getRowId: (row: (typeof records)[number]) => row.id,
      features: disabledFeatures,
    };
    const { rerender } = render(<TableList {...props} clientSide={false} />);
    try {
      act(() => onlineManager.setOnline(false));
      expect(screen.getByRole("status")).toHaveTextContent("common:tableList.offline");
      expect(screen.getByRole("cell", { name: "Alpha" })).toBeVisible();
      rerender(<TableList {...props} rows={[]} clientSide={false} status="loading" />);
      expect(screen.getByText("common:tableList.offline")).toBeVisible();
      act(() => onlineManager.setOnline(true));
      expect(screen.queryByText("common:tableList.offline")).not.toBeInTheDocument();
      expect(screen.getByRole("table")).toHaveAttribute("aria-busy", "true");
      act(() => onlineManager.setOnline(false));
      rerender(<TableList {...props} clientSide={true} />);
      expect(screen.queryByText("common:tableList.offline")).not.toBeInTheDocument();
    } finally {
      act(() => onlineManager.setOnline(true));
    }
  });

  it.each([
    {
      label: "card-only lists",
      presentations: { table: false as const, cards: "all" as const },
      busyRegion: () => screen.getByRole("region", { name: "common:tableList.cardView" }),
      error: new LocalizedTableListError("Select at most 2 custom field columns."),
      errorDescription: "Select at most 2 custom field columns.",
    },
    {
      label: "tables",
      presentations: undefined,
      busyRegion: () => screen.getByRole("table"),
      error: new Error("Expected failure"),
      errorDescription: "common:tableList.error.description",
    },
  ])("renders loading, empty, and error states in $label", ({ presentations, busyRegion, error, errorDescription }) => {
    const commonProps = {
      config,
      rows: [],
      getRowId: (row: (typeof records)[number]) => row.id,
      presentations,
      features: disabledFeatures,
    };
    const { rerender } = render(<TableList {...commonProps} status="loading" />);
    expect(busyRegion()).toHaveAttribute("aria-busy", "true");
    expect(within(busyRegion()).queryAllByRole("listitem")).toHaveLength(0);

    rerender(<TableList {...commonProps} />);
    expect(screen.getByText("common:tableList.empty.title")).toBeVisible();

    rerender(<TableList {...commonProps} status="error" error={error} />);
    expect(screen.getByText("common:tableList.error.title")).toBeVisible();
    expect(screen.getByText(errorDescription)).toBeVisible();
  });

  it("hides independently disabled features and passes accessibility checks", async () => {
    const { container } = render(
      <TableList config={config} rows={records} getRowId={(row) => row.id} features={disabledFeatures} />,
    );
    expect(screen.queryByRole("textbox", { name: "common:tableList.search.label" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "common:tableList.actions.nextPage" })).not.toBeInTheDocument();
    expect(screen.getByRole("table")).toBeVisible();
    expect(screen.queryByRole("region", { name: "common:tableList.cardView" })).not.toBeInTheDocument();
    await expectAccessible(container);
  });

  it("keeps search available for an empty searchable collection", () => {
    render(
      <TableList
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

  it("shows a non-destructive refresh status while preserving rows", () => {
    render(
      <TableList
        config={config}
        rows={records}
        getRowId={(row) => row.id}
        status="refreshing"
        features={disabledFeatures}
      />,
    );
    expect(screen.getByRole("status")).toHaveTextContent("common:tableList.refreshing");
    expect(screen.getByRole("cell", { name: "Alpha" })).toBeVisible();
  });
});
