package com.researchspace.model.field.rule;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.Normalizer2;
import com.ibm.icu.util.ULocale;
import java.math.BigDecimal;

/**
 * How display rules read text and number values, shared by {@link FieldDisplayRuleEvaluator} and
 * {@link FieldDisplayRuleValidator}. The TypeScript {@code ruleValues.ts} implements the same
 * rules; both run the shared vectors.
 */
public final class RuleValues {

  /**
   * The characters trimmed from values. Trimming makes a whitespace-only value count as no value,
   * keeps a stray space from deciding "is" or "contains", and lets {@code " 5"} read as a number.
   *
   * <p>The browser trims with JavaScript's {@code trim()}, and the server must trim exactly the
   * same characters, because both decide visibility, mandatory checks and Sign. No Java library
   * does:
   *
   * <ul>
   *   <li>{@code String.trim()} removes only characters up to U+0020, control characters included,
   *       and keeps U+00A0 (no-break space), U+FEFF and the other Unicode space separators such as
   *       U+3000.
   *   <li>{@code String.strip()} and Commons {@code StringUtils.strip} use {@code
   *       Character.isWhitespace}, which excludes U+00A0, U+2007 and U+202F and includes U+001C to
   *       U+001F.
   *   <li>Guava {@code CharMatcher.whitespace()} adds U+0085 and misses U+FEFF.
   * </ul>
   *
   * <p>A no-break space pasted from Word or Excel before {@code 5} would be the number 5 in the
   * browser and not a number on the server. So this list is JavaScript's set (ECMAScript WhiteSpace
   * and LineTerminator), written out and pinned by the shared vectors.
   */
  private static final String WHITESPACE =
      "\t\n\u000B\f\r \u00A0\u1680"
          + "\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A"
          + "\u2028\u2029\u202F\u205F\u3000\uFEFF";

  /**
   * The longest number read, checked before parsing. Far beyond any real measurement; it keeps
   * parsing cheap ({@code BigDecimal} takes seconds on a million digits).
   */
  private static final int MAX_NUMBER_LENGTH = 1000;

  /**
   * The characters a number may contain. {@code BigDecimal} also reads non-ASCII digits such as
   * {@code ٥}, and {@code decimal.js} also reads {@code Infinity}, {@code NaN}, {@code 0x}, {@code
   * 0b} and {@code 0o} prefixes and {@code _} separators; this list keeps both to the forms the
   * other accepts.
   */
  private static final String NUMBER_CHARACTERS = "0123456789+-.eE";

  /**
   * The most digits in a written exponent. Beyond it the libraries fail differently ({@code
   * BigDecimal} rejects exponents outside an {@code int}, {@code decimal.js} reads very large
   * negative ones as zero), so both reject it first.
   */
  private static final int MAX_EXPONENT_DIGITS = 9;

  /**
   * The largest power of ten, either way, of a non-zero number's leading digit. A size bound, not
   * an agreement check: both libraries read such numbers alike, but it keeps the plain form of any
   * number accepted to a few thousand characters. It covers everything a 1000-character value with
   * a three-digit exponent can write.
   */
  private static final int MAX_MAGNITUDE = 2000;

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

  /** Whether the value, as given, is a number; rule values are checked untrimmed. */
  static boolean isNumber(String value) {
    return parseNumber(value) != null;
  }

  /** A field's value as a number, or null when it is not a number after trimming. */
  static BigDecimal readNumber(String value) {
    return parseNumber(trim(value));
  }

  /**
   * A number in canonical form: trimmed, in plain decimal notation without trailing zeros, so
   * {@code 1.50} becomes {@code 1.5}, {@code +5} becomes {@code 5}, {@code 1e3} becomes {@code
   * 1000} and {@code -0} becomes {@code 0}. When the plain form would be longer than {@link
   * #MAX_NUMBER_LENGTH}, the trimmed value is returned as written ({@code 1e1000} stays {@code
   * 1e1000}), so the result is always a number as written and canonical again. Null when the value
   * is not a number. The TypeScript {@code canonicalRuleNumber} returns the same strings.
   */
  public static String canonicalNumber(String value) {
    String trimmed = trim(value);
    BigDecimal number = parseNumber(trimmed);
    if (number == null) {
      return null;
    }
    String plain = number.stripTrailingZeros().toPlainString();
    return plain.length() > MAX_NUMBER_LENGTH ? trimmed : plain;
  }

  /**
   * The value as a number, or null when {@code BigDecimal} cannot read it, it fails the checks that
   * keep {@code BigDecimal} and {@code decimal.js} in agreement, or it is outside the size bound.
   */
  private static BigDecimal parseNumber(String value) {
    if (value.length() > MAX_NUMBER_LENGTH || !onlyNumberCharacters(value)) {
      return null;
    }
    if (exponentDigits(value) > MAX_EXPONENT_DIGITS) {
      return null;
    }
    BigDecimal number;
    try {
      number = new BigDecimal(value);
    } catch (NumberFormatException e) {
      // Not a number is an expected answer for user data, so it is not logged.
      return null;
    }
    // A size bound on the plain form, not an agreement check.
    boolean inRange =
        number.signum() == 0 || Math.abs(number.precision() - number.scale() - 1) <= MAX_MAGNITUDE;
    return inRange ? number : null;
  }

  private static boolean onlyNumberCharacters(String value) {
    for (int i = 0; i < value.length(); i++) {
      if (NUMBER_CHARACTERS.indexOf(value.charAt(i)) < 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * The characters after the later of the first {@code e} and the first {@code E}, less a sign; 0
   * when there is neither. A value with more than one marker is not a number either way, so which
   * one is measured only decides whether it is rejected here or by the parser.
   */
  private static int exponentDigits(String value) {
    int marker = Math.max(value.indexOf('e'), value.indexOf('E'));
    if (marker < 0) {
      return 0;
    }
    int digits = value.length() - marker - 1;
    boolean signed =
        digits > 0 && (value.charAt(marker + 1) == '+' || value.charAt(marker + 1) == '-');
    return signed ? digits - 1 : digits;
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
