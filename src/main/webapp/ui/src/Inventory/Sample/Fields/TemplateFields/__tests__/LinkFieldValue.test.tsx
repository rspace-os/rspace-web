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
// Typed targets are existence-checked against the server on Apply; default to "exists".
const { mockCheckLinkTargetExists } = vi.hoisted(() => ({
  mockCheckLinkTargetExists: vi.fn(() => Promise.resolve(true)),
}));
vi.mock("@/Inventory/components/Fields/Link/linkTargetExists", () => ({
  checkLinkTargetExists: mockCheckLinkTargetExists,
}));
// The real dialog fetches revision history; stub it with a confirm button.
vi.mock("@/Inventory/components/Fields/Link/VersionLockDialog", () => ({
  default: ({
    open,
    globalId,
    onConfirm,
  }: {
    open: boolean;
    globalId: string;
    onConfirm: (v: number | null) => void;
  }) =>
    open ? (
      <div data-testid="version-lock-dialog" data-globalid={globalId}>
        <button
          type="button"
          data-testid="version-lock-dialog-confirm-7"
          onClick={() => onConfirm(7)}
        >
          confirm 7
        </button>
      </div>
    ) : null,
}));
// LinkField has its own tests; stub it so we can assert it is used for the committed-link display
// (and drive its "Edit" affordance) without pulling in its info/version dialog chain.
vi.mock("@/Inventory/components/Fields/Link/LinkField", () => ({
  default: ({
    link,
    onEdit,
    onOpen,
  }: {
    link: { relationType: string; targetGlobalId: string };
    onEdit: () => void;
    onOpen: () => void;
  }) => (
    <div data-testid="link-field-display">
      <span>{link.relationType}</span>
      <span>{link.targetGlobalId}</span>
      <button type="button" onClick={onEdit}>
        Edit link
      </button>
      <button type="button" data-testid="open-target" onClick={onOpen}>
        Open target
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

  it("opens a Gallery target at its location in the Gallery, not a download", async () => {
    const openSpy = vi.spyOn(window, "open").mockImplementation(() => null);
    const user = userEvent.setup();
    const field = linkField({
      link: {
        relationType: "References",
        targetGlobalId: "GL21",
        versionPin: null,
      },
    });
    renderField({
      field,
      sourceGlobalId: "SA1",
      disabled: false,
      onChange: () => {},
    });

    await user.click(screen.getByTestId("open-target"));
    expect(openSpy).toHaveBeenCalledWith("/gallery/item/21", "_blank");
    openSpy.mockRestore();
  });

  it("lets a target Global ID be typed directly", async () => {
    const user = userEvent.setup();
    const setAttributesDirty = vi.fn();
    const field = linkField({ setAttributesDirty });
    renderField({
      field,
      sourceGlobalId: "SA1",
      disabled: false,
      onChange: () => {},
    });

    await user.type(
      screen.getByRole("textbox", { name: /target global id/i }),
      "SS33",
    );
    await user.click(screen.getByRole("button", { name: /open/i }));
    await user.click(screen.getByRole("option", { name: "References" }));
    await user.click(screen.getByRole("button", { name: /apply link/i }));

    expect(setAttributesDirty).toHaveBeenCalledWith({
      link: {
        relationType: "References",
        targetGlobalId: "SS33",
        versionPin: null,
      },
    });
  });

  it("rejects a typed target with an unsupported prefix", async () => {
    const user = userEvent.setup();
    const setAttributesDirty = vi.fn();
    const field = linkField({ setAttributesDirty });
    renderField({
      field,
      sourceGlobalId: "SA1",
      disabled: false,
      onChange: () => {},
    });

    await user.type(
      screen.getByRole("textbox", { name: /target global id/i }),
      "XX5",
    );
    await user.click(screen.getByRole("button", { name: /open/i }));
    await user.click(screen.getByRole("option", { name: "References" }));

    expect(
      screen.getByText(
        /target must be an inventory item or an eln document, notebook or gallery file/i,
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /apply link/i })).toBeDisabled();
    expect(setAttributesDirty).not.toHaveBeenCalled();
  });

  it("flags the field model while link edits are unapplied so record save is blocked", async () => {
    const user = userEvent.setup();
    const setError = vi.fn();
    const field = linkField({
      link: {
        relationType: "References",
        targetGlobalId: "SA20",
        versionPin: null,
      },
      setError,
    });
    renderField({
      field,
      sourceGlobalId: "SA1",
      disabled: false,
      onChange: () => {},
    });

    await user.click(screen.getByRole("button", { name: /edit link/i }));
    // remove the target: the staged state now diverges from the committed link
    await user.click(screen.getByTestId("CancelIcon"));

    expect(setError).toHaveBeenLastCalledWith(true);

    // discarding restores the committed link and clears the flag
    await user.click(screen.getByRole("button", { name: /discard link/i }));
    expect(setError).toHaveBeenLastCalledWith(false);
  });

  it("shows None in view mode when the field has no link", () => {
    renderField({
      field: linkField(),
      sourceGlobalId: "SA1",
      disabled: true,
      onChange: () => {},
    });

    expect(screen.getByText("None")).toBeInTheDocument();
  });

  it("rejects applying a typed target that does not exist on the server", async () => {
    mockCheckLinkTargetExists.mockResolvedValueOnce(false);
    const user = userEvent.setup();
    const setAttributesDirty = vi.fn();
    const field = linkField({ setAttributesDirty });
    renderField({
      field,
      sourceGlobalId: "SA1",
      disabled: false,
      onChange: () => {},
    });

    await user.type(
      screen.getByRole("textbox", { name: /target global id/i }),
      "SS9999",
    );
    await user.click(screen.getByRole("button", { name: /open/i }));
    await user.click(screen.getByRole("option", { name: "References" }));
    await user.click(screen.getByRole("button", { name: /apply link/i }));

    expect(
      await screen.findByText(
        /SS9999 does not exist, or you do not have permission to view it/i,
      ),
    ).toBeInTheDocument();
    expect(setAttributesDirty).not.toHaveBeenCalled();
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

describe("LinkFieldValue version pin is edited in the editor and committed on Apply", () => {
  function renderCommitted(targetGlobalId = "SA20") {
    const field = linkField({
      link: {
        relationType: "References",
        targetGlobalId,
        versionPin: null,
      },
    });
    renderField({
      field,
      sourceGlobalId: "SA1",
      disabled: false,
      onChange: () => {},
    });
    return { field };
  }

  it("stages a pin chosen via the clock and only commits it on Apply", async () => {
    const user = userEvent.setup();
    const { field } = renderCommitted();

    await user.click(screen.getByRole("button", { name: /edit link/i }));
    await user.click(
      screen.getByRole("button", { name: /pin version for sa20/i }),
    );
    expect(screen.getByTestId("version-lock-dialog")).toHaveAttribute(
      "data-globalid",
      "SA20",
    );
    await user.click(screen.getByTestId("version-lock-dialog-confirm-7"));

    // staged in the editor, not yet committed
    expect(screen.getByText(/pinned to v7/i)).toBeInTheDocument();
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock inspection
    const setAttributesDirty = field.setAttributesDirty;
    expect(vi.mocked(setAttributesDirty)).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: /apply link/i }));

    expect(vi.mocked(setAttributesDirty)).toHaveBeenCalledWith({
      link: {
        relationType: "References",
        targetGlobalId: "SA20",
        versionPin: 7,
      },
    });
  });

  it("resets the staged pin when the target is changed", async () => {
    const user = userEvent.setup();
    renderCommitted();

    await user.click(screen.getByRole("button", { name: /edit link/i }));
    await user.click(
      screen.getByRole("button", { name: /pin version for sa20/i }),
    );
    await user.click(screen.getByTestId("version-lock-dialog-confirm-7"));
    expect(screen.getByText(/pinned to v7/i)).toBeInTheDocument();

    // a pin belongs to a specific target, so retargeting reverts to Latest
    await user.type(
      screen.getByRole("textbox", { name: /target global id/i }),
      "9",
    );

    expect(screen.queryByText(/pinned to v7/i)).not.toBeInTheDocument();
    expect(screen.getByText(/^latest$/i)).toBeInTheDocument();
  });
});
