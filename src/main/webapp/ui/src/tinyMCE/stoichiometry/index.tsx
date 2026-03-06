import React from "react";
import { createRoot } from "react-dom/client";
import createAccentedTheme from "../../accentedTheme";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { ACCENT_COLOR } from "../../assets/branding/chemistry";
import ErrorBoundary from "@/components/ErrorBoundary";
import StoichiometryDialog from "./StoichiometryDialog";
import Alerts from "@/components/Alerts/Alerts";
import Analytics from "@/components/Analytics";
import CssBaseline from "@mui/material/CssBaseline";
import { createStoichiometryTheme } from "@/tinyMCE/stoichiometry/theme";

// Define types for external interfaces
type ButtonConfig = {
  tooltip: string;
  icon: string;
  onAction: () => void;
};

type MenuItemConfig = {
  text: string;
  icon: string;
  onAction: () => void;
};

type InsertAction = {
  text: string;
  icon: string;
  action: () => void;
  aliases?: string[];
};

export interface Editor {
  ui: {
    registry: {
      addButton: (name: string, config: ButtonConfig) => void;
      addMenuItem: (name: string, config: MenuItemConfig) => void;
    };
  };
  execCommand: (command: string, ui: boolean, value?: string) => void;
  id: string; // the id the current document field prefixed with "rtf_"
  addCommand: (name: string, func: () => void) => void;
  setDirty: (state: boolean) => void;
  selection: {
    getNode: () => HTMLElement;
    select: (node: HTMLElement) => void;
  };
  getDoc: () => Document;
  getParam: (name: string) => unknown;
}

// Declare the global tinymce object
declare const tinymce: {
  PluginManager: {
    add: (name: string, plugin: new (editor: Editor) => unknown) => void;
  };
  activeEditor: Editor;
};

declare global {
  interface Window {
    insertActions?: Map<string, InsertAction>;
  }
}

