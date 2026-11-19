import { waitFor } from "@testing-library/react";
import { createInstance } from "i18next";
import ICU from "i18next-icu";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { createTestI18n } from "@/__tests__/helpers/createTestI18n";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { createDisplayRule, serializeDisplayRule } from "@/modules/common/field-rules/displayRule";
import commonEn from "@/modules/common/i18n/locales/en-US/common.json";
import { createLegacyFieldDisplay, type LegacyFieldDisplay } from "../legacyFieldDisplay";

const GMO_KEY = "6f1c2e0a-0000-4000-8000-000000000001";
const showWhenYes = serializeDisplayRule(createDisplayRule(GMO_KEY, "IS_ANY_OF", ["Yes"]));

function dependentRow(id: string, name: string): string {
  return `<tr class="field" data-field-id="${id}" data-field-type="STRING" data-field-name="${name}" data-display-rule='${showWhenYes}'><td><input type="text"></td></tr>`;
}

let display: LegacyFieldDisplay;

beforeEach(async () => {
  document.body.innerHTML = `
    <table><tbody>
      <tr class="field" data-field-id="1" data-field-type="RADIO" data-field-key="${GMO_KEY}" data-field-name="GMO?">
        <td><input type="radio" name="gmo" value="Yes"><input type="radio" name="gmo" value="No"></td>
      </tr>
      ${dependentRow("2", "Containment level")}
      ${dependentRow("3", "GMO permit")}
    </tbody></table>`;
  display = createLegacyFieldDisplay(await createTestI18n({ common: commonEn }, "common"));
  display.refresh();
});

afterEach(() => {
  document.body.innerHTML = "";
});

function answer(value: string): HTMLInputElement {
  const input = document.querySelector<HTMLInputElement>(`input[value="${value}"]`);
  if (input === null) throw new Error(`No answer ${value}`);
  return input;
}

function choose(value: string) {
  const control = answer(value);
  control.focus();
  control.checked = true;
  display.fieldChanged(control);
}

describe("createLegacyFieldDisplay", () => {
  test("announces the fields a change reveals, politely, and leaves focus on the changed control", () => {
    choose("Yes");

    expect(document.querySelector('[role="status"]')).toHaveAttribute("aria-live", "polite");
    expect(document.querySelector('[role="status"]')).toHaveTextContent(
      "2 more fields are shown: Containment level and GMO permit",
    );
    expect(answer("Yes")).toHaveFocus();
  });

  test("mounts an empty polite region on load, and announces nothing on a refresh without a change", () => {
    const region = document.querySelector('[role="status"]');
    expect(region).toHaveAttribute("aria-live", "polite");
    expect(region).toBeEmptyDOMElement();

    answer("Yes").checked = true;
    display.refresh();

    expect(region).toBeEmptyDOMElement();
  });

  /** An i18n instance whose `common` catalog arrives (or fails) when the test says so. */
  async function i18nWithCatalog(load: () => Promise<typeof commonEn>) {
    const instance = createInstance();
    await instance
      .use(ICU)
      .use({
        type: "backend",
        init: () => {},
        read: (_language: string, _namespace: string, callback: (error: unknown, data: unknown) => void) => {
          load().then(
            (data) => callback(null, data),
            (error: unknown) => callback(error, false),
          );
        },
      })
      .init({ lng: "en-US", load: "currentOnly", fallbackLng: false, ns: [], defaultNS: "common" });
    return instance;
  }

  function changeWith(i18n: Awaited<ReturnType<typeof i18nWithCatalog>>) {
    document.querySelector('[role="status"]')?.remove();
    const early = createLegacyFieldDisplay(i18n);
    early.refresh();
    const control = answer("Yes");
    control.checked = true;
    early.fieldChanged(control);
    return document.querySelector('[role="status"]');
  }

  test("announces nothing, not even the message key, when the catalog fails to load", async () => {
    const restoreConsole = silenceConsole(["warn", "error"], [/failed loading/i, /catalog down/]);
    let fail: (error: Error) => void = () => {};
    const region = changeWith(await i18nWithCatalog(() => new Promise((_, reject) => (fail = reject))));

    fail(new Error("catalog down"));
    await new Promise((resolve) => setTimeout(resolve, 20));

    expect(region).toBeEmptyDOMElement();
    restoreConsole();
  });

  test("announces in words once a delayed catalog arrives", async () => {
    let arrive: (catalog: typeof commonEn) => void = () => {};
    const region = changeWith(await i18nWithCatalog(() => new Promise((resolve) => (arrive = resolve))));
    expect(region).toBeEmptyDOMElement();

    arrive(commonEn);

    await waitFor(() => expect(region).toHaveTextContent("2 more fields are shown: Containment level and GMO permit"));
  });

  test("clears the message when fields hide, so revealing them again is announced again", () => {
    choose("Yes");
    choose("No");
    expect(document.querySelector('[role="status"]')).toHaveTextContent("");

    choose("Yes");
    expect(document.querySelector('[role="status"]')).toHaveTextContent(
      "2 more fields are shown: Containment level and GMO permit",
    );
  });

  test("hides fields with the hidden attribute, so they leave the accessibility tree", () => {
    expect(document.querySelector('[data-field-id="2"]')).not.toBeVisible();
    choose("Yes");
    expect(document.querySelector('[data-field-id="2"]')).toBeVisible();
  });
});
