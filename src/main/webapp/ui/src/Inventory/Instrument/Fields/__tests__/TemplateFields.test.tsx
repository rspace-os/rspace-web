import { describe, expect, it, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { makeMockInstrument } from "@/stores/models/__tests__/InstrumentModel/mocking";
import { LANDING_PAGE_FIELD_NAME } from "@/stores/models/InstrumentModel";
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
