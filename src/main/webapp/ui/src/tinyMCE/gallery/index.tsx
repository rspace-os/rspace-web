import React from "react";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/rspace/gallery";
import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import { createRoot } from "react-dom/client";
import GalleryPicker from "../../eln/gallery/picker";
import { MemoryRouter } from "react-router-dom";
import { LandmarksProvider } from "@/components/LandmarksContext";

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
  getDoc: () => Document;
}

// Declare the global tinymce object
declare const tinymce: {
  PluginManager: {
    add: (name: string, plugin: new (editor: Editor) => unknown) => void;
  };
  activeEditor: Editor;
};

class GalleryPlugin {
  constructor(editor: Editor) {
    function* renderGallery(domContainer) {
      const root = createRoot(domContainer);
      while (true) {
        const newProps = yield;
        root.render(
          <StyledEngineProvider injectFirst>
            <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
              <MemoryRouter>
                <LandmarksProvider>
                  <GalleryPicker
                    open={newProps?.open ?? false}
                    onClose={newProps?.onClose}
                    onSubmit={(files) => {
                      // TODO: insert into editor
                      newProps?.onClose?.();
                    }}
                  />
                </LandmarksProvider>
              </MemoryRouter>
            </ThemeProvider>
          </StyledEngineProvider>,
        );
      }
    }

    if (!document.getElementById("tinymce-gallery")) {
      const div = document.createElement("div");
      div.id = "tinymce-gallery";
      document.body.appendChild(div);
    }
    const galleryRenderer = renderGallery(
      document.getElementById("tinymce-gallery"),
    );
    galleryRenderer.next({ open: false });

    // Add a button to the toolbar
    editor.ui.registry.addButton("btnMediaGallery", {
      tooltip: "Insert from RSpace Gallery",
      icon: "image",
      onAction() {
        galleryRenderer.next({
          open: true,
          onClose: () => {
            galleryRenderer.next({ open: false });
          },
        });
      },
    });

    // Adds a menu item to the insert menu
    editor.ui.registry.addMenuItem("optMediaGallery", {
      text: "From RSpace Gallery",
      icon: "image",
      onAction() {
        galleryRenderer.next({
          open: true,
          onClose: () => {
            galleryRenderer.next({ open: false });
          },
        });
      },
    });

    // Adds an option to the slash-menu
    if (!window.insertActions) window.insertActions = new Map();
    window.insertActions.set("optMediaGallery", {
      text: "From RSpace Gallery",
      icon: "image",
      action: () => {
        galleryRenderer.next({
          open: true,
          onClose: () => {
            galleryRenderer.next({ open: false });
          },
        });
      },
    });
  }
}

tinymce.PluginManager.add("gallery", GalleryPlugin);
