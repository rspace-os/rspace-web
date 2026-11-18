import { describe, expect, it, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expectAccessible } from "@/__tests__/accessibility";
import { createDisplayRule } from "@/modules/common/field-rules/displayRule";
import { makeMockInstrument } from "@/stores/models/__tests__/InstrumentModel/mocking";
import { makeMockSample } from "@/stores/models/__tests__/SampleModel/mocking";
import type { FieldModelAttrs } from "@/stores/models/FieldModel";
import materialTheme from "@/theme";
import InstrumentTemplateFields from "../../../../Instrument/Fields/TemplateFields";
import Fields from "../Fields";

vi.mock("@/common/InvApiService", () => ({ default: {} }));
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({
    uiStore: { setPageNavigationConfirmation: () => {}, setDirty: () => {} },
    unitStore: { getUnit: () => ({ label: "ml" }) },
  }),
}));

const GMO_KEY = "6f1c2e0a-0000-4000-8000-000000000001";

/** A GMO question, and a mandatory permit number shown only when the answer is Yes. */
const gmoFields = (answer: Array<string>, permit = ""): Array<FieldModelAttrs> => [
  {
    id: 1,
    globalId: "SF1",
    name: "Is this a GMO?",
    type: "radio",
    definition: { options: ["Yes", "No"] },
    selectedOptions: answer,
    columnIndex: 1,
    attachment: null,
    mandatory: true,
    fieldKey: GMO_KEY,
  },
  {
    id: 2,
    globalId: "SF2",
    name: "GMO permit",
    type: "string",
    content: permit,
    selectedOptions: null,
    columnIndex: 2,
    attachment: null,
    mandatory: true,
    fieldKey: "6f1c2e0a-0000-4000-8000-000000000002",
    displayRule: createDisplayRule(GMO_KEY, "IS_ANY_OF", ["Yes"]),
  },
];

function renderSampleFields(fields: Array<FieldModelAttrs>) {
  const sample = makeMockSample({ fields });
  sample.currentlyEditableFields.add("fields");
  render(
    <ThemeProvider theme={materialTheme}>
      <Fields onErrorStateChange={() => {}} sample={sample} />
    </ThemeProvider>,
  );
  return sample;
}

describe("Template fields with display rules", () => {
  it("hides a field until its condition holds, and shows it without a reload", async () => {
    const user = userEvent.setup();
    renderSampleFields(gmoFields([]));
    expect(screen.queryByRole("textbox", { name: /GMO permit/ })).not.toBeInTheDocument();

    const yes = screen.getByRole("radio", { name: "Yes" });
    await user.click(yes);

    expect(screen.getByRole("textbox", { name: /GMO permit/ })).toBeInTheDocument();
    expect(yes).toHaveFocus();
    expect(screen.getByRole("status")).toHaveTextContent("common:fieldDisplay.fieldsShown");
  });

  it("announces a field each time it is revealed, keeping focus on the answer", async () => {
    const user = userEvent.setup();
    renderSampleFields(gmoFields([]));
    const status = screen.getByRole("status");

    await user.click(screen.getByRole("radio", { name: "Yes" }));
    expect(status).toHaveTextContent("common:fieldDisplay.fieldsShown");
    await user.click(screen.getByRole("radio", { name: "No" }));
    expect(status).toBeEmptyDOMElement();
    const yes = screen.getByRole("radio", { name: "Yes" });
    await user.click(yes);

    expect(status).toHaveTextContent("common:fieldDisplay.fieldsShown");
    expect(yes).toHaveFocus();
  });

  it("does not announce the fields of another record as revealed", () => {
    const first = makeMockSample({ globalId: "SA1", fields: gmoFields([]) });
    const second = makeMockSample({ globalId: "SA2", fields: gmoFields(["Yes"]) });
    for (const sample of [first, second]) sample.currentlyEditableFields.add("fields");
    const fields = (sample: typeof first) => (
      <ThemeProvider theme={materialTheme}>
        <Fields onErrorStateChange={() => {}} sample={sample} />
      </ThemeProvider>
    );
    const { rerender } = render(fields(first));
    rerender(fields(second));

    expect(screen.getByRole("textbox", { name: /GMO permit/ })).toBeInTheDocument();
    expect(screen.getByRole("status")).toBeEmptyDOMElement();
  });

  it("keeps the value of a field that is hidden and shown again", async () => {
    const user = userEvent.setup();
    const sample = renderSampleFields(gmoFields(["Yes"], "P-123"));

    await user.click(screen.getByRole("radio", { name: "No" }));
    expect(screen.queryByRole("textbox", { name: /GMO permit/ })).not.toBeInTheDocument();
    expect(sample.fields[1].content).toBe("P-123");

    await user.click(screen.getByRole("radio", { name: "Yes" }));
    expect(screen.getByRole("textbox", { name: /GMO permit/ })).toHaveValue("P-123");
  });

  it("does not require a mandatory field while it is hidden", () => {
    const sample = makeMockSample({ fields: gmoFields(["No"]) });
    expect(sample.fields[1].validate().isOk).toBe(true);

    sample.fields[0].setAttributesDirty({ selectedOptions: ["Yes"] });
    expect(sample.fields[1].validate().isOk).toBe(false);
  });

  it("hides fields in the instrument form too", () => {
    const instrument = makeMockInstrument({ fields: gmoFields(["No"]) });
    render(
      <ThemeProvider theme={materialTheme}>
        <InstrumentTemplateFields onErrorStateChange={() => {}} instrument={instrument} />
      </ThemeProvider>,
    );
    expect(screen.getByRole("radiogroup")).toBeInTheDocument();
    expect(screen.queryByText("GMO permit")).not.toBeInTheDocument();
  });

  it("is accessible", async () => {
    renderSampleFields(gmoFields(["Yes"]));
    await expectAccessible(document.body);
  });
});
