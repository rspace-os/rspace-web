import path from "node:path";
import { readFileSync } from "node:fs";
import { vi } from "vitest";

type JQueryWithTestDoubles = JQueryStatic & {
  get: ReturnType<typeof vi.fn>;
  post: ReturnType<typeof vi.fn>;
};

type JQueryRequestChain = {
  fail: (handler: (...args: Array<unknown>) => void) => JQueryRequestChain;
  done: (handler: (...args: Array<unknown>) => void) => JQueryRequestChain;
  always: (handler: (...args: Array<unknown>) => void) => JQueryRequestChain;
};

type JQueryWindow = Window & typeof globalThis & {
  $: JQueryWithTestDoubles;
  jQuery: JQueryWithTestDoubles;
};

export type LegacyEditorHarness = {
  $: JQueryWithTestDoubles;
  RS: Record<string, unknown>;
  activeEditor: {
    id: string;
    execCommand: ReturnType<typeof vi.fn>;
    getBody: () => HTMLDivElement;
  };
  editorBody: HTMLDivElement;
  execCommand: ReturnType<typeof vi.fn>;
  addFromGallery: ReturnType<typeof vi.fn>;
  fileUpload: ReturnType<typeof vi.fn>;
  blockPage: ReturnType<typeof vi.fn>;
  unblockPage: ReturnType<typeof vi.fn>;
  ajaxFailed: ReturnType<typeof vi.fn>;
  getFieldIdFromTextFieldId: ReturnType<typeof vi.fn>;
};

const WEBAPP_ROOT = path.resolve(process.cwd(), "..");
const SCRIPTS_ROOT = path.resolve(WEBAPP_ROOT, "scripts");
const EDITOR_SCRIPTS_ROOT = path.resolve(
  SCRIPTS_ROOT,
  "pages/workspace/editor",
);
const JQUERY_PATH = path.resolve(
  SCRIPTS_ROOT,
  "bower_components/jquery/dist/jquery.js",
);

let jqueryLoaded = false;

function setGlobal(name: string, value: unknown) {
  Object.defineProperty(window, name, {
    configurable: true,
    writable: true,
    value,
  });
  Object.defineProperty(globalThis, name, {
    configurable: true,
    writable: true,
    value,
  });
}

function getJQueryWindow(): JQueryWindow {
  return window as JQueryWindow;
}

function ensureJQueryLoaded() {
  if (jqueryLoaded && (window as Partial<JQueryWindow>).$) {
    return;
  }

  window.eval(`${readFileSync(JQUERY_PATH, "utf8")}`);
  const jqueryWindow = getJQueryWindow();
  setGlobal("$", jqueryWindow.$);
  setGlobal("jQuery", jqueryWindow.jQuery);
  jqueryLoaded = true;
}

export function loadLegacyEditorScript(scriptName: string) {
  const scriptPath = path.resolve(EDITOR_SCRIPTS_ROOT, scriptName);
  window.eval(`${readFileSync(scriptPath, "utf8")}\n//# sourceURL=${scriptPath}`);
}

export function createJQueryRequestChain() {
  let failHandler: ((...args: Array<unknown>) => void) | undefined;
  let doneHandler: ((...args: Array<unknown>) => void) | undefined;
  let alwaysHandler: ((...args: Array<unknown>) => void) | undefined;

  const chain: JQueryRequestChain = {
    fail(handler) {
      failHandler = handler;
      return chain;
    },
    done(handler) {
      doneHandler = handler;
      return chain;
    },
    always(handler) {
      alwaysHandler = handler;
      return chain;
    },
  };

  return {
    chain,
    resolve(...args: Array<unknown>) {
      alwaysHandler?.(...args);
      doneHandler?.(...args);
    },
    reject(...args: Array<unknown>) {
      alwaysHandler?.(...args);
      failHandler?.(...args);
    },
  };
}

export function bootstrapLegacyEditorHarness(): LegacyEditorHarness {
  ensureJQueryLoaded();
  document.body.innerHTML = "";

  const $ = getJQueryWindow().$;
  const execCommand = vi.fn();
  const editorBody = document.createElement("div");
  const activeEditor = {
    id: "textFieldId_11",
    execCommand,
    getBody: () => editorBody,
  };
  const addFromGallery = vi.fn();
  const fileUpload = vi.fn();
  const blockPage = vi.fn();
  const unblockPage = vi.fn();
  const ajaxFailed = vi.fn();
  const getFieldIdFromTextFieldId = vi.fn(() => "11");

  ($ as any).get = vi.fn();
  ($ as any).post = vi.fn();
  ($.fn as any).fileupload = function (...args: Array<unknown>) {
    fileUpload(...args);
    return this;
  };

  const RS = {
    createAbsoluteUrl(relPath = "") {
      return `${window.location.protocol}//${window.location.host}${relPath}`;
    },
    safelyParseHtmlInto$Html(html: string) {
      try {
        const virtualDoc = document.implementation.createHTMLDocument("virtual");
        const $virtualDiv = $("<div></div>", virtualDoc);
        $virtualDiv.append(html);
        return $virtualDiv.contents();
      } catch {
        return $("");
      }
    },
    convert$HtmlToHtmlString($html: JQuery) {
      let htmlString = "";
      $.each($html, function () {
        const element = $(this).get(0);
        if (!element) {
          return;
        }
        if (element.nodeType === Node.ELEMENT_NODE) {
          htmlString += (element as Element).outerHTML;
        } else if (element.nodeType === Node.TEXT_NODE) {
          htmlString += element.nodeValue ?? "";
        }
      });
      return htmlString;
    },
    getVersionIdFromGlobalId(globalId: string) {
      if (globalId.indexOf("v") > 0) {
        return globalId.split("v")[1];
      }
      return null;
    },
    tinymceInsertInternalLink: vi.fn(
      (
        id: string | number,
        globalId: string,
        name: string,
        editor: { execCommand: (...args: Array<unknown>) => void },
      ) => {
        editor.execCommand(
          "mceInsertContent",
          false,
          `<a data-record-id="${id}" href="/globalId/${globalId}">${name}</a>`,
        );
      },
    ),
    blockPage,
    unblockPage,
    ajaxFailed,
  };

  setGlobal("RS", RS);
  setGlobal("tinymce", { activeEditor });
  setGlobal("addFromGallery", addFromGallery);
  setGlobal("getFieldIdFromTextFieldId", getFieldIdFromTextFieldId);

  return {
    $,
    RS,
    activeEditor,
    editorBody,
    execCommand,
    addFromGallery,
    fileUpload,
    blockPage,
    unblockPage,
    ajaxFailed,
    getFieldIdFromTextFieldId,
  };
}




