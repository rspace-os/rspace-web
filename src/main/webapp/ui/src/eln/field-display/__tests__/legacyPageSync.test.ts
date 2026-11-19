/*
 * Runs the document page's own legacy scripts (documentView.js and
 * documentEdit.js, with the page's jQuery) against field rows as
 * structuredDocumentMainPanel.jsp renders them, to check that the two paths
 * that reload field data keep a picklist's answer, its dependents and the Sign
 * calculation in step.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import vm from "node:vm";
import { HttpResponse, http } from "msw";
import { afterEach, beforeAll, beforeEach, describe, expect, test, vi } from "vitest";
import { createTestI18n } from "@/__tests__/helpers/createTestI18n";
import { server } from "@/__tests__/mswServer";
import { createDisplayRule, serializeDisplayRule } from "@/modules/common/field-rules/displayRule";
import commonEn from "@/modules/common/i18n/locales/en-US/common.json";
import { createLegacyFieldDisplay } from "../legacyFieldDisplay";

const WEBAPP = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../../../..");
const HAZARD_KEY = "6f1c2e0a-0000-4000-8000-000000000011";
const showWhenYes = serializeDisplayRule(createDisplayRule(HAZARD_KEY, "IS_ANY_OF", ["Yes"]));
const CONTROLS_KEY = "6f1c2e0a-0000-4000-8000-000000000013";
const showWhenControls = serializeDisplayRule(createDisplayRule(CONTROLS_KEY, "HAS_VALUE", []));
const REMOVED_CHOICE = "fieldSelectedChoicesFinal_13=Removed";

type LegacyPage = Window & {
  $: JQueryStatic;
  updateFieldsContentFromServer: (force: boolean) => PromiseLike<unknown>;
  _updateAutosavedFields: (fields: Array<{ id: number; fieldData: string }>) => void;
  checkAndMarkField: (control: Element) => void;
  _autosavePickList: ($field: JQuery) => PromiseLike<unknown>;
};
const legacy = window as unknown as LegacyPage;
const renderToolbar = vi.fn();

function runScript(relativePath: string) {
  // run as a classic script in the global context, as the browser would: its functions become globals
  const filename = path.join(WEBAPP, relativePath);
  vm.runInThisContext(fs.readFileSync(filename, "utf8"), { filename });
}

beforeAll(() => {
  runScript("scripts/bower_components/jquery/dist/jquery.min.js");
  // the pages' ready handlers set up dialogs and toolbars this test does not need
  legacy.$.fn.ready = function ready() {
    return this;
  };
  Object.assign(window, {
    RS: {
      blockPage: () => {},
      unblockPage: () => {},
      msg: (key: string) => key,
      escapeHtml: (text: string) => legacy.$("<div>").text(text).html(),
    },
    createURL: (url: string) => url,
    renderToolbar,
    recordId: "552",
  });
  runScript("scripts/pages/workspace/editor/documentView.js");
  runScript("scripts/pages/workspace/editor/documentEdit.js");
});

/**
 * A picklist "Hazard?" whose "Yes" shows the mandatory, empty "Hazard permit",
 * and a choice field "Controls" whose stored value "Removed" is no longer an
 * option, with "Controls note" shown while it has a value.
 */
async function renderPage(storedAnswer: string) {
  document.body.innerHTML = `
    <input type="hidden" id="lastModificationRecord" value="0">
    <table>
      <tr id="field_11" name="picklist" class="field" data-field-id="11" data-field-key="${HAZARD_KEY}"
          data-field-type="RADIO" data-field-name="Hazard?" data-field-value="${storedAnswer}">
        <td><select id="11" class="formPickList 11">
          ${storedAnswer === "" ? '<option value="" selected></option>' : ""}
          <option class="radioLi 11" value="Yes"${storedAnswer === "Yes" ? " selected" : ""}>Yes</option>
          <option class="radioLi 11" value="No"${storedAnswer === "No" ? " selected" : ""}>No</option>
        </select><input type="hidden" name="fieldId" value="11"><p id="radioText_11">${storedAnswer}</p></td>
      </tr>
      <tr id="field_13" name="choice" class="field" data-field-id="13" data-field-key="${CONTROLS_KEY}"
          data-field-type="CHOICE" data-field-name="Controls" data-field-value="${REMOVED_CHOICE}">
        <td><input type="checkbox" class="13" id="13" name="fieldSelectedChoicesFinal_13" value="Gloves">
          <p id="choiceText_13">Removed</p></td>
      </tr>
      <tr id="field_14" name="string" class="field" data-field-id="14" data-field-type="STRING"
          data-field-name="Controls note" data-display-rule='${showWhenControls}'>
        <td><input class="inputField 14" id="14" name="fieldData" value=""><p id="plainText_14"></p></td>
      </tr>
      <tr id="field_12" name="string" class="field" data-field-id="12" data-field-type="STRING"
          data-field-name="Hazard permit" data-display-rule='${showWhenYes}'>
        <td><input class="inputField 12" id="12" name="fieldData" value=""><p id="plainText_12"></p></td>
      </tr>
    </table>`;
  const display = createLegacyFieldDisplay(await createTestI18n({ common: commonEn }, "common"));
  Object.assign(window, { elnFieldDisplay: display });
  display.refresh();
}

