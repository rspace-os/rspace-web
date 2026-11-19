package com.researchspace.model.field.rule;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.Normalizer2;
import com.ibm.icu.util.ULocale;
import java.math.BigDecimal;
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
  private static final String WHITESPACE =
      "\t\n\u000B\f\r \u00A0\u1680"
          + "\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A"
          + "\u2028\u2029\u202F\u205F\u3000\uFEFF";

  /**
   * A number, as a field value after trimming or as a rule's value: everything ELN and Inventory
   * already store, such as {@code 5}, {@code .5}, {@code 5.}, {@code +5} and {@code 1e-3}. The
   * fraction's digits follow only a point, so no run of digits can be split two ways and rejection
   * stays linear. The exponent has at most three digits because {@code BigDecimal} and {@code
   * decimal.js} treat larger exponents differently.
   */
  private static final Pattern NUMBER =
      Pattern.compile("[+-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([eE][+-]?[0-9]{1,3})?");

  /**
   * The longest number read. Far beyond any real measurement; it keeps parsing cheap ({@code
   * BigDecimal} takes seconds on a million digits) and bounds what Java and TypeScript must agree
   * on.
   */
  private static final int MAX_NUMBER_LENGTH = 1000;

  private static final Normalizer2 NFC = Normalizer2.getNFCInstance();

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
    return WHITESPACE.indexOf(c) >= 0;
  }

  /** Whether the value is empty after trimming; a source with such a value has no value. */
  static boolean isBlank(String value) {
    return trim(value).isEmpty();
  }

  /**
   * Text as compared: trimmed, Unicode NFC so that composed and decomposed accents match, then
   * lower case, with final sigma read as sigma. Lower-casing is not case folding, so {@code
   * STRASSE} does not match {@code straße}.
   *
   * <p>ICU4J does both steps, so the result depends on the ICU4J version's Unicode tables, not the
   * JDK's: Java 17 and 21 compare alike. Lower-casing still picks final sigma from the surrounding
   * letters ({@code ΟΔΟΣ} becomes {@code οδος}), and that context depends on each engine's rules
   * and Unicode tables (the JDK lowers {@code A1Σ} to {@code a1ς}, ICU and V8 to {@code a1σ}), so
   * both sides read final sigma as sigma.
   */
  static String normaliseText(String value) {
    return UCharacter.toLowerCase(ULocale.ROOT, NFC.normalize(trim(value)))
        .replace('\u03C2', '\u03C3');
  }

  /** A field's value as a number, or null when it is not a {@link #NUMBER} after trimming. */
  static BigDecimal readNumber(String value) {
    String trimmed = trim(value);
    return isNumber(trimmed) ? new BigDecimal(trimmed) : null;
  }

  /** Whether the value, as given, is a {@link #NUMBER}; rule values are checked untrimmed. */
  static boolean isNumber(String value) {
    return value.length() <= MAX_NUMBER_LENGTH && NUMBER.matcher(value).matches();
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
    if (kind == SourceKind.NUMBER && !isNumber(value)) {
      return FieldDisplayRuleValidator.VALUE_NOT_A_NUMBER;
    }
    return null;
  }
}
