import { createRoot } from "react-dom/client";
import Analytics from "../../components/Analytics";
import { getSorting, stableSort } from "../../util/table";
import type { Order } from "../../util/types";
// eslint-disable-next-line no-duplicate-imports
import Clustermarket, { getHeaders, getOrder, getOrderBy, getSelectedBookings } from "./Clustermarket";

document.addEventListener("DOMContentLoaded", () => {
  const domContainer = document.getElementById("tinymce-clustermarket");
  // biome-ignore lint/style/noNonNullAssertion: initial biome migration
  const root = createRoot(domContainer!);
  root.render(
    <Analytics>
      <Clustermarket
        {...({
          // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
          clustermarket_web_url: (parent.tinymce.activeEditor as any)?.settings.clustermarket_web_url,
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        } as any)}
      />
    </Analytics>,
  );
});

function createTinyMceTable() {
  const clustermarketTable = document.createElement("table");
  clustermarketTable.setAttribute("data-tableSource", "clustermarket");

  const tableHeader = document.createElement("tr");
  const headers = getHeaders();
  const headersWithNotes = headers
    .slice(0, 4)
    .concat([{ id: "notes", numeric: false, label: "Notes" }] as unknown as typeof headers, headers.slice(4));
  headersWithNotes.forEach((cell) => {
    const columnName = document.createElement("th");
    columnName.textContent = cell.label;
    tableHeader.appendChild(columnName);
  });
  clustermarketTable.appendChild(tableHeader);
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  stableSort(getSelectedBookings(), getSorting(getOrder() as Order, getOrderBy())).forEach((booking: any) => {
    const row = document.createElement("tr");

    headersWithNotes.forEach((headerCell) => {
      const cell = document.createElement("td");

      const textContent = booking[headerCell.id];
      if (headerCell.id === "bookingID") {
        const link = document.createElement("a");
        link.href =
          // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
          (parent.tinymce.activeEditor as any)?.settings.clustermarket_web_url +
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
          // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
          (parent.tinymce.activeEditor as any)?.settings.clustermarket_web_url +
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

parent.tinymce.activeEditor?.on("clustermarket-insert", () => {
  if (parent?.tinymce) {
    const ed = parent.tinymce.activeEditor;

    if (getSelectedBookings().length > 0) {
      const clustermarketTable = createTinyMceTable();
      ed?.execCommand("mceInsertContent", false, clustermarketTable.outerHTML);
    }
    ed?.windowManager.close();
  }
});