const pickList = () => document.getElementById("11") as HTMLSelectElement;
const permitRow = () => document.getElementById("field_12");
const controlsNoteRow = () => document.getElementById("field_14");

function serveUpdatedFields(answer: string) {
  server.use(
    http.get("*/workspace/editor/structuredDocument/ajax/getUpdatedFields", () =>
      HttpResponse.json({
        data: [
          { id: 11, type: "RADIO", fieldData: answer, mandatoryStateSatisfied: true },
          { id: 12, type: "STRING", fieldData: "", mandatoryStateSatisfied: false },
          {
            id: 13,
            type: "CHOICE",
            fieldData: REMOVED_CHOICE,
            choiceOptionSelectedAsList: ["Removed"],
            choiceOptionSelectedAsString: "Removed",
            mandatoryStateSatisfied: true,
          },
        ],
      }),
    ),
  );
}

beforeEach(() => {
  renderToolbar.mockClear();
});

afterEach(() => {
  document.body.innerHTML = "";
});

describe("reloading field data from the server (_updateFields)", () => {
  test("an empty picklist reloaded as Yes reveals the empty mandatory field and blocks Sign", async () => {
    await renderPage("");
    expect(permitRow()).not.toBeVisible();
    serveUpdatedFields("Yes");

    await legacy.updateFieldsContentFromServer(true);

    expect(pickList()).toHaveValue("Yes");
    expect(permitRow()).toBeVisible();
    expect(renderToolbar).toHaveBeenLastCalledWith({ canSign: false });
  });

  test("a picklist the user had picked from, reloaded as No, hides it again and allows Sign", async () => {
    await renderPage("Yes");
    // the user picks Yes; the JSP's inline onchange calls checkAndMarkField (jsdom runs inline
    // handlers in its own global, where the scripts' functions are not visible)
    legacy.$("#11").val("Yes");
    legacy.checkAndMarkField(pickList());
    expect(permitRow()).toBeVisible();
    serveUpdatedFields("No");

    await legacy.updateFieldsContentFromServer(true);

    expect(pickList()).toHaveValue("No");
    expect(permitRow()).not.toBeVisible();
    expect(renderToolbar).toHaveBeenLastCalledWith({ canSign: true });
  });

  test("a choice field the user had changed, reloaded with a value no longer offered, counts that value again", async () => {
    await renderPage("");
    expect(controlsNoteRow()).toBeVisible();
    // the user checks and unchecks Gloves: autosave would store no options
    legacy.checkAndMarkField(document.getElementById("13") as HTMLInputElement);
    expect(controlsNoteRow()).not.toBeVisible();
    serveUpdatedFields("");

    await legacy.updateFieldsContentFromServer(true);

    expect(controlsNoteRow()).toBeVisible();
  });
});

describe("autosaving a picklist (_autosavePickList)", () => {
  test("stores no data while an empty picklist shows its placeholder, and the first option once picked", async () => {
    const saved: Array<string | null> = [];
    server.use(
      http.post("*/workspace/editor/structuredDocument/ajax/autosaveField", async ({ request }) => {
        saved.push(new URLSearchParams(await request.text()).get("dataValue"));
        return HttpResponse.json({ data: true });
      }),
    );
    await renderPage("");
    expect(pickList()).toHaveValue("");
    await legacy._autosavePickList(legacy.$("#field_11"));

    legacy.$("#11").val("Yes");
    legacy.checkAndMarkField(pickList());
    expect(permitRow()).toBeVisible();
    await legacy._autosavePickList(legacy.$("#field_11"));

    expect(saved).toEqual(["", "Yes"]);
  });
});

describe("restoring autosaved data on load (_updateAutosavedFields)", () => {
  test("an autosaved Yes over an empty picklist reveals the dependent", async () => {
    await renderPage("");

    legacy._updateAutosavedFields([{ id: 11, fieldData: "Yes" }]);

    expect(pickList()).toHaveValue("Yes");
    expect(permitRow()).toBeVisible();
  });

  test("an autosaved No over a saved Yes hides the dependent", async () => {
    await renderPage("Yes");
    expect(permitRow()).toBeVisible();

    legacy._updateAutosavedFields([{ id: 11, fieldData: "No" }]);

    expect(pickList()).toHaveValue("No");
    expect(permitRow()).not.toBeVisible();
  });
});
