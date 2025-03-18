// @flow

import React from "react";
import { ThemeProvider } from "@mui/material/styles";
import { createRoot } from "react-dom/client";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import createAccentedTheme from "../../../accentedTheme";
import AppBar from "../../../components/AppBar";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { ACCENT_COLOR } from "../../../assets/branding/rspace/inventory";
import { Optional } from "../../../util/optional";

type Editor = {
  ui: {
    registry: {
      addMenuItem: (
        string,
        { text: string, icon: string, onAction: () => void }
      ) => void,
      addButton: (
        string,
        { tooltip: string, icon: string, onAction: () => void }
      ) => void,
      ...
    },
    ...
  },
  ...
};

function IdentifiersDialog({
  open,
  onClose,
  editor,
}: {|
  open: boolean,
  onClose: () => void,
  editor: Editor,
|}) {
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="lg">
      <AppBar
        variant="dialog"
        currentPage="Inventory Identifiers"
        accessibilityTips={{}}
      />
      <DialogTitle>Insert Identifiers Table</DialogTitle>
      <DialogContent>hi</DialogContent>
    </Dialog>
  );
}

type IdentifiersDialogProps = {|
  open?: boolean,
  onClose?: () => void,
|};

class IdentifiersPlugin {
  constructor(editor: Editor) {
    function* renderIdentifiers(
      domContainer: HTMLElement
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
              <IdentifiersDialog
                editor={editor}
                open={false}
                onClose={() => {}}
                {...newProps}
              />
            </ThemeProvider>
          </StyledEngineProvider>
        );
      }
    }

    const element = Optional.fromNullable(
      document.getElementById("tinymce-inventory-identifiers")
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
      icon: "newPlugin",
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
      icon: "newPlugin",
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
      icon: "newPlugin",
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

// $FlowExpectedError[cannot-resolve-name]
tinyMCE.PluginManager.add("identifiers", IdentifiersPlugin);
