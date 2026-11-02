import { createRoot, type Root } from "react-dom/client";

const roots = new Map<string, Root>();

/**
 * The React root that a TinyMCE plugin renders its dialog into, attached to a
 * container with the given id at the end of the page.
 *
 * TinyMCE constructs a plugin for every editor, and each field that enters
 * edit mode gets a new editor, so every instance of a plugin shares this one
 * container. React allows one root per container, so the root is created on
 * first use and reused afterwards; re-render the dialog by calling `render` on
 * it again with new props.
 */
export function getPluginDialogRoot(containerId: string): Root {
  const existingRoot = roots.get(containerId);
  if (existingRoot) return existingRoot;

  let container = document.getElementById(containerId);
  if (!container) {
    container = document.createElement("div");
    container.id = containerId;
    document.body.appendChild(container);
  }
  const root = createRoot(container);
  roots.set(containerId, root);
  return root;
}
