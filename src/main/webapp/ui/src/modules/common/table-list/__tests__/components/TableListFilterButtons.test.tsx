import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { TableList } from "../../TableList";
import { config, emptyFilters, records } from "../fixtures/tableListFixtures";

function Harness() {
  const [mode, setMode] = useState<"now" | "later" | null>(null);
  return (
    <TableList
      queryString={false}
      config={config}
      rows={records}
      getRowId={(row) => row.id}
      features={{
        filtering: { value: emptyFilters, onChange: () => undefined },
        sorting: false,
        pagination: false,
        columns: false,
      }}
      filterButtons={{
        legend: "Quick filters",
        buttons: [
          {
            id: "now",
            label: "Available now",
            pressed: mode === "now",
            count: 2,
            onClick: () => setMode(mode === "now" ? null : "now"),
          },
          {
            id: "later",
            label: "Free later today",
            pressed: mode === "later",
            disabled: true,
            onClick: () => setMode("later"),
          },
        ],
        onReset: () => setMode(null),
      }}
    />
  );
}

describe("TableList filter buttons", () => {
  it("lets a controlled page reset its whole view in one update", async () => {
    const user = userEvent.setup();
    const reset = vi.fn();
    const changeFilters = vi.fn();
    const changePage = vi.fn();
    render(
      <TableList
        queryString={false}
        config={config}
        rows={records}
        getRowId={(row) => row.id}
        onReset={reset}
        features={{
          filtering: { value: { search: "microscope", expression: null }, onChange: changeFilters },
          pagination: { value: { pageIndex: 2, pageSize: 20 }, rowCount: 100, onChange: changePage },
          sorting: false,
          columns: false,
        }}
      />,
    );
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.resetToDefaults" }));
    expect(reset).toHaveBeenCalledOnce();
    expect(changeFilters).not.toHaveBeenCalled();
    expect(changePage).not.toHaveBeenCalled();
  });

  it("renders page-owned counts and states and includes them in reset", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const availableNow = screen.getByRole("button", { name: "Available now" });
    expect(within(availableNow).getByText("2")).toBeVisible();
    expect(availableNow).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("button", { name: "Free later today" })).toBeDisabled();

    await user.click(availableNow);
    expect(availableNow).toHaveAttribute("aria-pressed", "true");
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.resetToDefaults" }));
    expect(availableNow).toHaveAttribute("aria-pressed", "false");
  });
});
