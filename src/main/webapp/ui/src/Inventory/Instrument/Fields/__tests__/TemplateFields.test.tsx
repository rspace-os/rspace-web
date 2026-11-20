import { describe, expect, it, vi } from "vitest";
import { setupRealAppI18n } from "@/__tests__/helpers/realI18n";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import CommonEditActions from "@/Inventory/components/CommonEditActions";
import { createDisplayRule } from "@/modules/common/field-rules/displayRule";
import { makeMockInstrument } from "@/stores/models/__tests__/InstrumentModel/mocking";
import type { FieldModelAttrs } from "@/stores/models/FieldModel";
import { LANDING_PAGE_FIELD_NAME } from "@/stores/models/InstrumentModel";
import type { StoreContainer } from "@/stores/stores/RootStore";
import { storesContext } from "@/stores/stores-context";
import materialTheme from "@/theme";
import TemplateFields from "../TemplateFields";

// Stub the target-picker dialogs -- they pull in the store/ApiService chain and are
// tested independently.
vi.mock("@/Inventory/components/Fields/Link/LinkTargetBrowser", () => ({
  default: () => null,
}));
vi.mock("@/Inventory/components/Fields/Link/ElnRecordPicker", () => ({
  default: () => null,
}));
// RecordTypeIcon needs app context to resolve its icon; irrelevant here.
vi.mock("@/components/RecordTypeIcon", () => ({ default: () => null }));
// Target existence checks hit the server; default to "exists".
vi.mock("@/Inventory/components/Fields/Link/linkTargetExists", () => ({
  checkLinkTargetExists: vi.fn(() => Promise.resolve(true)),
}));
// Version-summary hook; default to no summary so unrelated assertions are unaffected.
vi.mock("@/Inventory/components/Fields/Link/useLinkTargetSummary", () => ({
  default: () => null,
}));
// Version-lock dialog fetches revision history; stub with a no-op.
vi.mock("@/Inventory/components/Fields/Link/VersionLockDialog", () => ({
  default: () => null,
}));
// LinkField has its own tests; stub it so we can assert it is used for the committed-link
// display without pulling in its info/version dialog chain.
vi.mock("@/Inventory/components/Fields/Link/LinkField", () => ({
  default: ({ link }: { link: { relationType: string; targetGlobalId: string } }) => (
    <div data-testid="link-field-display">
      <span>{link.relationType}</span>
      <span>{link.targetGlobalId}</span>
    </div>
  ),
}));

const LINK_FIELD_ATTRS = {
  id: 10,
  globalId: "IF10",
  name: "Reference",
  type: "link" as const,
  content: null,
  selectedOptions: null,
  definition: null,
  columnIndex: 1,
  attachment: null,
  mandatory: false,
  allowedRelationTypes: ["References"],
  link: null,
};

function renderTemplateFields(instrument: ReturnType<typeof makeMockInstrument>) {
  return render(
    <ThemeProvider theme={materialTheme}>
      <TemplateFields onErrorStateChange={() => {}} instrument={instrument} />
    </ThemeProvider>,
  );
}

describe("TemplateFields -- link field", () => {
  it("renders the link editor, not 'Unknown field type', when the field is editable", () => {
    const instrument = makeMockInstrument({ fields: [LINK_FIELD_ATTRS] });
    instrument.currentlyEditableFields.add("fields");

    renderTemplateFields(instrument);

    expect(screen.queryByText("inventory:instrument.templateFields.unknownFieldType")).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "inventory:sample.fields.linkFieldValue.applyLabel" }),
    ).toBeInTheDocument();
  });

  it("renders 'None' placeholder, not 'Unknown field type', when view-only with no link set", () => {
    const instrument = makeMockInstrument({ fields: [LINK_FIELD_ATTRS] });
    // fields is not in currentlyEditableFields by default, so isFieldEditable("fields") === false

    renderTemplateFields(instrument);

    expect(screen.queryByText("inventory:instrument.templateFields.unknownFieldType")).not.toBeInTheDocument();
    expect(screen.getByText("inventory:sample.fields.linkFieldValue.none")).toBeInTheDocument();
  });

  it("renders the committed link card, not 'Unknown field type', when view-only with a committed link", () => {
    const instrument = makeMockInstrument({
      fields: [
        {
          ...LINK_FIELD_ATTRS,
          link: { relationType: "References", targetGlobalId: "SA20", versionPin: null },
        },
      ],
    });
    // fields is not in currentlyEditableFields by default

    renderTemplateFields(instrument);

    expect(screen.queryByText("inventory:instrument.templateFields.unknownFieldType")).not.toBeInTheDocument();
    expect(screen.getByTestId("link-field-display")).toBeInTheDocument();
    expect(screen.getByText("References")).toBeInTheDocument();
    expect(screen.getByText("SA20")).toBeInTheDocument();
  });
});

