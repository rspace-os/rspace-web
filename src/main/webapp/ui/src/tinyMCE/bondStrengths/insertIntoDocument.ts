import DOMPurify from "dompurify";

export type BondStrengthsDocumentRow =
  | { bond: string; kcalMol: string; kjMol: string }
  | { bond: string; notAvailable: string };

/** Text and picture for the document, already translated and formatted. The picture is a PNG data URI. */
export type BondStrengthsDocumentContent = {
  imageSrc: string | null;
  imageAlt: string;
  caption: string;
  columns: { bond: string; kcalMol: string; kjMol: string };
  rows: ReadonlyArray<BondStrengthsDocumentRow>;
};

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
    tableRow.append(element(doc, "td", row.bond));
    if ("notAvailable" in row) {
      const cell = element(doc, "td", row.notAvailable);
      cell.setAttribute("colspan", "2");
      tableRow.append(cell);
    } else {
      tableRow.append(element(doc, "td", row.kcalMol), element(doc, "td", row.kjMol));
    }
    body.append(tableRow);
  }

  table.append(head, body);
  return table;
}

/**
 * Adds the picture and the table after the top-level block that holds the chem image. Going to
 * the top level keeps the HTML valid when the image sits inside a list or a table cell.
 * `pictureHtml` is the editor markup for the picture once it is stored in the Gallery.
 */
export function insertBondStrengthsAfter(
  chemImage: HTMLImageElement,
  content: BondStrengthsDocumentContent,
  pictureHtml: string | null,
): void {
  const doc = chemImage.ownerDocument;
  let block: Element = chemImage;
  while (block.parentElement && block.parentElement !== doc.body) {
    block = block.parentElement;
  }

  const nodes: Element[] = [];
  if (pictureHtml) {
    const holder = element(doc, "div");
    holder.innerHTML = DOMPurify.sanitize(pictureHtml);
    holder.querySelector("img")?.setAttribute("alt", content.imageAlt);
    nodes.push(...Array.from(holder.children));
  }
  nodes.push(buildTable(doc, content));
  block.after(...nodes);
}
