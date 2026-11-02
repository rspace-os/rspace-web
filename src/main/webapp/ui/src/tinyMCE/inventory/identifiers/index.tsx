import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Typography from "@mui/material/Typography";
import React from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import AppBar from "../../../components/AppBar";
import useOauthToken from "../../../hooks/auth/useOauthToken";
import IgsnManagementPage from "../../../Inventory/Identifiers/IGSN/IgsnManagementPage";
import { type Identifier, IdentifiersRefreshProvider } from "../../../Inventory/useIdentifiers";
import RsSet from "../../../util/set";
import { toTitleCase } from "../../../util/Util";

export type Editor = {
  ui: {
    registry: {
      addMenuItem: (menuItemIdentifier: string, options: { text: string; icon: string; onAction: () => void }) => void;
      addButton: (buttonIdentifier: string, options: { tooltip: string; icon: string; onAction: () => void }) => void;
    };
  };
  execCommand: (command: string, someFlag: boolean, htmlContent: string) => void;
};

export default function IdentifiersDialog({
  open,
  onClose,
  editor,
}: {
  open: boolean;
  onClose: () => void;
  editor: Editor;
}) {
  const { t } = useTranslation(["workspace", "common"]);
  const [selectedIgsns, setSelectedIgsns] = React.useState<RsSet<Identifier>>(new RsSet([]));
  const { getToken } = useOauthToken();

  const getBase64 = (file: File): Promise<string> =>
    new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.readAsDataURL(file);
      reader.onload = () => {
        if (typeof reader.result !== "string") {
          reject(new Error("Expected reader.result to be a string"));
          return;
        }
        resolve(reader.result);
      };
      reader.onerror = (error: ProgressEvent<FileReader>) => reject(error);
    });

  async function fetchBarcodeUrl(igsn: Identifier) {
    const token = await getToken();
    const { data } = await axios.get<Blob>(`/api/inventory/v1/barcodes`, {
      params: new URLSearchParams({
        content: `https://doi.org/${igsn.doi}`,
        barcodeType: "QR",
      }),
      headers: { Authorization: `Bearer ${token}` },
      responseType: "blob",
    });
    const file = new File([data], "", { type: "image/png" });
    const dataUrl = await getBase64(file);
    return dataUrl;
  }

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="lg">
      <AppBar variant="dialog" currentPage={t("tinymce.identifiers.pageTitle")} accessibilityTips={{}} />
      <DialogTitle>{t("tinymce.identifiers.dialogTitle")}</DialogTitle>
      <DialogContent>
        <Typography variant="body1">{t("tinymce.identifiers.instructions")}</Typography>
        <IdentifiersRefreshProvider>
          <IgsnManagementPage selectedIgsns={selectedIgsns} setSelectedIgsns={setSelectedIgsns} />
        </IdentifiersRefreshProvider>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t("common:actions.close")}</Button>
        <Button
          variant="contained"
          color="primary"
          onClick={() => {
            void Promise.all(
              [...selectedIgsns].map((igsn) =>
                fetchBarcodeUrl(igsn).then((barcodeUrl) => ({
                  igsn,
                  barcodeUrl,
                })),
              ),
            ).then((data) => {
              editor.execCommand(
                "mceInsertContent",
                false,
                tableHtml({
                  data,
                }).outerHTML,
              );
            });
            onClose();
          }}
        >
          {t("tinymce.identifiers.insertBarcodeTable")}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function tableHtml({ data }: { data: Array<{ igsn: Identifier; barcodeUrl: string }> }): HTMLTableElement {
  const identifiersTable = document.createElement("table");
  identifiersTable.setAttribute("data-tableSource", "identifiers");
  const tableHeader = document.createElement("tr");
  ["IGSN DOI", "State", "Linked Item", "Barcode"].forEach((cell) => {
    const columnName = document.createElement("th");
    columnName.textContent = cell;
    tableHeader.appendChild(columnName);
  });
  identifiersTable.appendChild(tableHeader);
  return data.reduce((table, { igsn, barcodeUrl }) => {
    const row = document.createElement("tr");
    const igsnCell = document.createElement("td");
    igsnCell.textContent = igsn.doi;
    row.appendChild(igsnCell);
    const stateCell = document.createElement("td");
    stateCell.textContent = toTitleCase(igsn.state);
    row.appendChild(stateCell);
    const linkedItemCell = document.createElement("td");
    linkedItemCell.textContent = igsn.associatedGlobalId ?? "NONE";
    row.appendChild(linkedItemCell);
    const barcodeCell = document.createElement("td");
    const img = document.createElement("img");
    img.src = barcodeUrl;
    img.width = 100;
    barcodeCell.appendChild(img);
    row.appendChild(barcodeCell);
    table.appendChild(row);
    return table;
  }, identifiersTable);
}