class StoichiometryPlugin {
  constructor(editor: Editor) {
    const theme = createStoichiometryTheme(createAccentedTheme(ACCENT_COLOR));
    const TEMP_STOICHIOMETRY_ID_ATTR = "data-stoichiometry-temp-id";

    function* renderDialog(
      domContainer: HTMLElement,
    ): Generator<
      void,
      void,
      Partial<React.ComponentProps<typeof StoichiometryDialog>>
    > {
      const root = createRoot(domContainer);
      let props: React.ComponentProps<typeof StoichiometryDialog> = {
        open: false,
        onClose: () => {},
        chemId: null,
        recordId: recordId,
        stoichiometryId: undefined,
        stoichiometryRevision: undefined,
        onTableCreated: () => {},
        onSave: () => {},
        onDelete: () => {},
      };
      while (true) {
        props = {
          ...props,
          ...(yield),
        };
        root.render(
          <StyledEngineProvider injectFirst>
            <CssBaseline />
            <ThemeProvider theme={theme}>
              <Analytics>
                <ErrorBoundary>
                  <Alerts>
                    <StoichiometryDialog {...props} />
                  </Alerts>
                </ErrorBoundary>
              </Analytics>
            </ThemeProvider>
          </StyledEngineProvider>,
        );
      }
    }

    if (!document.getElementById("tinymce-stoichiometry")) {
      const div = document.createElement("div");
      div.id = "tinymce-stoichiometry";
      document.body.appendChild(div);
    }
    const container = document.getElementById("tinymce-stoichiometry");
    if (!container) {
      throw new Error("tinymce-stoichiometry container not found");
    }

    const recordId = editor.getParam("recordId") as number;
    const dialogRenderer = renderDialog(container);
    dialogRenderer.next();
    const initialProps: React.ComponentProps<typeof StoichiometryDialog> = {
      open: false,
      onClose: () => {},
      chemId: null,
      recordId,
      stoichiometryId: undefined,
      stoichiometryRevision: undefined,
      onTableCreated: () => {},
    };
    dialogRenderer.next(initialProps);

    const resetDialog = () => {
      dialogRenderer.next({
        open: false,
        onClose: () => {},
        chemId: null,
        stoichiometryId: undefined,
        stoichiometryRevision: undefined,
        onTableCreated: () => {},
      });
    };

    const getSelectedChemicalContext = () => {
      const node = editor.selection.getNode();
      if (!/chem/.test(String(node.className))) {
        return null;
      }

      const chemNodeId = node.getAttribute("id");
      if (!chemNodeId) {
        return null;
      }
      const parsedChemId = Number.parseInt(chemNodeId, 10);
      const chemId = Number.isNaN(parsedChemId) ? null : parsedChemId;

      let stoichiometryId: number | undefined;
      let stoichiometryRevision: number | undefined;
      try {
        const { id, revision } = JSON.parse(
          node.getAttribute("data-stoichiometry-table") ?? "{}",
        ) as {
          id?: unknown;
          revision?: unknown;
        };
        stoichiometryId = typeof id === "number" ? id : undefined;
        stoichiometryRevision =
          typeof revision === "number" ? revision : undefined;
      } catch {}

      return {
        chemId,
        chemNodeId,
        stoichiometryId,
        stoichiometryRevision,
      };
    };

    const findChemNode = ({
      chemNodeId,
      temporaryStoichiometryId,
    }: {
      chemNodeId?: string;
      temporaryStoichiometryId?: string;
    }): HTMLElement | null => {
      if (chemNodeId) {
        const byId = editor.getDoc().getElementById(chemNodeId);
        if (byId) {
          return byId;
        }
      }
      if (!temporaryStoichiometryId) {
        return null;
      }
      return editor
        .getDoc()
        .querySelector(
          `img.chem[${TEMP_STOICHIOMETRY_ID_ATTR}="${temporaryStoichiometryId}"]`,
        );
    };

    const updateChemicalNodeStoichiometry = (
      target: {
        chemNodeId?: string;
        temporaryStoichiometryId?: string;
      },
      stoichiometry:
        | {
            id: number;
            revision: number;
          }
        | null,
    ) => {
      const targetNode = findChemNode(target);
      if (!targetNode) {
        return;
      }

      if (stoichiometry) {
        targetNode.setAttribute(
          "data-stoichiometry-table",
          JSON.stringify(stoichiometry),
        );
        targetNode.removeAttribute(TEMP_STOICHIOMETRY_ID_ATTR);
      } else {
        targetNode.removeAttribute("data-stoichiometry-table");
        targetNode.removeAttribute(TEMP_STOICHIOMETRY_ID_ATTR);
      }

      editor.selection.select(targetNode);
      editor.execCommand("mceReplaceContent", false, targetNode.outerHTML);
      editor.setDirty(true);
    };

    const createTemporaryStoichiometryId = () =>
      `tmp-stoich-${Date.now()}-${Math.random().toString(16).slice(2)}`;

    const insertTemporaryStoichiometryAnchor = (
      temporaryStoichiometryId: string,
    ): string => {
      const temporaryNodeId = `stoich-anchor-${Date.now()}-${Math.random()
        .toString(16)
        .slice(2)}`;
      const html = `<img class="chem" id="${temporaryNodeId}" src="" width="1" height="1" ${TEMP_STOICHIOMETRY_ID_ATTR}="${temporaryStoichiometryId}" alt="Stoichiometry table anchor" />`;
      editor.execCommand("mceInsertContent", false, html);
      editor.setDirty(true);
      return temporaryNodeId;
    };

    const openStoichiometryDialog = ({
      chemId,
      chemNodeId,
      stoichiometryId,
      stoichiometryRevision,
    }: {
      chemId: number | null;
      chemNodeId?: string;
      stoichiometryId: number | undefined;
      stoichiometryRevision: number | undefined;
    }) => {
      let activeChemNodeId = chemNodeId;
      let temporaryStoichiometryId: string | undefined;

      if (!activeChemNodeId && stoichiometryId === undefined) {
        temporaryStoichiometryId = createTemporaryStoichiometryId();
        activeChemNodeId =
          insertTemporaryStoichiometryAnchor(temporaryStoichiometryId);
      }

      dialogRenderer.next({
        open: true,
        onClose: resetDialog,
        chemId,
        stoichiometryId,
        stoichiometryRevision,
        onTableCreated: (id: number, revision) => {
          updateChemicalNodeStoichiometry(
            {
              chemNodeId: activeChemNodeId,
              temporaryStoichiometryId,
            },
            { id, revision },
          );
          temporaryStoichiometryId = undefined;
          dialogRenderer.next({
            stoichiometryId: id,
            stoichiometryRevision: revision,
          });
        },
        onSave: (id, version) => {
          updateChemicalNodeStoichiometry(
            {
              chemNodeId: activeChemNodeId,
              temporaryStoichiometryId,
            },
            { id, revision: version },
          );
          dialogRenderer.next({
            stoichiometryId: id,
            stoichiometryRevision: version,
          });
        },
        onDelete: () => {
          updateChemicalNodeStoichiometry(
            {
              chemNodeId: activeChemNodeId,
              temporaryStoichiometryId,
            },
            null,
          );
          resetDialog();
        },
      });
    };

    const openStoichiometryAction = () => {
      editor.execCommand("cmdStoichiometry", false);
    };

    editor.addCommand("cmdStoichiometry", function () {
      const selectedChemical = getSelectedChemicalContext();
      openStoichiometryDialog({
        chemId: selectedChemical?.chemId ?? null,
        chemNodeId: selectedChemical?.chemNodeId,
        stoichiometryId: selectedChemical?.stoichiometryId,
        stoichiometryRevision: selectedChemical?.stoichiometryRevision,
      });
    });

    editor.ui.registry.addButton("btnStoichiometry", {
      tooltip: "Insert Stoichiometry Table",
      icon: "stoichiometry",
      onAction: openStoichiometryAction,
    });

    editor.ui.registry.addMenuItem("optStoichiometry", {
      text: "Stoichiometry Table",
      icon: "stoichiometry",
      onAction: openStoichiometryAction,
    });

    if (!window.insertActions) {
      window.insertActions = new Map();
    }
    window.insertActions.set("optStoichiometry", {
      text: "Stoichiometry Table",
      icon: "stoichiometry",
      aliases: ["Reaction Table"],
      action: openStoichiometryAction,
    });
  }
}

tinymce.PluginManager.add("stoichiometry", StoichiometryPlugin);
