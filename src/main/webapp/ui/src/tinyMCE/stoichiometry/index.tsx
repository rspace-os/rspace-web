import React from "react";
import { createRoot } from "react-dom/client";
import createAccentedTheme from "../../accentedTheme";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { ACCENT_COLOR } from "../../assets/branding/chemistry";
import ErrorBoundary from "@/components/ErrorBoundary";
import StoichiometryDialog from "./dialog";
import Alerts from "@/components/Alerts/Alerts";
import Analytics from "@/components/Analytics";
import CssBaseline from "@mui/material/CssBaseline";

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
}

// Declare the global tinymce object
declare const tinymce: {
  PluginManager: {
    add: (name: string, plugin: new (editor: Editor) => unknown) => void;
  };
  activeEditor: Editor;
};

class StoichiometryPlugin {
  constructor(editor: Editor) {
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
            <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
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

    const dialogRenderer = renderDialog(container);
    dialogRenderer.next();
    const initialProps: React.ComponentProps<typeof StoichiometryDialog> = {
      open: false,
      onClose: () => {},
      chemId: null,
      stoichiometryId: undefined,
      stoichiometryRevision: undefined,
      onTableCreated: () => {},
    };
    dialogRenderer.next(initialProps);

    editor.addCommand("cmdStoichiometry", function () {
      const node = editor.selection.getNode();
      if (!/chem/.test(node.className))
        throw new Error("Selected node is not a chemical element.");
      const chemIdAttr = node.getAttribute("id");
      if (!chemIdAttr) {
        throw new Error("Chemical element is missing required id attribute.");
      }
      const chemId = parseInt(chemIdAttr, 10);
      if (isNaN(chemId)) {
        throw new Error(
          "Chemical element id attribute must be a valid number.",
        );
      }

      let stoichiometryId: number | undefined;
      let stoichiometryRevision: number | undefined;
      try {
        const { id, revision } = JSON.parse(
          node.getAttribute("data-stoichiometry-table") ?? "{}",
        );
        stoichiometryId = id;
        stoichiometryRevision = revision;
      } catch {}

      const markElementWithStoichiometry = (id: number, revision: number) => {
        const currentNode = editor.selection.getNode();
        currentNode.setAttribute(
          "data-stoichiometry-table",
          JSON.stringify({ id, revision }),
        );
        editor.selection.select(currentNode);
        editor.execCommand("mceReplaceContent", false, currentNode.outerHTML);
        editor.setDirty(true);
      };

      const updateElementWithStoichiometry = (id: number, revision: number) => {
        const currentNode = editor.selection.getNode();
        currentNode.setAttribute(
          "data-stoichiometry-table",
          JSON.stringify({ id, revision }),
        );
        editor.selection.select(currentNode);
        editor.execCommand("mceReplaceContent", false, currentNode.outerHTML);
        editor.setDirty(true);
      };

      const unmarkElementWithStoichiometry = () => {
        const currentNode = editor.selection.getNode();
        currentNode.removeAttribute("data-stoichiometry-table");
        editor.selection.select(currentNode);
        editor.execCommand("mceReplaceContent", false, currentNode.outerHTML);
        editor.setDirty(true);
      };

      dialogRenderer.next({
        open: true,
        onClose: () => {
          dialogRenderer.next({
            open: false,
            onClose: () => {},
            chemId: null,
            stoichiometryId: undefined,
            stoichiometryRevision: undefined,
            onTableCreated: () => {},
          });
        },
        chemId,
        stoichiometryId,
        stoichiometryRevision,
        onTableCreated: (id: number, revision) => {
          markElementWithStoichiometry(id, revision);
          dialogRenderer.next({
            stoichiometryId: id,
            stoichiometryRevision: revision,
          });
        },
        onSave: (id, version) => {
          updateElementWithStoichiometry(id, version);
          dialogRenderer.next({
            stoichiometryId: id,
            stoichiometryRevision: version,
          });
        },
        onDelete: () => {
          unmarkElementWithStoichiometry();
          dialogRenderer.next({
            open: false,
            onClose: () => {},
            chemId: null,
            stoichiometryId: undefined,
            stoichiometryRevision: undefined,
          });
        },
      });
    });
  }
}

tinymce.PluginManager.add("stoichiometry", StoichiometryPlugin);
