package com.researchspace.model.collection;

import com.researchspace.model.collection.types.runtime.RuntimeChoiceType;
import com.researchspace.model.collection.types.runtime.RuntimeDateType;
import com.researchspace.model.collection.types.runtime.RuntimeNumberType;
import com.researchspace.model.collection.types.runtime.RuntimeRadioType;
import com.researchspace.model.collection.types.runtime.RuntimeTextType;
import com.researchspace.model.collection.types.runtime.RuntimeTimeType;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, explicit implementations for the stable runtime field identifiers. */
public final class RuntimeFieldTypeRegistry {
  private static final RuntimeFieldTypeRegistry BUILT_INS =
      new RuntimeFieldTypeRegistry(
          List.of(
              new Registration(RuntimeFieldValueType.TEXT, new RuntimeTextType()),
              new Registration(RuntimeFieldValueType.NUMBER, new RuntimeNumberType()),
              new Registration(RuntimeFieldValueType.DATE, new RuntimeDateType()),
              new Registration(RuntimeFieldValueType.TIME, new RuntimeTimeType()),
              new Registration(RuntimeFieldValueType.RADIO, new RuntimeRadioType()),
              new Registration(RuntimeFieldValueType.CHOICE, new RuntimeChoiceType())));

  private final Map<RuntimeFieldValueType, RuntimeFieldType> types;

  public RuntimeFieldTypeRegistry(Collection<Registration> registrations) {
    EnumMap<RuntimeFieldValueType, RuntimeFieldType> registered =
        new EnumMap<>(RuntimeFieldValueType.class);
    for (Registration registration : registrations) {
      if (registered.putIfAbsent(registration.identifier(), registration.type()) != null) {
        throw new IllegalArgumentException(
            "Duplicate runtime field type: " + registration.identifier());
      }
    }
    for (RuntimeFieldValueType identifier : RuntimeFieldValueType.values()) {
      if (!registered.containsKey(identifier)) {
        throw new IllegalArgumentException("Missing runtime field type: " + identifier);
      }
    }
    types = Map.copyOf(registered);
  }

  public static RuntimeFieldTypeRegistry builtIns() {
    return BUILT_INS;
  }

  public RuntimeFieldType require(RuntimeFieldValueType identifier) {
    return types.get(Objects.requireNonNull(identifier, "Runtime field identifier"));
  }

  public record Registration(RuntimeFieldValueType identifier, RuntimeFieldType type) {
    public Registration {
      Objects.requireNonNull(identifier, "Runtime field identifier");
      Objects.requireNonNull(type, "Runtime field implementation");
    }
  }
}
