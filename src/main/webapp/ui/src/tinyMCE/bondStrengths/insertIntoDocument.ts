import DOMPurify from "dompurify";

export type BondStrengthsDocumentRow = { bond: string; kcalMol: string; kjMol: string };

/** Text and picture for the document, already translated and formatted. The picture is a PNG data URI. */
export type BondStrengthsDocumentContent = {
  imageSrc: string | null;
  /** The chemical the result is for. A later insert for the same chemical replaces this one. */
  forChemicalId: string;
  imageAlt: string;
  /** Explains the picture; only used when the picture goes in. */
  legend: string;
  caption: string;
  columns: { bond: string; kcalMol: string; kjMol: string };
  /** Only the bonds that have a value. */
  rows: ReadonlyArray<BondStrengthsDocumentRow>;
  /** Sentences on why other bonds have no value, shown as one paragraph. */
  notes: ReadonlyArray<string>;
};

export type BondStrengthsInsertOutcome = "inserted" | "insertedWithoutPicture";

function element<K extends keyof HTMLElementTagNameMap>(
  doc: Document,
  tagName: K,
  text?: string,
): HTMLElementTagNameMap[K] {
  const node = doc.createElement(tagName);
  if (text !== undefined) node.textContent = text;
  return node;
}

function buildTable(doc: Document, content: BondStrengthsDocumentContent): HTMLTableElement {
  const table = element(doc, "table");
  // Same look as a table made with the editor's own table button.
  table.setAttribute("border", "1");
  table.setAttribute("cellpadding", "5");
  table.append(element(doc, "caption", content.caption));

  const headerRow = element(doc, "tr");
  for (const heading of [content.columns.bond, content.columns.kcalMol, content.columns.kjMol]) {
    const cell = element(doc, "th", heading);
    cell.setAttribute("scope", "col");
    headerRow.append(cell);
  }
  const head = element(doc, "thead");
  head.append(headerRow);

  const body = element(doc, "tbody");
  for (const row of content.rows) {
    const tableRow = element(doc, "tr");
    tableRow.append(element(doc, "td", row.bond), element(doc, "td", row.kcalMol), element(doc, "td", row.kjMol));
    body.append(tableRow);
  }

  table.append(head, body);
  return table;
}

const RESULT_ATTRIBUTE = "data-bond-strengths-for";

/**
 * Adds the picture and the table after the top-level block that holds the chem image. Going to
 * the top level keeps the HTML valid when the image sits inside a list or a table cell.
 * A result already in the document for the same chemical is replaced where it stands. So is a
 * result straight after the structure whose chemical is no longer in the document, when that
 * structure is the only one in its block: import and copy give the structure a new id but keep
 * the old result.
 * The result cannot be edited in place, because the next insert replaces it whole and would take
 * anything typed inside it along. When it ends the field, an empty line follows to keep writing on.
 * `pictureHtml` is the editor markup for the picture once it is stored in the Gallery.
 */
export function insertBondStrengthsAfter(
  chemImage: HTMLImageElement,
  content: BondStrengthsDocumentContent,
  pictureHtml: string | null,
): void {
  const doc = chemImage.ownerDocument;
  const nodes: Element[] = [];
  if (pictureHtml) {
    const holder = element(doc, "div");
    holder.innerHTML = DOMPurify.sanitize(pictureHtml);
    holder.querySelector("img")?.setAttribute("alt", content.imageAlt);
    nodes.push(...Array.from(holder.children), element(doc, "p", content.legend));
  }
  nodes.push(buildTable(doc, content));
  if (content.notes.length > 0) {
    nodes.push(element(doc, "p", content.notes.join(" ")));
  }

  const result = element(doc, "div");
  result.setAttribute(RESULT_ATTRIBUTE, content.forChemicalId);
  result.className = "mceNonEditable";
  result.setAttribute("contenteditable", "false");
  result.append(...nodes);

  let block: Element = chemImage;
  while (block.parentElement && block.parentElement !== doc.body) {
    block = block.parentElement;
  }

  const chemicalIds = new Set(Array.from(doc.body.querySelectorAll("img.chem"), (image) => image.id));
  const next = block.nextElementSibling;
  const onlyStructureInBlock = block === chemImage || block.querySelectorAll("img.chem").length === 1;
  const orphanAfterStructure =
    onlyStructureInBlock &&
    next?.matches(`div[${RESULT_ATTRIBUTE}]`) &&
    !chemicalIds.has(next.getAttribute(RESULT_ATTRIBUTE) ?? "")
      ? [next]
      : [];
  const earlier = [
    ...orphanAfterStructure,
    ...Array.from(doc.body.querySelectorAll(`div[${RESULT_ATTRIBUTE}]`)).filter(
      (candidate) => candidate.getAttribute(RESULT_ATTRIBUTE) === content.forChemicalId,
    ),
  ];
  if (earlier.length > 0) {
    earlier[0].replaceWith(result);
    for (const duplicate of earlier.slice(1)) duplicate.remove();
  } else {
    block.after(result);
  }
  if (!result.nextElementSibling) {
    const emptyLine = element(doc, "p");
    const placeholder = element(doc, "br");
    placeholder.setAttribute("data-mce-bogus", "1");
    emptyLine.append(placeholder);
    result.after(emptyLine);
  }
}
