import { afterEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { cleanup, render, screen, within } from "@/__tests__/customQueries";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";

vi.mock("../../Link/LinkTargetBrowser", () => ({
  default: ({ open }: { open: boolean }) =>
    open ? <div data-testid="link-target-browser" /> : null,
}));

vi.mock("../../Link/ElnRecordPicker", () => ({
  default: ({ open }: { open: boolean }) =>
    open ? <div data-testid="eln-record-picker" /> : null,
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
        data-current-pin={
          currentVersionPin == null ? "" : String(currentVersionPin)
        }
      >
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

import UpdateField from "../UpdateField";
import { type ExtraField } from "../../../../../stores/definitions/ExtraField";
import { type InventoryRecord } from "../../../../../stores/definitions/InventoryRecord";

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
  await user.click(within(listbox).getByText(label));
}

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
    expect(within(listbox).getByText("Text")).toBeInTheDocument();
    expect(within(listbox).getByText("Number")).toBeInTheDocument();
    expect(within(listbox).getByText("Link")).toBeInTheDocument();
  });

  it("reveals Relation type and Target inputs only when Link is selected", async () => {
    const extraField = makeExtraField();
    const record = makeRecord();
    render(
      <ThemeProvider theme={materialTheme}>
        <UpdateField extraField={extraField} index={0} record={record} />
      </ThemeProvider>,
    );

    expect(
      screen.queryByRole("combobox", { name: /relation type/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("textbox", { name: /target global id/i }),
    ).not.toBeInTheDocument();

    await selectFieldType("Link");

    expect(
      screen.getByRole("combobox", { name: /relation type/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("textbox", { name: /target global id/i }),
    ).toBeInTheDocument();
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
    await user.click(
      screen.getByRole("button", { name: /browse inventory/i }),
    );
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

    await user.type(screen.getByRole("textbox", { name: /field name/i }), "Linked sample");
    await selectFieldType("Link");
    await user.type(screen.getByRole("combobox", { name: /relation type/i }), "References");
    await user.type(screen.getByRole("textbox", { name: /target global id/i }), "SA99");

    await user.click(screen.getByRole("button", { name: /update field/i }));

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

    await user.type(
      screen.getByRole("textbox", { name: /field name/i }),
      "Linked sample",
    );
    await selectFieldType("Link");
    await user.type(
      screen.getByRole("combobox", { name: /relation type/i }),
      "References",
    );
    await user.type(
      screen.getByRole("textbox", { name: /target global id/i }),
      "SA99",
    );

    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock inspection
    const setAttributesDirty = extraField.setAttributesDirty;
    expect(vi.mocked(setAttributesDirty)).not.toHaveBeenCalled();
    expect(
      screen.getByRole("button", { name: /update field/i }),
    ).toBeEnabled();
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

    await user.type(
      screen.getByRole("textbox", { name: /target global id/i }),
      "0",
    );

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
    await user.type(
      screen.getByRole("textbox", { name: /target global id/i }),
      "SA99",
    );

    // the relation prompt appears and is attached to the relation field, while the target
    // field keeps its own default helper text (the prompt must not leak onto the target field)
    expect(
      screen.getByText("Pick a DataCite relation type"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Paste a Global ID, or use Browse Inventory above."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("combobox", { name: /relation type/i }),
    ).toHaveAttribute("aria-invalid", "true");
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
    await user.click(screen.getByRole("button", { name: /browse eln/i }));
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

    await user.type(
      screen.getByRole("textbox", { name: /field name/i }),
      "Linked doc",
    );
    await selectFieldType("Link");
    await user.type(
      screen.getByRole("combobox", { name: /relation type/i }),
      "References",
    );
    await user.type(
      screen.getByRole("textbox", { name: /target global id/i }),
      "SD42",
    );

    await user.click(screen.getByRole("button", { name: /update field/i }));

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

    await user.type(
      screen.getByRole("textbox", { name: /field name/i }),
      "Linked sample",
    );
    await selectFieldType("Link");
    await user.type(
      screen.getByRole("combobox", { name: /relation type/i }),
      "References",
    );
    await user.type(
      screen.getByRole("textbox", { name: /target global id/i }),
      "SA99999",
    );

    await user.click(screen.getByRole("button", { name: /update field/i }));

    expect(
      await screen.findByText(
        /SA99999 does not exist, or you do not have permission to view it/i,
      ),
    ).toBeInTheDocument();
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

    await user.type(
      screen.getByRole("textbox", { name: /field name/i }),
      "Linked sample",
    );
    await selectFieldType("Link");
    await user.type(
      screen.getByRole("combobox", { name: /relation type/i }),
      "References",
    );
    const targetInput = screen.getByRole("textbox", {
      name: /target global id/i,
    });
    await user.type(targetInput, "SA99999");
    await user.click(screen.getByRole("button", { name: /update field/i }));
    await screen.findByText(/does not exist/i);

    await user.type(targetInput, "9");

    expect(screen.queryByText(/does not exist/i)).not.toBeInTheDocument();
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

    await user.clear(
      screen.getByRole("textbox", { name: /target global id/i }),
    );

    // the model is flagged so the record-level Save reports the missing target
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock inspection
    const setInvalidInput = extraField.setInvalidInput;
    expect(vi.mocked(setInvalidInput)).toHaveBeenLastCalledWith(true);
    // and the target field itself explains what is wrong
    expect(
      screen.getByText(/target global id is required/i),
    ).toBeInTheDocument();
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

    await user.clear(
      screen.getByRole("textbox", { name: /target global id/i }),
    );
    await user.click(screen.getByRole("button", { name: /cancel update/i }));

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

    await user.type(screen.getByRole("textbox", { name: /field name/i }), "Pinned link");
    await selectFieldType("Link");
    await user.type(screen.getByRole("combobox", { name: /relation type/i }), "References");
    await user.type(screen.getByRole("textbox", { name: /target global id/i }), "SA6v1");

    expect(
      screen.getByText(/without a version.*clock/i),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /update field/i }),
    ).toBeDisabled();
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

    await user.type(screen.getByRole("textbox", { name: /field name/i }), "Loop");
    await selectFieldType("Link");
    await user.type(screen.getByRole("combobox", { name: /relation type/i }), "References");
    await user.type(screen.getByRole("textbox", { name: /target global id/i }), "SA1");

    expect(
      screen.getByRole("button", { name: /update field/i }),
    ).toBeDisabled();
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

    await user.click(
      screen.getByRole("button", { name: /pin version for sa42/i }),
    );
    expect(screen.getByTestId("version-lock-dialog")).toHaveAttribute(
      "data-globalid",
      "SA42",
    );
    await user.click(screen.getByTestId("version-lock-dialog-confirm-7"));

    // staged in the editor, not yet committed
    expect(screen.getByText(/pinned to v7/i)).toBeInTheDocument();
    // eslint-disable-next-line @typescript-eslint/unbound-method -- mock inspection
    const updateExtraField = record.updateExtraField;
    expect(vi.mocked(updateExtraField)).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: /update field/i }));

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

    await user.click(
      screen.getByRole("button", { name: /pin version for sa42/i }),
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

  it("greys the clock for targets that cannot be version-pinned", () => {
    renderExistingLinkField("GL5");

    expect(
      screen.getByRole("button", { name: /pin version for gl5/i }),
    ).toBeDisabled();
  });
});
