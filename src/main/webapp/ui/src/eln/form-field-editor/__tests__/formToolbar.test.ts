/*
 * Runs the form editor page's toolbar script (form.js, with the page's jQuery)
 * against a fake field editor and server, to check that Update and Publish
 * send the field list revision the editor checked, so that a save from
 * another tab in between is refused rather than published, and that they do
 * nothing when the editor cannot check its fields.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import vm from "node:vm";
import { HttpResponse, http } from "msw";
import { beforeAll, beforeEach, describe, expect, onTestFinished, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";

const WEBAPP = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../../../..");

type DialogOptions = {
  close: () => void;
  buttons: Record<string, (this: HTMLElement) => void>;
};
type LegacyPage = Window & { $: JQueryStatic };
const legacy = window as unknown as LegacyPage;
const dialogs = new Map<string, DialogOptions>();
const opened = new Set<string>();

function runScript(relativePath: string) {
  const filename = path.join(WEBAPP, relativePath);
  vm.runInThisContext(fs.readFileSync(filename, "utf8"), { filename });
}

const apprise = vi.fn();
const ajaxFailed = vi.fn();
const alert = vi.fn();

beforeAll(() => {
  runScript("scripts/bower_components/jquery/dist/jquery.min.js");
  // jQuery UI's dialog, as far as form.js uses it
  Object.assign(legacy.$.fn, {
    dialog(this: JQuery, arg: DialogOptions | string) {
      const id = this.attr("id") ?? "";
      if (typeof arg === "object") dialogs.set(id, arg);
      else if (arg === "open") opened.add(id);
      else if (arg === "close" && opened.delete(id)) dialogs.get(id)?.close.call(this[0]);
      return this;
    },
    bootstrapButton(this: JQuery) {
      return this;
    },
  });
  Object.assign(window, {
    RS: {
      blockPage: () => {},
      unblockPage: () => {},
      msg: (key: string) => key,
      ajaxFailed,
    },
    apprise,
    alert,
    createURL: (url: string) => url,
    editable: "EDIT_MODE",
    recordId: "5",
    editLockToken: "page-token",
  });
});

/** The field editor's toolbar handle: saving or checking the fields resolves with their revision. */
function fakeEditor(save: () => Promise<number>) {
  const editor = {
    save: vi.fn(save),
    isDirty: () => false,
    fieldCount: () => 1,
    markPublished: vi.fn(),
    setReadOnly: vi.fn(),
    reportConflict: vi.fn(),
    confirmAbandon: () => Promise.resolve(false),
  };
  Object.assign(window, { formFieldEditor: editor });
  return editor;
}

/**
 * The server's field list revision; a publish or update sent with another one is refused, as the
 * server does when another tab saved the fields after this page checked them.
 */
function serveForm(revision: { current: number }) {
  const sent: Array<URLSearchParams> = [];
  const refuseStale = async (request: Request, ok: unknown) => {
    const body = new URLSearchParams(await request.text());
    sent.push(body);
    return Number(body.get("fieldsRevision")) === revision.current
      ? HttpResponse.json({ data: ok, errorMsg: null })
      : HttpResponse.json({ data: null, errorMsg: { errorMessages: ["changed elsewhere"] } }, { status: 409 });
  };
  server.use(
    http.get("*/workspace/editor/form/ajax/publishAndShare", () =>
      HttpResponse.text(
        `<form id="rsFormSharingForm" method="POST" action="/workspace/editor/form/ajax/publishAndShare">
          <input type="radio" name="groupOptions" value="READ" checked>
          <input type="hidden" name="formId" value="5">
        </form>`,
      ),
    ),
    http.post("*/workspace/editor/form/ajax/publishAndShare", ({ request }) => refuseStale(request, "PUBLISHED")),
    http.post("*/workspace/editor/form/ajax/updateForm", ({ request }) => refuseStale(request, 5)),
  );
  return sent;
}

beforeEach(async () => {
  dialogs.clear();
  opened.clear();
  apprise.mockClear();
  ajaxFailed.mockClear();
  alert.mockClear();
  document.body.innerHTML = `
    <span id="publishingStatus">UNPUBLISHED</span>
    <button id="save">Save</button><button id="update">Update</button><button id="abandon">Revert</button>
    <a href="#" id="publish" class="publishAction">Publish</a>
    <a href="#" id="unpublish" class="publishAction" style="display:none">Unpublish</a>
    <div id="publishShareDlg"><div id="publishShareDlgContent"></div></div>`;
  runScript("scripts/pages/workspace/editor/form.js");
  await vi.waitFor(() => expect(dialogs.has("publishShareDlg")).toBe(true));
});

