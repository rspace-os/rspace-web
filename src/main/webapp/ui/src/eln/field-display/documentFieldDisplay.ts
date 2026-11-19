/**
 * Applies form display rules to the fields of an ELN document rendered by the
 * legacy JSP and jQuery pages (document edit and view, notebook, public views).
 *
 * Each field element carries its definition as data attributes, written by
 * `include/fieldDisplayAttributes.jspf` and `JournalController`:
 *  - `data-field-type`: the `FieldType` name, such as `RADIO` or `CHOICE`;
 *    every field element has it;
 *  - `data-field-source-kind`: what rules read from the field (`OPTIONS`,
 *    `TEXT`, `NUMBER` or `NONE`; a password field is `NONE`). Without it, a
 *    radio or choice field is `OPTIONS` and any other field `NONE`;
 *  - `data-field-key`: the form field's key;
 *  - `data-display-rule`: its stored rule JSON, absent when it has none;
 *  - `data-field-id` and `data-field-name`: for callers and announcements;
 *  - `data-field-value`: the stored data of a field rules can read. Legacy
 *    code that reloads a field's data updates it through `syncStoredValue`.
 */
import * as v from "valibot";
import {
  type FieldDisplayRule,
  parseDisplayRule,
  type SourceKind,
  SourceKindSchema,
} from "@/modules/common/field-rules/displayRule";
import { parseElnChoiceValues } from "@/modules/common/field-rules/elnChoiceValues";
import { displayRuleVisibility } from "@/modules/common/field-rules/evaluateDisplayRules";

/** Document field rows and notebook field wrappers. */
export const FIELD_SELECTOR = "tr.field[data-field-type], div.journalField[data-field-type]";

/**
 * Set on a picklist, or a choice field's element, once the user changes it;
 * cleared when its data is reloaded.
 */
const ANSWERED_ATTRIBUTE = "data-field-answered";

/**
 * Set on a text or number field's element once the user types in it, so its
 * input counts from then on; cleared when its data is reloaded. Until then the
 * stored data counts, as it is what the server reads.
 */
const EDITED_ATTRIBUTE = "data-field-edited";

/** The input a text or number field is edited in; a password field's input is never read. */
const VALUE_INPUT_SELECTOR = 'input[name="fieldData"]:not([type="password"])';

export type FieldDisplayResult = {
  /** Whether each field's rule shows it, by `data-field-id`, whether or not it is still rendered. */
  readonly shownById: ReadonlyMap<string, boolean>;
  /** Names of fields with a rule that were hidden and are now shown, in page order. */
  readonly revealedNames: ReadonlyArray<string>;
  /** Names of fields that were shown and are now hidden, in page order. */
  readonly concealedNames: ReadonlyArray<string>;
  /** Fields left shown, although their rule hides them, because they contain focus. */
  readonly held: ReadonlyArray<HTMLElement>;
};

function sourceKind(element: HTMLElement): SourceKind {
  const kind = element.dataset.fieldSourceKind;
  if (v.is(SourceKindSchema, kind)) return kind;
  const type = element.dataset.fieldType;
  return type === "RADIO" || type === "CHOICE" ? "OPTIONS" : "NONE";
}

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
 * picklist rendered with data has no empty option, so it shows an option even
 * after its data is cleared).
 */
function radioSelection(element: HTMLElement, storedValue: string): Array<string> {
  const checked = element.querySelector<HTMLInputElement>('input[type="radio"]:checked');
  if (checked) return [checked.value];
  const select = element.querySelector("select");
  if (select?.hasAttribute(ANSWERED_ATTRIBUTE)) return asSelection(select.value);
  return asSelection(storedValue);
}

/**
 * Choice data as the server reads it, stored values that are not options
 * included, until the user changes a checkbox: from then on the checked boxes
 * are what autosave will store.
 */
function choiceSelection(element: HTMLElement, storedValue: string | undefined): Array<string> {
  const boxes = Array.from(element.querySelectorAll<HTMLInputElement>('input[type="checkbox"]'));
  if (boxes.length > 0 && (storedValue === undefined || element.hasAttribute(ANSWERED_ATTRIBUTE))) {
    return boxes.filter((box) => box.checked).map((box) => box.value);
  }
  return parseElnChoiceValues(storedValue ?? "");
}

