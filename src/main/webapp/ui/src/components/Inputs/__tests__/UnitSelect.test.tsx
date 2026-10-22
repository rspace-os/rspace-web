import type { SelectChangeEvent } from "@mui/material/Select";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import UnitSelect from "../UnitSelect";

vi.mock("@/stores/use-stores", () => ({
  default: () => ({
    unitStore: {
      unitsOfCategory: (categories: Array<string>) =>
        [
          { id: 1, label: "common:units.items", category: "dimensionless" },
          { id: 3, label: "ml", category: "volume" },
          { id: 4, label: "l", category: "volume" },
        ].filter((unit) => categories.includes(unit.category)),
    },
  }),
}));

describe("UnitSelect", () => {
  it("shows the chosen unit's label", () => {
    render(<UnitSelect categories={["volume"]} value={3} handleChange={() => undefined} />);
    expect(screen.getByRole("combobox")).toHaveTextContent("ml");
  });

  it("renders empty for the unset-unit marker instead of an out-of-range value", () => {
    render(<UnitSelect categories={["volume"]} value={0} handleChange={() => undefined} />);
    expect(screen.getByRole("combobox")).toHaveTextContent(/^[​\s]*$/);
  });

  it("keeps the unit id when selecting its translated label", async () => {
    const user = userEvent.setup();
    const handleChange = vi.fn<(event: SelectChangeEvent<number>) => void>();

    render(<UnitSelect categories={["dimensionless"]} value={0} handleChange={handleChange} />);

    await user.click(screen.getByRole("combobox"));
    const listbox = await screen.findByRole("listbox");
    await user.click(within(listbox).getByRole("option", { name: "common:units.items" }));

    expect(handleChange).toHaveBeenCalledTimes(1);
    expect(handleChange.mock.calls[0]?.[0].target.value).toBe(1);
  });
});
