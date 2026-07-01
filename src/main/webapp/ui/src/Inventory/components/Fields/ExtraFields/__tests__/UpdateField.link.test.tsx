import { ThemeProvider } from "@mui/material/styles";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, within } from "@/__tests__/customQueries";
import materialTheme from "../../../../../theme";

vi.mock("../../Link/LinkTargetBrowser", () => ({
  default: ({ open }: { open: boolean }) => (open ? <div data-testid="link-target-browser" /> : null),
}));

vi.mock("../../Link/ElnRecordPicker", () => ({
  default: ({ open }: { open: boolean }) => (open ? <div data-testid="eln-record-picker" /> : null),
}));

// Typed targets are existence-checked against the server on Apply; default to "exists".
const { mockCheckLinkTargetExists } = vi.hoisted(() => ({
  mockCheckLinkTargetExists: vi.fn(() => Promise.resolve(true)),
}));
vi.mock("../../Link/linkTargetExists", () => ({
  checkLinkTargetExists: mockCheckLinkTargetExists,
}));

// The real dialog fetches revision history; stub it with confirm buttons.
vi.mock("../../Link/VersionLockDialog", () => ({
  default: ({
    open,
    globalId,
    currentVersionPin,
    onConfirm,
  }: {
    open: boolean;
    globalId: string;
    currentVersionPin: number | null;
    onConfirm: (v: number | null) => void;
  }) =>
    open ? (
      <div
        data-testid="version-lock-dialog"
        data-globalid={globalId}
        data-current-pin={currentVersionPin == null ? "" : String(currentVersionPin)}
      >
        <button type="button" data-testid="version-lock-dialog-confirm-7" onClick={() => onConfirm(7)}>
          {"\n          confirm 7\n        "}
        </button>
      </div>
    ) : null,
}));

// The editor greys the version-pin clock for a no-access committed target via
// this summary hook (which also pulls the ApiService chain at import); default
// to "no summary" (null) so unrelated tests are unaffected.
const { mockUseLinkTargetSummary } = vi.hoisted(() => ({
  mockUseLinkTargetSummary: vi.fn(),
}));
vi.mock("../../Link/useLinkTargetSummary", () => ({
  default: (globalId: string) => mockUseLinkTargetSummary(globalId) as unknown,
}));

import type { ExtraField } from "../../../../../stores/definitions/ExtraField";
import type { InventoryRecord } from "../../../../../stores/definitions/InventoryRecord";
import UpdateField from "../UpdateField";

const FIELD_NAME_LABEL = "inventory:fields.extraFields.fields.fieldName";
const RELATION_TYPE_LABEL = "inventory:fields.extraFields.fields.relationType";
const UPDATE_FIELD_LABEL = "inventory:fields.extraFields.updateField.ariaUpdate";
const CANCEL_UPDATE_LABEL = "inventory:fields.extraFields.updateField.ariaCancel";
const RELATION_HELPER = "inventory:fields.extraFields.link.relationHelper";
const TARGET_HELPER = "inventory:fields.extraFields.link.targetHelper";
const TARGET_NOT_FOUND = "inventory:fields.extraFields.link.targetNotFound";
const FIELD_TYPE_LABELS = {
  Text: "inventory:fields.extraFields.fieldTypes.text",
  Number: "inventory:fields.extraFields.fieldTypes.number",
  Link: "inventory:fields.extraFields.fieldTypes.link",
} as const;

function makeExtraField(
  overrides: Partial<ExtraField> & {
    link?: {
      relationType: string;
      targetGlobalId: string;
      versionPin: number | null;
    } | null;
  } = {},
): ExtraField {
  return {
    id: null,
    name: "",
    type: "Text",
    content: "",
    owner: { fieldNamesInUse: [] } as unknown as InventoryRecord,
    initial: true,
    deleteFieldRequest: false,
    newFieldRequest: true,
    editing: true,
    editable: true,
    invalidInput: false,
    hasContent: false,
    isValid: { isValid: true },
    setAttributes: vi.fn(),
    setAttributesDirty: vi.fn(),
    setEditing: vi.fn(),
    setInvalidInput: vi.fn(),
    link: null,
    ...overrides,
  } as unknown as ExtraField;
}

