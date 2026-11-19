/**
 * Applies form display rules to the fields of an ELN document rendered by the
 * legacy JSP and jQuery pages (document edit and view, notebook, public views).
 *
 * Each field element carries its definition as data attributes, written by
 * `include/fieldDisplayAttributes.jspf` and `JournalController`:
 *  - `data-field-type`: the `FieldType` name, such as `RADIO` or `CHOICE`;
 *    every field element has it;
 *  - `data-field-key`: the form field's key;
 *  - `data-display-rule`: its stored rule JSON, absent when it has none;
 *  - `data-field-id` and `data-field-name`: for callers and announcements;
 *  - `data-field-value`: stored radio data on the document page, and stored
 *    radio or choice data in the notebook view, which has no inputs. Legacy
 *    code that reloads a field's data updates it through `syncStoredValue`.
 */
import * as v from "valibot";
import { type FieldDisplayRule, parseDisplayRule } from "@/modules/common/field-rules/displayRule";
import { parseElnChoiceValues } from "@/modules/common/field-rules/elnChoiceValues";
import { displayRuleVisibility } from "@/modules/common/field-rules/evaluateDisplayRules";

/** Document field rows and notebook field wrappers. */
export const FIELD_SELECTOR = "tr.field[data-field-type], div.journalField[data-field-type]";

/** Set on a picklist once the user picks an option; cleared when its data is reloaded. */
const ANSWERED_ATTRIBUTE = "data-field-answered";

export type FieldDisplayResult = {
  /** Whether each field is shown, by `data-field-id`. */
  readonly shownById: ReadonlyMap<string, boolean>;
  /** Names of fields with a rule that were hidden and are now shown, in page order. */
  readonly revealedNames: ReadonlyArray<string>;
  /** Names of fields that were shown and are now hidden, in page order. */
  readonly concealedNames: ReadonlyArray<string>;
};

/** A stored rule that cannot be read is treated as no rule, as on the server, so it never hides data. */
function readRule(element: HTMLElement): FieldDisplayRule | null {
  const json = element.dataset.displayRule;
  if (json === undefined || json === "") return null;
  try {
    return parseDisplayRule(json);
  } catch (error) {
    if (error instanceof v.ValiError || error instanceof SyntaxError) return null;
    throw error;
  }
}

const asSelection = (value: string): Array<string> => (value === "" ? [] : [value]);

/**
 * Radio data as the server reads it: any non-empty stored value is selected,
 * even one that is no longer an option. The page's controls take over once
 * they say more: a checked radio, or a picklist the user has picked from (a
 * select always shows an option, even when the field has no data).
 */
function radioSelection(element: HTMLElement, storedValue: string): Array<string> {
  const checked = element.querySelector<HTMLInputElement>('input[type="radio"]:checked');
  if (checked) return [checked.value];
  const select = element.querySelector("select");
  if (select?.hasAttribute(ANSWERED_ATTRIBUTE)) return asSelection(select.value);
  return asSelection(storedValue);
}

function selectedOptions(element: HTMLElement): Array<string> {
  const storedValue = element.dataset.fieldValue ?? "";
  switch (element.dataset.fieldType) {
    case "RADIO":
      return radioSelection(element, storedValue);
    case "CHOICE": {
      const boxes = Array.from(element.querySelectorAll<HTMLInputElement>('input[type="checkbox"]'));
      if (boxes.length > 0) return boxes.filter((box) => box.checked).map((box) => box.value);
      return parseElnChoiceValues(storedValue);
    }
    default:
      return [];
  }
}

/**
 * Shows the fields whose rule holds and hides the rest, using the `hidden`
 * attribute so hidden fields leave the tab order and the accessibility tree.
 * Values are never touched, so a hidden field keeps its data.
 */
export function applyFieldDisplayRules(root: ParentNode): FieldDisplayResult {
  // Only the outermost fields: a field's rich text may carry the same class and attributes (the
  // sanitiser keeps both), and must never act as a field or a rule's source. The notebook view
  // balances each field's markup on the server, so a real field is never nested in another.
  const elements = Array.from(root.querySelectorAll<HTMLElement>(FIELD_SELECTOR)).filter(
    (element) => element.parentElement?.closest(FIELD_SELECTOR) == null,
  );
  const rules = elements.map(readRule);
  const visibility = displayRuleVisibility(
    elements.map((element, i) => {
      const type = element.dataset.fieldType;
      return {
        key: element.dataset.fieldKey || null,
        canBeSource: type === "RADIO" || type === "CHOICE",
        rule: rules[i],
        allowedOptions: [],
        selectedOptions: selectedOptions(element),
      };
    }),
  );

  const shownById = new Map<string, boolean>();
  const revealedNames: Array<string> = [];
  const concealedNames: Array<string> = [];
  elements.forEach((element, i) => {
    const shown = visibility[i];
    if (element.dataset.fieldId !== undefined) shownById.set(element.dataset.fieldId, shown);
    if (shown && element.hidden && rules[i] !== null) revealedNames.push(element.dataset.fieldName ?? "");
    if (!shown && !element.hidden) concealedNames.push(element.dataset.fieldName ?? "");
    element.hidden = !shown;
  });
  return { shownById, revealedNames, concealedNames };
}

/** Records that the user changed a field's control, so a picklist's value counts from now on. */
export function markAnswered(control: Element): void {
  if (control instanceof HTMLSelectElement) control.setAttribute(ANSWERED_ATTRIBUTE, "");
}

/**
 * Records radio data the page has just reloaded (from the server or an
 * autosave), which legacy code writes into the inputs without change events.
 * A picklist shows the value, or no option when it has none.
 */
export function syncStoredValue(element: HTMLElement, value: string): void {
  element.dataset.fieldValue = value;
  const select = element.querySelector("select");
  if (select) {
    select.removeAttribute(ANSWERED_ATTRIBUTE);
    select.value = value;
    if (select.value !== value) select.selectedIndex = -1;
  }
}

/**
 * Whether the mandatory fields that are shown all have data, given each
 * field's `mandatoryStateSatisfied` from the server. Fields the page does not
 * know are treated as shown.
 */
export function shownMandatoryFieldsSatisfied(
  fields: ReadonlyArray<{ id: number | string; mandatoryStateSatisfied: boolean }>,
  shownById: ReadonlyMap<string, boolean>,
): boolean {
  return fields.every(
    ({ id, mandatoryStateSatisfied }) => mandatoryStateSatisfied || shownById.get(String(id)) === false,
  );
}
