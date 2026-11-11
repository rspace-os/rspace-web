package com.researchspace.model.collection.types;

import com.researchspace.model.collection.Field;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/** One finite object whose child declarations control parsing and rendering. */
public final class DeclaredObjectFieldType<R> extends DeclaredValueFieldType<R, R> {
  public DeclaredObjectFieldType(
      Class<R> rowType,
      List<? extends Field<R, ?>> fields,
      Function<Map<String, Object>, R> adapter,
      Consumer<R> rowValidator) {
    super(rowType, InputKind.OBJECT, rowType, fields, adapter, rowValidator, value -> {}, Map.of());
  }
}
