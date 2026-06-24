import { describe, expect, test } from "vitest";
import { render, screen } from "@/__tests__/customQueries";
import TimeField from "../TimeField";

describe("TimeField", () => {
  test("renders a Date value (as FieldModel supplies for time fields) without crashing", () => {
    // Regression: complex templates feed a Date here; date-fns parse threw on
    // it after the MUI v9 upgrade ("dateString.match is not a function"),
    // crashing the whole template form. Rendering at all is the key assertion.
    render(<TimeField value={new Date(2026, 0, 1, 12, 30)} onChange={() => {}} />);
    expect(screen.getByDisplayValue("12:30")).toBeInTheDocument();
  });

  test("renders an HH:mm string value", () => {
    render(<TimeField value="09:05" onChange={() => {}} />);
    expect(screen.getByDisplayValue("09:05")).toBeInTheDocument();
  });

  test("renders a null value without crashing", () => {
    render(<TimeField value={null} onChange={() => {}} />);
    expect(screen.getByDisplayValue("")).toBeInTheDocument();
  });

  test("shows the no-value label when disabled with no value", () => {
    render(<TimeField value="" onChange={() => {}} disabled />);
    expect(screen.getByText("values.none")).toBeInTheDocument();
  });
});
