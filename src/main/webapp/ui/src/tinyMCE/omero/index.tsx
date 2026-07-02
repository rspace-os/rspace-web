import { createRoot } from "react-dom/client";
// eslint-disable-next-line no-duplicate-imports
import Omero, { getHeaders, getOrder, getOrderBy, getSelectedItems } from "./Omero";
import { omeroSort } from "./ResultsTable";

document.addEventListener("DOMContentLoaded", () => {
  const domContainer = document.getElementById("tinymce-omero");
  // biome-ignore lint/style/noNonNullAssertion: initial biome migration
  const root = createRoot(domContainer!);
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  root.render(<Omero omero_web_url={(parent.tinymce.activeEditor as any)?.settings.omero_web_url} />);
});

function createTinyMceTable() {
  const omeroTable = document.createElement("table");
  omeroTable.setAttribute("data-tableSource", "omero");

  const tableHeader = document.createElement("tr");
  const headers = getHeaders();
  const headersWithNotes = headers
    .slice(0, 4)
    .concat([{ id: "notes", numeric: false, label: "Notes" }], headers.slice(4));
  headersWithNotes.forEach((cell) => {
    const columnName = document.createElement("th");
    columnName.textContent = cell.label;
    tableHeader.appendChild(columnName);
  });
  omeroTable.appendChild(tableHeader);
  omeroSort(getSelectedItems(), getOrder() as "asc" | "desc", getOrderBy()).forEach((item) => {
    const row = document.createElement("tr");

    headersWithNotes.forEach((headerCell) => {
      const cell = document.createElement("td");

      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      const textContent = (item as any)[headerCell.id];
      if (headerCell.id === "path") {
        setCellContents(cell, "path", item);
      } else if (headerCell.id === "description") {
        setCellContents(cell, "description", item);
      } else if (textContent) {
        cell.textContent = textContent;
      }

      row.appendChild(cell);
    });

    omeroTable.appendChild(row);
  });
  return omeroTable;
}

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
const setCellContents = (cell: HTMLTableCellElement, type: string, item: any) => {
  hideUnwantedLinks(item);
  if (type === "description") {
    const dtRestToFormat = document.querySelector(`[id=${item.type}_rest_description_${item.id}]`);
    if (dtRestToFormat) {
      dtRestToFormat.outerHTML = dtRestToFormat.outerHTML.replace(
        /class.+?restOfDescription/,
        'style="font-weight:lighter;font-size:0.9em;font-family:Verdana"',
      );
    }
    const dtFirstToFormat = document.querySelector(`[id=${item.type}_first_description_${item.id}]`);
    if (dtFirstToFormat) {
      dtFirstToFormat.outerHTML = dtFirstToFormat.outerHTML.replace(
        /class.+?firstDescription/,
        'style="font-weight:bold;font-style:italic;font-size:1em;font-family:Times"',
      );
    }
  } else if (type === "path") {
    const dtNameToFormat = document.querySelector(`[id=${item.type}_name_display_${item.id}]`);
    if (dtNameToFormat) {
      dtNameToFormat.outerHTML = dtNameToFormat.outerHTML.replace(
        /class.+?nameText/,
        'style="font-weight:bold;font-size:1.3em;font-family:Times"',
      );
    }
  }
  const path = document.querySelector(
    `[id=${CSS.escape(type)}_tablecell_${CSS.escape(item.type)}${CSS.escape(item.id)}]`,
  );
  if (path?.innerHTML) {
    cell.innerHTML = path.innerHTML;
    if (item.wellDetails) {
      cell.style.whiteSpace = "nowrap";
    }
  }
};

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
const hideUnwantedLinks = (item: any) => {
  const ids = [
    "_fetch_children_",
    "_fetch_details_",
    "_details_fetched_",
    "_hide_grid_",
    "_show_grid_",
    "_link_parent_",
    "_change_thumbnail_label_",
  ];
  // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
  ids.map((id) => {
    const idElement = document.querySelector(`[id=${CSS.escape(item.type)}${id}${CSS.escape(item.id)}]`);
    if (idElement?.innerHTML) {
      idElement.innerHTML = "";
      idElement.outerHTML = "";
    }
  });
};

parent.tinymce.activeEditor?.on("omero-insert", () => {
  if (parent?.tinymce) {
    const ed = parent.tinymce.activeEditor;

    if (getSelectedItems().length > 0) {
      const omeroTable = createTinyMceTable();
      ed?.execCommand("mceInsertContent", false, omeroTable.outerHTML);
    }
    ed?.windowManager.close();
  }
});
