import { chipClasses } from "@mui/material/Chip";
import { ThemeProvider } from "@mui/material/styles";
import { textFieldClasses } from "@mui/material/TextField";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { HeadingContext } from "@/components/DynamicHeadingLevel";
import materialTheme from "../../../../../theme";
import LinkEditor, { type LinkEditorProps } from "../LinkEditor";

// the three picker dialogs are closed in every case here and fetch on open; stub them out so the
// layout assertions below are about the editor's own structure
vi.mock("../LinkTargetBrowser", () => ({ default: () => null }));
vi.mock("../ElnRecordPicker", () => ({ default: () => null }));
vi.mock("../VersionLockDialog", () => ({ default: () => null }));

const { mockDeploymentProperty, filestoreNode, remoteFile } = vi.hoisted(() => {
  const filestoreNode = { id: 12, name: "My S3", path: [] };
  return {
    mockDeploymentProperty: vi.fn(),
    filestoreNode,
    remoteFile: { id: null, name: "run.dat", path: [filestoreNode], remotePath: "/XRD/run.dat" },
  };
});
vi.mock("@/hooks/api/useDeploymentProperty", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/hooks/api/useDeploymentProperty")>()),
  useDeploymentProperty: (name: string) => mockDeploymentProperty(name) as unknown,
}));
// the Gallery picker is lazy-loaded and fetches listings; stub it to show what the editor asked
// for and to answer with a pick
vi.mock("@/eln/gallery/picker", async () => {
  const { default: RsSet } = await import("@/util/set");
  return {
    default: ({
      open,
      initialSection,
      onlyAllowSingleSelection,
      onSubmit,
      validateSelection,
    }: {
      open: boolean;
      initialSection?: string;
      onlyAllowSingleSelection?: boolean;
      onSubmit: (files: unknown) => void;
      validateSelection: (file: unknown) => { isOk: boolean };
    }) =>
      open ? (
        <div
          data-testid="gallery-picker"
          data-section={initialSection}
          data-single={String(Boolean(onlyAllowSingleSelection))}
          data-filestore-node={validateSelection(filestoreNode).isOk ? "accepted" : "refused"}
          data-remote-file={validateSelection(remoteFile).isOk ? "accepted" : "refused"}
        >
          <button type="button" onClick={() => onSubmit(new RsSet([remoteFile]))}>
            {"pick run.dat"}
          </button>
        </div>
      ) : null,
  };
});

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
    const deleteIcon = chip?.querySelector<HTMLElement>(`.${chipClasses.deleteIcon}`);
    expect(deleteIcon).toBeInTheDocument();

    await user.click(deleteIcon as HTMLElement);
    expect(onTargetChange).toHaveBeenCalledWith("", "");
  });

  it("keeps the target field narrow, since a Global ID is short", () => {
    const { container } = renderEditor();

    const field = within(row(container, "LinkEditor-targetRow"))
      .getByRole("textbox", { name: "inventory:fields.link.editor.targetGlobalId" })
      .closest<HTMLElement>(`.${textFieldClasses.root}`);
    // a Global ID tops out around 20 characters, so the field is sized rather than filling the row
    expect(window.getComputedStyle(field as HTMLElement).width).toBe("11em");
  });
});

describe("LinkEditor target heading", () => {
  it("labels the target group 'Target' by default", () => {
    renderEditor();

    expect(screen.getByRole("heading", { name: "inventory:fields.link.editor.target" })).toBeInTheDocument();
  });

  it("lets the caller name the target group, so a template can call it a default link target", () => {
    // the same editor serves an item's own link, an extra-field link and a template's default; only
    // the template's copy is a "default link target"
    renderEditor({ targetHeading: "Default link target" });

    expect(screen.getByRole("heading", { name: "Default link target" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "inventory:fields.link.editor.target" })).not.toBeInTheDocument();
  });
});

describe("LinkEditor target row alignment", () => {
  it("gives the target field no floating label, so its input line starts level with the buttons", () => {
    // jsdom has no layout, so this asserts the structural cause rather than the pixels: a
    // variant="standard" TextField with a label stacks the label above the input, dropping the
    // input ~16px below the neighbouring buttons. Without one they share a top edge. The group
    // heading and the helper text carry the naming instead.
    const { container } = renderEditor();

    const targetRow = row(container, "LinkEditor-targetRow");
    expect(targetRow.querySelector("label")).toBeNull();

    const input = within(targetRow).getByRole("textbox", {
      name: "inventory:fields.link.editor.targetGlobalId",
    });
    expect(input).toHaveAttribute("placeholder", "inventory:fields.link.editor.targetPlaceholder");
  });
});

