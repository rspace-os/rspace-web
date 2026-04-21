import React from "react";
import { createRoot } from "react-dom/client";
import StoichiometryDialogEntrypoint from "./StoichiometryDialogEntrypoint";

const STOICHIOMETRY_TABLE_ONLY_ATTRIBUTE = "data-stoichiometry-table-only";
const STOICHIOMETRY_TABLE_DATA_ATTRIBUTE = "data-stoichiometry-table";
const EMPTY_STOICHIOMETRY_TABLE_PLACEHOLDER = "Empty Stoichiometry Table";
const STOICHIOMETRY_TABLE_ONLY_HEIGHT_PX = 45;

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

interface Editor {
  ui: {
    registry: {
      addMenuItem: (
        name: string,
        config: {
          text: string;
          icon: string;
          onAction: () => void;
        },
      ) => void;
    };
  };
  execCommand: (command: string, ui: boolean, value?: string) => void;
  addCommand: (name: string, func: () => void) => void;
  setDirty: (state: boolean) => void;
  selection: {
    getNode: () => HTMLElement;
    select: (node: HTMLElement) => void;
  };
  getDoc: () => Document | null;
  getParam: (name: string) => unknown;
  focus: () => void;
}

// Declare the global tinymce object
declare const tinymce: {
  PluginManager: {
    add: (name: string, plugin: new (editor: Editor) => unknown) => void;
  };
};

// As TinyMCE Elements are cross-realm, normal `instanceof` checks would fail here
const isHTMLElement = (node: unknown): node is HTMLElement => {
  if (typeof node !== "object" || node === null) {
    return false;
  }

  if (!("nodeType" in node) || node.nodeType !== Node.ELEMENT_NODE) {
    return false;
  }

  return (
    "tagName" in node &&
    typeof node.tagName === "string" &&
    "getAttribute" in node &&
    typeof node.getAttribute === "function" &&
    "hasAttribute" in node &&
    typeof node.hasAttribute === "function" &&
    "matches" in node &&
    typeof node.matches === "function" &&
    "closest" in node &&
    typeof node.closest === "function" &&
    "querySelectorAll" in node &&
    typeof node.querySelectorAll === "function" &&
    "children" in node &&
    "textContent" in node &&
    "ownerDocument" in node &&
    "id" in node
  );
};

