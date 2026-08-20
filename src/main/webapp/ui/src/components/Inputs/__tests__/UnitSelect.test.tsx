import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import UnitSelect from "../UnitSelect";

// A minimal unit store: two volume units, so the dropdown has real options to (not) display.
vi.mock("@/stores/use-stores", () => ({
  default: () => ({
    unitStore: {
      unitsOfCategory: () => [
        { id: 3, label: "ml" },
        { id: 4, label: "l" },
      ],
      getUnit: (id: number) => (id === 3 ? { id: 3, label: "ml" } : id === 4 ? { id: 4, label: "l" } : null),
    },
  }),
}));

describe("UnitSelect", () => {
  it("shows the chosen unit's label", () => {
    render(<UnitSelect categories={["volume"]} value={3} handleChange={() => undefined} />);
    expect(screen.getByRole("combobox")).toHaveTextContent("ml");
  });

  it("renders empty for the unset-unit marker instead of an out-of-range value", () => {
    // A non-positive id means "no unit chosen" (the operation wizard clears the unit when a picked
    // template changes the measurement category); MUI would warn on an out-of-range value.
    render(<UnitSelect categories={["volume"]} value={0} handleChange={() => undefined} />);
    expect(screen.getByRole("combobox")).toHaveTextContent(/^[​\s]*$/);
  });
});