function makeRecord(): InventoryRecord {
  return {
    globalId: "SA1",
    removeExtraField: vi.fn(),
    updateExtraField: vi.fn(),
  } as unknown as InventoryRecord;
}

async function selectFieldType(label: "Text" | "Number" | "Link") {
  const user = userEvent.setup();
  await user.click(screen.getByTestId("FieldTypeSelect"));
  const listbox = await screen.findByRole("listbox");
  await user.click(within(listbox).getByText(FIELD_TYPE_LABELS[label]));
}

beforeEach(() => {
  mockUseLinkTargetSummary.mockReturnValue(null);
});

describe("UpdateField — Field Type select includes Link", () => {
  afterEach(cleanup);

  it("offers Text, Number and Link in the Field Type dropdown", async () => {
    const extraField = makeExtraField();
    const record = makeRecord();
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <UpdateField extraField={extraField} index={0} record={record} />
      </ThemeProvider>,
    );

    await user.click(screen.getByTestId("FieldTypeSelect"));
    const listbox = await screen.findByRole("listbox");
    expect(within(listbox).getByText(FIELD_TYPE_LABELS.Text)).toBeInTheDocument();
    expect(within(listbox).getByText(FIELD_TYPE_LABELS.Number)).toBeInTheDocument();
    expect(within(listbox).getByText(FIELD_TYPE_LABELS.Link)).toBeInTheDocument();
  });

  it("reveals Relation type and Target inputs only when Link is selected", async () => {
    const extraField = makeExtraField();
    const record = makeRecord();
    render(
      <ThemeProvider theme={materialTheme}>
        <UpdateField extraField={extraField} index={0} record={record} />
      </ThemeProvider>,
    );

    expect(screen.queryByRole("combobox", { name: RELATION_TYPE_LABEL })).not.toBeInTheDocument();
    expect(
      screen.queryByRole("textbox", { name: "inventory:fields.link.editor.targetGlobalId" }),
    ).not.toBeInTheDocument();

    await selectFieldType("Link");

    expect(screen.getByRole("combobox", { name: RELATION_TYPE_LABEL })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "inventory:fields.link.editor.targetGlobalId" })).toBeInTheDocument();
  });

  it("opens the inventory browser dialog from the Browse Inventory button", async () => {
    const extraField = makeExtraField();
    const record = makeRecord();
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <UpdateField extraField={extraField} index={0} record={record} />
      </ThemeProvider>,
    );

    await selectFieldType("Link");
    await user.click(screen.getByRole("button", { name: "inventory:fields.link.editor.browseInventory" }));
    expect(screen.getByTestId("link-target-browser")).toBeInTheDocument();
  });

  it("calls record.updateExtraField with the link payload on Apply", async () => {
    const extraField = makeExtraField();
    const record = makeRecord();
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <UpdateField extraField={extraField} index={0} record={record} />
      </ThemeProvider>,
    );

    await user.type(screen.getByRole("textbox", { name: FIELD_NAME_LABEL }), "Linked sample");
    await selectFieldType("Link");
    await user.type(screen.getByRole("combobox", { name: RELATION_TYPE_LABEL }), "References");
    await user.type(screen.getByRole("textbox", { name: "inventory:fields.link.editor.targetGlobalId" }), "SA99");

    await user.click(screen.getByRole("button", { name: UPDATE_FIELD_LABEL }));

    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock inspection
    const updateExtraField = record.updateExtraField;
    expect(vi.mocked(updateExtraField).mock.calls.at(-1)).toEqual([
      "",
      {
        name: "Linked sample",
        type: "Link",
        link: {
          relationType: "References",
          targetGlobalId: "SA99",
          versionPin: null,
        },
      },
    ]);
  });

  it("leaves the model untouched until Apply, with Apply enabled once filled", async () => {
    // mid-edit values live only in the editor; the record-level Save is greyed
    // out while the editor is open (ExtraFieldModel.isValid), so nothing needs
    // to be synced early. Pushing staged values into the model as they were
    // typed made canSubmit's "something changed" check compare equal values,
    // greying out Apply on a fully-filled new link.
    const extraField = makeExtraField();
    const record = makeRecord();
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <UpdateField extraField={extraField} index={0} record={record} />
      </ThemeProvider>,
    );

    await user.type(screen.getByRole("textbox", { name: FIELD_NAME_LABEL }), "Linked sample");
    await selectFieldType("Link");
    await user.type(screen.getByRole("combobox", { name: RELATION_TYPE_LABEL }), "References");
    await user.type(screen.getByRole("textbox", { name: "inventory:fields.link.editor.targetGlobalId" }), "SA99");

    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock inspection
    const setAttributesDirty = extraField.setAttributesDirty;
    expect(vi.mocked(setAttributesDirty)).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: UPDATE_FIELD_LABEL })).toBeEnabled();
  });

  it("does not sync edits to the model for an existing field (commit-on-Apply preserved)", async () => {
    const extraField = makeExtraField({
      id: 1,
      name: "Existing",
      type: "Link",
      initial: false,
      newFieldRequest: false,
      link: {
        relationType: "References",
        targetGlobalId: "SA42",
        versionPin: null,
      },
    });
    const record = makeRecord();
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <UpdateField extraField={extraField} index={0} record={record} />
      </ThemeProvider>,
    );

    await user.type(screen.getByRole("textbox", { name: "inventory:fields.link.editor.targetGlobalId" }), "0");

    // An existing field must not be mutated until the user presses Update, so
    // that Cancel can revert. The live-sync is scoped to brand-new fields only.
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock inspection
    const setAttributesDirty = extraField.setAttributesDirty;
    expect(vi.mocked(setAttributesDirty)).not.toHaveBeenCalled();
  });

  it("shows the relation-type error on the relation field, not the target field", async () => {
    const extraField = makeExtraField();
    const record = makeRecord();
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <UpdateField extraField={extraField} index={0} record={record} />
      </ThemeProvider>,
    );

    await selectFieldType("Link");
    // provide a valid target but leave the relation type empty
    await user.type(screen.getByRole("textbox", { name: "inventory:fields.link.editor.targetGlobalId" }), "SA99");

    // the relation prompt appears and is attached to the relation field, while the target
    // field keeps its own default helper text (the prompt must not leak onto the target field)
    expect(screen.getByText(RELATION_HELPER)).toBeInTheDocument();
    expect(screen.getByText(TARGET_HELPER)).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: RELATION_TYPE_LABEL })).toHaveAttribute("aria-invalid", "true");
  });

  it("opens the ELN picker from the Browse ELN button", async () => {
    const extraField = makeExtraField();
    const record = makeRecord();
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <UpdateField extraField={extraField} index={0} record={record} />
      </ThemeProvider>,
    );

    await selectFieldType("Link");
    await user.click(screen.getByRole("button", { name: "inventory:fields.link.editor.browseEln" }));
    expect(screen.getByTestId("eln-record-picker")).toBeInTheDocument();
  });

  it("accepts an ELN document target (SD) in the link payload on Apply", async () => {
    const extraField = makeExtraField();
    const record = makeRecord();
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <UpdateField extraField={extraField} index={0} record={record} />
      </ThemeProvider>,
    );

    await user.type(screen.getByRole("textbox", { name: FIELD_NAME_LABEL }), "Linked doc");
    await selectFieldType("Link");
    await user.type(screen.getByRole("combobox", { name: RELATION_TYPE_LABEL }), "References");
    await user.type(screen.getByRole("textbox", { name: "inventory:fields.link.editor.targetGlobalId" }), "SD42");

    await user.click(screen.getByRole("button", { name: UPDATE_FIELD_LABEL }));

    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock inspection
    const updateExtraField = record.updateExtraField;
    expect(vi.mocked(updateExtraField).mock.calls.at(-1)).toEqual([
      "",
      {
        name: "Linked doc",
        type: "Link",
        link: {
          relationType: "References",
          targetGlobalId: "SD42",
          versionPin: null,
        },
      },
    ]);
  });

  it("rejects applying a typed target that does not exist on the server", async () => {
    mockCheckLinkTargetExists.mockResolvedValueOnce(false);
    const extraField = makeExtraField();
    const record = makeRecord();
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <UpdateField extraField={extraField} index={0} record={record} />
      </ThemeProvider>,
    );

    await user.type(screen.getByRole("textbox", { name: FIELD_NAME_LABEL }), "Linked sample");
    await selectFieldType("Link");
    await user.type(screen.getByRole("combobox", { name: RELATION_TYPE_LABEL }), "References");
    await user.type(screen.getByRole("textbox", { name: "inventory:fields.link.editor.targetGlobalId" }), "SA99999");

    await user.click(screen.getByRole("button", { name: UPDATE_FIELD_LABEL }));

    expect(await screen.findByText(TARGET_NOT_FOUND)).toBeInTheDocument();
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock inspection
    const updateExtraField = record.updateExtraField;
    expect(vi.mocked(updateExtraField)).not.toHaveBeenCalled();
  });

  it("clears the existence error once the target is edited", async () => {
    mockCheckLinkTargetExists.mockResolvedValueOnce(false);
    const extraField = makeExtraField();
    const record = makeRecord();
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <UpdateField extraField={extraField} index={0} record={record} />
      </ThemeProvider>,
    );

    await user.type(screen.getByRole("textbox", { name: FIELD_NAME_LABEL }), "Linked sample");
    await selectFieldType("Link");
    await user.type(screen.getByRole("combobox", { name: RELATION_TYPE_LABEL }), "References");
    const targetInput = screen.getByRole("textbox", {
      name: "inventory:fields.link.editor.targetGlobalId",
    });
    await user.type(targetInput, "SA99999");
    await user.click(screen.getByRole("button", { name: UPDATE_FIELD_LABEL }));
    await screen.findByText(TARGET_NOT_FOUND);

    await user.type(targetInput, "9");

    expect(screen.queryByText(TARGET_NOT_FOUND)).not.toBeInTheDocument();
  });

  it("flags the model when an existing link's target is removed, so Save is blocked instead of silently reverting", async () => {
    const extraField = makeExtraField({
      id: 1,
      name: "Existing",
      type: "Link",
      initial: false,
      newFieldRequest: false,
      link: {
        relationType: "References",
        targetGlobalId: "SA42",
        versionPin: null,
      },
    });
    const record = makeRecord();
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <UpdateField extraField={extraField} index={0} record={record} />
      </ThemeProvider>,
    );

    await user.clear(screen.getByRole("textbox", { name: "inventory:fields.link.editor.targetGlobalId" }));

    // the model is flagged so the record-level Save reports the missing target
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock inspection
    const setInvalidInput = extraField.setInvalidInput;
    expect(vi.mocked(setInvalidInput)).toHaveBeenLastCalledWith(true);
    // and the target field itself explains what is wrong
    expect(screen.getByText(/target global id is required/i)).toBeInTheDocument();
  });

  it("clears the invalid flag when the edit is cancelled", async () => {
    const extraField = makeExtraField({
      id: 1,
      name: "Existing",
      type: "Link",
      initial: false,
      newFieldRequest: false,
      link: {
        relationType: "References",
        targetGlobalId: "SA42",
        versionPin: null,
      },
    });
    const record = makeRecord();
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <UpdateField extraField={extraField} index={0} record={record} />
      </ThemeProvider>,
    );

    await user.clear(screen.getByRole("textbox", { name: "inventory:fields.link.editor.targetGlobalId" }));
    await user.click(screen.getByRole("button", { name: CANCEL_UPDATE_LABEL }));

    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock inspection
    const setInvalidInput = extraField.setInvalidInput;
    expect(vi.mocked(setInvalidInput)).toHaveBeenLastCalledWith(false);
  });

  it("rejects a typed version suffix and points the user at the clock", async () => {
    const extraField = makeExtraField();
    const record = makeRecord();
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <UpdateField extraField={extraField} index={0} record={record} />
      </ThemeProvider>,
    );

    await user.type(screen.getByRole("textbox", { name: FIELD_NAME_LABEL }), "Pinned link");
    await selectFieldType("Link");
    await user.type(screen.getByRole("combobox", { name: RELATION_TYPE_LABEL }), "References");
    await user.type(screen.getByRole("textbox", { name: "inventory:fields.link.editor.targetGlobalId" }), "SA6v1");

    expect(screen.getByText(/without a version.*clock/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: UPDATE_FIELD_LABEL })).toBeDisabled();
  });

  it("disables Apply when self-link is selected", async () => {
    const extraField = makeExtraField();
    const record = makeRecord();
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <UpdateField extraField={extraField} index={0} record={record} />
      </ThemeProvider>,
    );

    await user.type(screen.getByRole("textbox", { name: FIELD_NAME_LABEL }), "Loop");
    await selectFieldType("Link");
    await user.type(screen.getByRole("combobox", { name: RELATION_TYPE_LABEL }), "References");
    await user.type(screen.getByRole("textbox", { name: "inventory:fields.link.editor.targetGlobalId" }), "SA1");

    expect(screen.getByRole("button", { name: UPDATE_FIELD_LABEL })).toBeDisabled();
  });
});

