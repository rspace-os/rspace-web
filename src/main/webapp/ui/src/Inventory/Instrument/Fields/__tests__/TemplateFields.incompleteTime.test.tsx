import { describe, expect, it, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { setupRealAppI18n } from "@/__tests__/helpers/realI18n";
import { createDisplayRule } from "@/modules/common/field-rules/displayRule";
import { makeMockInstrument } from "@/stores/models/__tests__/InstrumentModel/mocking";
import type { FieldModelAttrs } from "@/stores/models/FieldModel";
import materialTheme from "@/theme";
import TemplateFields from "../TemplateFields";

/*
 * Simulates a picker-reported error: the stub calls onChange with valid: false
 * for an incomplete entry, which the real MUI X picker does not do for hours
 * typed into an empty field (it keeps the sections and publishes nothing).
 * These tests pin how Inventory handles an invalid callback, and that a
 * component holding its own text keeps it while hidden.
 */
vi.mock("@/components/Inputs/TimeField", async () => {
  const { useState } = await import("react");
  return {
    default: function StubTimeField({
      id,
      onChange,
    }: {
      id?: string;
      onChange: (event: { target: { value: string | null; valid: boolean } }) => void;
    }) {
      const [text, setText] = useState("");
      return (
        <input
          id={id}
          value={text}
          onChange={({ target }) => {
            setText(target.value);
            const complete = /^\d\d:\d\d$/.test(target.value);
            onChange({ target: { value: complete ? target.value : null, valid: complete || target.value === "" } });
          }}
        />
      );
    },
  };
});

const startTime: FieldModelAttrs = {
  id: 11,
  globalId: "IF11",
  name: "Start time",
  type: "time",
  content: "",
  selectedOptions: null,
  columnIndex: 1,
  attachment: null,
  mandatory: false,
};

function renderTemplateFields(fields: Array<FieldModelAttrs>) {
  const instrument = makeMockInstrument({ fields });
  instrument.currentlyEditableFields.add("fields");
  render(
    <ThemeProvider theme={materialTheme}>
      <TemplateFields onErrorStateChange={() => {}} instrument={instrument} />
    </ThemeProvider>,
  );
  return instrument;
}

describe("TemplateFields -- time field", () => {
  it("refuses a time the picker reports invalid", async () => {
    const user = userEvent.setup();
    const instrument = renderTemplateFields([startTime]);

    await user.type(screen.getByRole("textbox", { name: "Start time" }), "08:");

    expect(instrument.submittable.isOk).toBe(false);
  });
});

describe("TemplateFields -- a time hidden while the picker reports it invalid", () => {
  setupRealAppI18n();
  const SERVICED_KEY = "6f1c2e0a-0000-4000-8000-000000000041";

  it("shows the reported-invalid time again as typed once the field is shown, and saves once it is cleared", async () => {
    const user = userEvent.setup();
    const instrument = renderTemplateFields([
      {
        id: 1,
        globalId: "IF1",
        name: "Serviced?",
        type: "radio",
        definition: { options: ["Yes", "No"] },
        selectedOptions: ["Yes"],
        columnIndex: 1,
        attachment: null,
        mandatory: false,
        fieldKey: SERVICED_KEY,
      },
      {
        ...startTime,
        id: 2,
        globalId: "IF2",
        columnIndex: 2,
        fieldKey: "6f1c2e0a-0000-4000-8000-000000000042",
        displayRule: createDisplayRule(SERVICED_KEY, "IS_ANY_OF", ["Yes"]),
      },
    ]);
    const saveReasons = () => instrument.submittable.orElseGet((errors) => errors.map(({ message }) => message));

    await user.type(screen.getByRole("textbox", { name: "Start time" }), "08:");
    await user.click(screen.getByRole("radio", { name: "No" }));
    expect(screen.queryByRole("textbox", { name: "Start time" })).not.toBeInTheDocument();
    expect(saveReasons()).toEqual([expect.stringContaining('"Start time" is hidden')]);

    await user.click(screen.getByRole("radio", { name: "Yes" }));
    const time = screen.getByRole("textbox", { name: "Start time" });
    expect(time).toHaveValue("08:");

    await user.clear(time);
    expect(instrument.submittable.isOk).toBe(true);
  });
});
