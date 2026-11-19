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

type LegacyPage = Window & {
  $: JQueryStatic;
  updateFieldsContentFromServer: (force: boolean) => PromiseLike<unknown>;
  _updateAutosavedFields: (fields: Array<{ id: number; fieldData: string }>) => void;
  checkAndMarkField: (control: Element) => void;
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

/** A picklist "Hazard?" whose "Yes" shows the mandatory, empty "Hazard permit". */
async function renderPage(storedAnswer: string) {
  document.body.innerHTML = `
    <input type="hidden" id="lastModificationRecord" value="0">
    <table>
      <tr id="field_11" name="picklist" class="field" data-field-id="11" data-field-key="${HAZARD_KEY}"
          data-field-type="RADIO" data-field-name="Hazard?" data-field-value="${storedAnswer}">
        <td><select id="11" class="formPickList 11">
          <option value="Yes"${storedAnswer === "Yes" ? " selected" : ""}>Yes</option>
          <option value="No"${storedAnswer === "No" ? " selected" : ""}>No</option>
        </select><p id="radioText_11">${storedAnswer}</p></td>
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

function serveUpdatedFields(answer: string) {
  server.use(
    http.get("*/workspace/editor/structuredDocument/ajax/getUpdatedFields", () =>
      HttpResponse.json({
        data: [
          { id: 11, type: "RADIO", fieldData: answer, mandatoryStateSatisfied: true },
          { id: 12, type: "STRING", fieldData: "", mandatoryStateSatisfied: false },
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
