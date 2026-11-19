import { describe, expect, it, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { makeMockInstrument } from "@/stores/models/__tests__/InstrumentModel/mocking";
import materialTheme from "@/theme";
import TemplateFields from "../TemplateFields";

// The picker reports a time it cannot read only in states hard to type in jsdom, so a stub reports one.
vi.mock("@/components/Inputs/TimeField", () => ({
  default: ({ onChange }: { onChange: (event: { target: { value: string | null; valid: boolean } }) => void }) => (
    <button
      type="button"
      aria-label="Enter an incomplete time"
      onClick={() => onChange({ target: { value: null, valid: false } })}
    />
  ),
}));

describe("TemplateFields -- time field", () => {
  it("refuses a time the picker reports invalid", async () => {
    const user = userEvent.setup();
    const instrument = makeMockInstrument({
      fields: [
        {
          id: 11,
          globalId: "IF11",
          name: "Start time",
          type: "time",
          content: "",
          selectedOptions: null,
          columnIndex: 1,
          attachment: null,
          mandatory: false,
        },
      ],
    });
    instrument.currentlyEditableFields.add("fields");
    render(
      <ThemeProvider theme={materialTheme}>
        <TemplateFields onErrorStateChange={() => {}} instrument={instrument} />
      </ThemeProvider>,
    );

    await user.click(screen.getByRole("button", { name: "Enter an incomplete time" }));

    expect(instrument.submittable.isOk).toBe(false);
  });
});
