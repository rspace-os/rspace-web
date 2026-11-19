import Decimal from "decimal.js";

/*
 * How display rules read text and number values, shared by the evaluator and
 * the validator. The Java `RuleValues` implements the same rules; both run the
 * shared vectors.
 */

/** The longest number read, checked before parsing; the same as Java's `RuleValues.MAX_NUMBER_LENGTH`. */
const MAX_NUMBER_LENGTH = 1000;

/**
 * The characters a number may contain. `decimal.js` also reads `Infinity`,
 * `NaN`, `0x`, `0b` and `0o` prefixes and `_` separators, and Java's
 * `BigDecimal` reads non-ASCII digits; this list keeps both to the forms the
 * other accepts.
 */
const NUMBER_CHARACTERS = "0123456789+-.eE";

/**
 * The most digits in a written exponent. Beyond it the libraries fail
 * differently (`decimal.js` reads very large negative exponents as zero,
 * `BigDecimal` rejects exponents outside an `int`), so both reject it first.
 */
const MAX_EXPONENT_DIGITS = 9;

/**
 * The largest power of ten, either way, of a non-zero number's leading digit.
 * A size bound, not an agreement check: both libraries read such numbers
 * alike, but it keeps the plain form of any number accepted to a few thousand
 * characters. It covers everything a 1000-character value with a three-digit
 * exponent can write.
 */
const MAX_MAGNITUDE = 2000;

/** The value without leading or trailing whitespace. */
export function trimRuleValue(value: string): string {
  // Trimming uses the native set, which the Java side mirrors.
  return value.trim();
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
  return trimRuleValue(value).normalize("NFC").toLowerCase().replaceAll("ς", "σ");
}

/** Whether a rule's value, as given (untrimmed), is a number operand. */
export function isRuleNumber(value: string): boolean {
  return parseNumber(value) !== null;
}

/** A field's value as a number, or null when it is not a number after trimming. */
export function readRuleNumber(value: string): Decimal | null {
  return parseNumber(trimRuleValue(value));
}

/**
 * A number in canonical form: trimmed, in plain decimal notation without
 * trailing zeros, so `1.50` becomes `1.5`, `+5` becomes `5`, `1e3` becomes
 * `1000` and `-0` becomes `0`. When the plain form would be longer than
 * `MAX_NUMBER_LENGTH`, the trimmed value is returned as written (`1e1000`
 * stays `1e1000`), so the result is always a number as written and canonical
 * again. Null when the value is not a number. Java's
 * `RuleValues.canonicalNumber` returns the same strings.
 */
export function canonicalRuleNumber(value: string): string | null {
  const trimmed = trimRuleValue(value);
  const plain = parseNumber(trimmed)?.toFixed();
  if (plain === undefined) return null;
  return plain.length > MAX_NUMBER_LENGTH ? trimmed : plain;
}

/**
 * The value as a number, or null when `decimal.js` cannot read it, it fails
 * the checks that keep `decimal.js` and `BigDecimal` in agreement, or it is
 * outside the size bound.
 */
function parseNumber(value: string): Decimal | null {
  if (value.length > MAX_NUMBER_LENGTH || ![...value].every((c) => NUMBER_CHARACTERS.includes(c))) return null;
  if (exponentDigits(value) > MAX_EXPONENT_DIGITS) return null;
  let number: Decimal;
  try {
    number = new Decimal(value);
  } catch {
    return null;
  }
  // A size bound on the plain form, not an agreement check.
  return number.isZero() || Math.abs(number.e) <= MAX_MAGNITUDE ? number : null;
}

/**
 * The characters after the later of the first `e` and the first `E`, less a
 * sign; 0 when there is neither. A value with more than one marker is not a
 * number either way, so which one is measured only decides whether it is
 * rejected here or by the parser.
 */
function exponentDigits(value: string): number {
  const marker = Math.max(value.indexOf("e"), value.indexOf("E"));
  if (marker < 0) return 0;
  const digits = value.length - marker - 1;
  const sign = value[marker + 1];
  return digits > 0 && (sign === "+" || sign === "-") ? digits - 1 : digits;
}
