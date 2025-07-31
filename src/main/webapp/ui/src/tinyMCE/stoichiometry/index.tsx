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
    ): Generator<void, void, React.ComponentProps<typeof StoichiometryDialog>> {
      const root = createRoot(domContainer);
      while (true) {
        const newProps: React.ComponentProps<typeof StoichiometryDialog> =
          yield;
        root.render(
          <StyledEngineProvider injectFirst>
            <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
              <Analytics>
                <ErrorBoundary>
                  <Alerts>
                    <StoichiometryDialog {...newProps} />
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
      hasStoichiometryTable: false,
    };
    dialogRenderer.next(initialProps);

    editor.addCommand("cmdStiochiometry", function () {
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

      const hasStoichiometryTable = node.getAttribute(
        "data-has-stoichiometry-table",
      );

      dialogRenderer.next({
        open: true,
        onClose: () => {
          dialogRenderer.next({
            open: false,
            onClose: () => {},
            chemId: null,
            hasStoichiometryTable: false,
          });
        },
        chemId,
        hasStoichiometryTable: hasStoichiometryTable === "true",
      });

      // Only mark the element if it doesn't already have the attribute
      if (!hasStoichiometryTable) {
        /*
         * Mark the chemistry element as having a generated stoichiometry table
         * So that we start showing the table when the document isn't being edited
         * We use mceReplaceContent to ensure TinyMCE properly tracks the change
         */
        const currentHtml = node.outerHTML;
        const modifiedHtml = currentHtml.replace(
          /(<img[^>]*)/,
          '$1 data-has-stoichiometry-table="true"',
        );

        // Replace the current element with the modified version
        editor.selection.select(node);
        editor.execCommand("mceReplaceContent", false, modifiedHtml);
        editor.setDirty(true);
      }
    });
  }
}

tinymce.PluginManager.add("stoichiometry", StoichiometryPlugin);
