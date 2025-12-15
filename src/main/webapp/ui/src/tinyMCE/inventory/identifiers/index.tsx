import React from "react";
import { ThemeProvider } from "@mui/material/styles";
import { createRoot } from "react-dom/client";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import createAccentedTheme from "../../../accentedTheme";
import AppBar from "../../../components/AppBar";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { ACCENT_COLOR } from "../../../assets/branding/rspace/inventory";
import { Optional } from "../../../util/optional";
import IgsnManagementPage from "../../../Inventory/Identifiers/IGSN/IgsnManagementPage";
import Button from "@mui/material/Button";
import useOauthToken from "../../../hooks/auth/useOauthToken";
import axios from "@/common/axios";
import { toTitleCase } from "../../../util/Util";
import {
  type Identifier,
  IdentifiersRefreshProvider,
} from "../../../Inventory/useIdentifiers";
import Typography from "@mui/material/Typography";
import RsSet from "../../../util/set";
import Alerts from "../../../components/Alerts/Alerts";

declare global {
  interface Window {
    insertActions?: Map<
      string,
      {
        text: string;
        icon: string;
        action: () => void;
        aliases?: string[];
      }
    >;
  }
}

type Editor = {
  ui: {
    registry: {
      addMenuItem: (
        menuItemIdentifier: string,
        options: { text: string; icon: string; onAction: () => void },
      ) => void;
      addButton: (
        buttonIdentifier: string,
        options: { tooltip: string; icon: string; onAction: () => void },
      ) => void;
    };
  };
  execCommand: (
    command: string,
    someFlag: boolean,
    htmlContent: string,
  ) => void;
};

function IdentifiersDialog({
  open,
  onClose,
  editor,
}: {
  open: boolean;
  onClose: () => void;
  editor: Editor;
}) {
  const [selectedIgsns, setSelectedIgsns] = React.useState<RsSet<Identifier>>(
    new RsSet([]),
  );
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
      <AppBar
        variant="dialog"
        currentPage="Inventory Identifiers"
        accessibilityTips={{}}
      />
      <DialogTitle>Insert Barcodes Table</DialogTitle>
      <DialogContent>
        <Typography variant="body1">
          Select from newly registered IGSN IDs or those already with linked
          items, and insert a table into your document, each with a QR code.
        </Typography>
        <IdentifiersRefreshProvider>
          <IgsnManagementPage
            selectedIgsns={selectedIgsns}
            setSelectedIgsns={setSelectedIgsns}
          />
        </IdentifiersRefreshProvider>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
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
          Insert Barcode Table
        </Button>
      </DialogActions>
    </Dialog>
  );
}

type IdentifiersDialogProps = {
  open?: boolean;
  onClose?: () => void;
};

class IdentifiersPlugin {
  constructor(editor: Editor) {
    function* renderIdentifiers(
      domContainer: HTMLElement,
    ): Generator<IdentifiersDialogProps, void, IdentifiersDialogProps> {
      const root = createRoot(domContainer);
      let newProps: IdentifiersDialogProps = {
        open: false,
        onClose: () => {},
      };
      while (true) {
        newProps = yield newProps;
        root.render(
          <StyledEngineProvider injectFirst>
            <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
              <Alerts>
                <IdentifiersDialog
                  editor={editor}
                  open={false}
                  onClose={() => {}}
                  {...newProps}
                />
              </Alerts>
            </ThemeProvider>
          </StyledEngineProvider>,
        );
      }
    }

    const element = Optional.fromNullable(
      document.getElementById("tinymce-inventory-identifiers"),
    ).orElseGet(() => {
      const div = document.createElement("div");
      div.id = "tinymce-inventory-identifiers";
      document.body?.appendChild(div);
      return div;
    });
    const identifiersRenderer = renderIdentifiers(element);
    identifiersRenderer.next({ open: false });

    editor.ui.registry.addMenuItem("optIdentifiers", {
      text: "Inventory Identifiers",
      icon: "inventory_identifiers",
      onAction: () => {
        identifiersRenderer.next({
          open: true,
          onClose: () => {
            identifiersRenderer.next({ open: false });
          },
        });
      },
    });
    editor.ui.registry.addButton("identifiers", {
      tooltip: "Inventory Identifiers",
      icon: "inventory_identifiers",
      onAction: () => {
        identifiersRenderer.next({
          open: true,
          onClose: () => {
            identifiersRenderer.next({ open: false });
          },
        });
      },
    });
    if (!window.insertActions) window.insertActions = new Map();
    window.insertActions.set("optIdentifiers", {
      text: "Inventory Identifiers",
      aliases: ["IGSN"],
      icon: "inventory_identifiers",
      action: () => {
        identifiersRenderer.next({
          open: true,
          onClose: () => {
            identifiersRenderer.next({ open: false });
          },
        });
      },
    });
  }
}

// @ts-expect-error TS does not recognise the PluginManager property
tinyMCE.PluginManager.add("identifiers", IdentifiersPlugin);

function tableHtml({
  data,
}: {
  data: Array<{ igsn: Identifier; barcodeUrl: string }>;
}): HTMLTableElement {
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
