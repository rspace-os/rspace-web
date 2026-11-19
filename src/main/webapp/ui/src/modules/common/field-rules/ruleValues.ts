import Decimal from "decimal.js";

/*
 * How display rules read text and number values, shared by the evaluator and
 * the validator. The Java `RuleValues` implements the same rules; both run the
 * shared vectors.
 */

/**
 * The characters trimmed from values: JavaScript's `\s` set, written out so
 * that Java and TypeScript trim identically.
 */
const WHITESPACE = new Set(
  "\t\n\v\f\r \u00A0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF",
);

/**
 * A number, as a field value after trimming or as a rule's value: everything
 * ELN and Inventory already store, such as `5`, `.5`, `5.`, `+5` and `1e-3`.
 * The fraction's digits follow only a point, so no run of digits can be split
 * two ways and rejection stays linear. The exponent has at most three digits
 * because `decimal.js` and Java's `BigDecimal` treat larger exponents
 * differently.
 */
const NUMBER = /^[+-]?([0-9]+(\.[0-9]*)?|\.[0-9]+)([eE][+-]?[0-9]{1,3})?$/;

/** The longest number read; the same as Java's `RuleValues.MAX_NUMBER_LENGTH`, so both sides agree. */
const MAX_NUMBER_LENGTH = 1000;

/** The value without leading or trailing whitespace from the shared set. */
export function trimRuleValue(value: string): string {
  let start = 0;
  let end = value.length;
  while (start < end && WHITESPACE.has(value[start])) start++;
  while (end > start && WHITESPACE.has(value[end - 1])) end--;
  return value.slice(start, end);
}

/** Whether the value is empty after trimming; a source with such a value has no value. */
export function isBlankRuleValue(value: string): boolean {
  return trimRuleValue(value) === "";
}

/**
 * Text as compared: trimmed, Unicode NFC so that composed and decomposed
 * accents match, then lower case, with final sigma read as sigma. Lower-casing
 * is not case folding, so `STRASSE` does not match `straße`.
 *
 * The browser uses its own Unicode tables, so for characters newer than its
 * Unicode version it can differ from the server, which pins Unicode with ICU4J.
 * The server decides on save.
 */
export function normaliseRuleText(value: string): string {
  // Lower-casing picks final sigma by context, which engines judge differently, so both sides read it as sigma.
  return trimRuleValue(value).normalize("NFC").toLowerCase().replaceAll("\u03C2", "\u03C3");
}

/** Whether a rule's value, as given (untrimmed), is a number operand. */
export function isRuleNumber(value: string): boolean {
  return value.length <= MAX_NUMBER_LENGTH && NUMBER.test(value);
}

/** A field's value as a number, or null when it is not a number after trimming. */
export function readRuleNumber(value: string): Decimal | null {
  const trimmed = trimRuleValue(value);
  return isRuleNumber(trimmed) ? new Decimal(trimmed) : null;
}
