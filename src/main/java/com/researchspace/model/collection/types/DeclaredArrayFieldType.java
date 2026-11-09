package com.researchspace.model.collection.types;

import com.researchspace.model.collection.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** Declared object rows with row validation and inclusive array bounds. */
public final class DeclaredArrayFieldType<R> extends DeclaredValueFieldType<List<R>, R> {
  @SuppressWarnings({"unchecked", "rawtypes"})
  public DeclaredArrayFieldType(
      Class<R> rowType,
      List<? extends Field<R, ?>> fields,
      Function<Map<String, Object>, R> adapter,
      Consumer<R> rowValidator,
      Consumer<List<R>> arrayValidator,
      int minRows,
      int maxRows) {
    super(
        (Class) List.class,
        InputKind.ARRAY,
        rowType,
        fields,
        adapter,
        rowValidator,
        validator(arrayValidator, minRows, maxRows),
        bounds(minRows, maxRows));
  }

  private static Map<String, Object> bounds(int minRows, int maxRows) {
    if (minRows < 0 || maxRows < minRows) {
      throw new IllegalArgumentException("Invalid array row bounds");
    }
    Map<String, Object> bounds = new LinkedHashMap<>();
    if (minRows > 0) {
      bounds.put("minItems", minRows);
    }
    if (maxRows < Integer.MAX_VALUE) {
      bounds.put("maxItems", maxRows);
    }
    return bounds;
  }

  private static <R> Consumer<List<R>> validator(
      Consumer<List<R>> arrayValidator, int minRows, int maxRows) {
    bounds(minRows, maxRows);
    Objects.requireNonNull(arrayValidator, "Array validator");
    return rows -> {
      if (rows.size() < minRows || rows.size() > maxRows) {
        throw new IllegalArgumentException("Array violates its row bounds");
      }
      arrayValidator.accept(rows);
    };
  }
}