describe("TemplateFields -- time field", () => {
  it("accepts a valid time, so the instrument can still be saved", async () => {
    const user = userEvent.setup();
    const instrument = makeMockInstrument({
      fields: [
        {
          id: 11,
          globalId: "IF11",
          name: "Start time",
          type: "time",
          content: "08:00",
          selectedOptions: null,
          columnIndex: 1,
          attachment: null,
          mandatory: true,
        },
      ],
    });
    instrument.currentlyEditableFields.add("fields");
    renderTemplateFields(instrument);

    await user.click(screen.getByRole("spinbutton", { name: "Hours" }));
    await user.keyboard("0930");

    expect(instrument.fields[0].content).toBe("09:30");
    expect(instrument.submittable.isOk).toBe(true);
  });
});

describe("TemplateFields -- date field", () => {
  const calibrated = {
    id: 13,
    globalId: "IF13",
    name: "Calibrated on",
    type: "date" as const,
    content: "",
    selectedOptions: null,
    columnIndex: 1,
    attachment: null,
    mandatory: false,
  };

  it("refuses an impossible date until it is corrected", async () => {
    const user = userEvent.setup();
    const instrument = makeMockInstrument({ fields: [calibrated] });
    instrument.currentlyEditableFields.add("fields");
    renderTemplateFields(instrument);

    await user.click(screen.getByRole("spinbutton", { name: "Year" }));
    await user.keyboard("20240230");
    expect(instrument.submittable.isOk).toBe(false);
    expect(instrument.fields[0].paramsForBackend).toHaveProperty("content", "");

    await user.keyboard("15");
    expect(instrument.fields[0].content).toBe("2024-02-15");
    expect(instrument.submittable.isOk).toBe(true);
  });

  it("allows clearing an optional date that was impossible", async () => {
    const user = userEvent.setup();
    const instrument = makeMockInstrument({ fields: [calibrated] });
    instrument.currentlyEditableFields.add("fields");
    renderTemplateFields(instrument);

    await user.click(screen.getByRole("spinbutton", { name: "Year" }));
    await user.keyboard("20240230");
    expect(instrument.submittable.isOk).toBe(false);

    await user.keyboard("{Control>}a{/Control}{Backspace}");
    expect(instrument.fields[0].content).toBe("");
    expect(instrument.submittable.isOk).toBe(true);
  });
});

describe("TemplateFields -- URI field", () => {
  const manual = {
    id: 14,
    globalId: "IF14",
    name: "Manual",
    type: "uri" as const,
    content: "",
    selectedOptions: null,
    columnIndex: 1,
    attachment: null,
    mandatory: false,
  };

  it("marks a URI the server cannot parse invalid until it is corrected", async () => {
    const user = userEvent.setup();
    const instrument = makeMockInstrument({ fields: [manual] });
    instrument.currentlyEditableFields.add("fields");
    renderTemplateFields(instrument);
    const textbox = screen.getByRole("textbox", { name: "Manual" });

    await user.type(textbox, "https://lab.example.org/lsm 980");
    expect(screen.getByText("inventory:instrument.templateFields.invalid")).toBeInTheDocument();
    expect(instrument.submittable.isOk).toBe(false);

    await user.clear(textbox);
    await user.type(textbox, "manuals/lsm980.pdf");
    expect(screen.queryByText("inventory:instrument.templateFields.invalid")).not.toBeInTheDocument();
    expect(instrument.submittable.isOk).toBe(true);
  });
});

