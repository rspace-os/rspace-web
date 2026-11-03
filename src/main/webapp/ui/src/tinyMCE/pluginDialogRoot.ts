import { createElement, Fragment, type ReactNode } from "react";
import { createRoot, type Root } from "react-dom/client";

export type PluginDialog = {
  open(node: ReactNode): void;
  update(node: ReactNode): void;
  release(): void;
};

const roots = new Map<string, { root: Root; container: HTMLElement; owner: string | null }>();
let nextOwner = 0;

/**
 * A plugin instance's handle to the shared dialog root for this container id.
 * Only open creates a root and takes ownership. Each instance has a unique React
 * key so taking over from another editor mounts fresh component state. Updates
 * and release affect the tree only while this instance owns it. Register release
 * on the editor's remove event to drop references to that editor.
 *
 * Roots are reused while their containers remain connected; the next open
 * unmounts a detached root and finds or creates a connected container.
 */
export function createPluginDialog(containerId: string): PluginDialog {
  const key = String(nextOwner++);

  return {
    open(node) {
      let entry = roots.get(containerId);
      if (!entry?.container.isConnected) {
        if (entry) {
          entry.owner = null;
          entry.root.unmount();
        }
        let container = document.getElementById(containerId);
        if (!container) {
          container = document.createElement("div");
          container.id = containerId;
          document.body.appendChild(container);
        }
        entry = { root: createRoot(container), container, owner: null };
        roots.set(containerId, entry);
      }
      entry.owner = key;
      entry.root.render(createElement(Fragment, { key }, node));
    },
    update(node) {
      const entry = roots.get(containerId);
      if (entry?.owner === key) {
        entry.root.render(createElement(Fragment, { key }, node));
      }
    },
    release() {
      const entry = roots.get(containerId);
      if (entry?.owner === key) {
        entry.root.render(null);
        entry.owner = null;
      }
    },
  };
}
