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
 *  - `data-field-value`: stored radio or choice data, used only where the
 *    element has no inputs (the notebook view renders plain text).
 */
import * as v from "valibot";
import { type FieldDisplayRule, parseDisplayRule } from "@/modules/common/field-rules/displayRule";
import { parseElnChoiceValues } from "@/modules/common/field-rules/elnChoiceValues";
import { displayRuleVisibility } from "@/modules/common/field-rules/evaluateDisplayRules";

export const FIELD_SELECTOR = "[data-field-type]";

/** Set on a picklist once the user picks an option, see `pickListSelection`. */
const ANSWERED_ATTRIBUTE = "data-field-answered";

export type FieldDisplayResult = {
  /** Whether each field is shown, by `data-field-id`. */
  readonly shownById: ReadonlyMap<string, boolean>;
  /** Names of fields with a rule that were hidden and are now shown, in page order. */
  readonly revealedNames: ReadonlyArray<string>;
  /** Whether any field was shown and is now hidden. */
  readonly anyNewlyHidden: boolean;
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

/**
 * A select always shows an option, even when the field has no data, so its
 * value counts only if the server rendered an option as selected or the user
 * has picked one. Otherwise the page would disagree with the stored data, which
 * the server uses for the Sign button.
 */
function pickListSelection(select: HTMLSelectElement): Array<string> {
  const answered =
    select.hasAttribute(ANSWERED_ATTRIBUTE) || Array.from(select.options).some((option) => option.defaultSelected);
  return answered && select.value !== "" ? [select.value] : [];
}

function selectedOptions(element: HTMLElement): Array<string> {
  const storedValue = element.dataset.fieldValue ?? "";
  switch (element.dataset.fieldType) {
    case "RADIO": {
      const radios = Array.from(element.querySelectorAll<HTMLInputElement>('input[type="radio"]'));
      if (radios.length > 0) return radios.filter((radio) => radio.checked).map((radio) => radio.value);
      const select = element.querySelector("select");
      if (select) return pickListSelection(select);
      return storedValue === "" ? [] : [storedValue];
    }
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
  // Rich text inside a field is the author's content, not a field, whatever attributes it has.
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
  let anyNewlyHidden = false;
  elements.forEach((element, i) => {
    const shown = visibility[i];
    if (element.dataset.fieldId !== undefined) shownById.set(element.dataset.fieldId, shown);
    if (shown && element.hidden && rules[i] !== null) revealedNames.push(element.dataset.fieldName ?? "");
    if (!shown && !element.hidden) anyNewlyHidden = true;
    element.hidden = !shown;
  });
  return { shownById, revealedNames, anyNewlyHidden };
}

/** Records that the user changed a field's control, so a picklist's value counts from now on. */
export function markAnswered(control: Element): void {
  if (control instanceof HTMLSelectElement) control.setAttribute(ANSWERED_ATTRIBUTE, "");
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
