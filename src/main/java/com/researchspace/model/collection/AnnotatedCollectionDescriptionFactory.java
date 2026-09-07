package com.researchspace.model.collection;

import com.researchspace.model.User;
import com.researchspace.model.collection.CollectionDescription.Field;
import com.researchspace.model.collection.CollectionDescription.InternalFilter;
import com.researchspace.model.collection.CollectionDescription.Relationship;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.CollectionDescription.WriteOperation;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.hibernate.proxy.HibernateProxy;

/** Maps an annotated API record and its entity bean properties to a collection description. */
final class AnnotatedCollectionDescriptionFactory {

  private AnnotatedCollectionDescriptionFactory() {}

  static <T> CollectionDescription<T> create(
      Class<?> resourceType,
      Class<T> entityType,
      List<Relationship<T>> relationships,
      List<Sort> defaultSort,
      AccessPolicy accessPolicy) {
    return create(resourceType, entityType, relationships, defaultSort, accessPolicy, List.of());
  }

  static <T> CollectionDescription<T> create(
      Class<?> resourceType,
      Class<T> entityType,
      List<Relationship<T>> relationships,
      List<Sort> defaultSort,
      AccessPolicy accessPolicy,
      List<InternalFilter> internalFilters) {
    ApiV2ResourceDefinition definition = resourceType.getAnnotation(ApiV2ResourceDefinition.class);
    if (definition == null || !resourceType.isRecord()) {
      throw new IllegalArgumentException(
          "Resource definition must be an annotated record: " + resourceType.getName());
    }
    if (definition.entity() != entityType) {
      throw new IllegalArgumentException(
          "Resource definition entity must be " + entityType.getName());
    }
    Map<String, PropertyDescriptor> properties = properties(entityType);
    List<Relationship<T>> describedRelationships = new ArrayList<>(relationships);
    if (definition.auditFields()) {
      describedRelationships.addAll(auditRelationships(properties));
    }
    return new CollectionDescription<>(
        definition.name(),
        entityType,
        fieldsFrom(resourceType, definition, properties),
        describedRelationships,
        definition.id(),
        defaultSort,
        accessPolicy,
        internalFilters);
  }

  private static <T> List<Field<T, ?>> fieldsFrom(
      Class<?> resourceType,
      ApiV2ResourceDefinition definition,
      Map<String, PropertyDescriptor> properties) {
    List<Field<T, ?>> fields =
        new ArrayList<>(
            Arrays.stream(resourceType.getRecordComponents())
                .<Field<T, ?>>map(
                    component -> fieldFrom(resourceType, component, properties, definition.id()))
                .toList());
    if (definition.auditFields()) {
      fields.addAll(auditFields(properties));
    }
    return List.copyOf(fields);
  }

  private static <T> List<Field<T, ?>> auditFields(Map<String, PropertyDescriptor> properties) {
    List<Field<T, ?>> fields = new ArrayList<>();
    AnnotatedCollectionDescriptionFactory.<T, Date>auditProperty(
            properties, Date.class, "createdAt", "creationDate")
        .map(
            source ->
                auditDateField("createdAt", "Time at which the resource was created.", source))
        .ifPresent(fields::add);
    AnnotatedCollectionDescriptionFactory.<T, Date>auditProperty(
            properties, Date.class, "updatedAt", "modificationDate")
        .map(
            source ->
                auditDateField("updatedAt", "Time at which the resource was last updated.", source))
        .ifPresent(fields::add);
    AnnotatedCollectionDescriptionFactory.<T>legacyAuditUserField(
            properties, "createdBy", "Username of the user who created the resource.", "createdBy")
        .ifPresent(fields::add);
    AnnotatedCollectionDescriptionFactory.<T>legacyAuditUserField(
            properties,
            "updatedBy",
            "Username of the user who last updated the resource.",
            "updatedBy",
            "lastUpdatedBy",
            "modifiedBy")
        .ifPresent(fields::add);
    return List.copyOf(fields);
  }

  private static <T> List<Relationship<T>> auditRelationships(
      Map<String, PropertyDescriptor> properties) {
    List<Relationship<T>> relationships = new ArrayList<>();
    AnnotatedCollectionDescriptionFactory.<T>auditUserRelationship(
            properties, "createdBy", "createdBy")
        .ifPresent(relationships::add);
    AnnotatedCollectionDescriptionFactory.<T>auditUserRelationship(
            properties, "updatedBy", "updatedBy", "lastUpdatedBy", "modifiedBy")
        .ifPresent(relationships::add);
    return List.copyOf(relationships);
  }

  private static <T> Field<T, Date> auditDateField(
      String name, String description, AuditProperty<T, Date> source) {
    return Field.readOnly(name, source.property(), CollectionFieldTypes.instant(), source.reader())
        .allowNull()
        .documented(auditDocumentation(description));
  }