class StoichiometryPlugin {
  constructor(editor: Editor) {
    const isStoichiometryTableOnlyNode = (
      node: Node | null,
    ): node is HTMLElement => {
      if (!isHTMLElement(node)) {
        return false;
      }

      return (
        node.tagName === "DIV" &&
        node.getAttribute(STOICHIOMETRY_TABLE_ONLY_ATTRIBUTE) === "true"
      );
    };

    const findStoichiometryRootNode = (node: Node | null): HTMLElement | null => {
      if (!isHTMLElement(node)) {
        return null;
      }

      if (node.matches("img.chem")) {
        return node;
      }

      return node.closest(`div[${STOICHIOMETRY_TABLE_ONLY_ATTRIBUTE}="true"]`);
    };

    const createStoichiometryNodeId = (): string => {
      return `stoichiometry-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
    };

    const getEditorDoc = (): Document | null => {
      return editor.getDoc();
    };

    const syncStoichiometryTableOnlyHeight = (node: HTMLElement) => {
      if (!isStoichiometryTableOnlyNode(node)) {
        return;
      }

      node.style.height = `${STOICHIOMETRY_TABLE_ONLY_HEIGHT_PX}px`;
    };

    const syncStoichiometryTableOnlyPlaceholder = (node: HTMLElement) => {
      if (!isStoichiometryTableOnlyNode(node)) {
        return;
      }

      syncStoichiometryTableOnlyHeight(node);

      const hasStoichiometryData = node.hasAttribute(
        STOICHIOMETRY_TABLE_DATA_ATTRIBUTE,
      );
      const hasElementChildren = node.children.length > 0;
      const textContent = node.textContent?.trim() ?? "";
      const hasOnlyPlaceholderText =
        textContent === EMPTY_STOICHIOMETRY_TABLE_PLACEHOLDER;

      if (hasStoichiometryData) {
        if (!hasElementChildren && hasOnlyPlaceholderText) {
          node.textContent = "";
        }
        return;
      }

      if (!hasElementChildren && textContent.length === 0) {
        node.textContent = EMPTY_STOICHIOMETRY_TABLE_PLACEHOLDER;
      }
    };

    const getStoichiometryTableOnlyText = (stoichiometry: {
      id: number;
      revision: number;
    }): string => {
      return `Stoichiometry Table ID ${stoichiometry.id}, revision ${stoichiometry.revision}`;
    };

    const syncStoichiometryTableOnlyNodePresentation = (node: HTMLElement) => {
      if (!isStoichiometryTableOnlyNode(node)) {
        return;
      }

      syncStoichiometryTableOnlyHeight(node);

      if (node.hasAttribute(STOICHIOMETRY_TABLE_DATA_ATTRIBUTE)) {
        try {
          node.textContent = getStoichiometryTableOnlyText(
            getStoichiometryDataFromTableOnlyNode(node),
          );
          return;
        } catch {
          return;
        }
      }

      syncStoichiometryTableOnlyPlaceholder(node);
    };

    const syncExistingStoichiometryTableOnlyPlaceholders = () => {
      const editorDoc = getEditorDoc();
      if (!editorDoc) {
        return;
      }

      editorDoc
        .querySelectorAll(`div[${STOICHIOMETRY_TABLE_ONLY_ATTRIBUTE}="true"]`)
        .forEach((node) => {
          if (isHTMLElement(node)) {
            syncStoichiometryTableOnlyNodePresentation(node);
          }
        });
    };

    const getStoichiometryDataFromTableOnlyNode = (
      node: HTMLElement,
    ): { id: number; revision: number } => {
      const rawStoichiometry = node.getAttribute(STOICHIOMETRY_TABLE_DATA_ATTRIBUTE);
      if (rawStoichiometry === null) {
        throw new Error(
          "Stoichiometry table node is missing data-stoichiometry-table.",
        );
      }

      let parsedStoichiometry: unknown;
      try {
        parsedStoichiometry = JSON.parse(rawStoichiometry);
      } catch {
        throw new Error(
          "Stoichiometry table node has invalid data-stoichiometry-table JSON.",
        );
      }

      if (
        typeof parsedStoichiometry !== "object" ||
        parsedStoichiometry === null ||
        !("id" in parsedStoichiometry) ||
        !("revision" in parsedStoichiometry) ||
        typeof parsedStoichiometry.id !== "number" ||
        typeof parsedStoichiometry.revision !== "number"
      ) {
        throw new Error(
          "Stoichiometry table node has malformed data-stoichiometry-table attributes.",
        );
      }

      return {
        id: parsedStoichiometry.id,
        revision: parsedStoichiometry.revision,
      };
    };

    const buildStoichiometryTableOnlyHtml = (nodeId: string): string => {
      return `<div id="${nodeId}" ${STOICHIOMETRY_TABLE_ONLY_ATTRIBUTE}="true" class="mceNonEditable" data-mce-contenteditable="false" contenteditable="false" role="button" tabindex="-1" aria-label="Reaction table">${EMPTY_STOICHIOMETRY_TABLE_PLACEHOLDER}</div>`;
    };

    const insertStoichiometryTableOnly = (): string => {
      const nodeId = createStoichiometryNodeId();

      editor.execCommand(
        "mceInsertContent",
        false,
        buildStoichiometryTableOnlyHtml(nodeId),
      );
      editor.setDirty(true);

      const insertedNode = getEditorDoc()?.getElementById(nodeId);
      if (isHTMLElement(insertedNode)) {
        editor.selection.select(insertedNode);
        editor.focus();
      }

      return nodeId;
    };

    const createDialogRenderer = (domContainer: HTMLElement) => {
      const root = createRoot(domContainer);
      let props: React.ComponentProps<typeof StoichiometryDialogEntrypoint> = {
        open: false,
        onClose: () => {},
        chemId: null,
        autoCreateTableOnOpen: false,
        recordId,
        stoichiometryId: undefined,
        stoichiometryRevision: undefined,
        onTableCreated: () => {},
        onSave: () => {},
        onDelete: () => {},
      };

      return (
        nextProps: Partial<
          React.ComponentProps<typeof StoichiometryDialogEntrypoint>
        > = {},
      ) => {
        props = {
          ...props,
          ...nextProps,
        };

        root.render(<StoichiometryDialogEntrypoint {...props} />);
      };
    };

    if (!document.getElementById("tinymce-stoichiometry")) {
      const div = document.createElement("div");
      div.id = "tinymce-stoichiometry";
      document.body.appendChild(div);
    }
    const container = document.getElementById("tinymce-stoichiometry");
    if (!container) {
      throw new Error("tinymce-stoichiometry container not found");
    }

    syncExistingStoichiometryTableOnlyPlaceholders();

    const recordId = editor.getParam("recordId") as number;
    const renderDialog = createDialogRenderer(container);
    renderDialog();

    const resetDialog = () => {
      renderDialog({
        open: false,
        onClose: () => {},
        chemId: null,
        autoCreateTableOnOpen: false,
        stoichiometryId: undefined,
        stoichiometryRevision: undefined,
        onTableCreated: () => {},
      });
    };

    const getSelectedStoichiometryContext = () => {
      const selectedNode = findStoichiometryRootNode(editor.selection.getNode());
      if (!selectedNode) {
        return null;
      }

      if (isStoichiometryTableOnlyNode(selectedNode) && !selectedNode.id) {
        throw new Error("Stoichiometry table node is missing an id attribute.");
      }

      const nodeId = selectedNode.getAttribute("id");
      if (!nodeId) {
        return null;
      }

      if (selectedNode.matches("img.chem")) {
        let stoichiometryId: number | undefined;
        let stoichiometryRevision: number | undefined;
        try {
          const { id, revision } = JSON.parse(
            selectedNode.getAttribute(STOICHIOMETRY_TABLE_DATA_ATTRIBUTE) ?? "{}",
          ) as {
            id?: unknown;
            revision?: unknown;
          };
          stoichiometryId = typeof id === "number" ? id : undefined;
          stoichiometryRevision =
            typeof revision === "number" ? revision : undefined;
        } catch {}

        const parsedChemId = Number.parseInt(nodeId, 10);

        return {
          chemId: !Number.isNaN(parsedChemId) ? parsedChemId : null,
          nodeId,
          nodeType: "chem" as const,
          stoichiometryId,
          stoichiometryRevision,
        };
      }

      const { id, revision } = getStoichiometryDataFromTableOnlyNode(selectedNode);

      return {
        chemId: null,
        nodeId,
        nodeType: "tableOnly" as const,
        stoichiometryId: id,
        stoichiometryRevision: revision,
      };
    };

    const findStoichiometryNode = (nodeId?: string): HTMLElement | null => {
      const editorDoc = getEditorDoc();
      if (!nodeId || !editorDoc) {
        return null;
      }
      return editorDoc.getElementById(nodeId);
    };

    const removeStoichiometryNode = (nodeId?: string) => {
      const targetNode = findStoichiometryNode(nodeId);
      if (!targetNode) {
        return;
      }

      editor.selection.select(targetNode);
      editor.execCommand("Delete", false);
      editor.setDirty(true);
    };

    const updateStoichiometryNode = (
      nodeId: string | undefined,
      stoichiometry:
        | {
            id: number;
            revision: number;
          }
        | null,
    ) => {
      const targetNode = findStoichiometryNode(nodeId);
      if (!targetNode) {
        return;
      }

      if (stoichiometry) {
        targetNode.setAttribute(
          STOICHIOMETRY_TABLE_DATA_ATTRIBUTE,
          JSON.stringify(stoichiometry),
        );
      } else {
        targetNode.removeAttribute(STOICHIOMETRY_TABLE_DATA_ATTRIBUTE);
      }

      syncStoichiometryTableOnlyPlaceholder(targetNode);

      editor.selection.select(targetNode);
      editor.execCommand("mceReplaceContent", false, targetNode.outerHTML);
      editor.setDirty(true);

      const updatedNode = findStoichiometryNode(nodeId);
      if (updatedNode) {
        syncStoichiometryTableOnlyNodePresentation(updatedNode);
      }
    };

    const openStoichiometryDialog = ({
      chemId,
      nodeId,
      nodeType,
      stoichiometryId,
      stoichiometryRevision,
    }: {
      chemId: number | null;
      nodeId?: string;
      nodeType?: "chem" | "tableOnly";
      stoichiometryId: number | undefined;
      stoichiometryRevision: number | undefined;
    }) => {
      let activeChemId = chemId;
      let activeNodeId = nodeId;
      let activeNodeType = nodeType;
      let shouldRemoveAnchorOnClose = false;
      let anchorWasInsertedForNewTable = false;
      let autoCreateTableOnOpen = false;

      if (!activeNodeId && stoichiometryId === undefined) {
        activeNodeId = insertStoichiometryTableOnly();
        activeChemId = null;
        activeNodeType = "tableOnly";
        shouldRemoveAnchorOnClose = true;
        anchorWasInsertedForNewTable = true;
        autoCreateTableOnOpen = true;
      }

      const closeDialog = () => {
        if (shouldRemoveAnchorOnClose) {
          removeStoichiometryNode(activeNodeId);
        }
        resetDialog();
      };

      renderDialog({
        open: true,
        onClose: closeDialog,
        chemId: activeChemId,
        autoCreateTableOnOpen,
        stoichiometryId,
        stoichiometryRevision,
        onTableCreated: (id: number, revision) => {
          shouldRemoveAnchorOnClose = false;
          updateStoichiometryNode(activeNodeId, { id, revision });
          renderDialog({
            stoichiometryId: id,
            stoichiometryRevision: revision,
          });
        },
        onSave: (id, version) => {
          shouldRemoveAnchorOnClose = false;
          updateStoichiometryNode(activeNodeId, {
            id,
            revision: version,
          });
          renderDialog({
            stoichiometryId: id,
            stoichiometryRevision: version,
          });
        },
        onDelete: () => {
          if (
            anchorWasInsertedForNewTable ||
            activeNodeType === "tableOnly"
          ) {
            removeStoichiometryNode(activeNodeId);
          } else {
            updateStoichiometryNode(activeNodeId, null);
          }
          resetDialog();
        },
      });
    };


    editor.ui.registry.addMenuItem("stoichiometryMenuItem", {
      text: "Reaction Table",
      icon: "stoichiometry",
      onAction: () => {
        editor.execCommand("cmdCreateStoichiometry", false);
      },
    });

    if (!window.insertActions) {
      window.insertActions = new Map();
    }
    window.insertActions.set("stoichiometryMenuItem", {
      text: "Stoichiometry Table",
      icon: "stoichiometry",
      aliases: ["Stoichiometry"],
      action: () => {
        editor.execCommand("cmdCreateStoichiometry", false);
      },
    });

    editor.addCommand("cmdCreateStoichiometry", function () {
      openStoichiometryDialog({
        chemId: null,
        nodeId: undefined,
        nodeType: undefined,
        stoichiometryId: undefined,
        stoichiometryRevision: undefined,
      });
    });

    editor.addCommand("cmdStoichiometry", function () {
      const selectedStoichiometry = getSelectedStoichiometryContext();
      openStoichiometryDialog({
        chemId: selectedStoichiometry?.chemId ?? null,
        nodeId: selectedStoichiometry?.nodeId,
        nodeType: selectedStoichiometry?.nodeType,
        stoichiometryId: selectedStoichiometry?.stoichiometryId,
        stoichiometryRevision: selectedStoichiometry?.stoichiometryRevision,
      });
    });
  }
}

tinymce.PluginManager.add("stoichiometry", StoichiometryPlugin);
