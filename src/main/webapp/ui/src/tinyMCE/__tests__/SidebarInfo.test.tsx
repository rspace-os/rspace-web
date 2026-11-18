import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { galleryPictureHtml } from "../bondStrengths/galleryPicture";
import type { BondStrengthsDocumentContent } from "../bondStrengths/insertIntoDocument";
import SidebarInfo from "../SidebarInfo";

const bondStrengthsContent: BondStrengthsDocumentContent = {
  imageSrc: "data:image/png;base64,AAAA",
  forChemicalId: "chem-4",
  imageAlt: "Structure with bond strengths",
  legend: "Numbers on bonds are in kcal/mol.",
  caption: "Predicted bond strengths",
  columns: { bond: "Bond", kcalMol: "kcal/mol", kjMol: "kJ/mol" },
  rows: [
    { bond: "C1-H (×3), weakest bond", kcalMol: "89.3", kjMol: "373.5" },
    { bond: "C1-C2", kcalMol: "101.0", kjMol: "422.6" },
  ],
  notes: ["Ring, double and triple bonds have no value (1 bond).", "No value for C3-H. Low confidence."],
};

const galleryPicture =
  '<p><img class="imageDropped" src="/thumbnail/data?sourceId=7" alt="image picture.png"></p>&nbsp;';
vi.mock("../bondStrengths/galleryPicture", () => ({
  galleryPictureHtml: vi.fn(() => Promise.resolve(galleryPicture)),
}));

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
      tinymce: { activeEditor: { id: "rtf_12", execCommand: vi.fn(), undoManager: { transact }, nodeChanged } },
    });
    render(<SidebarInfo iframe={iframe} />);
    act(() => {
      chemicalImage.dispatchEvent(new Event("click", { bubbles: true }));
    });

    await user.click(await screen.findByRole("button", { name: "Insert bond strengths" }));
    await waitFor(() => expect(nodeChanged).toHaveBeenCalled());
    expect(galleryPictureHtml).toHaveBeenCalledExactlyOnceWith(
      "data:image/png;base64,AAAA",
      "12",
      "bond-strengths-chem-4.png",
    );

    // The iframe document has no window, so role queries and jest-dom matchers cannot read it.
    const [, result] = Array.from(iframeDocument.body.children);
    expect(Array.from(iframeDocument.body.children)).toEqual([chemicalParagraph, result, nextParagraph]);
    expect(result.getAttribute("data-bond-strengths-for")).toBe("chem-4");
    const [pictureParagraph, legend, table, notes] = Array.from(result.children) as [
      HTMLParagraphElement,
      HTMLParagraphElement,
      HTMLTableElement,
      HTMLParagraphElement,
    ];
    expect(result.children).toHaveLength(4);
    expect(legend.textContent).toBe("Numbers on bonds are in kcal/mol.");
    expect(notes.textContent).toBe(
      "Ring, double and triple bonds have no value (1 bond). No value for C3-H. Low confidence.",
    );
    expect(chemicalImage.parentElement).toBe(chemicalParagraph);
    expect(pictureParagraph.firstElementChild?.tagName).toBe("IMG");
    // The picture is the Gallery image, never the data URI, which would bloat the field.
    expect(pictureParagraph.firstElementChild?.getAttribute("src")).toBe("/thumbnail/data?sourceId=7");
    expect(pictureParagraph.firstElementChild?.className).toBe("imageDropped");
    expect(pictureParagraph.firstElementChild?.getAttribute("alt")).toBe("Structure with bond strengths");
    expect(table.caption?.textContent).toBe("Predicted bond strengths");
    expect(Array.from(table.rows, (row) => Array.from(row.cells, (cell) => cell.textContent))).toEqual([
      ["Bond", "kcal/mol", "kJ/mol"],
      ["C1-H (×3), weakest bond", "89.3", "373.5"],
      ["C1-C2", "101.0", "422.6"],
    ]);
    expect(transact).toHaveBeenCalledOnce();
    expect(nodeChanged).toHaveBeenCalledOnce();

    // A second insert for the same chemical replaces the first, where it stands.
    await user.click(screen.getByRole("button", { name: "Insert bond strengths" }));
    await waitFor(() => expect(nodeChanged).toHaveBeenCalledTimes(2));
    const afterSecondInsert = Array.from(iframeDocument.body.children);
    expect(afterSecondInsert).toHaveLength(3);
    expect(afterSecondInsert[1]).not.toBe(result);
    expect(afterSecondInsert[1].getAttribute("data-bond-strengths-for")).toBe("chem-4");
  });
  it("replaces the result after a structure whose id changed, as after an import", async () => {
    const user = userEvent.setup();
    const { iframe, iframeDocument } = createIframeWithDocument();
    const chemicalParagraph = iframeDocument.createElement("p");
    const chemicalImage = createChemicalImage(iframeDocument, "chem-4");
    chemicalParagraph.append(chemicalImage);
    const importedResult = iframeDocument.createElement("div");
    importedResult.setAttribute("data-bond-strengths-for", "chem-2");
    const otherParagraph = iframeDocument.createElement("p");
    const otherImage = createChemicalImage(iframeDocument, "chem-5");
    otherParagraph.append(otherImage);
    const otherResult = iframeDocument.createElement("div");
    otherResult.setAttribute("data-bond-strengths-for", "chem-5");
    iframeDocument.body.append(chemicalParagraph, importedResult, otherParagraph, otherResult);
    const nodeChanged = vi.fn();
    Object.assign(globalThis, {
      tinymce: {
        activeEditor: {
          id: "rtf_12",
          execCommand: vi.fn(),
          undoManager: { transact: (change: () => void) => change() },
          nodeChanged,
        },
      },
    });
    render(<SidebarInfo iframe={iframe} />);
    act(() => {
      chemicalImage.dispatchEvent(new Event("click", { bubbles: true }));
    });

    await user.click(await screen.findByRole("button", { name: "Insert bond strengths" }));
    await waitFor(() => expect(nodeChanged).toHaveBeenCalled());

    const children = Array.from(iframeDocument.body.children);
    expect(children).toHaveLength(4);
    expect(children[1].getAttribute("data-bond-strengths-for")).toBe("chem-4");
    expect(children[3]).toBe(otherResult);
  });
});
