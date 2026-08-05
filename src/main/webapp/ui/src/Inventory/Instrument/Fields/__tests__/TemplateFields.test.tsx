import { describe, expect, it, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";

import { makeMockInstrument } from "@/stores/models/__tests__/InstrumentModel/mocking";
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