describe("TemplateFields -- Landing page", () => {
  const landingPage = (content: string) => ({
    id: 12,
    globalId: "IF12",
    name: LANDING_PAGE_FIELD_NAME,
    type: "uri" as const,
    content,
    selectedOptions: null,
    columnIndex: 1,
    attachment: null,
    mandatory: true,
  });

  it("shows no mandatory error for the blank Landing page of a new instrument", () => {
    const instrument = makeMockInstrument({ id: null, globalId: null, fields: [landingPage("")] });
    instrument.currentlyEditableFields.add("fields");
    renderTemplateFields(instrument);

    expect(screen.getByRole("textbox", { name: LANDING_PAGE_FIELD_NAME })).toBeInTheDocument();
    expect(screen.queryByText("inventory:instrument.templateFields.mandatory")).not.toBeInTheDocument();
  });

  it("still marks a mandatory field required while it may stay empty as it was loaded", () => {
    const serial = { ...landingPage(""), id: 13, globalId: "IF13", name: "Serial", type: "string" as const };
    const instrument = makeMockInstrument({ fields: [landingPage(""), serial] });
    instrument.currentlyEditableFields.add("fields");
    renderTemplateFields(instrument);

    expect(instrument.requiresValue(instrument.fields[1])).toBe(false);
    expect(within(screen.getByRole("group", { name: "Serial" })).getByText("*")).toBeVisible();
  });

  it.each(["", "   "])("refuses a Landing page that was filled in and is cleared to %j", async (cleared) => {
    const user = userEvent.setup();
    const instrument = makeMockInstrument({ fields: [landingPage("https://lab.example.org/lsm980")] });
    instrument.currentlyEditableFields.add("fields");
    renderTemplateFields(instrument);
    const textbox = screen.getByRole("textbox", { name: LANDING_PAGE_FIELD_NAME });

    await user.clear(textbox);
    if (cleared) await user.type(textbox, cleared);

    expect(instrument.submittable.isOk).toBe(false);
  });
});