describe("UpdateField — version pin is edited in the editor and committed on Update", () => {
  afterEach(cleanup);

  function renderExistingLinkField(targetGlobalId = "SA42") {
    const extraField = makeExtraField({
      id: 1,
      name: "Linked sample",
      type: "Link",
      initial: false,
      newFieldRequest: false,
      link: {
        relationType: "References",
        targetGlobalId,
        versionPin: null,
      },
    });
    const record = makeRecord();
    render(
      <ThemeProvider theme={materialTheme}>
        <UpdateField extraField={extraField} index={0} record={record} />
      </ThemeProvider>,
    );
    return { extraField, record };
  }

  it("stages a pin chosen via the clock and only commits it on Update", async () => {
    const { record } = renderExistingLinkField();
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "inventory:fields.link.editor.pinVersionFor" }));
    expect(screen.getByTestId("version-lock-dialog")).toHaveAttribute("data-globalid", "SA42");
    await user.click(screen.getByTestId("version-lock-dialog-confirm-7"));

    // staged in the editor, not yet committed
    expect(screen.getByText("inventory:fields.link.editor.pinnedVersion")).toBeInTheDocument();
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock inspection
    const updateExtraField = record.updateExtraField;
    expect(vi.mocked(updateExtraField)).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: UPDATE_FIELD_LABEL }));

    expect(vi.mocked(updateExtraField).mock.calls.at(-1)).toEqual([
      "Linked sample",
      {
        name: "Linked sample",
        type: "Link",
        link: {
          relationType: "References",
          targetGlobalId: "SA42",
          versionPin: 7,
        },
      },
    ]);
  });

  it("resets the staged pin when the target is changed", async () => {
    renderExistingLinkField();
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "inventory:fields.link.editor.pinVersionFor" }));
    await user.click(screen.getByTestId("version-lock-dialog-confirm-7"));
    expect(screen.getByText("inventory:fields.link.editor.pinnedVersion")).toBeInTheDocument();

    // a pin belongs to a specific target, so retargeting reverts to Latest
    await user.type(screen.getByRole("textbox", { name: "inventory:fields.link.editor.targetGlobalId" }), "9");

    expect(screen.queryByText("inventory:fields.link.editor.pinnedVersion")).not.toBeInTheDocument();
    expect(screen.getByText("inventory:fields.link.editor.latest")).toBeInTheDocument();
  });

  it("greys the clock for targets that cannot be version-pinned", () => {
    renderExistingLinkField("GL5");

    expect(screen.getByRole("button", { name: "inventory:fields.link.editor.pinVersionFor" })).toBeDisabled();
  });

  it("greys the clock while editing a no-access committed target", () => {
    // SD supports version-pinning, so this isolates the no-access rule from the
    // supports-pinning rule: an unshared target cannot be pinned. A successful
    // retarget+Update (which commits a readable target) re-enables it on the
    // next edit; Discard leaves the unshared target and keeps it greyed.
    mockUseLinkTargetSummary.mockReturnValue({
      globalId: "SD5",
      name: null,
      type: null,
      deleted: false,
      readable: false,
    });
    renderExistingLinkField("SD5");

    expect(screen.getByRole("button", { name: "inventory:fields.link.editor.pinVersionFor" })).toBeDisabled();
  });
});
