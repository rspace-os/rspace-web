import { describe, expect, test } from "vitest";
import { createDisplayRule, serializeDisplayRule } from "@/modules/common/field-rules/displayRule";
import { applyFieldDisplayRules, markAnswered, shownMandatoryFieldsSatisfied } from "../documentFieldDisplay";

const GMO_KEY = "6f1c2e0a-0000-4000-8000-000000000001";
const LEVEL_KEY = "6f1c2e0a-0000-4000-8000-000000000002";

const showWhenYes = serializeDisplayRule(createDisplayRule(GMO_KEY, "IS_ANY_OF", ["Yes"]));
const showWhenCl3 = serializeDisplayRule(createDisplayRule(LEVEL_KEY, "IS_ANY_OF", ["CL3"]));
const showWhenNo = serializeDisplayRule(createDisplayRule(GMO_KEY, "IS_ANY_OF", ["No"]));

/** Builds a row the way the JSP does: attributes are set through the DOM, so values are escaped. */
function row(attributes: Record<string, string>, inner = ""): HTMLElement {
  const element = document.createElement("div");
  for (const [name, value] of Object.entries(attributes)) element.setAttribute(name, value);
  element.innerHTML = inner;
  return element;
}

function find<E extends Element>(root: ParentNode, selector: string): E {
  const element = root.querySelector<E>(selector);
  if (element === null) throw new Error(`No element matches ${selector}`);
  return element;
}

function page(...rows: Array<HTMLElement>): HTMLElement {
  const root = document.createElement("div");
  root.append(...rows);
  return root;
}

const radio = (id: string, options: Array<string>, checked?: string) =>
  options
    .map((option) => `<input type="radio" name="radio_${id}" value="${option}"${option === checked ? " checked" : ""}>`)
    .join("");

function gmoPage(checked?: string) {
  const gmo = row(
    { "data-field-id": "1", "data-field-type": "RADIO", "data-field-key": GMO_KEY, "data-field-name": "GMO?" },
    radio("1", ["Yes", "No"], checked),
  );
  const level = row(
    {
      "data-field-id": "2",
      "data-field-type": "RADIO",
      "data-field-key": LEVEL_KEY,
      "data-field-name": "Containment level",
      "data-display-rule": showWhenYes,
    },
    radio("2", ["CL2", "CL3"], "CL3"),
  );
  const approval = row(
    {
      "data-field-id": "3",
      "data-field-type": "STRING",
      "data-field-key": "6f1c2e0a-0000-4000-8000-000000000003",
      "data-field-name": "CL3 approval",
      "data-display-rule": showWhenCl3,
    },
    `<input type="text" value="kept">`,
  );
  return { root: page(gmo, level, approval), gmo, level, approval };
}

