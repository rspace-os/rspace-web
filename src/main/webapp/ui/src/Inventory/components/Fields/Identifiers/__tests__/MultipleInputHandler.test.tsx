import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import type { IdentifierField } from "../../../../../stores/definitions/Identifier";
import type { InventoryRecord } from "../../../../../stores/definitions/InventoryRecord";
import materialTheme from "../../../../../theme";
import MultipleInputHandler from "../MultipleInputHandler";
import "@/__tests__/__mocks__/matchMedia";

const mockActiveResult = {
  updateIdentifiers: vi.fn(),
} as unknown as InventoryRecord;

const Wrapper = ({ children }: { children: React.ReactNode }) => (
  <ThemeProvider theme={materialTheme}>{children}</ThemeProvider>
);

const datesField = (dateValue: Date): IdentifierField => ({
  key: "Dates",
  value: [{ value: dateValue, type: "CREATED" }],
  options: [{ value: "CREATED", label: "inventory:identifierModel.dateTypes.created" }],
  selectLabelLabel: "inventory:fields.identifiers.dates.typeLabel",
  handler: vi.fn(),
});

describe("MultipleInputHandler — Dates field", () => {
  test("a Date value in editable mode does not show the Invalid Date error", () => {
    const { container } = render(
      <MultipleInputHandler field={datesField(new Date(2027, 4, 25))} activeResult={mockActiveResult} editable />,
      { wrapper: Wrapper },
    );
    expect(container).not.toHaveTextContent("common:inputs.dateField.invalidDate");
  });

  test("in read-only mode a Date value is displayed as a yyyy-MM-dd string", () => {
    render(
      <MultipleInputHandler
        field={datesField(new Date(2027, 4, 25))}
        activeResult={mockActiveResult}
        editable={false}
      />,
      { wrapper: Wrapper },
    );
    expect(screen.getByText("2027-05-25")).toBeInTheDocument();
  });
});
