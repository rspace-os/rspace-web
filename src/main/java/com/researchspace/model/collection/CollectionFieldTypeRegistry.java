package com.researchspace.model.collection;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.util.ClassUtils;

/** Immutable factories for server-declared annotation types, including primitive aliases. */
public final class CollectionFieldTypeRegistry {
  /** Options attached to one annotated field, rather than mutable registry configuration. */
  public record DeclarationOptions(int maxLength) {
    public static final DeclarationOptions DEFAULT = new DeclarationOptions(-1);

    /** Rejects text-only options when resolving another type. */
    public void requireNoOptions() {
      if (maxLength != -1) {
        throw new IllegalArgumentException("Maximum length is only supported for text fields");
      }
    }
  }

  /** One exact-class factory. Primitive classes and their wrappers share a registration. */
  public record Registration<V>(
      Class<V> javaType, Function<DeclarationOptions, CollectionFieldType<V>> factory) {
    public Registration {
      Objects.requireNonNull(javaType, "Registered Java type");
      Objects.requireNonNull(factory, "Field type factory");
    }
  }

  private static final CollectionFieldTypeRegistry BUILT_INS =
      new CollectionFieldTypeRegistry(
          List.of(
              new Registration<>(String.class, CollectionFieldTypeRegistry::text),
              fixed(Long.class, CollectionFieldTypes.longNumber()),
              fixed(Integer.class, CollectionFieldTypes.integer()),
              fixed(Date.class, CollectionFieldTypes.instant()),
              fixed(Boolean.class, CollectionFieldTypes.bool())));

  private final Map<Class<?>, Registration<?>> registrations;

  public CollectionFieldTypeRegistry(List<? extends Registration<?>> registrations) {
    Map<Class<?>, Registration<?>> byClass = new LinkedHashMap<>();
    for (Registration<?> registration : List.copyOf(registrations)) {
      Class<?> boxed = ClassUtils.resolvePrimitiveIfNecessary(registration.javaType());
      if (byClass.putIfAbsent(boxed, registration) != null) {
        throw new IllegalArgumentException("Duplicate resource field type " + boxed.getName());
      }
    }
    this.registrations = Collections.unmodifiableMap(byClass);
  }

  public static CollectionFieldTypeRegistry builtIns() {
    return BUILT_INS;
  }

  /** A snapshot that can be copied to assemble a separate registry with custom registrations. */
  public List<Registration<?>> registrations() {
    return List.copyOf(registrations.values());
  }

  public CollectionFieldType<?> resolve(Class<?> javaType, DeclarationOptions options) {
    Objects.requireNonNull(javaType, "Resource field Java type");
    Objects.requireNonNull(options, "Field declaration options");
    if (javaType.isArray() || Iterable.class.isAssignableFrom(javaType)) {
      throw new IllegalArgumentException(
          "Array resource fields require declared children. Use CollectionFieldTypes.array with"
              + " Field.embedded children in a programmatic CollectionDescription.");
    }
    Class<?> boxed = ClassUtils.resolvePrimitiveIfNecessary(javaType);
    Registration<?> registration = registrations.get(boxed);
    if (registration != null) {
      CollectionFieldType<?> result =
          Objects.requireNonNull(registration.factory().apply(options), "Resolved field type");
      if (!boxed.equals(result.javaType())) {
        throw new IllegalArgumentException("Field type factory must return " + boxed.getName());
      }
      return result;
    }
    options.requireNoOptions();
    if (javaType.isEnum()) {
      return enumeration(javaType);
    }
    throw new IllegalArgumentException("Unsupported resource field type " + javaType.getName());
  }

  private static CollectionFieldType<String> text(DeclarationOptions options) {
    if (options.maxLength() < -1) {
      throw new IllegalArgumentException("Text maximum length must not be less than -1");
    }
    return options.maxLength() == -1
        ? CollectionFieldTypes.text()
        : CollectionFieldTypes.text(options.maxLength());
  }

  private static <V> Registration<V> fixed(Class<V> javaType, CollectionFieldType<V> type) {
    return new Registration<>(
        javaType,
        options -> {
          options.requireNoOptions();
          return type;
        });
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static CollectionFieldType<?> enumeration(Class<?> javaType) {
    return CollectionFieldTypes.enumeration((Class<? extends Enum>) javaType);
  }
}