describe("applyFieldDisplayRules", () => {
  test("hides a chain whose source has no value, and keeps the hidden fields' values", () => {
    const { root, level, approval } = gmoPage();

    const result = applyFieldDisplayRules(root);

    expect(level).toHaveAttribute("hidden");
    expect(approval).toHaveAttribute("hidden");
    expect(approval.querySelector("input")).toHaveValue("kept");
    expect(result.shownById).toEqual(
      new Map([
        ["1", true],
        ["2", false],
        ["3", false],
      ]),
    );
  });

  test("shows dependents when the source's selected radio matches, and reports them as revealed", () => {
    const { root, gmo, level, approval } = gmoPage();
    applyFieldDisplayRules(root);

    find<HTMLInputElement>(gmo, 'input[value="Yes"]').checked = true;
    const result = applyFieldDisplayRules(root);

    expect(level).not.toHaveAttribute("hidden");
    expect(approval).not.toHaveAttribute("hidden");
    expect(result.revealedNames).toEqual(["Containment level", "CL3 approval"]);
    expect(result.anyNewlyHidden).toBe(false);
  });

  test("reports newly hidden fields", () => {
    const { root, gmo } = gmoPage("Yes");
    applyFieldDisplayRules(root);

    find<HTMLInputElement>(gmo, 'input[value="No"]').checked = true;
    expect(applyFieldDisplayRules(root).anyNewlyHidden).toBe(true);
  });

  test("reads every checked box of a choice field", () => {
    const choice = row(
      { "data-field-id": "1", "data-field-type": "CHOICE", "data-field-key": GMO_KEY },
      `<input type="checkbox" value="No" checked><input type="checkbox" value="Yes" checked>`,
    );
    const dependent = row({ "data-field-id": "2", "data-field-type": "STRING", "data-display-rule": showWhenYes });

    applyFieldDisplayRules(page(choice, dependent));

    expect(dependent).not.toHaveAttribute("hidden");
  });

  test("counts a picklist's displayed option only once it was stored or picked", () => {
    const pickList = row(
      { "data-field-id": "1", "data-field-type": "RADIO", "data-field-key": GMO_KEY },
      `<select><option value="Yes">Yes</option><option value="No">No</option></select>`,
    );
    const dependent = row({ "data-field-id": "2", "data-field-type": "STRING", "data-display-rule": showWhenYes });
    const root = page(pickList, dependent);

    applyFieldDisplayRules(root);
    expect(dependent).toHaveAttribute("hidden");

    markAnswered(find(pickList, "select"));
    applyFieldDisplayRules(root);
    expect(dependent).not.toHaveAttribute("hidden");
  });

  test("counts a picklist option the server rendered as selected", () => {
    const pickList = row(
      { "data-field-id": "1", "data-field-type": "RADIO", "data-field-key": GMO_KEY },
      `<select><option value="No">No</option><option value="Yes" selected>Yes</option></select>`,
    );
    const dependent = row({ "data-field-id": "2", "data-field-type": "STRING", "data-display-rule": showWhenYes });

    applyFieldDisplayRules(page(pickList, dependent));

    expect(dependent).not.toHaveAttribute("hidden");
  });

  test("reads stored choice data, as the server does, where a field has no inputs", () => {
    const rule = serializeDisplayRule(createDisplayRule(GMO_KEY, "IS_ANY_OF", ["b=c"]));
    const choice = row({
      "data-field-id": "1",
      "data-field-type": "CHOICE",
      "data-field-key": GMO_KEY,
      // The server shows the text between the first and second "=" of each segment.
      "data-field-value": "fieldChoices=a&fieldChoices=b=c",
    });
    const dependent = row({ "data-field-id": "2", "data-field-type": "TEXT", "data-display-rule": rule });

    applyFieldDisplayRules(page(choice, dependent));

    expect(dependent).toHaveAttribute("hidden");
  });

  test("reads stored radio data where a field has no inputs", () => {
    const source = row({
      "data-field-id": "1",
      "data-field-type": "RADIO",
      "data-field-key": GMO_KEY,
      "data-field-value": "Yes",
    });
    const dependent = row({ "data-field-id": "2", "data-field-type": "TEXT", "data-display-rule": showWhenYes });

    applyFieldDisplayRules(page(source, dependent));

    expect(dependent).not.toHaveAttribute("hidden");
  });

  test("shows a field whose stored rule cannot be read", () => {
    const field = row({
      "data-field-id": "1",
      "data-field-type": "STRING",
      "data-display-rule": '{"operator":"NOPE"}',
    });

    applyFieldDisplayRules(page(field));

    expect(field).not.toHaveAttribute("hidden");
  });

  test("ignores elements inside a field's rich text that look like fields", () => {
    const source = row(
      { "data-field-id": "1", "data-field-type": "RADIO", "data-field-key": GMO_KEY },
      radio("1", ["Yes", "No"], "Yes"),
    );
    const text = row(
      { "data-field-id": "2", "data-field-type": "TEXT" },
      `<p data-field-type="RADIO" data-field-key="${GMO_KEY}" data-display-rule='${showWhenNo}'>notes</p>`,
    );
    const dependent = row({ "data-field-id": "3", "data-field-type": "STRING", "data-display-rule": showWhenYes });

    const result = applyFieldDisplayRules(page(source, text, dependent));

    expect(text.querySelector("p")).not.toHaveAttribute("hidden");
    expect(dependent).not.toHaveAttribute("hidden");
    expect([...result.shownById.keys()]).toEqual(["1", "2", "3"]);
  });
});

describe("shownMandatoryFieldsSatisfied", () => {
  const shownById = new Map([
    ["1", true],
    ["2", false],
  ]);

  test("ignores hidden mandatory fields", () => {
    expect(
      shownMandatoryFieldsSatisfied(
        [
          { id: 1, mandatoryStateSatisfied: true },
          { id: 2, mandatoryStateSatisfied: false },
        ],
        shownById,
      ),
    ).toBe(true);
  });

  test("requires shown fields and fields the page does not know", () => {
    expect(shownMandatoryFieldsSatisfied([{ id: 1, mandatoryStateSatisfied: false }], shownById)).toBe(false);
    expect(shownMandatoryFieldsSatisfied([{ id: 9, mandatoryStateSatisfied: false }], shownById)).toBe(false);
  });
});
