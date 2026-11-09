import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { TableListColumns } from "../components/TableListColumns";
import { TableListSorting } from "../components/TableListSorting";
import { TableList } from "../TableList";
import { config } from "./fixtures/tableList";

// dnd-kit portals its screen-reader instructions to document.body, so these queries use `screen`.
const instructions = "common:tableList.dragAndDrop.instructions";

describe("Table list drag-and-drop instructions", () => {
  it("are translated in the sort panel", async () => {
    render(
      <TableListSorting
        controlPanelId="table-list-control-panel"
        config={config}
        sorting={[{ field: "title", direction: "asc" }]}
        onChange={vi.fn()}
        onClose={vi.fn()}
      />,
    );
    expect(await screen.findByText(instructions)).toBeInTheDocument();
  });

  it("are translated in the filter panel", async () => {
    const user = userEvent.setup();
    render(
      <TableList
        config={config}
        rows={[]}
        getRowId={(row) => String(row.id)}
        features={{
          filtering: { value: { search: "", expression: null }, onChange: vi.fn() },
          sorting: false,
          pagination: false,
          columns: false,
        }}
      />,
    );
    await user.click(screen.getByRole("button", { name: "common:tableList.filters.noneApplied" }));
    expect(await screen.findByText(instructions)).toBeInTheDocument();
  });

  it("are translated in the column panel", async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <TableListColumns
          controlPanelId="table-list-control-panel"
          config={config}
          visibleFields={["title"]}
          onChange={vi.fn()}
          onClose={vi.fn()}
        />
      </QueryClientProvider>,
    );
    expect(await screen.findByText(instructions)).toBeInTheDocument();
  });
});