function selectedOptions(element: HTMLElement): Array<string> {
  if (sourceKind(element) !== "OPTIONS") return [];
  const storedValue = element.dataset.fieldValue;
  switch (element.dataset.fieldType) {
    case "RADIO":
      return radioSelection(element, storedValue ?? "");
    case "CHOICE":
      return choiceSelection(element, storedValue);
    default:
      return [];
  }
}

/** A text or number field's data: what the user typed once they have, otherwise the stored data. */
function scalarValue(element: HTMLElement): string {
  const input = element.hasAttribute(EDITED_ATTRIBUTE)
    ? element.querySelector<HTMLInputElement>(VALUE_INPUT_SELECTOR)
    : null;
  return input?.value ?? element.dataset.fieldValue ?? "";
}

/**
 * Shows the fields whose rule holds and hides the rest, using the `hidden`
 * attribute so hidden fields leave the tab order and the accessibility tree.
 * Values are never touched, so a hidden field keeps its data. A shown field
 * that contains `focused` stays shown until it no longer does, so focus never
 * moves on its own; `shownById` still reports it as hidden.
 */
export function applyFieldDisplayRules(
  root: ParentNode,
  { focused = null }: { focused?: Element | null } = {},
): FieldDisplayResult {
  // Only the outermost fields: a field's rich text may carry the same class and attributes (the
  // sanitiser keeps both), and must never act as a field or a rule's source. The notebook view
  // balances each field's markup on the server, so a real field is never nested in another.
  const elements = Array.from(root.querySelectorAll<HTMLElement>(FIELD_SELECTOR)).filter(
    (element) => element.parentElement?.closest(FIELD_SELECTOR) == null,
  );
  const rules = elements.map(readRule);
  const visibility = displayRuleVisibility(
    elements.map((element, i) => {
      const kind = sourceKind(element);
      return {
        key: element.dataset.fieldKey || null,
        canBeSource: kind !== "NONE",
        sourceKind: kind,
        value: kind === "TEXT" || kind === "NUMBER" ? scalarValue(element) : "",
        rule: rules[i],
        allowedOptions: [],
        selectedOptions: selectedOptions(element),
      };
    }),
  );

  const shownById = new Map<string, boolean>();
  const revealedNames: Array<string> = [];
  const concealedNames: Array<string> = [];
  const held: Array<HTMLElement> = [];
  elements.forEach((element, i) => {
    const shown = visibility[i];
    if (element.dataset.fieldId !== undefined) shownById.set(element.dataset.fieldId, shown);
    if (!shown && !element.hidden && focused !== null && element.contains(focused)) {
      held.push(element);
      return;
    }
    if (shown && element.hidden && rules[i] !== null) revealedNames.push(element.dataset.fieldName ?? "");
    if (!shown && !element.hidden) concealedNames.push(element.dataset.fieldName ?? "");
    element.hidden = !shown;
  });
  return { shownById, revealedNames, concealedNames, held };
}

/**
 * Records that the user changed a field's control, so a picklist's value, a
 * choice field's boxes or a text or number field's input count from now on.
 *
 * @returns `"typed"` for a text or number field's input, which callers may
 *   apply after typing pauses; `"picked"` for an option control; null for
 *   anything rules do not read, such as a password field.
 */
export function markAnswered(control: Element): "typed" | "picked" | null {
  if (control instanceof HTMLSelectElement) {
    control.setAttribute(ANSWERED_ATTRIBUTE, "");
    return "picked";
  }
  if (!(control instanceof HTMLInputElement)) return null;
  if (control.type === "radio") return "picked";
  const field = control.closest<HTMLElement>(FIELD_SELECTOR);
  if (field === null) return null;
  if (control.type === "checkbox") {
    field.setAttribute(ANSWERED_ATTRIBUTE, "");
    return "picked";
  }
  const kind = sourceKind(field);
  if ((kind === "TEXT" || kind === "NUMBER") && control.matches(VALUE_INPUT_SELECTOR)) {
    field.setAttribute(EDITED_ATTRIBUTE, "");
    return "typed";
  }
  return null;
}

/**
 * Records data the page has just reloaded (from the server or an autosave),
 * which legacy code writes into the inputs without change or input events. A
 * picklist shows the value, or no option when it has none. Fields that rules
 * cannot read, password fields among them, are left alone.
 */
export function syncStoredValue(element: HTMLElement, value: string): void {
  if (sourceKind(element) === "NONE") return;
  element.dataset.fieldValue = value;
  element.removeAttribute(ANSWERED_ATTRIBUTE);
  element.removeAttribute(EDITED_ATTRIBUTE);
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
