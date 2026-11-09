import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { TableList } from "../TableList";
import type { FilterState, TableListFeatures } from "../types";
import { config, records, type TestRecord } from "./fixtures/tableList";

function renderRemoteSearch(filters: FilterState<TestRecord>) {
  const onFiltersChange = vi.fn();
  const onPageChange = vi.fn();
  const features: TableListFeatures<TestRecord> = {
    filtering: { value: filters, onChange: onFiltersChange },
    sorting: false,
    pagination: { value: { pageIndex: 2, pageSize: 2 }, rowCount: records.length, onChange: onPageChange },
    columns: false,
  };
  render(
    <TableList config={config} rows={records} getRowId={(row) => row.id} features={features} clientSide={false} />,
  );
  return { onFiltersChange, onPageChange };
}

async function advancePastDebounce() {
  await new Promise((resolve) => setTimeout(resolve, 350));
}

describe("TableList search", () => {
  it("commits immediately on Enter, cancels the pending commit, and returns to the first page", async () => {
    const user = userEvent.setup();
    const { onFiltersChange, onPageChange } = renderRemoteSearch({ search: "", expression: null });
    await user.type(screen.getByRole("textbox", { name: "common:tableList.search.label" }), "Ada{Enter}");

    expect(onFiltersChange).toHaveBeenCalledOnce();
    expect(onFiltersChange).toHaveBeenCalledWith({ search: "Ada", expression: null });
    expect(onPageChange).toHaveBeenCalledWith({ pageIndex: 0, pageSize: 2 });
    await advancePastDebounce();
    expect(onFiltersChange).toHaveBeenCalledOnce();
  });

  it.each([
    ["clears immediately and cancels a pending search", "common:tableList.search.clear"],
    ["cancels a pending search when the table view is reset", "common:tableList.actions.resetToDefaults"],
  ])("%s", async (_label, buttonName) => {
    const user = userEvent.setup();
    const { onFiltersChange } = renderRemoteSearch({ search: "Existing", expression: null });
    const input = screen.getByRole("textbox", { name: "common:tableList.search.label" });

    await user.clear(input);
    await user.type(input, "Ada");
    await user.click(screen.getByRole("button", { name: buttonName }));

    expect(onFiltersChange).toHaveBeenCalledOnce();
    expect(onFiltersChange).toHaveBeenCalledWith({ search: "", expression: null });
    await advancePastDebounce();
    expect(onFiltersChange).toHaveBeenCalledOnce();
  });

  it("drops a draft when the committed search changes while it waits, even back to the earlier value", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const table = (search: string) => (
      <TableList
        config={config}
        rows={records}
        getRowId={(row) => row.id}
        clientSide={false}
        features={{
          filtering: { value: { search, expression: null }, onChange },
          sorting: false,
          pagination: false,
          columns: false,
        }}
      />
    );
    const { rerender } = render(table("A"));
    const input = screen.getByRole("textbox", { name: "common:tableList.search.label" });

    await user.type(input, "da");
    rerender(table("B"));
    expect(input).toHaveValue("B");
    rerender(table("A"));
    expect(input).toHaveValue("A");

    await advancePastDebounce();
    expect(onChange).not.toHaveBeenCalled();
  });
});
