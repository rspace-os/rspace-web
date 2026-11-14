import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { TableListSorting } from "../components/TableListSorting";
import { config } from "./fixtures/tableList";

describe("Sort priority", () => {
  it("offers only unused sortable fields and appends an ascending rule", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const fields = config.fields.map((field) =>
      field.name === "owner" ? { ...field, capabilities: { ...field.capabilities, sortable: false } } : field,
    );
    render(
      <TableListSorting
        config={{ ...config, fields }}
        sorting={[{ field: "title", direction: "desc" }]}
        onChange={onChange}
        onClose={vi.fn()}
      />,
    );
    await user.click(screen.getByRole("button", { name: "common:tableList.actions.addSort" }));
    const menu = await screen.findByRole("menu");
    expect(
      within(menu).queryByRole("menuitem", { name: "common:tableList.examples.fields.title" }),
    ).not.toBeInTheDocument();
    expect(
      within(menu).queryByRole("menuitem", { name: "common:tableList.examples.fields.owner" }),
    ).not.toBeInTheDocument();
    await user.click(within(menu).getByRole("menuitem", { name: "common:tableList.examples.fields.score" }));
    expect(onChange).toHaveBeenCalledWith([
      { field: "title", direction: "desc" },
      { field: "score", direction: "asc" },
    ]);
  });

  it("disables Add sort when every sortable field is used", () => {
    render(
      <TableListSorting
        config={config}
        sorting={[
          { field: "id", direction: "asc" },
          { field: "title", direction: "asc" },
          { field: "owner", direction: "asc" },
          { field: "score", direction: "asc" },
          { field: "enabled", direction: "asc" },
          { field: "modifiedAt", direction: "asc" },
        ]}
        onChange={vi.fn()}
        onClose={vi.fn()}
      />,
    );
    expect(screen.getByRole("button", { name: "common:tableList.actions.addSort" })).toBeDisabled();
  });
});
