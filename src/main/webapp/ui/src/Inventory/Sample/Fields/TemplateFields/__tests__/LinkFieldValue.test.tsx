import { ThemeProvider } from "@mui/material/styles";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@/__tests__/customQueries";
import type { Field } from "@/stores/definitions/Field";
import materialTheme from "@/theme";
import LinkFieldValue from "../LinkFieldValue";

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
// The editor greys the version-pin clock for a no-access committed target via
// this summary hook; default to "no summary" (null) so unrelated tests are
// unaffected, and override per-test for the no-access case.
const { mockUseLinkTargetSummary } = vi.hoisted(() => ({
  mockUseLinkTargetSummary: vi.fn(),
}));
vi.mock("@/Inventory/components/Fields/Link/useLinkTargetSummary", () => ({
  default: (globalId: string) => mockUseLinkTargetSummary(globalId) as unknown,
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
        <button type="button" data-testid="version-lock-dialog-confirm-7" onClick={() => onConfirm(7)}>
          confirm 7
        </button>
      </div>
    ) : null,
}));
// LinkField has its own tests; stub it so we can assert it is used for the committed-link display
// without pulling in its info/version dialog chain. The edit affordance is no longer LinkField's
// inline button but the settings cog LinkFieldValue overlays on the card, so the stub omits it.
vi.mock("@/Inventory/components/Fields/Link/LinkField", () => ({
  // LinkField now renders its own static Open link from the target/versionPin, so
  // LinkFieldValue no longer passes an onOpen callback (open routing is covered by
  // LinkField's own tests).
  default: ({ link }: { link: { relationType: string; targetGlobalId: string } }) => (
    <div data-testid="link-field-display">
      <span>{link.relationType}</span>
      <span>{link.targetGlobalId}</span>
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
    setLinkEditInProgress: vi.fn(),
    ...overrides,
  } as unknown as Field;
}

// The editor's Apply button uses the custom `callToAction` palette, so a theme is required.
function renderField(props: { field: Field; sourceGlobalId: string; disabled: boolean; onChange: () => void }) {
  return render(
    <ThemeProvider theme={materialTheme}>
      <LinkFieldValue {...props} />
    </ThemeProvider>,
  );
}

beforeEach(() => {
  mockUseLinkTargetSummary.mockReturnValue(null);
});

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
    expect(screen.queryByRole("button", { name: /apply link/i })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /edit link/i }));

    // the editor is now shown
    expect(screen.getByRole("button", { name: /apply link/i })).toBeInTheDocument();
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

    await user.type(screen.getByRole("textbox", { name: /target global id/i }), "SS33");
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

    await user.type(screen.getByRole("textbox", { name: /target global id/i }), "XX5");
    await user.click(screen.getByRole("button", { name: /open/i }));
    await user.click(screen.getByRole("option", { name: "References" }));

    expect(
      screen.getByText(/target must be an inventory item or an eln document, notebook or gallery file/i),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /apply link/i })).toBeDisabled();
    expect(setAttributesDirty).not.toHaveBeenCalled();
  });

  it("flags the field model while link edits are unapplied so record save is blocked", async () => {
    const user = userEvent.setup();
    const setLinkEditInProgress = vi.fn();
    const field = linkField({
      link: {
        relationType: "References",
        targetGlobalId: "SA20",
        versionPin: null,
      },
      setLinkEditInProgress,
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

    expect(setLinkEditInProgress).toHaveBeenLastCalledWith(true);

    // discarding restores the committed link and clears the flag
    await user.click(screen.getByRole("button", { name: /discard link/i }));
    expect(setLinkEditInProgress).toHaveBeenLastCalledWith(false);
  });

  it("blocks record save when the editor is merely opened on a committed link, without flagging an error", async () => {
    // RSDEV-1201: opening the editor on a committed link (the cog path) puts the field into an
    // editing state. The record save must be blocked until the user applies or discards, even
    // before any value is changed - otherwise saving in this state silently dropped the committed
    // link and displayed it as "None". The block must NOT go through field.error, which would
    // immediately surface an inline "Invalid value" message and a section-header error just from
    // opening the editor; it uses the dedicated linkEditInProgress flag instead.
    const user = userEvent.setup();
    const setError = vi.fn();
    const setLinkEditInProgress = vi.fn();
    const field = linkField({
      link: {
        relationType: "References",
        targetGlobalId: "SA20",
        versionPin: null,
      },
      setError,
      setLinkEditInProgress,
    });
    renderField({
      field,
      sourceGlobalId: "SA1",
      disabled: false,
      onChange: () => {},
    });

    // open the editor without making any change
    await user.click(screen.getByRole("button", { name: /edit link/i }));

    expect(setLinkEditInProgress).toHaveBeenLastCalledWith(true);
    // opening the editor is an in-progress state, not an error
    expect(setError).not.toHaveBeenCalledWith(true);
  });

  it("does not block save for an empty optional field whose editor is open by default", () => {
    // RSDEV-1201: an empty link field drops straight into the editor, but with nothing committed
    // and nothing typed there is nothing to lose by saving, so it must stay saveable.
    const setLinkEditInProgress = vi.fn();
    const field = linkField({ link: null, setLinkEditInProgress });
    renderField({
      field,
      sourceGlobalId: "SA1",
      disabled: false,
      onChange: () => {},
    });

    // the editor is shown without any user interaction
    expect(screen.getByRole("button", { name: /apply link/i })).toBeInTheDocument();
    expect(setLinkEditInProgress).not.toHaveBeenCalledWith(true);
  });

  it("keeps showing a committed link in view mode even if the editor was left open", async () => {
    // RSDEV-1201: after opening the editor (editing=true) the record can flip to view mode
    // (disabled=true), e.g. when the surrounding edit is cancelled. The committed link must still
    // render, not collapse to the "None" placeholder.
    const user = userEvent.setup();
    const field = linkField({
      link: {
        relationType: "References",
        targetGlobalId: "SA20",
        versionPin: null,
      },
    });
    const { rerender } = renderField({
      field,
      sourceGlobalId: "SA1",
      disabled: false,
      onChange: () => {},
    });

    await user.click(screen.getByRole("button", { name: /edit link/i }));
    expect(screen.getByRole("button", { name: /apply link/i })).toBeInTheDocument();

    // record switches to view mode while the editor is still open
    rerender(
      <ThemeProvider theme={materialTheme}>
        <LinkFieldValue field={field} sourceGlobalId="SA1" disabled={true} onChange={() => {}} />
      </ThemeProvider>,
    );

    expect(screen.getByTestId("link-field-display")).toBeInTheDocument();
    expect(screen.queryByText("None")).not.toBeInTheDocument();
  });

  it("clears the save block when the editor was left open but the record is in view mode", async () => {
    // RSDEV-1201 (review follow-up): the editor (and its Apply/Discard buttons) is never shown in
    // view mode (disabled=true), so a committed-link field flipped to view mode while the editor was
    // left open must NOT keep reporting an in-progress edit. Otherwise FieldModel.validate() blocks
    // save with an "Apply or discard" message the user cannot act on, because the editor is hidden.
    const user = userEvent.setup();
    const setLinkEditInProgress = vi.fn();
    const field = linkField({
      link: {
        relationType: "References",
        targetGlobalId: "SA20",
        versionPin: null,
      },
      setLinkEditInProgress,
    });
    const { rerender } = renderField({
      field,
      sourceGlobalId: "SA1",
      disabled: false,
      onChange: () => {},
    });

    // opening the editor on the committed link blocks save while still editable
    await user.click(screen.getByRole("button", { name: /edit link/i }));
    expect(setLinkEditInProgress).toHaveBeenLastCalledWith(true);

    // record flips to view mode while the editor was left open
    rerender(
      <ThemeProvider theme={materialTheme}>
        <LinkFieldValue field={field} sourceGlobalId="SA1" disabled={true} onChange={() => {}} />
      </ThemeProvider>,
    );

    // no editor is visible to apply/discard from, so the field must stop blocking save
    expect(setLinkEditInProgress).toHaveBeenLastCalledWith(false);
  });

  it("shows the field name while editing", () => {
    // the editor opens immediately for an empty field; because the FormField
    // label above the editor is hidden (the name is meant to live inside the
    // card), the editor itself must surface the field name
    renderField({
      field: linkField({ name: "Parent sample" }),
      sourceGlobalId: "SA1",
      disabled: false,
      onChange: () => {},
    });

    expect(screen.getByRole("button", { name: /apply link/i })).toBeInTheDocument();
    expect(screen.getByText("Parent sample")).toBeInTheDocument();
  });

  it("shows the field name after opening the editor on a committed link", async () => {
    // the cog->editor path is the second way into the editor; the committed
    // card (stubbed here) does not render the name, so finding it after Edit
    // proves the editor itself surfaces the field name
    const user = userEvent.setup();
    const field = linkField({
      name: "Parent sample",
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

    await user.click(screen.getByRole("button", { name: /edit link/i }));

    expect(screen.getByRole("button", { name: /apply link/i })).toBeInTheDocument();
    expect(screen.getByText("Parent sample")).toBeInTheDocument();
  });

  it("greys out the version-pin clock while editing a no-access committed link", async () => {
    // an unshared ELN target cannot be version-pinned; the clock stays greyed
    // until Apply commits a different, readable target (and is reimposed on the
    // next edit after Discard reverts to the unshared target)
    mockUseLinkTargetSummary.mockReturnValue({
      globalId: "SD5",
      name: null,
      type: null,
      deleted: false,
      readable: false,
    });
    const user = userEvent.setup();
    const field = linkField({
      link: {
        relationType: "References",
        targetGlobalId: "SD5",
        versionPin: null,
      },
    });
    renderField({
      field,
      sourceGlobalId: "SA1",
      disabled: false,
      onChange: () => {},
    });

    await user.click(screen.getByRole("button", { name: /edit link/i }));

    expect(screen.getByRole("button", { name: /pin version for sd5/i })).toBeDisabled();
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

    await user.type(screen.getByRole("textbox", { name: /target global id/i }), "SS9999");
    await user.click(screen.getByRole("button", { name: /open/i }));
    await user.click(screen.getByRole("option", { name: "References" }));
    await user.click(screen.getByRole("button", { name: /apply link/i }));

    expect(
      await screen.findByText(/SS9999 does not exist, or you do not have permission to view it/i),
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

    expect(screen.getByText("An item cannot link to itself.")).toBeInTheDocument();
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
    await user.click(screen.getByRole("button", { name: /pin version for sa20/i }));
    expect(screen.getByTestId("version-lock-dialog")).toHaveAttribute("data-globalid", "SA20");
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
    await user.click(screen.getByRole("button", { name: /pin version for sa20/i }));
    await user.click(screen.getByTestId("version-lock-dialog-confirm-7"));
    expect(screen.getByText(/pinned to v7/i)).toBeInTheDocument();

    // a pin belongs to a specific target, so retargeting reverts to Latest
    await user.type(screen.getByRole("textbox", { name: /target global id/i }), "9");

    expect(screen.queryByText(/pinned to v7/i)).not.toBeInTheDocument();
    expect(screen.getByText(/^latest$/i)).toBeInTheDocument();
  });
});
