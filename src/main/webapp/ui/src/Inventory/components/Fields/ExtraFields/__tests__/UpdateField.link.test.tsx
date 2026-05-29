import { afterEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";

vi.mock("../../Link/LinkTargetBrowser", () => ({
  default: ({ open }: { open: boolean }) =>
    open ? <div data-testid="link-target-browser" /> : null,
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