  private static <T> Optional<Field<T, ?>> legacyAuditUserField(
      Map<String, PropertyDescriptor> properties,
      String name,
      String description,
      String... candidates) {
    Method reader = auditReadMethod(properties, candidates);
    if (reader == null || User.class.isAssignableFrom(reader.getReturnType())) {
      return Optional.empty();
    }
    if (reader.getReturnType() != String.class) {
      throw new IllegalArgumentException(
          "Audit property " + reader.getName() + " must return a username or User");
    }
    Function<T, String> valueReader = entity -> (String) invoke(reader, entity);
    return Optional.of(
        Field.readOnly(
                name, readerProperty(properties, reader), CollectionFieldTypes.text(), valueReader)
            .allowNull()
            .documented(auditDocumentation(description)));
  }

  private static <T> Optional<Relationship<T>> auditUserRelationship(
      Map<String, PropertyDescriptor> properties, String name, String... candidates) {
    Method reader = auditReadMethod(properties, candidates);
    if (reader == null || !User.class.isAssignableFrom(reader.getReturnType())) {
      return Optional.empty();
    }
    String property = readerProperty(properties, reader);
    Function<T, User> userReader = entity -> (User) invoke(reader, entity);
    return Optional.of(
        Relationship.referenceToOne(
                name,
                "users",
                CollectionFieldTypes.longNumber(),
                User.class,
                userReader,
                AnnotatedCollectionDescriptionFactory::auditUserId,
                property + ".id")
            .allowNull()
            .documented(
                auditDocumentation(
                    name.equals("createdBy")
                        ? "User who created the resource."
                        : "User who last updated the resource.")));
  }

  /**
   * Reads the audit user's identifier without initialising a lazy association.
   *
   * <p>{@code User::getId} looks harmless but is not: {@code ResourceRenderer} runs at the HTTP
   * boundary, after the manager's transaction has closed, so calling a getter on an uninitialised
   * proxy throws {@code LazyInitializationException} and the whole response becomes a 500. Every
   * resource with {@code auditFields = true} over a lazily mapped {@code User} would hit that on
   * read, list, update and delete.
   *
   * <p>A proxy already carries its identifier, so taking it from the initialiser is both correct
   * outside a session and one fewer SELECT per rendered row. At depth 0 the identifier is all the
   * reference needs; a deeper expansion resolves the target through its own registration.
   */
  private static Long auditUserId(User user) {
    if (user instanceof HibernateProxy proxy) {
      return (Long) proxy.getHibernateLazyInitializer().getIdentifier();
    }
    return user.getId();
  }

  private static String readerProperty(Map<String, PropertyDescriptor> properties, Method reader) {
    return properties.entrySet().stream()
        .filter(entry -> reader.equals(entry.getValue().getReadMethod()))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Audit reader is not a bean property"));
  }

  private static OpenApiSchemaDocumentation auditDocumentation(String description) {
    return new OpenApiSchemaDocumentation(
        null, description, null, null, null, null, List.of(), false);
  }

  private static <T, V> Optional<AuditProperty<T, V>> auditProperty(
      Map<String, PropertyDescriptor> properties, Class<V> type, String... candidates) {
    Method reader = auditReadMethod(properties, candidates);
    if (reader == null) {
      return Optional.empty();
    }
    if (!type.isAssignableFrom(reader.getReturnType())) {
      throw new IllegalArgumentException(
          "Audit property " + reader.getName() + " must return " + type.getName());
    }
    return Optional.of(
        new AuditProperty<>(
            readerProperty(properties, reader), entity -> type.cast(invoke(reader, entity))));
  }

  private record AuditProperty<T, V>(String property, Function<T, V> reader) {}

  private static Method auditReadMethod(
      Map<String, PropertyDescriptor> properties, String... candidates) {
    for (String candidate : candidates) {
      PropertyDescriptor descriptor = properties.get(candidate);
      if (descriptor != null && descriptor.getReadMethod() != null) {
        return descriptor.getReadMethod();
      }
    }
    return null;
  }

  private static Map<String, PropertyDescriptor> properties(Class<?> entityType) {
    try {
      Map<String, PropertyDescriptor> properties = new LinkedHashMap<>();
      for (PropertyDescriptor property :
          Introspector.getBeanInfo(entityType).getPropertyDescriptors()) {
        properties.put(property.getName(), property);
      }
      return properties;
    } catch (IntrospectionException ex) {
      throw new IllegalArgumentException("Cannot inspect entity " + entityType.getName(), ex);
    }
  }

