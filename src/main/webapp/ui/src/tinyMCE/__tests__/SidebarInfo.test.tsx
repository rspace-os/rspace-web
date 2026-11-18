import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { BondStrengthsDocumentContent } from "../bondStrengths/insertIntoDocument";
import SidebarInfo from "../SidebarInfo";

const bondStrengthsContent: BondStrengthsDocumentContent = {
  imageSrc: "data:image/svg+xml;charset=utf-8,%3Csvg%2F%3E",
  imageAlt: "Structure with bond strengths",
  caption: "Predicted bond strengths",
  columns: { bond: "Bond", kcalMol: "kcal/mol", kjMol: "kJ/mol" },
  rows: [
    { bond: "C1-H (×3), weakest bond", kcalMol: "89.3", kjMol: "373.5" },
    { bond: "C2:C3", notAvailable: "n/a (ring bond)" },
  ],
};

vi.mock("../ChemCard", () => ({
  default: ({
    item,
    onClose,
    onInsertBondStrengths,
  }: {
    item: { id: string };
    onClose: (id?: string) => void;
    onInsertBondStrengths: (id: string | undefined, content: BondStrengthsDocumentContent) => void;
  }) => (
    <section aria-label={`Chemical ${item.id}`}>
      <span>{item.id}</span>
      <button onClick={() => onClose(item.id)} type="button">
        {"Close"}
      </button>
      <button onClick={() => onInsertBondStrengths(item.id, bondStrengthsContent)} type="button">
        {"Insert bond strengths"}
      </button>
    </section>
  ),
}));

function createIframeWithDocument() {
  const iframe = document.createElement("iframe");
  const iframeDocument = document.implementation.createHTMLDocument("iframe");

  Object.defineProperty(iframe, "contentDocument", {
    configurable: true,
    value: iframeDocument,
  });
  Object.defineProperty(iframe, "contentWindow", {
    configurable: true,
    value: { document: iframeDocument },
  });

  return { iframe, iframeDocument };
}

function createChemicalImage(iframeDocument: Document, id: string) {
  const chemicalImage = iframeDocument.createElement("img");

  chemicalImage.id = id;
  chemicalImage.className = "chem";
  chemicalImage.src = "data:image/gif;base64,R0lGODlhAQABAAAAACw=";
  iframeDocument.body.appendChild(chemicalImage);

  return chemicalImage;
}

describe("SidebarInfo accessibility", () => {
  beforeEach(() => {
    delete (globalThis as { tinymce?: unknown }).tinymce;
  });

  afterEach(() => {
    delete (globalThis as { tinymce?: unknown }).tinymce;
  });

  it("has no accessibility violations when showing selected chemistry cards", async () => {
    const { iframe, iframeDocument } = createIframeWithDocument();
    const execCommand = vi.fn();
    const chemicalImage = createChemicalImage(iframeDocument, "chem-1");

    Object.assign(globalThis, {
      tinymce: {
        activeEditor: {
          execCommand,
        },
      },
    });

    const { baseElement } = render(<SidebarInfo iframe={iframe} />);

    act(() => {
      chemicalImage.dispatchEvent(new Event("click", { bubbles: true }));
    });

    expect(await screen.findByRole("region", { name: "Chemical chem-1" })).toBeVisible();

    // @ts-expect-error toBeAccessible is from @sa11y/vitest
    // eslint-disable-next-line @typescript-eslint/no-unsafe-call
    await expect(baseElement).toBeAccessible();
  });

  it("shows a chemical card when a chemistry image is inserted via custom event", async () => {
    const { iframe, iframeDocument } = createIframeWithDocument();

    createChemicalImage(iframeDocument, "chem-2");

    render(<SidebarInfo iframe={iframe} />);

    act(() => {
      document.dispatchEvent(new CustomEvent("tinymce-chem-inserted", { detail: "chem-2" }));
    });

    expect(await screen.findByRole("region", { name: "Chemical chem-2" })).toBeVisible();
  });

  it("removes cards when chemistry images are removed from the editor document", async () => {
    const { iframe, iframeDocument } = createIframeWithDocument();
    const chemicalImage = createChemicalImage(iframeDocument, "chem-3");

    render(<SidebarInfo iframe={iframe} />);

    act(() => {
      chemicalImage.dispatchEvent(new Event("click", { bubbles: true }));
    });

    expect(await screen.findByRole("region", { name: "Chemical chem-3" })).toBeVisible();

    act(() => {
      chemicalImage.remove();
    });

    await waitFor(() => {
      expect(screen.queryByRole("region", { name: "Chemical chem-3" })).not.toBeInTheDocument();
    });
  });

  it("inserts bond strengths after the block that holds the chemistry image, as one undo step", async () => {
    const user = userEvent.setup();
    const { iframe, iframeDocument } = createIframeWithDocument();
    const chemicalParagraph = iframeDocument.createElement("p");
    const nextParagraph = iframeDocument.createElement("p");
    const chemicalImage = createChemicalImage(iframeDocument, "chem-4");
    chemicalParagraph.append("Before ", chemicalImage, " after");
    iframeDocument.body.append(chemicalParagraph, nextParagraph);
    const transact = vi.fn((change: () => void) => change());
    const nodeChanged = vi.fn();
    Object.assign(globalThis, {
      tinymce: { activeEditor: { execCommand: vi.fn(), undoManager: { transact }, nodeChanged } },
    });
    render(<SidebarInfo iframe={iframe} />);
    act(() => {
      chemicalImage.dispatchEvent(new Event("click", { bubbles: true }));
    });

    await user.click(await screen.findByRole("button", { name: "Insert bond strengths" }));

    // The iframe document has no window, so role queries and jest-dom matchers cannot read it.
    const [, pictureParagraph, table] = Array.from(iframeDocument.body.children) as [
      Element,
      HTMLParagraphElement,
      HTMLTableElement,
    ];
    expect(Array.from(iframeDocument.body.children)).toEqual([
      chemicalParagraph,
      pictureParagraph,
      table,
      nextParagraph,
    ]);
    expect(chemicalImage.parentElement).toBe(chemicalParagraph);
    expect(pictureParagraph.firstElementChild?.tagName).toBe("IMG");
    expect(pictureParagraph.firstElementChild?.getAttribute("src")).toBe(bondStrengthsContent.imageSrc);
    expect(pictureParagraph.firstElementChild?.getAttribute("alt")).toBe("Structure with bond strengths");
    expect(table.caption?.textContent).toBe("Predicted bond strengths");
    expect(Array.from(table.rows, (row) => Array.from(row.cells, (cell) => cell.textContent))).toEqual([
      ["Bond", "kcal/mol", "kJ/mol"],
      ["C1-H (×3), weakest bond", "89.3", "373.5"],
      ["C2:C3", "n/a (ring bond)"],
    ]);
    expect(transact).toHaveBeenCalledOnce();
    expect(nodeChanged).toHaveBeenCalledOnce();
  });
});
