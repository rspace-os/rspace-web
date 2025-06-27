import React from "react";
import { createRoot } from "react-dom/client";
import createAccentedTheme from "../../accentedTheme";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { ACCENT_COLOR } from "../../assets/branding/pubchem";

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

interface Editor {
  ui: {
    registry: {
      addButton: (name: string, config: ButtonConfig) => void;
      addMenuItem: (name: string, config: MenuItemConfig) => void;
    };
  };
}

// Declare the global tinymce object
declare const tinymce: {
  PluginManager: {
    add: (name: string, plugin: new (editor: Editor) => unknown) => void;
  };
};

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

type PubchemDialogProps = {
  open: boolean;
  onClose: () => void;
};

function PubchemDialog(_props: PubchemDialogProps): React.ReactNode {
  return null;
}

class PubchemPlugin {
  constructor(editor: Editor) {
    function* renderPubchem(
      domContainer: HTMLElement
    ): Generator<void, void, PubchemDialogProps> {
      const root = createRoot(domContainer);
      while (true) {
        const newProps: PubchemDialogProps = yield;
        root.render(
          <StyledEngineProvider injectFirst>
            <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
              <PubchemDialog {...newProps} />
            </ThemeProvider>
          </StyledEngineProvider>
        );
      }
    }

    if (!document.getElementById("tinymce-pubchem")) {
      const div = document.createElement("div");
      div.id = "tinymce-pubchem";
      document.body.appendChild(div);
    }
    const container = document.getElementById("tinymce-pubchem");
    if (!container) {
      throw new Error("tinymce-pubchem container not found");
    }

    const pubchemRenderer = renderPubchem(container);
    pubchemRenderer.next();
    const initialProps: PubchemDialogProps = { open: false, onClose: () => {} };
    pubchemRenderer.next(initialProps);

    // Add a button to the toolbar
    editor.ui.registry.addButton("pubchem", {
      tooltip: "Insert PubChem Compound",
      icon: "pubchem",
      onAction() {
        pubchemRenderer.next({
          open: true,
          onClose: () => {
            pubchemRenderer.next({ open: false, onClose: () => {} });
          },
        });
      },
    });

    // Adds a menu item to the insert menu
    editor.ui.registry.addMenuItem("optPubchem", {
      text: "PubChem Compound",
      icon: "pubchem",
      onAction() {
        pubchemRenderer.next({
          open: true,
          onClose: () => {
            pubchemRenderer.next({ open: false, onClose: () => {} });
          },
        });
      },
    });

    // Adds an option to the slash-menu
    if (!window.insertActions) {
      window.insertActions = new Map();
    }
    window.insertActions.set("optPubchem", {
      text: "pubchem",
      icon: "pubchem",
      action: () => {
        pubchemRenderer.next({
          open: true,
          onClose: () => {
            pubchemRenderer.next({ open: false, onClose: () => {} });
          },
        });
      },
    });
  }
}

tinymce.PluginManager.add("pubchem", PubchemPlugin);
