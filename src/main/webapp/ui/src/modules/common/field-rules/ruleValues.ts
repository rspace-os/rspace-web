import Decimal from "decimal.js";

/*
 * How display rules read text and number values, shared by the evaluator and
 * the validator. The Java `RuleValues` implements the same rules; both run the
 * shared vectors.
 */

/**
 * The characters trimmed from values: JavaScript's `\s` set, written out so
 * that Java and TypeScript trim identically. `\v` is U+000B, which Java
 * writes as `\u000B` (its `\v` is a class of vertical whitespace).
 */
const WHITESPACE = /^[\t\n\v\f\r \u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF]$/;

/** A rule's number value: plain decimal notation only, as the editor writes it. */
const RULE_NUMBER = /^-?(0|[1-9][0-9]*)(\.[0-9]+)?$/;

/**
 * A field's number value, after trimming: everything ELN and Inventory already
 * store, such as `.5`, `5.`, `+5` and `1e-3`. The fraction's digits follow
 * only a point, so no run of digits can be split two ways and rejection stays
 * linear.
 */
const DATA_NUMBER = /^[+-]?([0-9]+(\.[0-9]*)?|\.[0-9]+)([eE][+-]?[0-9]{1,3})?$/;

/** The value without leading or trailing whitespace from the shared set. */
export function trimRuleValue(value: string): string {
  // A loop rather than an anchored regex, which would rescan a long inner run of whitespace.
  let start = 0;
  let end = value.length;
  while (start < end && WHITESPACE.test(value[start])) start++;
  while (end > start && WHITESPACE.test(value[end - 1])) end--;
  return value.slice(start, end);
}

/** Whether the value is empty after trimming; a source with such a value has no value. */
export function isBlankRuleValue(value: string): boolean {
  return trimRuleValue(value) === "";
}

/**
 * Text as compared: trimmed, Unicode NFC so that composed and decomposed
 * accents match, then lower case, with final sigma read as sigma. Lower-casing is not case folding, so
 * `STRASSE` does not match `straße`.
 */
export function normaliseRuleText(value: string): string {
  // Lower-casing picks final sigma by context, which Java judges differently, so both read it as sigma.
  return trimRuleValue(value).normalize("NFC").toLowerCase().replaceAll("\u03C2", "\u03C3");
}

/** Whether a rule's value is a valid number operand. */
export function isRuleNumber(value: string): boolean {
  return RULE_NUMBER.test(value);
}

/** The longest trimmed field value read as a number; the same as Java's `RuleValues.MAX_NUMBER_LENGTH`. */
const MAX_NUMBER_LENGTH = 1000;

/**
 * A field's value as a number, or null when it is blank, not a number, or
 * longer than `MAX_NUMBER_LENGTH` characters after trimming.
 */
export function readRuleNumber(value: string): Decimal | null {
  const trimmed = trimRuleValue(value);
  return trimmed.length <= MAX_NUMBER_LENGTH && DATA_NUMBER.test(trimmed) ? new Decimal(trimmed) : null;
}