  private static <T> Field<T, ?> fieldFrom(
      Class<?> resourceType,
      RecordComponent component,
      Map<String, PropertyDescriptor> properties,
      String idField) {
    ApiV2ResourceField definition = component.getAnnotation(ApiV2ResourceField.class);
    if (definition == null) {
      throw new IllegalArgumentException(
          "Missing @ApiV2ResourceField on " + resourceType.getName() + "." + component.getName());
    }
    String property = definition.property().isBlank() ? component.getName() : definition.property();
    PropertyDescriptor descriptor = properties.get(property);
    Method reader = descriptor == null ? null : descriptor.getReadMethod();
    if (reader == null) {
      throw new IllegalArgumentException("Resource field requires a readable property " + property);
    }
    requireCompatibleType(component, reader.getReturnType(), "reader");

    CollectionFieldType<?> type = fieldType(component.getType(), definition.maxLength());
    return fieldFromTyped(component, definition, property, reader, descriptor, type, idField);
  }

  private static <T, V> Field<T, V> fieldFromTyped(
      RecordComponent component,
      ApiV2ResourceField definition,
      String property,
      Method reader,
      PropertyDescriptor descriptor,
      CollectionFieldType<V> type,
      String idField) {
    Function<T, V> read = entity -> type.javaType().cast(invoke(reader, entity));
    Method writer = descriptor.getWriteMethod();
    boolean createWritable =
        !component.getName().equals(idField)
            && writer != null
            && definition.createAccess() != ApiV2ResourceField.AccessPreset.NEVER;
    boolean updateWritable =
        !component.getName().equals(idField)
            && writer != null
            && definition.updateAccess() != ApiV2ResourceField.AccessPreset.NEVER;
    Field<T, V> field;
    if (!createWritable && !updateWritable) {
      field = Field.readOnly(component.getName(), property, type, read);
    } else {
      requireCompatibleType(component, writer.getParameterTypes()[0], "writer");
      BiConsumer<T, V> write = (entity, value) -> invoke(writer, entity, value);
      field = Field.writable(component.getName(), property, type, read, write);
      if (createWritable && !updateWritable) {
        field = field.writeOnlyOn(WriteOperation.CREATE);
      } else if (!createWritable) {
        field = field.writeOnlyOn(WriteOperation.UPDATE);
      }
    }
    if (definition.requiredOnCreate()) {
      field = field.required();
    }
    if (definition.nullable()) {
      field = field.allowNull();
    }
    field =
        field.documented(
            new OpenApiSchemaDocumentation(
                definition.title(),
                definition.description(),
                definition.example(),
                definition.pattern(),
                definition.minimum(),
                definition.maximum(),
                List.of(definition.enumValues()),
                definition.deprecated(),
                definition.format(),
                definition.defaultValue(),
                definition.minLength() < 0 ? null : definition.minLength(),
                List.of(definition.additionalExamples()),
                Map.of()));
    return field
        .readableBy(definition.readAccess().resolve())
        .creatableBy(definition.createAccess().resolve())
        .updatableBy(definition.updateAccess().resolve())
        .withQueryCapabilities(definition.filterable(), definition.sortable());
  }

  private static CollectionFieldType<?> fieldType(Class<?> type, int maxLength) {
    if (type == String.class) {
      if (maxLength < -1) {
        throw new IllegalArgumentException("Text maximum length must not be less than -1");
      }
      return maxLength == -1 ? CollectionFieldTypes.text() : CollectionFieldTypes.text(maxLength);
    }
    if (maxLength != -1) {
      throw new IllegalArgumentException("Maximum length is only supported for text fields");
    }
    if (type == Long.class || type == long.class) {
      return CollectionFieldTypes.longNumber();
    }
    if (type == Date.class) {
      return CollectionFieldTypes.instant();
    }
    if (type == Boolean.class || type == boolean.class) {
      return CollectionFieldTypes.bool();
    }
    if (type.isEnum()) {
      return enumFieldType(type);
    }
    throw new IllegalArgumentException("Unsupported resource field type " + type.getName());
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static CollectionFieldType<?> enumFieldType(Class<?> type) {
    return CollectionFieldTypes.enumeration((Class<? extends Enum>) type);
  }

  private static void requireCompatibleType(
      RecordComponent component, Class<?> accessorType, String accessor) {
    if (!boxed(component.getType()).equals(boxed(accessorType))) {
      throw new IllegalArgumentException(
          "Resource field "
              + component.getName()
              + " does not match entity "
              + accessor
              + " type "
              + accessorType.getName());
    }
  }

  private static Class<?> boxed(Class<?> type) {
    if (type == long.class) {
      return Long.class;
    }
    if (type == boolean.class) {
      return Boolean.class;
    }
    return type;
  }

  private static Object invoke(Method method, Object target, Object... arguments) {
    try {
      return method.invoke(target, arguments);
    } catch (InvocationTargetException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new IllegalStateException("Resource accessor failed", cause);
    } catch (IllegalAccessException ex) {
      throw new IllegalStateException("Cannot invoke resource accessor", ex);
    }
  }
}
