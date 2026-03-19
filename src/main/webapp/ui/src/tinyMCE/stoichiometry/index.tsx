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

    const findChemNode = (chemNodeId?: string): HTMLElement | null => {
      if (!chemNodeId) {
        return null;
      }
      return editor.getDoc().getElementById(chemNodeId);
    };

    const updateChemicalNodeStoichiometry = (
      chemNodeId: string | undefined,
      stoichiometry:
        | {
            id: number;
            revision: number;
          }
        | null,
    ) => {
      const targetNode = findChemNode(chemNodeId);
      if (!targetNode) {
        return;
      }

      if (stoichiometry) {
        targetNode.setAttribute(
          "data-stoichiometry-table",
          JSON.stringify(stoichiometry),
        );
      } else {
        targetNode.removeAttribute("data-stoichiometry-table");
      }

      editor.selection.select(targetNode);
      editor.execCommand("mceReplaceContent", false, targetNode.outerHTML);
      editor.setDirty(true);
    };

    const insertTemporaryStoichiometryAnchor = (): string => {
      const temporaryNodeId = `stoich-anchor-${Date.now()}-${Math.random()
        .toString(16)
        .slice(2)}`;
      const html = `<img class="chem" id="${temporaryNodeId}" src="" width="1" height="1" alt="Stoichiometry table anchor" />`;
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

      if (!activeChemNodeId && stoichiometryId === undefined) {
        activeChemNodeId = insertTemporaryStoichiometryAnchor();
      }

      dialogRenderer.next({
        open: true,
        onClose: resetDialog,
        chemId,
        stoichiometryId,
        stoichiometryRevision,
        onTableCreated: (id: number, revision) => {
          updateChemicalNodeStoichiometry(activeChemNodeId, { id, revision });
          dialogRenderer.next({
            stoichiometryId: id,
            stoichiometryRevision: revision,
          });
        },
        onSave: (id, version) => {
          updateChemicalNodeStoichiometry(activeChemNodeId, {
            id,
            revision: version,
          });
          dialogRenderer.next({
            stoichiometryId: id,
            stoichiometryRevision: version,
          });
        },
        onDelete: () => {
          updateChemicalNodeStoichiometry(activeChemNodeId, null);
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
