/* eslint-disable testing-library/no-node-access */
import React from "react";
import { waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
// eslint-disable-next-line vitest/no-mocks-import
import "@/__tests__/__mocks__/matchMedia";

const rootRenderCalls: Array<{
  container: Element;
  node: React.ReactNode;
}> = [];
const mockCreateRoot = vi.fn((container: Element) => ({
  render: vi.fn((node: React.ReactNode) => {
    rootRenderCalls.push({ container, node });
  }),
}));
const mockDialogEntrypoint = vi.fn(() => null);

vi.mock("react-dom/client", () => ({
  createRoot: mockCreateRoot,
}));

vi.mock("@/tinyMCE/stoichiometry/StoichiometryDialogEntrypoint", () => ({
  default: mockDialogEntrypoint,
}));

type InsertAction = {
  text: string;
  icon: string;
  action: () => void;
  aliases?: string[];
};

type MenuItemConfig = {
  text: string;
  icon: string;
  onAction: () => void;
};

type ButtonConfig = {
  tooltip: string;
  icon: string;
  onAction: () => void;
};

type EditorCommand = () => void;

type StoichiometryPluginConstructor = new (editor: EditorStub) => unknown;

type EditorStub = {
  ui: {
    registry: {
      addButton: ReturnType<typeof vi.fn>;
      addContextToolbar: ReturnType<typeof vi.fn>;
      addMenuItem: ReturnType<typeof vi.fn>;
    };
  };
  execCommand: ReturnType<typeof vi.fn>;
  id: string;
  addCommand: ReturnType<typeof vi.fn>;
  setDirty: ReturnType<typeof vi.fn>;
  selection: {
    getNode: () => HTMLElement;
    select: (node: HTMLElement) => void;
  };
  getDoc: () => Document | null;
  getParam: (name: string) => unknown;
  focus: () => void;
  dom: {
    is: (node: HTMLElement, selector: string) => boolean;
  };
};

type DialogProps = {
  open?: boolean;
  chemId?: number | null;
  autoCreateTableOnOpen?: boolean;
  recordId?: number;
  onClose?: () => void;
  onDelete?: () => void;
  onSave?: (id: number, revision: number) => void;
  onTableCreated?: (id: number, revision: number) => void;
};

function findRenderedComponentProps<Props>(
  node: React.ReactNode,
  component: React.ComponentType<Props>,
): Props | null {
  if (!React.isValidElement(node)) {
    return null;
  }

  if (node.type === component) {
    return node.props as Props;
  }

  const children = React.Children.toArray(
    (node.props as { children?: React.ReactNode }).children,
  );

  for (const child of children) {
    const view = findRenderedComponentProps(child, component);
    if (view) {
      return view;
    }
  }

  return null;
}

function registerTinymcePlugins() {
  const registeredPlugins = new Map<string, StoichiometryPluginConstructor>();

  (
    globalThis as typeof globalThis & {
      tinymce: {
        PluginManager: {
          add: (name: string, plugin: StoichiometryPluginConstructor) => void;
        };
        activeEditor: EditorStub | null;
      };
    }
  ).tinymce = {
    PluginManager: {
      add: (name, plugin) => {
        registeredPlugins.set(name, plugin);
      },
    },
    activeEditor: null,
  };

  return registeredPlugins;
}

function createTableOnlyNode(
  editorDocument: Document,
  options: {
    id: string;
    stoichiometry?: { id: number | string; revision: number | string } | string;
    childText?: string;
  },
) {
  const tableOnlyNode = editorDocument.createElement("div");
  tableOnlyNode.id = options.id;
  tableOnlyNode.setAttribute("data-stoichiometry-table-only", "true");

  if (options.stoichiometry !== undefined) {
    tableOnlyNode.setAttribute(
      "data-stoichiometry-table",
      typeof options.stoichiometry === "string"
        ? options.stoichiometry
        : JSON.stringify(options.stoichiometry),
    );
  }

  if (options.childText) {
    const child = editorDocument.createElement("span");
    child.textContent = options.childText;
    tableOnlyNode.appendChild(child);
  }

  editorDocument.body.appendChild(tableOnlyNode);
  return tableOnlyNode;
}

// This helper is needed as TinyMCE iframes are cross-realm
function expectToHaveTextContent(node: Node | null, text: string) {
  expect(node).not.toBeNull();
  expect(document.importNode(node as Node, true)).toHaveTextContent(text);
}

function createEditorHarness({
  editorDocument = document.implementation.createHTMLDocument("editor"),
  initialSelectionNode,
  execCommand,
}: {
  editorDocument?: Document | null;
  initialSelectionNode: HTMLElement;
  execCommand?: ReturnType<typeof vi.fn>;
}) {
  const commands = new Map<string, EditorCommand>();
  const buttons = new Map<string, ButtonConfig>();
  const menuItems = new Map<string, MenuItemConfig>();
  const selectionState = {
    current: initialSelectionNode,
  };

  const defaultExecCommand = vi.fn((command: string, _ui: boolean, value?: string) => {
    const registeredCommand = commands.get(command);
    if (registeredCommand) {
      registeredCommand();
      return;
    }

    if (!editorDocument) {
      return;
    }

    if (command === "mceInsertContent" && typeof value === "string") {
      const wrapper = editorDocument.createElement("div");
      wrapper.innerHTML = value;
      const insertedNode = wrapper.firstElementChild;
      if (insertedNode) {
        editorDocument.body.appendChild(insertedNode);
        selectionState.current = insertedNode as HTMLElement;
      }
      return;
    }

    if (command === "Delete") {
      selectionState.current.remove();
      return;
    }

    if (command === "mceReplaceContent" && typeof value === "string") {
      const wrapper = editorDocument.createElement("div");
      wrapper.innerHTML = value;
      const replacementNode = wrapper.firstElementChild;
      if (replacementNode) {
        selectionState.current.replaceWith(replacementNode);
        selectionState.current = replacementNode as HTMLElement;
      }
    }
  });

  const editor: EditorStub = {
    ui: {
      registry: {
        addButton: vi.fn((name: string, config: ButtonConfig) => {
          buttons.set(name, config);
        }),
        addContextToolbar: vi.fn(),
        addMenuItem: vi.fn((name: string, config: MenuItemConfig) => {
          menuItems.set(name, config);
        }),
      },
    },
    execCommand: execCommand ?? defaultExecCommand,
    id: "rtf_77",
    addCommand: vi.fn((name: string, func: EditorCommand) => {
      commands.set(name, func);
    }),
    setDirty: vi.fn(),
    selection: {
      getNode: () => selectionState.current,
      select: vi.fn((node: HTMLElement) => {
        selectionState.current = node;
      }),
    },
    getDoc: () => editorDocument,
    getParam: (name: string) => (name === "recordId" ? 77 : undefined),
    focus: vi.fn(),
    dom: {
      is: (node: HTMLElement, selector: string) => node.matches(selector),
    },
  };

  return {
    buttons,
    commands,
    editor,
    editorDocument,
    menuItems,
    selectionState,
  };
}

async function instantiateStoichiometryPlugin() {
  await import("@/tinyMCE/stoichiometry/index");
}

function getRegisteredStoichiometryPlugin(
  registeredPlugins: Map<string, StoichiometryPluginConstructor>,
) {
  const stoichiometryPlugin = registeredPlugins.get("stoichiometry");
  expect(stoichiometryPlugin).toBeDefined();
  return stoichiometryPlugin!;
}

function getLastRenderedDialogProps(): DialogProps | null {
  for (let i = rootRenderCalls.length - 1; i >= 0; i -= 1) {
    const renderCall = rootRenderCalls[i];
    const container = renderCall?.container;
    if (!(container instanceof HTMLElement) || container.id !== "tinymce-stoichiometry") {
      continue;
    }

    const view = findRenderedComponentProps(
      renderCall.node,
      mockDialogEntrypoint,
    );

    if (view) {
      return view;
    }
  }

  return null;
}

describe("TinyMCE stoichiometry plugin", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.clearAllMocks();
    rootRenderCalls.length = 0;
    document.body.innerHTML = "";
    window.insertActions = new Map<string, InsertAction>();
  });

  it("registers toolbar/menu/slash actions and opens a new table with a tableOnly div", async () => {
    const registeredPlugins = registerTinymcePlugins();
    const editorDocument = document.implementation.createHTMLDocument("editor");
    const initialSelectionNode: HTMLElement = editorDocument.createElement("p");
    editorDocument.body.appendChild(initialSelectionNode);
    const { buttons, editor, menuItems } = createEditorHarness({
      editorDocument,
      initialSelectionNode,
    });

    await instantiateStoichiometryPlugin();
    const StoichiometryPlugin = getRegisteredStoichiometryPlugin(registeredPlugins);
    new StoichiometryPlugin(editor);

    expect(menuItems.get("stoichiometryMenuItem")).toEqual(
      expect.objectContaining({ text: "Reaction Table", icon: "stoichiometry" }),
    );
    expect(buttons.get("stoichiometryInsertButton")).toEqual(
      expect.objectContaining({
        tooltip: "Insert reaction table",
        icon: "stoichiometry",
      }),
    );
    expect(window.insertActions?.get("stoichiometryMenuItem")).toEqual(
      expect.objectContaining({
        text: "Stoichiometry Table",
        icon: "stoichiometry",
        aliases: ["Stoichiometry"],
      }),
    );

    buttons.get("stoichiometryInsertButton")?.onAction();

    await waitFor(() => {
      expect(getLastRenderedDialogProps()).toEqual(
        expect.objectContaining({
          open: true,
          chemId: null,
          recordId: 77,
        }),
      );
    });

    const view = getLastRenderedDialogProps();
    expect(view).toEqual(
      expect.objectContaining({
        open: true,
        chemId: null,
        recordId: 77,
      }),
    );
    const insertedTableOnly = editorDocument.querySelector(
      '[data-stoichiometry-table-only="true"]',
    );
    expect(insertedTableOnly).not.toBeNull();
    expectToHaveTextContent(insertedTableOnly, "Empty Stoichiometry Table");
    expect(insertedTableOnly?.getAttribute("data-mce-contenteditable")).toBe(
      "false",
    );
    expect(insertedTableOnly?.getAttribute("contenteditable")).toBe("false");
    expect(insertedTableOnly?.classList.contains("mceNonEditable")).toBe(true);

    expect(view).not.toBeNull();
    view?.onClose?.();

    expect(
      editorDocument.querySelector('[data-stoichiometry-table-only="true"]'),
    ).toBeNull();
  });

  it("keeps the tableOnly div after a table has been created", async () => {
    const registeredPlugins = registerTinymcePlugins();
    const editorDocument = document.implementation.createHTMLDocument("editor");
    const initialSelectionNode: HTMLElement = editorDocument.createElement("p");
    editorDocument.body.appendChild(initialSelectionNode);
    const { editor, menuItems } = createEditorHarness({
      editorDocument,
      initialSelectionNode,
    });

    await instantiateStoichiometryPlugin();
    const StoichiometryPlugin = getRegisteredStoichiometryPlugin(registeredPlugins);
    new StoichiometryPlugin(editor);
    menuItems.get("stoichiometryMenuItem")?.onAction();

    await waitFor(() => {
      expect(getLastRenderedDialogProps()).toEqual(
        expect.objectContaining({
          open: true,
          chemId: null,
        }),
      );
    });

    let view = getLastRenderedDialogProps();
    expect(view).not.toBeNull();
    view?.onTableCreated?.(9, 2);

    const insertedTableOnly = editorDocument.querySelector(
      '[data-stoichiometry-table-only="true"]',
    );
    expect(insertedTableOnly?.getAttribute("data-stoichiometry-table")).toBe(
      JSON.stringify({ id: 9, revision: 2 }),
    );
    expectToHaveTextContent(
      insertedTableOnly,
      "Stoichiometry Table (no preview)",
    );

    view = getLastRenderedDialogProps();
    expect(view).not.toBeNull();
    view?.onClose?.();

    expect(
      editorDocument.querySelector('[data-stoichiometry-table-only="true"]'),
    ).not.toBeNull();
  });

  it("removes a newly inserted tableOnly div when its stoichiometry table is deleted", async () => {
    const registeredPlugins = registerTinymcePlugins();
    const editorDocument = document.implementation.createHTMLDocument("editor");
    const initialSelectionNode: HTMLElement = editorDocument.createElement("p");
    editorDocument.body.appendChild(initialSelectionNode);
    const { editor, menuItems } = createEditorHarness({
      editorDocument,
      initialSelectionNode,
    });

    await instantiateStoichiometryPlugin();
    const StoichiometryPlugin = getRegisteredStoichiometryPlugin(registeredPlugins);
    new StoichiometryPlugin(editor);
    menuItems.get("stoichiometryMenuItem")?.onAction();

    await waitFor(() => {
      expect(getLastRenderedDialogProps()).toEqual(
        expect.objectContaining({
          open: true,
          chemId: null,
        }),
      );
    });

    let view = getLastRenderedDialogProps();
    expect(view).not.toBeNull();
    view?.onTableCreated?.(9, 2);

    view = getLastRenderedDialogProps();
    expect(view).not.toBeNull();
    view?.onDelete?.();

    expect(
      editorDocument.querySelector('[data-stoichiometry-table-only="true"]'),
    ).toBeNull();
  });

  it("reopens stoichiometry from a selected tableOnly div child and shows its toolbar", async () => {
    const registeredPlugins = registerTinymcePlugins();
    const editorDocument = document.implementation.createHTMLDocument("editor");
    const tableOnlyNode = createTableOnlyNode(editorDocument, {
      id: "stoichiometry-1",
      stoichiometry: { id: 12, revision: 4 },
      childText: "table",
    });
    const child = tableOnlyNode.firstElementChild as HTMLElement;
    const { commands, editor, selectionState } = createEditorHarness({
      editorDocument,
      initialSelectionNode: child,
    });

    await instantiateStoichiometryPlugin();
    const StoichiometryPlugin = getRegisteredStoichiometryPlugin(registeredPlugins);
    new StoichiometryPlugin(editor);
    expectToHaveTextContent(tableOnlyNode, "Stoichiometry Table (no preview)");
    selectionState.current = tableOnlyNode;
    commands.get("cmdStoichiometry")?.();

    await waitFor(() => {
      expect(getLastRenderedDialogProps()).toEqual(
        expect.objectContaining({
          open: true,
          chemId: null,
          stoichiometryId: 12,
          stoichiometryRevision: 4,
          autoCreateTableOnOpen: false,
        }),
      );
    });
  });

  it("throws when an existing tableOnly node is missing stoichiometry data attributes", async () => {
    const registeredPlugins = registerTinymcePlugins();
    const editorDocument = document.implementation.createHTMLDocument("editor");
    const tableOnlyNode = createTableOnlyNode(editorDocument, {
      id: "stoichiometry-1",
      childText: "table",
    });
    const child = tableOnlyNode.firstElementChild as HTMLElement;
    const { commands, editor } = createEditorHarness({
      editorDocument,
      initialSelectionNode: child,
    });

    await instantiateStoichiometryPlugin();
    const StoichiometryPlugin = getRegisteredStoichiometryPlugin(registeredPlugins);
    new StoichiometryPlugin(editor);

    expect(() => commands.get("cmdStoichiometry")?.()).toThrow(
      "Stoichiometry table node is missing data-stoichiometry-table.",
    );
  });

  it("throws when an existing tableOnly node has malformed stoichiometry data attributes", async () => {
    const registeredPlugins = registerTinymcePlugins();
    const editorDocument = document.implementation.createHTMLDocument("editor");
    const tableOnlyNode = createTableOnlyNode(editorDocument, {
      id: "stoichiometry-1",
      stoichiometry: "not-json",
      childText: "table",
    });
    const child = tableOnlyNode.firstElementChild as HTMLElement;
    const { commands, editor } = createEditorHarness({
      editorDocument,
      initialSelectionNode: child,
    });

    await instantiateStoichiometryPlugin();
    const StoichiometryPlugin = getRegisteredStoichiometryPlugin(registeredPlugins);
    new StoichiometryPlugin(editor);

    expect(() => commands.get("cmdStoichiometry")?.()).toThrow(
      "Stoichiometry table node has invalid data-stoichiometry-table JSON.",
    );
  });

  it("throws when an existing tableOnly node has structurally malformed stoichiometry data attributes", async () => {
    const registeredPlugins = registerTinymcePlugins();
    const editorDocument = document.implementation.createHTMLDocument("editor");
    const tableOnlyNode = createTableOnlyNode(editorDocument, {
      id: "stoichiometry-1",
      stoichiometry: { id: "12", revision: 4 },
      childText: "table",
    });
    const child = tableOnlyNode.firstElementChild as HTMLElement;
    const { commands, editor } = createEditorHarness({
      editorDocument,
      initialSelectionNode: child,
    });

    await instantiateStoichiometryPlugin();
    const StoichiometryPlugin = getRegisteredStoichiometryPlugin(registeredPlugins);
    new StoichiometryPlugin(editor);

    expect(() => commands.get("cmdStoichiometry")?.()).toThrow(
      "Stoichiometry table node has malformed data-stoichiometry-table attributes.",
    );
  });

  it("adds placeholder text to an existing empty tableOnly div on plugin init", async () => {
    const registeredPlugins = registerTinymcePlugins();
    const editorDocument = document.implementation.createHTMLDocument("editor");
    const tableOnlyNode = createTableOnlyNode(editorDocument, {
      id: "stoichiometry-empty",
    });
    const { editor } = createEditorHarness({
      editorDocument,
      initialSelectionNode: tableOnlyNode,
    });

    await instantiateStoichiometryPlugin();
    const StoichiometryPlugin = getRegisteredStoichiometryPlugin(registeredPlugins);
    new StoichiometryPlugin(editor);

    expectToHaveTextContent(tableOnlyNode, "Empty Stoichiometry Table");
    // eslint-disable-next-line jest-dom/prefer-to-have-style
    expect(tableOnlyNode.style.height).toBe("45px");
  });

  it("renders stoichiometry table text when data is added through plugin callbacks", async () => {
    const registeredPlugins = registerTinymcePlugins();
    const editorDocument = document.implementation.createHTMLDocument("editor");
    const initialSelectionNode: HTMLElement = editorDocument.createElement("p");
    editorDocument.body.appendChild(initialSelectionNode);
    const { editor, menuItems } = createEditorHarness({
      editorDocument,
      initialSelectionNode,
    });

    await instantiateStoichiometryPlugin();
    const StoichiometryPlugin = getRegisteredStoichiometryPlugin(registeredPlugins);
    new StoichiometryPlugin(editor);
    menuItems.get("stoichiometryMenuItem")?.onAction();

    await waitFor(() => {
      expect(getLastRenderedDialogProps()).toEqual(
        expect.objectContaining({
          open: true,
          chemId: null,
        }),
      );
    });

    const view = getLastRenderedDialogProps();
    expect(view).not.toBeNull();
    view?.onTableCreated?.(15, 6);

    const tableOnlyNode = editorDocument.querySelector(
      '[data-stoichiometry-table-only="true"]',
    );
    expectToHaveTextContent(
      tableOnlyNode,
      "Stoichiometry Table (no preview)",
    );
    expect(tableOnlyNode?.children.length).toBe(0);
  });

  it("updates stoichiometry table text when data is saved through plugin callbacks", async () => {
    const registeredPlugins = registerTinymcePlugins();
    const editorDocument = document.implementation.createHTMLDocument("editor");
    const initialSelectionNode: HTMLElement = editorDocument.createElement("p");
    editorDocument.body.appendChild(initialSelectionNode);
    const { editor, menuItems } = createEditorHarness({
      editorDocument,
      initialSelectionNode,
    });

    await instantiateStoichiometryPlugin();
    const StoichiometryPlugin = getRegisteredStoichiometryPlugin(registeredPlugins);
    new StoichiometryPlugin(editor);
    menuItems.get("stoichiometryMenuItem")?.onAction();

    await waitFor(() => {
      expect(getLastRenderedDialogProps()).toEqual(
        expect.objectContaining({
          open: true,
          chemId: null,
        }),
      );
    });

    let view = getLastRenderedDialogProps();
    expect(view).not.toBeNull();
    view?.onTableCreated?.(15, 6);

    view = getLastRenderedDialogProps();
    expect(view).not.toBeNull();
    view?.onSave?.(15, 7);

    const tableOnlyNode = editorDocument.querySelector(
      '[data-stoichiometry-table-only="true"]',
    );
    expect(tableOnlyNode?.getAttribute("data-stoichiometry-table")).toBe(
      JSON.stringify({ id: 15, revision: 7 }),
    );
    expectToHaveTextContent(
      tableOnlyNode,
      "Stoichiometry Table (no preview)",
    );
  });

  it("does not crash when the editor document is unavailable", async () => {
    const registeredPlugins = registerTinymcePlugins();
    const currentSelectionNode = document.createElement("div");
    const { editor, menuItems } = createEditorHarness({
      editorDocument: null,
      initialSelectionNode: currentSelectionNode,
    });

    await instantiateStoichiometryPlugin();
    const StoichiometryPlugin = getRegisteredStoichiometryPlugin(registeredPlugins);
    expect(() => new StoichiometryPlugin(editor)).not.toThrow();

    menuItems.get("stoichiometryMenuItem")?.onAction();

    await waitFor(() => {
      expect(getLastRenderedDialogProps()).toEqual(
        expect.objectContaining({
          open: true,
          chemId: null,
          recordId: 77,
        }),
      );
    });

    expect(editor.execCommand).toHaveBeenCalledWith(
      "mceInsertContent",
      false,
      expect.stringContaining("data-stoichiometry-table-only=\"true\""),
    );
    expect(editor.selection.select).not.toHaveBeenCalled();
    expect(editor.focus).not.toHaveBeenCalled();
  });
});




