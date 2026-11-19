import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expectAccessible } from "@/__tests__/accessibility";
import { SETTLE_DELAY_MS } from "@/Inventory/components/Fields/useShownTemplateFields";
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

function renderSampleFields(
  fields: Array<FieldModelAttrs>,
  onErrorStateChange: (fieldName: string, hasError: boolean) => void = () => {},
) {
  const sample = makeMockSample({ fields });
  sample.currentlyEditableFields.add("fields");
  render(
    <ThemeProvider theme={materialTheme}>
      <Fields onErrorStateChange={onErrorStateChange} sample={sample} />
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

  it("announces a field each time it is shown or hidden, keeping focus on the answer", async () => {
    const user = userEvent.setup();
    renderSampleFields(gmoFields([]));
    const status = screen.getByRole("status");

    await user.click(screen.getByRole("radio", { name: "Yes" }));
    expect(status).toHaveTextContent("common:fieldDisplay.fieldsShown");
    await user.click(screen.getByRole("radio", { name: "No" }));
    expect(status).toHaveTextContent("common:fieldDisplay.fieldsHidden");
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

  it("does not announce a reload of the same record as a change", () => {
    const loaded = makeMockSample({ globalId: "SA1", fields: gmoFields([]) });
    const reloaded = makeMockSample({ globalId: "SA1", fields: gmoFields(["Yes"]) });
    for (const sample of [loaded, reloaded]) sample.currentlyEditableFields.add("fields");
    const fields = (sample: typeof loaded) => (
      <ThemeProvider theme={materialTheme}>
        <Fields onErrorStateChange={() => {}} sample={sample} />
      </ThemeProvider>
    );
    const { rerender } = render(fields(loaded));
    rerender(fields(reloaded));

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

const WEIGHT_KEY = "6f1c2e0a-0000-4000-8000-000000000011";

/** A number source, loaded as `weight`, and a mandatory permit shown while it is greater than 2. */
const weightFields = (weight: string): Array<FieldModelAttrs> => [
  {
    id: 1,
    globalId: "SF1",
    name: "Weight",
    type: "number",
    content: weight,
    selectedOptions: null,
    columnIndex: 1,
    attachment: null,
    mandatory: false,
    fieldKey: WEIGHT_KEY,
  },
  {
    id: 2,
    globalId: "SF2",
    name: "Heavy goods permit",
    type: "string",
    content: "",
    selectedOptions: null,
    columnIndex: 2,
    attachment: null,
    mandatory: true,
    fieldKey: "6f1c2e0a-0000-4000-8000-000000000012",
    displayRule: createDisplayRule(WEIGHT_KEY, "GREATER_THAN", ["2"]),
  },
];

/** The weight fields after the GMO fields, so an option and a number both decide what is shown. */
const mixedFields = (): Array<FieldModelAttrs> => [
  ...gmoFields([]),
  ...weightFields("4").map((field, index) => ({
    ...field,
    id: 3 + index,
    globalId: `SF${3 + index}`,
    columnIndex: 3 + index,
  })),
];

const BATCH_KEY = "6f1c2e0a-0000-4000-8000-000000000013";

/** A plain text source, and a note shown while it contains "LOT". */
const batchFields = (code: string): Array<FieldModelAttrs> => [
  {
    id: 1,
    globalId: "SF1",
    name: "Batch code",
    type: "string",
    content: code,
    selectedOptions: null,
    columnIndex: 1,
    attachment: null,
    mandatory: false,
    fieldKey: BATCH_KEY,
  },
  {
    id: 2,
    globalId: "SF2",
    name: "Lot note",
    type: "string",
    content: "",
    selectedOptions: null,
    columnIndex: 2,
    attachment: null,
    mandatory: false,
    fieldKey: "6f1c2e0a-0000-4000-8000-000000000014",
    displayRule: createDisplayRule(BATCH_KEY, "CONTAINS", ["LOT"]),
  },
];

describe("Template fields with a number source", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    // Testing Library flushes each user action with a timer it advances only through Jest's API
    vi.stubGlobal("jest", { advanceTimersByTime: (ms: number) => vi.advanceTimersByTime(ms) });
  });
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });
  const setupUser = () => userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
  const wait = (ms: number) => act(() => vi.advanceTimersByTime(ms));
  const permitBox = () => screen.queryByRole("textbox", { name: /Heavy goods permit/ });

  /** Focuses the permit, then changes the weight to one that hides it, past the pause. */
  async function focusPermitThenHideIt(sample: ReturnType<typeof renderSampleFields>) {
    const user = setupUser();
    await user.click(screen.getByRole("textbox", { name: /Heavy goods permit/ }));
    act(() => sample.fields[0].setAttributesDirty({ content: "1" }));
    await wait(SETTLE_DELAY_MS);
    expect(sample.isFieldHidden(sample.fields[1])).toBe(true);
    return user;
  }

  it("moves fields once typing pauses, while the record already follows each key", async () => {
    const user = setupUser();
    const sample = renderSampleFields(weightFields("4"));
    const permitField = sample.fields[1];
    const weight = screen.getByRole("spinbutton", { name: "Weight" });

    await user.clear(weight);
    await user.type(weight, "1");
    expect(sample.isFieldHidden(permitField)).toBe(true);
    expect(permitField.validate().isOk).toBe(true);
    await wait(SETTLE_DELAY_MS - 1);
    expect(permitBox()).toBeInTheDocument();

    await wait(1);
    expect(permitBox()).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("common:fieldDisplay.fieldsHidden");
    expect(weight).toHaveFocus();
  });

  it("restarts the pause with each edit", async () => {
    const user = setupUser();
    renderSampleFields(weightFields("4"));
    const weight = screen.getByRole("spinbutton", { name: "Weight" });

    await user.clear(weight);
    await user.type(weight, "1");
    await wait(SETTLE_DELAY_MS - 100);
    await user.type(weight, "{Backspace}0");
    await wait(100);
    expect(permitBox()).toBeInTheDocument();

    await wait(SETTLE_DELAY_MS - 100);
    expect(permitBox()).not.toBeInTheDocument();
  });

  it("does not restart the pause for typing in a field no rule reads", async () => {
    const user = setupUser();
    const sample = renderSampleFields([
      ...weightFields("4"),
      {
        id: 3,
        globalId: "SF3",
        name: "Notes",
        type: "string",
        content: "",
        selectedOptions: null,
        columnIndex: 3,
        attachment: null,
        mandatory: false,
        fieldKey: "6f1c2e0a-0000-4000-8000-000000000015",
      },
    ]);
    const weight = screen.getByRole("spinbutton", { name: "Weight" });

    await user.clear(weight);
    await user.type(weight, "1");
    await wait(SETTLE_DELAY_MS - 100);
    await user.type(screen.getByRole("textbox", { name: "Notes" }), "x");
    expect(sample.fields[2].content).toBe("x");
    await wait(100);

    expect(permitBox()).not.toBeInTheDocument();
  });

  it("announces nothing when typing ends where it started", async () => {
    const user = setupUser();
    renderSampleFields(weightFields("4"));
    const weight = screen.getByRole("spinbutton", { name: "Weight" });

    await user.clear(weight);
    await user.type(weight, "5");
    await wait(SETTLE_DELAY_MS);

    expect(permitBox()).toBeInTheDocument();
    expect(screen.getByRole("status")).toBeEmptyDOMElement();
  });

  it("shows the field an option reveals at once while typed numbers are still pausing", async () => {
    const user = setupUser();
    renderSampleFields(mixedFields());
    const weight = screen.getByRole("spinbutton", { name: "Weight" });

    await user.clear(weight);
    await user.type(weight, "1");
    await user.click(screen.getByRole("radio", { name: "Yes" }));

    expect(screen.getByRole("textbox", { name: /GMO permit/ })).toBeInTheDocument();
    expect(permitBox()).toBeInTheDocument();
    await wait(SETTLE_DELAY_MS);
    expect(permitBox()).not.toBeInTheDocument();
  });

  it("keeps a field the values hide while focus stays inside it, such as in a frame", async () => {
    const sample = renderSampleFields(weightFields("4"));
    await focusPermitThenHideIt(sample);
    const permit = screen.getByRole("textbox", { name: /Heavy goods permit/ });

    // focus entering a frame inside the field reports no target, but the field still holds focus
    fireEvent.focusOut(permit);
    await wait(1);

    expect(permitBox()).toBeInTheDocument();
  });

  it("hides a field the values hide once focus really leaves it, though no target is reported", async () => {
    const sample = renderSampleFields(weightFields("4"));
    await focusPermitThenHideIt(sample);
    vi.spyOn(document, "hasFocus").mockReturnValue(true);

    // blurring lands focus on the body, outside the field, with a null relatedTarget
    act(() => screen.getByRole("textbox", { name: /Heavy goods permit/ }).blur());
    await wait(1);

    expect(permitBox()).not.toBeInTheDocument();
  });

  /** Focuses the permit, hides it by its values, then blurs it while the window is in the background. */
  async function keepPermitWhileWindowLosesFocus() {
    const sample = renderSampleFields(weightFields("4"));
    await focusPermitThenHideIt(sample);
    const hasFocus = vi.spyOn(document, "hasFocus").mockReturnValue(false);
    act(() => screen.getByRole("textbox", { name: /Heavy goods permit/ }).blur());
    await wait(1);
    return hasFocus;
  }

  it("keeps a field the values hide while the window, not the field, loses focus", async () => {
    await keepPermitWhileWindowLosesFocus();

    expect(permitBox()).toBeInTheDocument();
  });

  it("hides a field kept while the window lost focus once the window regains focus outside it", async () => {
    const hasFocus = await keepPermitWhileWindowLosesFocus();

    hasFocus.mockReturnValue(true);
    act(() => window.dispatchEvent(new FocusEvent("focus")));
    await wait(1);

    expect(permitBox()).not.toBeInTheDocument();
  });

  it("hides a field kept while the window lost focus once focus lands on a control outside the fields", async () => {
    const hasFocus = await keepPermitWhileWindowLosesFocus();
    render(<button type="button" aria-label="Elsewhere" />);

    hasFocus.mockReturnValue(true);
    act(() => screen.getByRole("button", { name: "Elsewhere" }).focus());
    await wait(1);

    expect(permitBox()).not.toBeInTheDocument();
  });

  it("hides a field the values hide once the record leaves edit mode, even if it held focus", async () => {
    const sample = renderSampleFields(weightFields("4"));
    await focusPermitThenHideIt(sample);
    // with the window in the background, the field still counts as holding focus once it is disabled
    vi.spyOn(document, "hasFocus").mockReturnValue(false);

    act(() => sample.currentlyEditableFields.delete("fields"));
    await wait(1);

    expect(screen.getByText("Weight")).toBeInTheDocument();
    expect(screen.queryByText("Heavy goods permit")).not.toBeInTheDocument();
  });

  it("does not treat a field the values hide as mandatory while focus keeps it on screen", async () => {
    const onErrorStateChange = vi.fn();
    const sample = renderSampleFields(weightFields("4"), onErrorStateChange);
    const user = await focusPermitThenHideIt(sample);
    const permit = screen.getByRole("textbox", { name: /Heavy goods permit/ });

    await user.type(permit, "x");
    await user.clear(permit);

    expect(permit).not.toBeRequired();
    expect(permit).not.toBeInvalid();
    expect(screen.queryByText("This field is mandatory. Please enter a value.")).not.toBeInTheDocument();
    expect(onErrorStateChange).toHaveBeenLastCalledWith("template_Heavy goods permit", false);
  });

  it("reads typed text in a plain text source", () => {
    const sample = makeMockSample({ fields: batchFields("B-7") });
    expect(sample.isFieldHidden(sample.fields[1])).toBe(true);

    sample.fields[0].setAttributesDirty({ content: "b-lot-7" });
    expect(sample.isFieldHidden(sample.fields[1])).toBe(false);
  });

  it("reads loaded numbers, and typed text that is not a number, from the record's current content", () => {
    const sample = makeMockSample({ fields: weightFields("2.5") });
    expect(sample.isFieldHidden(sample.fields[1])).toBe(false);

    sample.fields[0].setAttributesDirty({ content: "abc" });
    expect(sample.isFieldHidden(sample.fields[1])).toBe(true);
  });
});
