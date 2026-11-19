import { waitFor } from "@testing-library/react";
import { createInstance } from "i18next";
import ICU from "i18next-icu";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { createTestI18n } from "@/__tests__/helpers/createTestI18n";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { createDisplayRule, serializeDisplayRule } from "@/modules/common/field-rules/displayRule";
import commonEn from "@/modules/common/i18n/locales/en-US/common.json";
import { createLegacyFieldDisplay, type LegacyFieldDisplay, TYPING_PAUSE_MS } from "../legacyFieldDisplay";

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
      "2 more fields are shown: Containment level and GMO permit.",
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

  test("still announces reveals from a cached catalog that predates the hidden-fields message", async () => {
    const { fieldsHidden: _absent, ...olderFieldDisplay } = commonEn.fieldDisplay;
    const olderCatalog = { ...commonEn, fieldDisplay: olderFieldDisplay } as unknown as typeof commonEn;
    const showWhenNo = serializeDisplayRule(createDisplayRule(GMO_KEY, "IS_ANY_OF", ["No"]));
    document
      .querySelector("tbody")
      ?.insertAdjacentHTML(
        "beforeend",
        `<tr class="field" data-field-id="4" data-field-type="STRING" data-field-name="Export licence" data-display-rule='${showWhenNo}'><td><input type="text"></td></tr>`,
      );
    document.querySelector('[role="status"]')?.remove();
    const early = createLegacyFieldDisplay(await i18nWithCatalog(() => Promise.resolve(olderCatalog)));
    early.refresh();
    const region = document.querySelector('[role="status"]');
    for (const value of ["No", "Yes"]) {
      answer(value).checked = true;
      early.fieldChanged(answer(value));
    }

    // the change to Yes also hides "Export licence", which this catalog cannot say
    await waitFor(() => expect(region).toHaveTextContent("2 more fields are shown: Containment level and GMO permit."));
  });

  test("announces in words once a delayed catalog arrives", async () => {
    let arrive: (catalog: typeof commonEn) => void = () => {};
    const region = changeWith(await i18nWithCatalog(() => new Promise((resolve) => (arrive = resolve))));
    expect(region).toBeEmptyDOMElement();

    arrive(commonEn);

    await waitFor(() => expect(region).toHaveTextContent("2 more fields are shown: Containment level and GMO permit."));
  });

  test("announces the fields a change hides, and announces them again when shown again", () => {
    choose("Yes");
    choose("No");
    expect(document.querySelector('[role="status"]')).toHaveTextContent(
      "2 fields are hidden: Containment level and GMO permit.",
    );

    choose("Yes");
    expect(document.querySelector('[role="status"]')).toHaveTextContent(
      "2 more fields are shown: Containment level and GMO permit.",
    );
  });

  test("hides fields with the hidden attribute, so they leave the accessibility tree", () => {
    expect(document.querySelector('[data-field-id="2"]')).not.toBeVisible();
    choose("Yes");
    expect(document.querySelector('[data-field-id="2"]')).toBeVisible();
  });
});

describe("typing in a number source", () => {
  const DOSE_KEY = "6f1c2e0a-0000-4000-8000-000000000021";
  const showWhenOver5 = serializeDisplayRule(createDisplayRule(DOSE_KEY, "GREATER_THAN", ["5"]));
  const region = () => document.querySelector('[role="status"]');
  const dose = () => document.querySelector<HTMLInputElement>('[data-field-id="11"] input') as HTMLInputElement;
  const note = () => document.querySelector('[data-field-id="12"]');

  beforeEach(async () => {
    document.body.innerHTML = `
      <table><tbody>
        <tr class="field" data-field-id="11" data-field-type="NUMBER" data-field-source-kind="NUMBER"
            data-field-key="${DOSE_KEY}" data-field-name="Dose" data-field-value="7">
          <td><input name="fieldData" type="text" value="7"></td>
        </tr>
        <tr class="field" data-field-id="12" data-field-type="STRING" data-field-source-kind="TEXT"
            data-field-name="High dose reason" data-display-rule='${showWhenOver5}' data-field-value="">
          <td><input name="fieldData" type="text" value=""></td>
        </tr>
      </tbody></table>`;
    display = createLegacyFieldDisplay(await createTestI18n({ common: commonEn }, "common"));
    display.refresh();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function type(value: string) {
    dose().focus();
    dose().value = value;
    display.fieldChanged(dose());
  }

  test("applies once typing pauses, and announces the net change once", () => {
    type("3");
    vi.advanceTimersByTime(TYPING_PAUSE_MS - 1);
    expect(note()).toBeVisible();

    type("2");
    vi.advanceTimersByTime(TYPING_PAUSE_MS);

    expect(note()).not.toBeVisible();
    expect(region()).toHaveTextContent("One field is hidden: High dose reason.");
    expect(dose()).toHaveFocus();
  });

  test("announces nothing when the value is back where it started by the pause", () => {
    type("3");
    type("8");
    vi.advanceTimersByTime(TYPING_PAUSE_MS);

    expect(note()).toBeVisible();
    expect(region()).toBeEmptyDOMElement();
  });

  test("reports current visibility at once to a refresh, which cancels the pending update", () => {
    type("3");

    expect(display.refresh().shownById.get("12")).toBe(false);
    vi.advanceTimersByTime(TYPING_PAUSE_MS);
    expect(region()).toBeEmptyDOMElement();
  });

  test("keeps a field the user tabbed into shown until focus leaves it", () => {
    type("3");
    const reason = note()?.querySelector("input") as HTMLInputElement;
    reason.focus();
    vi.advanceTimersByTime(TYPING_PAUSE_MS);
    expect(note()).toBeVisible();
    expect(reason).toHaveFocus();

    dose().focus();
    vi.advanceTimersByTime(0);

    expect(note()).not.toBeVisible();
    expect(region()).toHaveTextContent("One field is hidden: High dose reason.");
  });
});