describe("TemplateFields -- a field hidden while its value is invalid", () => {
  setupRealAppI18n();
  const SERVICED_KEY = "6f1c2e0a-0000-4000-8000-000000000041";

  /** A Yes/No question, answered Yes, and `field`, shown only while the answer is Yes. */
  const shownWhileServiced = (field: Partial<FieldModelAttrs>): Array<FieldModelAttrs> => [
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
      id: 2,
      globalId: "IF2",
      name: "Service report",
      type: "uri",
      content: "",
      selectedOptions: null,
      columnIndex: 2,
      attachment: null,
      mandatory: false,
      fieldKey: "6f1c2e0a-0000-4000-8000-000000000042",
      displayRule: createDisplayRule(SERVICED_KEY, "IS_ANY_OF", ["Yes"]),
      ...field,
    },
  ];
  const saveReasons = (instrument: ReturnType<typeof makeMockInstrument>) =>
    instrument.submittable.orElseGet((errors) => errors.map(({ message }) => message));

  it("refuses Save, naming the field, when a URI the server cannot parse is hidden", async () => {
    const user = userEvent.setup();
    const instrument = makeMockInstrument({ fields: shownWhileServiced({}) });
    instrument.currentlyEditableFields.add("fields");
    renderTemplateFields(instrument);

    await user.type(screen.getByRole("textbox", { name: "Service report" }), "a b");
    await user.click(screen.getByRole("radio", { name: "No" }));

    expect(instrument.isFieldHidden(instrument.fields[1])).toBe(true);
    expect(saveReasons(instrument)).toEqual([expect.stringContaining('"Service report" is hidden')]);
  });

  it("refuses Save, naming the field, when an impossible date typed over a stored one is hidden", async () => {
    const user = userEvent.setup();
    const instrument = makeMockInstrument({
      fields: shownWhileServiced({ name: "Serviced on", type: "date", content: "2024-01-10" }),
    });
    instrument.currentlyEditableFields.add("fields");
    renderTemplateFields(instrument);

    await user.click(screen.getByRole("spinbutton", { name: "Year" }));
    await user.keyboard("20240230");
    await user.click(screen.getByRole("radio", { name: "No" }));

    expect(instrument.isFieldHidden(instrument.fields[1])).toBe(true);
    expect(saveReasons(instrument)).toEqual([expect.stringContaining('"Serviced on" is hidden')]);
  });

  it("shows an impossible date again as typed once the field is shown, and saves once it is cleared", async () => {
    const user = userEvent.setup();
    const instrument = makeMockInstrument({ fields: shownWhileServiced({ name: "Serviced on", type: "date" }) });
    instrument.currentlyEditableFields.add("fields");
    renderTemplateFields(instrument);

    await user.click(screen.getByRole("spinbutton", { name: "Year" }));
    await user.keyboard("20240230");
    await user.click(screen.getByRole("radio", { name: "No" }));
    expect(screen.queryByRole("spinbutton", { name: "Year" })).not.toBeInTheDocument();
    expect(saveReasons(instrument)).toEqual([expect.stringContaining('"Serviced on" is hidden')]);

    await user.click(screen.getByRole("radio", { name: "Yes" }));
    const year = screen.getByRole("spinbutton", { name: "Year" });
    expect(
      [year, screen.getByRole("spinbutton", { name: "Month" }), screen.getByRole("spinbutton", { name: "Day" })].map(
        ({ textContent }) => textContent,
      ),
    ).toEqual(["2024", "02", "30"]);

    await user.click(year);
    await user.keyboard("{Control>}a{/Control}{Backspace}");
    expect(instrument.submittable.isOk).toBe(true);
  });

  it("shows an unreadable number again once the field is shown, and saves once it is cleared", async () => {
    const user = userEvent.setup();
    const instrument = makeMockInstrument({ fields: shownWhileServiced({ name: "Hours run", type: "number" }) });
    instrument.currentlyEditableFields.add("fields");
    renderTemplateFields(instrument);
    const hours = screen.getByRole("spinbutton", { name: "Hours run" });
    // jsdom drops text such as "2e" that a browser keeps in a number input, so the input reports a number invalid instead
    const validity = vi.spyOn(hours as HTMLInputElement, "checkValidity").mockReturnValue(false);

    await user.type(hours, "2");
    await user.click(screen.getByRole("radio", { name: "No" }));
    expect(screen.queryByRole("spinbutton", { name: "Hours run" })).not.toBeInTheDocument();
    expect(saveReasons(instrument)).toEqual([expect.stringContaining('"Hours run" is hidden')]);

    await user.click(screen.getByRole("radio", { name: "Yes" }));
    // the same input, which holds the text the browser could not read
    expect(screen.getByRole("spinbutton", { name: "Hours run" })).toBe(hours);
    expect(hours).toHaveValue(2);
    expect(hours).toBeInvalid();

    validity.mockRestore();
    await user.clear(hours);
    expect(hours).not.toBeInvalid();
    expect(instrument.submittable.isOk).toBe(true);
  });

  it("names the nearest question that is shown when the field's own question is hidden too", () => {
    const CALIBRATED_KEY = "6f1c2e0a-0000-4000-8000-000000000043";
    const [serviced, report] = shownWhileServiced({});
    const instrument = makeMockInstrument({
      fields: [
        serviced,
        {
          ...serviced,
          id: 2,
          globalId: "IF2",
          name: "Calibrated?",
          columnIndex: 2,
          fieldKey: CALIBRATED_KEY,
          displayRule: report.displayRule,
        },
        {
          ...report,
          id: 3,
          globalId: "IF3",
          name: "Calibrated on",
          type: "date",
          columnIndex: 3,
          fieldKey: "6f1c2e0a-0000-4000-8000-000000000044",
          displayRule: createDisplayRule(CALIBRATED_KEY, "IS_ANY_OF", ["Yes"]),
        },
      ],
    });
    const [servicedField, calibrated, calibratedOn] = instrument.fields;
    calibratedOn.setError(true);

    calibrated.setAttributesDirty({ selectedOptions: ["No"] });
    expect(saveReasons(instrument)).toEqual([expect.stringContaining('Change "Calibrated?" to show it')]);

    calibrated.setAttributesDirty({ selectedOptions: ["Yes"] });
    servicedField.setAttributesDirty({ selectedOptions: ["No"] });
    expect(instrument.isFieldHidden(calibrated)).toBe(true);
    expect(saveReasons(instrument)).toEqual([expect.stringContaining('Change "Serviced?" to show it')]);
  });

  it("disables Save with an alert naming the hidden field and its question, until it is shown and corrected", async () => {
    const user = userEvent.setup();
    const instrument = makeMockInstrument({ fields: shownWhileServiced({}) });
    instrument.currentlyEditableFields.add("fields");
    const stores = { uiStore: { confirmDiscardAnyChanges: () => Promise.resolve() } } as unknown as StoreContainer;
    render(
      <ThemeProvider theme={materialTheme}>
        <storesContext.Provider value={stores}>
          <TemplateFields onErrorStateChange={() => {}} instrument={instrument} />
          <CommonEditActions editableObject={instrument} />
        </storesContext.Provider>
      </ThemeProvider>,
    );
    const save = screen.getByRole("button", { name: "Save" });

    await user.type(screen.getByRole("textbox", { name: "Service report" }), "a b");
    await user.click(screen.getByRole("radio", { name: "No" }));
    expect(save).toBeDisabled();
    const alert = screen.getByRole("alert");
    expect(alert).toMatchTextContent('"Service report" is hidden');
    expect(alert).toMatchTextContent('Change "Serviced?" to show it, then correct or clear it.');

    await user.click(screen.getByRole("radio", { name: "Yes" }));
    const report = screen.getByRole("textbox", { name: "Service report" });
    await user.clear(report);
    await user.type(report, "reports/2024.pdf");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(save).toBeEnabled();
  });
});