describe("LinkEditor heading level", () => {
  function renderAtLevel(props: Partial<LinkEditorProps>, level: 1 | 2 | 3 | 4 | 5 | 6) {
    return render(
      <ThemeProvider theme={materialTheme}>
        <HeadingContext level={level}>
          <LinkEditor
            relationType=""
            onRelationTypeChange={vi.fn()}
            relationOptions={[]}
            relationFreeSolo={false}
            relationLabel="Relationship type"
            targetGlobalId=""
            onTargetChange={vi.fn()}
            targetError={false}
            targetHelperText=""
            versionPin={null}
            onVersionPinChange={vi.fn()}
            canPinVersion={false}
            {...props}
          />
        </HeadingContext>
      </ThemeProvider>,
    );
  }

  it("renders its group headings at the ambient level, not one deeper", () => {
    // The item and extra-field callers render no heading of their own in edit mode: FieldLabel
    // only emits a Heading when disabled, so nesting a level here would skip one (h3 panel title
    // straight to h5) and axe's heading-order rule would flag it.
    renderAtLevel({}, 4);

    expect(screen.getByRole("heading", { name: "inventory:fields.link.editor.target" }).tagName).toBe("H4");
    expect(screen.getByRole("heading", { name: "inventory:fields.link.editor.version" }).tagName).toBe("H4");
  });

  it("descends one level when the caller already renders a heading above", () => {
    // the template editor wraps this in an InputWrapper whose FormControl does render a Heading
    // ("Default Link (optional)"), so there the groups genuinely are subheadings of it
    renderAtLevel({ nestHeadings: true, targetHeading: "Default link target" }, 4);

    expect(screen.getByRole("heading", { name: "Default link target" }).tagName).toBe("H5");
  });
});

describe("LinkEditor filestore target", () => {
  beforeEach(() => {
    mockDeploymentProperty.mockReset();
    mockDeploymentProperty.mockReturnValue({ tag: "success", value: true });
  });

  it("offers Browse filestores when the caller takes a filestore target", () => {
    renderEditor({ onFilestoreTargetChange: vi.fn() });

    expect(screen.getByRole("button", { name: "inventory:fields.link.editor.browseFilestores" })).toBeInTheDocument();
    expect(mockDeploymentProperty).toHaveBeenCalledWith("netfilestores.enabled");
  });

  it("offers none, and asks the server nothing, when the caller takes no filestore target", () => {
    renderEditor();

    expect(
      screen.queryByRole("button", { name: "inventory:fields.link.editor.browseFilestores" }),
    ).not.toBeInTheDocument();
    expect(mockDeploymentProperty).not.toHaveBeenCalled();
  });

  it("offers none while filestores are switched off on the server", () => {
    mockDeploymentProperty.mockReturnValue({ tag: "success", value: false });

    renderEditor({ onFilestoreTargetChange: vi.fn() });

    expect(
      screen.queryByRole("button", { name: "inventory:fields.link.editor.browseFilestores" }),
    ).not.toBeInTheDocument();
  });

  it("opens the Gallery picker on Filestores for one item and reports the pick", async () => {
    const user = userEvent.setup();
    const onFilestoreTargetChange = vi.fn();
    renderEditor({ onFilestoreTargetChange });

    await user.click(screen.getByRole("button", { name: "inventory:fields.link.editor.browseFilestores" }));
    const picker = await screen.findByTestId("gallery-picker");
    expect(picker).toHaveAttribute("data-section", "NetworkFiles");
    expect(picker).toHaveAttribute("data-single", "true");
    // only an item inside a filestore can be confirmed
    expect(picker).toHaveAttribute("data-filestore-node", "refused");
    expect(picker).toHaveAttribute("data-remote-file", "accepted");

    await user.click(screen.getByRole("button", { name: "pick run.dat" }));

    expect(onFilestoreTargetChange).toHaveBeenCalledWith({ filestoreId: 12, remotePath: "/XRD/run.dat" }, "My S3");
    expect(screen.queryByTestId("gallery-picker")).not.toBeInTheDocument();
  });

  it("shows a filestore target as name and path, without the version group, and removes it", async () => {
    const user = userEvent.setup();
    const onFilestoreTargetChange = vi.fn();
    const { container } = renderEditor({
      filestoreTarget: { filestoreId: 12, remotePath: "/XRD/run.dat" },
      filestoreName: "My S3",
      onFilestoreTargetChange,
    });

    expect(screen.getByText("inventory:fields.link.field.filestoreItemLabel")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "inventory:fields.link.editor.version" })).not.toBeInTheDocument();
    expect(container.querySelector('[data-test-id="LinkTarget-globalId"]')).not.toBeInTheDocument();

    const chip = container.querySelector<HTMLElement>('[data-test-id="LinkTarget-filestore"]');
    await user.click(chip?.querySelector<HTMLElement>(`.${chipClasses.deleteIcon}`) as HTMLElement);

    expect(onFilestoreTargetChange).toHaveBeenCalledWith(null, "");
  });
});
