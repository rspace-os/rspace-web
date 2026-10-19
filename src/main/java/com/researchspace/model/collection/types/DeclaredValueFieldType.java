package com.researchspace.model.collection.types;

import com.researchspace.model.collection.DeclaredFieldType;
import com.researchspace.model.collection.EmbeddedObjects;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.Operator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

abstract class DeclaredValueFieldType<V, R> implements DeclaredFieldType<V> {
  private final Class<V> javaType;
  private final InputKind inputKind;
  private final Class<R> rowType;
  private final List<? extends Field<R, ?>> fields;
  private final Function<Map<String, Object>, R> adapter;
  private final Consumer<R> rowValidator;
  private final Consumer<V> validator;
  private final Map<String, Object> writeConstraints;

  DeclaredValueFieldType(
      Class<V> javaType,
      InputKind inputKind,
      Class<R> rowType,
      List<? extends Field<R, ?>> fields,
      Function<Map<String, Object>, R> adapter,
      Consumer<R> rowValidator,
      Consumer<V> validator,
      Map<String, Object> writeConstraints) {
    this.javaType = javaType;
    this.inputKind = inputKind;
    this.rowType = rowType;
    this.fields = List.copyOf(fields);
    this.adapter = Objects.requireNonNull(adapter, "Embedded row adapter");
    this.rowValidator = Objects.requireNonNull(rowValidator, "Embedded row validator");
    this.validator = validator;
    this.writeConstraints = Map.copyOf(writeConstraints);
    EmbeddedObjects.validateDeclaration(this);
  }

  @Override
  public Class<V> javaType() {
    return javaType;
  }

  @Override
  public InputKind inputKind() {
    return inputKind;
  }

  @Override
  public Class<R> rowType() {
    return rowType;
  }

  @Override
  public List<? extends Field<R, ?>> fields() {
    return fields;
  }

  @Override
  public Map<String, Object> writeConstraints() {
    return writeConstraints;
  }

  @Override
  public Object assemble(Map<String, Object> values) {
    return rowType.cast(Objects.requireNonNull(adapter.apply(values), "Embedded row"));
  }

  @Override
  public void validateRow(Object row) {
    rowValidator.accept(rowType.cast(row));
  }

  @Override
  public void validate(V value) {
    validator.accept(value);
  }

  @Override
  public Set<Operator> operators() {
    return Set.of();
  }

  @Override
  public boolean sortable() {
    return false;
  }
}
