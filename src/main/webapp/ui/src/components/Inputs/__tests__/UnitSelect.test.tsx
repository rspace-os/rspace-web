import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import UnitSelect from "../UnitSelect";

describe("UnitSelect", () => {
  it("shows the chosen unit's label", () => {
    render(<UnitSelect categories={["volume"]} value={3} handleChange={() => undefined} />);
    expect(screen.getByRole("combobox")).toHaveTextContent("mL");
  });

  it("renders empty for the unset-unit marker instead of an out-of-range value", () => {
    render(<UnitSelect categories={["volume"]} value={0} handleChange={() => undefined} />);
    expect(screen.getByRole("combobox")).toHaveTextContent(/^[​\s]*$/);
  });
});
