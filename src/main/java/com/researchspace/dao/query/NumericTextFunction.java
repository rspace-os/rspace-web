package com.researchspace.dao.query;

import com.blazebit.persistence.spi.FunctionRenderContext;
import com.blazebit.persistence.spi.JpqlFunction;
import com.researchspace.model.collection.RuntimeFieldValueType;
import java.math.BigDecimal;

/**
 * Compares a text column as a number, for a value store whose column is a string.
 *
 * <p>A runtime field keeps every declared type in one text column, so a numeric comparison has to
 * convert. Blaze's own {@code CAST_DOUBLE} cannot be used: its MySQL/MariaDB dialect maps no cast
 * type for {@code Double}, so it falls back to the ANSI name and emits {@code cast(x as double
 * precision)}, which MariaDB rejects outright. This renders the spelling MariaDB accepts.
 *
 * <p>The conversion checks the normalized bounds enforced by {@code RuntimeFieldValueType} before
 * casting. MariaDB rounds values with more than 30 fractional digits, so casting without that check
 * would make an unprojectable value such as {@code 1e-31} compare equal to zero.
 */
public final class NumericTextFunction implements JpqlFunction {

  public static final String NAME = "numeric_text";

  @Override
  public boolean hasArguments() {
    return true;
  }

  @Override
  public boolean hasParenthesesIfNoArguments() {
    return true;
  }

  @Override
  public Class<?> getReturnType(Class<?> firstArgumentType) {
    return BigDecimal.class;
  }

  private static final String NUMERIC_PATTERN =
      "'^[+-]?([0-9]+[.]?[0-9]*|[.][0-9]+)([eE][+-]?[0-9]+)?$'";

  private static final String MIN_BIG_DECIMAL_SCALE = "-2147483648";
  private static final String MAX_BIG_DECIMAL_SCALE = "2147483647";
  private static final int MAX_INTEGER_DIGITS =
      RuntimeFieldValueType.MAX_NUMBER_PRECISION - RuntimeFieldValueType.MAX_NUMBER_SCALE;

  private static String validNumber(String value) {
    String lower = "lower(" + value + ")";
    String mantissa = "substring_index(" + lower + ", 'e', 1)";
    String digits = "replace(replace(replace(" + mantissa + ", '.', ''), '+', ''), '-', '')";
    String leadingZeroTrimmedDigits = "trim(leading '0' from " + digits + ")";
    String fractionDigits =
        "case when locate('.', "
            + mantissa
            + ") > 0 then char_length(substring_index("
            + mantissa
            + ", '.', -1)) else 0 end";
    String exponentText = "substring_index(" + lower + ", 'e', -1)";
    String exponentDigits =
        "case when left("
            + exponentText
            + ", 1) in ('+', '-') then substring("
            + exponentText
            + ", 2) else "
            + exponentText
            + " end";
    exponentDigits = "trim(leading '0' from " + exponentDigits + ")";
    String exponent =
        "case when locate('e', "
            + lower
            + ") = 0 then 0 when char_length("
            + exponentDigits
            + ") > 10 then case when left("
            + exponentText
            + ", 1) = '-' then -2147483649 else 2147483649 end else cast("
            + exponentText
            + " as signed) end";
    String trailingZeros =
        "(char_length(" + digits + ") - char_length(trim(trailing '0' from " + digits + ")))";
    String rawScale = "(" + fractionDigits + " - " + exponent + ")";
    String normalizedScale = "(" + rawScale + " - " + trailingZeros + ")";
    String integerDigits =
        "(char_length("
            + leadingZeroTrimmedDigits
            + ") - "
            + fractionDigits
            + " + "
            + exponent
            + ")";
    // Leading zeroes do not count toward precision; stripTrailingZeros defines the normalized
    // scale.
    return "("
        + value
        + " regexp "
        + NUMERIC_PATTERN
        + ") and "
        + "locate(char(10), "
        + value
        + ") = 0 and locate(char(13), "
        + value
        + ") = 0 and "
        + "(locate('e', "
        + lower
        + ") = 0 or char_length("
        + exponentDigits
        + ") <= 10) and "
        + rawScale
        + " between "
        + MIN_BIG_DECIMAL_SCALE
        + " and "
        + MAX_BIG_DECIMAL_SCALE
        + " and ("
        + leadingZeroTrimmedDigits
        + " = '' or ("
        + normalizedScale
        + " <= "
        + RuntimeFieldValueType.MAX_NUMBER_SCALE
        + " and "
        + integerDigits
        + " <= "
        + MAX_INTEGER_DIGITS
        + "))";
  }

  @Override
  public void render(FunctionRenderContext context) {
    if (context.getArgumentsSize() != 1) {
      throw new IllegalArgumentException(NAME + " takes exactly one argument");
    }
    String value = context.getArgument(0);
    context.addChunk(
        "(case when "
            + validNumber(value)
            + " then cast("
            + value
            + " as decimal(65,30)) else null end)");
  }
}
