package com.researchspace.model.field.rule;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * How display rules read text and number values, shared by {@link FieldDisplayRuleEvaluator} and
 * {@link FieldDisplayRuleValidator}. The TypeScript {@code ruleValues.ts} implements the same
 * rules; both run the shared vectors.
 */
final class RuleValues {

  /**
   * The characters trimmed from values: JavaScript's {@code \s} set, written out so that Java and
   * TypeScript trim identically. {@code String.trim()} and {@code strip()} each use another set.
   */
  private static final Pattern WHITESPACE =
      Pattern.compile(
          "[\\t\\n"
              + "\\u000B\\f\\r"
              + " \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]");

  /** A rule's number value: plain decimal notation only, as the editor writes it. */
  private static final Pattern RULE_NUMBER = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?");

  /**
   * A field's number value, after trimming: everything ELN and Inventory already store, such as
   * {@code .5}, {@code 5.}, {@code +5} and {@code 1e-3}.
   */
  private static final Pattern DATA_NUMBER =
      Pattern.compile("[+-]?([0-9]+\\.?[0-9]*|\\.[0-9]+)([eE][+-]?[0-9]{1,3})?");

  private RuleValues() {}

  /** The value without leading or trailing {@link #WHITESPACE}. */
  static String trim(String value) {
    int start = 0;
    int end = value.length();
    while (start < end && isWhitespace(value.charAt(start))) {
      start++;
    }
    while (end > start && isWhitespace(value.charAt(end - 1))) {
      end--;
    }
    return value.substring(start, end);
  }

  private static boolean isWhitespace(char c) {
    return WHITESPACE.matcher(String.valueOf(c)).matches();
  }

  /** Whether the value is empty after trimming; a source with such a value has no value. */
  static boolean isBlank(String value) {
    return trim(value).isEmpty();
  }

  /**
   * Text as compared: trimmed, Unicode NFC so that composed and decomposed accents match, then
   * lower case. Lower-casing is not case folding, so {@code STRASSE} does not match {@code straße}.
   */
  static String normaliseText(String value) {
    return Normalizer.normalize(trim(value), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
  }

  /** Whether a rule's value is a valid number operand. */
  static boolean isRuleNumber(String value) {
    return RULE_NUMBER.matcher(value).matches();
  }

  /** A field's value as a number, or null when it is blank or not a number. */
  static BigDecimal readNumber(String value) {
    String trimmed = trim(value);
    return DATA_NUMBER.matcher(trimmed).matches() ? new BigDecimal(trimmed) : null;
  }

  /**
   * Why a rule cannot be applied to a source of this kind, as a {@link FieldDisplayRuleValidator}
   * code, or null when it can. The validator reports the code; the evaluator ignores such a rule,
   * so the field is shown.
   */
  static String problem(FieldDisplayRule rule, SourceKind kind) {
    ConditionOperator operator = rule.operator();
    if (kind == SourceKind.NONE) {
      return FieldDisplayRuleValidator.SOURCE_TYPE;
    }
    if (!operator.appliesTo(kind)) {
      return FieldDisplayRuleValidator.OPERATOR_NOT_FOR_SOURCE;
    }
    if (!operator.comparesValue()) {
      return null;
    }
    if (rule.values().size() != 1) {
      return FieldDisplayRuleValidator.VALUE_COUNT;
    }
    String value = rule.values().get(0);
    if (isBlank(value)) {
      return FieldDisplayRuleValidator.VALUE_BLANK;
    }
    if (kind == SourceKind.NUMBER && !isRuleNumber(value)) {
      return FieldDisplayRuleValidator.VALUE_NOT_A_NUMBER;
    }
    return null;
  }
}
