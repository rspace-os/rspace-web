import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { runInNewContext } from "node:vm";
import { describe, expect, it, vi } from "vitest";

const __dirname = dirname(fileURLToPath(import.meta.url));
const SCRIPTS_ROOT = resolve(__dirname, "../../../../scripts");

function evaluateScript(relativePath: string, sandbox: Record<string, unknown>) {
  runInNewContext(readFileSync(resolve(SCRIPTS_ROOT, relativePath), "utf8"), sandbox);
}

function integration(enabled = false, available = false, options: Record<string, boolean> = {}) {
  return { enabled, available, options };
}

function loadChemistryConfiguration(enabled: boolean, available: boolean) {
  const toolbarRequest = {
    done(callback: (response: Array<string>) => void) {
      callback([]);
      return toolbarRequest;
    },
  };
  const request = {};
  const combinedRequest = {
    done(callback: (...responses: Array<unknown>) => void) {
      callback(
        [{}],
        [
          {
            data: {
              BOX: integration(),
              CHEMISTRY: integration(enabled, available),
              CLUSTERMARKET: integration(),
              DROPBOX: integration(false, false, { "dropbox.linking.enabled": false }),
              EGNYTE: integration(),
              GALAXY: integration(),
              GITHUB: integration(),
              GOOGLEDRIVE: integration(false, false, { "googledrive.linking.enabled": false }),
              NEXTCLOUD: integration(),
              OMERO: integration(),
              ONEDRIVE: integration(false, false, { "onedrive.linking.enabled": false }),
              OWNCLOUD: integration(),
              PROTOCOLS_IO: integration(),
              PYRAT: integration(),
            },
          },
        ],
        [[]],
      );
      return combinedRequest;
    },
    fail() {
      return combinedRequest;
    },
    always() {
      return combinedRequest;
    },
  };
  const deferred = { promise: () => ({}) };
  const sandbox: Record<string, unknown> = {
    $: {
      Deferred: () => deferred,
      get: () => request,
      getJSON: () => toolbarRequest,
      when: () => combinedRequest,
    },
    console: { error: vi.fn(), log: vi.fn(), warn: vi.fn() },
    document: { querySelector: () => null },
    localStorage: { getItem: () => null },
    Promise,
    recordId: 1,
    RS: { msg: (key: string) => key },
    Set,
    tinymce: { PluginManager: { add: () => {} } },
    window: {},
  };

  evaluateScript("pages/workspace/editor/tinymce5_configuration.js", sandbox);
  sandbox.ensureTinymceVitePluginsLoaded = () => Promise.resolve();
  (sandbox.initTinyMCE as (selector: string) => unknown)("field-id");
  return sandbox;
}

type ContextToolbar = {
  items: string;
  predicate: (node: { getAttribute: (name: string) => string | null }) => boolean;
};

function loadChemistryToolbars(chemistryEnabled: boolean, chemistryProvider = "indigo") {
  const toolbars = new Map<string, ContextToolbar>();
  const selectedNode = {
    getAttribute: vi.fn<(name: string) => string | null>(),
  };
  const editor = {
    dom: {
      is: (_node: unknown, selector: string) => selector === "img.chem",
    },
    execCommand: vi.fn(),
    selection: { getNode: () => selectedNode },
    ui: {
      registry: {
        addButton: vi.fn(),
        addContextToolbar: (name: string, toolbar: ContextToolbar) => toolbars.set(name, toolbar),
      },
    },
  };
  const sandbox = {
    $: vi.fn(),
    RS: { chemistryEnabled, chemistryProvider },
    tinymce: {
      PluginManager: {
        add: (_name: string, register: (registeredEditor: typeof editor) => void) => register(editor),
      },
    },
  };

  evaluateScript("externalTinymcePlugins/contexttoolbars/plugin.min.js", sandbox);
  return { selectedNode, toolbars };
}

describe("legacy TinyMCE chemistry integration", () => {
  it("requires both system availability and the user's app toggle", () => {
    const disabled = loadChemistryConfiguration(false, true);
    const unavailable = loadChemistryConfiguration(true, false);
    const enabled = loadChemistryConfiguration(true, true);

    expect(disabled.chemistryAvailable).toBe(false);
    expect(disabled.RS).toMatchObject({ chemistryEnabled: false });
    expect(unavailable.chemistryAvailable).toBe(false);
    expect(unavailable.RS).toMatchObject({ chemistryEnabled: false });
    expect(enabled.chemistryAvailable).toBe(true);
    expect(enabled.RS).toMatchObject({ chemistryEnabled: true });
  });

  it("keeps generic chemistry-image actions while hiding disabled chemistry actions", () => {
    const { selectedNode, toolbars } = loadChemistryToolbars(false);
    selectedNode.getAttribute.mockReturnValue("42");

    expect(toolbars.get("chemicalFileViewableKetcher")).toMatchObject({
      items: "attachmentinfopopup attachmentdownload | resizeImage",
    });
    expect(toolbars.get("chemicalFileViewableKetcher")?.predicate(selectedNode)).toBe(true);

    selectedNode.getAttribute.mockReturnValue(null);
    expect(toolbars.get("chemicalElementEditableKetcher")).toMatchObject({
      items: "downloadImage | resizeImage",
    });
    expect(toolbars.get("chemicalElementEditableKetcher")?.predicate(selectedNode)).toBe(true);
    expect(toolbars.has("stoichiometryTableOnly")).toBe(false);
  });

  it("includes Ketcher and stoichiometry actions when chemistry is enabled", () => {
    const { toolbars } = loadChemistryToolbars(true);

    expect(toolbars.get("chemicalFileViewableKetcher")?.items).toContain("ketcherViewable | stoichiometry");
    expect(toolbars.get("chemicalElementEditableKetcher")?.items).toContain("ketcherEditable | stoichiometry");
    expect(toolbars.get("stoichiometryTableOnly")?.items).toBe("stoichiometry");
  });

  it("hides Ketcher and stoichiometry actions for a non-Indigo provider", () => {
    const { toolbars } = loadChemistryToolbars(true, "chemaxon");

    expect(toolbars.get("chemicalFileViewableKetcher")?.items).toBe(
      "attachmentinfopopup attachmentdownload | resizeImage",
    );
    expect(toolbars.get("chemicalElementEditableKetcher")?.items).toBe("downloadImage | resizeImage");
    expect(toolbars.has("stoichiometryTableOnly")).toBe(false);
  });
});
