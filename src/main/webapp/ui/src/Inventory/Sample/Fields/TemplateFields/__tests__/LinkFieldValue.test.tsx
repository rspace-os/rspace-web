import { describe, it, expect, vi } from "vitest";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@/__tests__/customQueries";
import materialTheme from "@/theme";
import LinkFieldValue from "../LinkFieldValue";
import { type Field } from "@/stores/definitions/Field";

// The target-picker dialogs pull in the store/ApiService chain at module load, which is
// irrelevant to this component's relation-constraint behaviour; stub them out.
vi.mock("@/Inventory/components/Fields/Link/LinkTargetBrowser", () => ({
  default: () => null,
}));
vi.mock("@/Inventory/components/Fields/Link/ElnRecordPicker", () => ({
  default: () => null,
}));
// RecordTypeIcon needs app context to resolve its icon; irrelevant to this component's logic.
vi.mock("@/components/RecordTypeIcon", () => ({ default: () => null }));
// LinkField has its own tests; stub it so we can assert it is used for the committed-link display
// (and drive its "Edit" affordance) without pulling in its info/version dialog chain.
vi.mock("@/Inventory/components/Fields/Link/LinkField", () => ({
  default: ({
    link,
    onEdit,
  }: {
    link: { relationType: string; targetGlobalId: string };
    onEdit: () => void;
  }) => (
    <div data-testid="link-field-display">
      <span>{link.relationType}</span>
      <span>{link.targetGlobalId}</span>
      <button type="button" onClick={onEdit}>
        Edit link
      </button>
    </div>
  ),
}));

function linkField(overrides: Partial<Field> = {}): Field {
  return {
    name: "Related items",
    type: "link",
    allowedRelationTypes: ["References", "IsDerivedFrom"],
    link: null,
    setAttributesDirty: vi.fn(),
    setError: vi.fn(),
    ...overrides,
  } as unknown as Field;
}

// The editor's Apply button uses the custom `callToAction` palette, so a theme is required.
function renderField(props: {
  field: Field;
  sourceGlobalId: string;
  disabled: boolean;
  onChange: () => void;
}) {
  return render(
    <ThemeProvider theme={materialTheme}>
      <LinkFieldValue {...props} />
    </ThemeProvider>,
  );
}

describe("LinkFieldValue", () => {
  it("constrains the relationship type options to the field's allowed set", async () => {
    const user = userEvent.setup();
    renderField({
      field: linkField(),
      sourceGlobalId: "SA1",
      disabled: false,
      onChange: () => {},
    });

    await user.click(screen.getByRole("button", { name: /open/i }));

    const options = screen.getAllByRole("option").map((o) => o.textContent);
    expect(options).toEqual(["References", "IsDerivedFrom"]);
  });

  it("shows the full link card for a committed link and reveals the editor on Edit", async () => {
    const user = userEvent.setup();
    const field = linkField({
      link: {
        relationType: "References",
        targetGlobalId: "SA20",
        versionPin: null,
      },
    });
    renderField({
      field,
      sourceGlobalId: "SA1",
      disabled: false,
      onChange: () => {},
    });

    // a committed link renders via the shared LinkField card, not the inline editor
    expect(screen.getByTestId("link-field-display")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /apply link/i }),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /edit link/i }));

    // the editor is now shown
    expect(
      screen.getByRole("button", { name: /apply link/i }),
    ).toBeInTheDocument();
  });

  it("commits the staged link to the field model on Apply", async () => {
    const user = userEvent.setup();
    const setAttributesDirty = vi.fn();
    const field = linkField({
      link: {
        relationType: "References",
        targetGlobalId: "SA20",
        versionPin: null,
      },
      setAttributesDirty,
    });
    renderField({
      field,
      sourceGlobalId: "SA1",
      disabled: false,
      onChange: () => {},
    });

    await user.click(screen.getByRole("button", { name: /edit link/i }));
    await user.click(screen.getByRole("button", { name: /open/i }));
    await user.click(screen.getByRole("option", { name: "IsDerivedFrom" }));
    await user.click(screen.getByRole("button", { name: /apply link/i }));

    expect(setAttributesDirty).toHaveBeenCalledWith({
      link: {
        relationType: "IsDerivedFrom",
        targetGlobalId: "SA20",
        versionPin: null,
      },
    });
  });

  it("prevents applying a self-link", async () => {
    const user = userEvent.setup();
    const setAttributesDirty = vi.fn();
    const field = linkField({
      link: {
        relationType: "References",
        targetGlobalId: "SA10",
        versionPin: null,
      },
      setAttributesDirty,
    });
    renderField({
      field,
      sourceGlobalId: "SA10",
      disabled: false,
      onChange: () => {},
    });

    await user.click(screen.getByRole("button", { name: /edit link/i }));
    // re-pick a relation but keep the same (self) target -> must remain un-appliable
    await user.click(screen.getByRole("button", { name: /open/i }));
    await user.click(screen.getByRole("option", { name: "IsDerivedFrom" }));

    expect(
      screen.getByText("An item cannot link to itself."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /apply link/i })).toBeDisabled();
    expect(setAttributesDirty).not.toHaveBeenCalled();
  });
});
