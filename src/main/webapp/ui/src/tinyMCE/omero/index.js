import React from "react";
import Omero from "./Omero";
// eslint-disable-next-line no-duplicate-imports
import { getSelectedItems, getHeaders, getOrder, getOrderBy } from "./Omero";
import { getSorting, stableSort } from "../../util/table";
import { createRoot } from "react-dom/client";
import { omeroSort } from "./ResultsTable";

document.addEventListener("DOMContentLoaded", function () {
  const domContainer = document.getElementById("tinymce-omero");
  const root = createRoot(domContainer);
  root.render(
    <Omero omero_web_url={parent.tinymce.activeEditor.settings.omero_web_url} />
  );
});

function createTinyMceTable() {
  let omeroTable = document.createElement("table");
  omeroTable.setAttribute("data-tableSource", "omero");

  let tableHeader = document.createElement("tr");
  const headers = getHeaders();
  const headersWithNotes = headers
    .slice(0, 4)
    .concat(
      [{ id: "notes", numeric: false, label: "Notes" }],
      headers.slice(4)
    );
  headersWithNotes.forEach((cell) => {
    let columnName = document.createElement("th");
    columnName.textContent = cell.label;
    tableHeader.appendChild(columnName);
  });
  omeroTable.appendChild(tableHeader);
  omeroSort(getSelectedItems(), getOrder(), getOrderBy()).forEach((item) => {
    let row = document.createElement("tr");

    headersWithNotes.forEach((headerCell) => {
      let cell = document.createElement("td");

      let textContent = item[headerCell.id];
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

const setCellContents = (cell, type, item) => {
  hideUnwantedLinks(item);
  if (type === "description") {
    const dtRestToFormat = document.querySelector(
      "[id=" + item.type + "_rest_description_" + item.id + "]"
    );
    if (dtRestToFormat) {
      dtRestToFormat.outerHTML = dtRestToFormat.outerHTML.replace(
        /class.+?restOfDescription/,
        'style="font-weight:lighter;font-size:0.9em;font-family:Verdana"'
      );
    }
    const dtFirstToFormat = document.querySelector(
      "[id=" + item.type + "_first_description_" + item.id + "]"
    );
    if (dtFirstToFormat) {
      dtFirstToFormat.outerHTML = dtFirstToFormat.outerHTML.replace(
        /class.+?firstDescription/,
        'style="font-weight:bold;font-style:italic;font-size:1em;font-family:Times"'
      );
    }
  } else if (type === "path") {
    const dtNameToFormat = document.querySelector(
      "[id=" + item.type + "_name_display_" + item.id + "]"
    );
    if (dtNameToFormat) {
      dtNameToFormat.outerHTML = dtNameToFormat.outerHTML.replace(
        /class.+?nameText/,
        'style="font-weight:bold;font-size:1.3em;font-family:Times"'
      );
    }
  }
  const path = document.querySelector(
    "[id=" +
      CSS.escape(type) +
      "_tablecell_" +
      CSS.escape(item.type) +
      CSS.escape(item.id) +
      "]"
  );
  if (path && path.innerHTML) {
    cell.innerHTML = path.innerHTML;
    if (item.wellDetails) {
      cell.style.whiteSpace = "nowrap";
    }
  }
};

const hideUnwantedLinks = (item) => {
  const ids = [
    "_fetch_children_",
    "_fetch_details_",
    "_details_fetched_",
    "_hide_grid_",
    "_show_grid_",
    "_link_parent_",
    "_change_thumbnail_label_",
  ];
  ids.map((id) => {
    const idElement = document.querySelector(
      "[id=" + CSS.escape(item.type) + id + CSS.escape(item.id) + "]"
    );
    if (idElement && idElement.innerHTML) {
      idElement.innerHTML = "";
      idElement.outerHTML = "";
    }
  });
};

parent.tinymce.activeEditor.on("omero-insert", function () {
  if (parent && parent.tinymce) {
    const ed = parent.tinymce.activeEditor;

    if (getSelectedItems().length > 0) {
      const omeroTable = createTinyMceTable();
      ed.execCommand("mceInsertContent", false, omeroTable.outerHTML);
    }
    ed.windowManager.close();
  }
});
