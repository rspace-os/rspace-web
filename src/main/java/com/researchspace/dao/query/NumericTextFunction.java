package com.researchspace.dao.query;

import com.blazebit.persistence.spi.FunctionRenderContext;
import com.blazebit.persistence.spi.JpqlFunction;
import java.math.BigDecimal;

/**
 * Compares a text column as a number, for a value store whose column is a string.
 *
 * <p>A runtime field keeps every declared type in one text column, so a numeric comparison has to
 * convert. Blaze's own {@code CAST_DOUBLE} cannot be used: its MySQL/MariaDB dialect maps no cast
 * type for {@code Double}, so it falls back to the ANSI name and emits {@code cast(x as double
 * precision)}, which MariaDB rejects outright. This renders the spelling MariaDB accepts.
 *
 * <p>The scale is explicit rather than defaulted, because {@code cast(x as decimal)} alone means
 * {@code decimal(10,0)} and would silently truncate {@code -80.5} to {@code -80} — a wrong answer
 * rather than an error.
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

  @Override
  public void render(FunctionRenderContext context) {
    if (context.getArgumentsSize() != 1) {
      throw new IllegalArgumentException(NAME + " takes exactly one argument");
    }
    context.addChunk("(case when ");
    context.addArgument(0);
    context.addChunk(" regexp " + NUMERIC_PATTERN + " then cast(");
    context.addArgument(0);
    context.addChunk(" as decimal(30,10)) else null end)");
  }
}
