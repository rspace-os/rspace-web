import React from "react";
import Clustermarket from "./Clustermarket";
// eslint-disable-next-line no-duplicate-imports
import {
  getSelectedBookings,
  getHeaders,
  getOrder,
  getOrderBy,
} from "./Clustermarket";
import { getSorting, stableSort } from "../../util/table";
import { createRoot } from "react-dom/client";

document.addEventListener("DOMContentLoaded", function () {
  const domContainer = document.getElementById("tinymce-clustermarket");
  const root = createRoot(domContainer);
  root.render(
    <Clustermarket
      clustermarket_web_url={
        parent.tinymce.activeEditor.settings.clustermarket_web_url
      }
    />
  );
});

function createTinyMceTable() {
  let clustermarketTable = document.createElement("table");
  clustermarketTable.setAttribute("data-tableSource", "clustermarket");

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
  clustermarketTable.appendChild(tableHeader);
  stableSort(
    getSelectedBookings(),
    getSorting(getOrder(), getOrderBy())
  ).forEach((booking) => {
    let row = document.createElement("tr");

    headersWithNotes.forEach((headerCell) => {
      let cell = document.createElement("td");

      let textContent = booking[headerCell.id];
      if (headerCell.id === "bookingID") {
        const link = document.createElement("a");
        link.href =
          parent.tinymce.activeEditor.settings.clustermarket_web_url +
          "accounts/" +
          booking.labID +
          "/my_bookings/" +
          booking[headerCell.id];
        link.target = "_blank";
        link.text = booking[headerCell.id];
        cell.appendChild(link);
      } else if (headerCell.id === "equipmentName") {
        const link = document.createElement("a");
        link.href =
          parent.tinymce.activeEditor.settings.clustermarket_web_url +
          "accounts/" +
          booking.labID +
          "/equipment/" +
          booking.equipmentID;
        link.target = "_blank";
        link.text = booking[headerCell.id];
        cell.appendChild(link);
      } else if (textContent) cell.textContent = textContent;

      row.appendChild(cell);
    });

    clustermarketTable.appendChild(row);
  });
  return clustermarketTable;
}

parent.tinymce.activeEditor.on("clustermarket-insert", function () {
  if (parent && parent.tinymce) {
    const ed = parent.tinymce.activeEditor;

    if (getSelectedBookings().length > 0) {
      const clustermarketTable = createTinyMceTable();
      ed.execCommand("mceInsertContent", false, clustermarketTable.outerHTML);
    }
    ed.windowManager.close();
  }
});
