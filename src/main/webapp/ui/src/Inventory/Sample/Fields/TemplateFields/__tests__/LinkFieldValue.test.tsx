import { describe, it, expect, vi } from "vitest";
import userEvent from "@testing-library/user-event";
import { render, screen } from "@/__tests__/customQueries";
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

describe("LinkFieldValue", () => {
  it("constrains the relationship type options to the field's allowed set", async () => {
    const user = userEvent.setup();
    render(
      <LinkFieldValue
        field={linkField()}
        sourceGlobalId="SA1"
        disabled={false}
        onChange={() => {}}
      />,
    );

    await user.click(screen.getByRole("button", { name: /open/i }));

    const options = screen.getAllByRole("option").map((o) => o.textContent);
    expect(options).toEqual(["References", "IsDerivedFrom"]);
  });

  it("commits the staged link to the field model only when Apply is clicked", async () => {
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
    render(
      <LinkFieldValue
        field={field}
        sourceGlobalId="SA1"
        disabled={false}
        onChange={() => {}}
      />,
    );

    // changing the relationship type stages the edit but does not yet write to the model
    await user.click(screen.getByRole("button", { name: /open/i }));
    await user.click(screen.getByRole("option", { name: "IsDerivedFrom" }));
    expect(setAttributesDirty).not.toHaveBeenCalled();

    // Apply commits the full link (new relation + existing target)
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
    render(
      <LinkFieldValue
        field={field}
        sourceGlobalId="SA10"
        disabled={false}
        onChange={() => {}}
      />,
    );

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