async function openPublishDialog() {
  legacy.$("#publish").trigger("click");
  await vi.waitFor(() => expect(opened.has("publishShareDlg")).toBe(true));
}

function confirmPublishDialog() {
  const dialog = document.getElementById("publishShareDlg");
  const options = dialogs.get("publishShareDlg");
  if (!dialog || !options) throw new Error("no access dialog");
  options.buttons["legacyjs.workspace.form.okButton"].call(dialog);
}

describe("form.js toolbar", () => {
  test("publishes with the revision the editor checked", async () => {
    const editor = fakeEditor(() => Promise.resolve(7));
    const sent = serveForm({ current: 7 });

    await openPublishDialog();
    confirmPublishDialog();

    await vi.waitFor(() => expect(editor.markPublished).toHaveBeenCalled());
    expect(sent[0].get("fieldsRevision")).toBe("7");
    expect(legacy.$("#unpublish").css("display")).not.toBe("none");
  });

  test("does not publish fields another tab saved while the access dialog was open", async () => {
    const editor = fakeEditor(() => Promise.resolve(7));
    const revision = { current: 7 };
    const sent = serveForm(revision);

    await openPublishDialog();
    // the same user saves the fields in another tab, then confirms the dialog left open here
    revision.current = 8;
    confirmPublishDialog();

    await vi.waitFor(() => expect(editor.reportConflict).toHaveBeenCalled());
    expect(sent[0].get("fieldsRevision")).toBe("7");
    expect(editor.markPublished).not.toHaveBeenCalled();
    expect(editor.setReadOnly).toHaveBeenLastCalledWith(false);
    expect(alert).not.toHaveBeenCalled();
    expect(legacy.$("#unpublish").css("display")).toBe("none");
  });

  test("does not update a form whose fields another tab saved after the editor checked them", async () => {
    const editor = fakeEditor(() => Promise.resolve(7));
    const sent = serveForm({ current: 8 });

    legacy.$("#update").trigger("click");

    await vi.waitFor(() => expect(editor.reportConflict).toHaveBeenCalled());
    expect(sent[0].get("fieldsRevision")).toBe("7");
    expect(sent[0].get("lockToken")).toBe("page-token");
    expect(ajaxFailed).not.toHaveBeenCalled();
  });

  test("neither updates nor publishes while the editor refuses to save its fields, which says why itself", async () => {
    // as the editor does while a display condition is not filled out
    const editor = fakeEditor(() => Promise.reject(Object.assign(new Error(""), { name: "FormFieldsSaveError" })));
    const sent = serveForm({ current: 7 });
    // each action unblocks the page once the editor has answered, and would have sent its request by then
    const unblockPage = vi.spyOn((window as unknown as { RS: { unblockPage: () => void } }).RS, "unblockPage");
    onTestFinished(() => unblockPage.mockRestore());

    legacy.$("#publish").trigger("click");
    await vi.waitFor(() => expect(unblockPage).toHaveBeenCalledTimes(1));
    legacy.$("#update").trigger("click");
    await vi.waitFor(() => expect(unblockPage).toHaveBeenCalledTimes(2));

    expect(editor.save).toHaveBeenCalledTimes(2);
    expect(editor.setReadOnly).toHaveBeenLastCalledWith(false);
    expect(opened.has("publishShareDlg")).toBe(false);
    expect(sent).toHaveLength(0);
    expect(apprise).not.toHaveBeenCalled();
  });

  test.each([
    ["the editor's bundle did not load", () => Object.assign(window, { formFieldEditor: undefined })],
    [
      "the editor's fields did not load",
      () =>
        fakeEditor(() =>
          Promise.reject(Object.assign(new Error("not loaded"), { name: "FormFieldEditorUnavailableError" })),
        ),
    ],
  ])("neither updates nor publishes when %s, and says why", async (_case, setUp) => {
    setUp();
    const sent = serveForm({ current: 7 });

    legacy.$("#update").trigger("click");
    await vi.waitFor(() => expect(apprise).toHaveBeenCalledTimes(1));
    legacy.$("#publish").trigger("click");
    await vi.waitFor(() => expect(apprise).toHaveBeenCalledTimes(2));

    expect(apprise).toHaveBeenCalledWith("legacyjs.workspace.form.fieldEditorUnavailable");
    expect(opened.has("publishShareDlg")).toBe(false);
    expect(sent).toHaveLength(0);
  });
});
