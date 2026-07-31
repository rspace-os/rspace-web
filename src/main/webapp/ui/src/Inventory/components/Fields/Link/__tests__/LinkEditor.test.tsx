import { ThemeProvider } from "@mui/material/styles";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import materialTheme from "../../../../../theme";
import LinkEditor, { type LinkEditorProps } from "../LinkEditor";

// the three picker dialogs are closed in every case here and fetch on open; stub them out so the
// layout assertions below are about the editor's own structure
vi.mock("../LinkTargetBrowser", () => ({ default: () => null }));
vi.mock("../ElnRecordPicker", () => ({ default: () => null }));
vi.mock("../VersionLockDialog", () => ({ default: () => null }));

function renderEditor(overrides: Partial<LinkEditorProps> = {}) {
  const props: LinkEditorProps = {
    relationType: "",
    onRelationTypeChange: vi.fn(),
    relationOptions: ["References", "IsDerivedFrom"],
    relationFreeSolo: false,
    relationLabel: "Relationship type",
    targetGlobalId: "",
    onTargetChange: vi.fn(),
    targetError: false,
    targetHelperText: "",
    versionPin: null,
    onVersionPinChange: vi.fn(),
    canPinVersion: false,
    ...overrides,
  };
  return render(
    <ThemeProvider theme={materialTheme}>
      <LinkEditor {...props} />
    </ThemeProvider>,
  );
}

// this repo tags elements with `data-test-id`, which is not testing-library's default
// `testIdAttribute`; the sibling LinkField tests reach them by selector, so these do too
function row(container: HTMLElement, testId: string): HTMLElement {
  const found = container.querySelector<HTMLElement>(`[data-test-id="${testId}"]`);
  if (!found) throw new Error(`no element with data-test-id="${testId}"`);
  return found;
}

describe("LinkEditor layout", () => {
  it("groups the target field and both browse buttons on one row under a Target heading", () => {
    const { container } = renderEditor();

    expect(screen.getByRole("heading", { name: "inventory:fields.link.editor.target" })).toBeInTheDocument();

    const targetRow = row(container, "LinkEditor-targetRow");
    expect(
      within(targetRow).getByRole("textbox", { name: "inventory:fields.link.editor.targetGlobalId" }),
    ).toBeInTheDocument();
    expect(
      within(targetRow).getByRole("button", { name: "inventory:fields.link.editor.browseInventory" }),
    ).toBeInTheDocument();
    expect(
      within(targetRow).getByRole("button", { name: "inventory:fields.link.editor.browseEln" }),
    ).toBeInTheDocument();
  });

  it("puts the relationship type below the target group", () => {
    const { container } = renderEditor();

    const targetRow = row(container, "LinkEditor-targetRow");
    const relation = screen.getByRole("combobox", { name: "Relationship type" });

    expect(targetRow.compareDocumentPosition(relation) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("gives the version pill and its clock a Version heading", () => {
    const { container } = renderEditor();

    expect(screen.getByRole("heading", { name: "inventory:fields.link.editor.version" })).toBeInTheDocument();

    const versionRow = row(container, "LinkEditor-versionRow");
    expect(versionRow.querySelector('[data-test-id="LinkEditor-version"]')).toBeInTheDocument();
    expect(
      within(versionRow).getByRole("button", { name: "inventory:fields.link.editor.pinVersion" }),
    ).toBeInTheDocument();
  });

  it("shows the selected target below the id field and pickers, not inside that row", () => {
    const { container } = renderEditor({ targetGlobalId: "SA42" });

    const targetRow = row(container, "LinkEditor-targetRow");
    const chip = container.querySelector<HTMLElement>('[data-test-id="LinkTarget-globalId"]');
    expect(chip).toBeInTheDocument();
    expect(targetRow.contains(chip)).toBe(false);
    expect(targetRow.compareDocumentPosition(chip as HTMLElement) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("keeps the chip's delete affordance, which clears the target", async () => {
    const user = userEvent.setup();
    const onTargetChange = vi.fn();
    const { container } = renderEditor({ targetGlobalId: "SA42", onTargetChange });

    const chip = container.querySelector<HTMLElement>('[data-test-id="LinkTarget-globalId"]');
    const deleteIcon = chip?.querySelector<HTMLElement>(".MuiChip-deleteIcon");
    expect(deleteIcon).toBeInTheDocument();

    await user.click(deleteIcon as HTMLElement);
    expect(onTargetChange).toHaveBeenCalledWith("", "");
  });

  it("keeps the target field narrow, since a Global ID is short", () => {
    const { container } = renderEditor();

    const field = within(row(container, "LinkEditor-targetRow"))
      .getByRole("textbox", { name: "inventory:fields.link.editor.targetGlobalId" })
      .closest<HTMLElement>(".MuiTextField-root");
    // a Global ID tops out around 20 characters, so the field is sized rather than filling the row
    expect(window.getComputedStyle(field as HTMLElement).width).toBe("11em");
  });
});
