package com.researchspace.model.collection;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.util.ClassUtils;

/** Maps an annotated API record and its entity bean properties to a collection description. */
final class AnnotatedCollectionDescriptionFactory {

  private AnnotatedCollectionDescriptionFactory() {}

  static <T> CollectionDescription<T> create(
      Class<?> resourceType,
      Class<T> entityType,
      List<Relationship<T>> relationships,
      List<Sort> defaultSort,
      AccessPolicy accessPolicy,
      List<InternalFilter> internalFilters,
      CollectionFieldTypeRegistry fieldTypes) {
    ApiV2ResourceDefinition definition = resourceType.getAnnotation(ApiV2ResourceDefinition.class);
    if (definition == null || !resourceType.isRecord()) {
      throw new IllegalArgumentException(
          "Resource definition must be an annotated record: " + resourceType.getName());
    }
    if (definition.entity() != entityType) {
      throw new IllegalArgumentException(
          "Resource definition entity must be " + entityType.getName());
    }
    Map<String, PropertyDescriptor> properties = BeanPropertyAccess.properties(entityType);
    List<ResourceMember<T>> fields = new ArrayList<>();
    fields.addAll(fieldsFrom(resourceType, definition, properties, fieldTypes));
    if (definition.auditFields()) {
      fields.addAll(AuditResourceMembers.fields(properties));
    }
    fields.addAll(relationships);
    if (definition.auditFields()) {
      fields.addAll(AuditResourceMembers.relationships(properties));
    }
    return new CollectionDescription<>(
        definition.name(),
        entityType,
        fields,
        definition.id(),
        defaultSort,
        accessPolicy,
        internalFilters);
  }

  private static <T> List<Field<T, ?>> fieldsFrom(
      Class<?> resourceType,
      ApiV2ResourceDefinition definition,
      Map<String, PropertyDescriptor> properties,
      CollectionFieldTypeRegistry fieldTypes) {
    return Arrays.stream(resourceType.getRecordComponents())
        .<Field<T, ?>>map(
            component ->
                fieldFrom(resourceType, component, properties, definition.id(), fieldTypes))
        .toList();
  }

  private static <T> Field<T, ?> fieldFrom(
      Class<?> resourceType,
      RecordComponent component,
      Map<String, PropertyDescriptor> properties,
      String idField,
      CollectionFieldTypeRegistry fieldTypes) {
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

    CollectionFieldType<?> type =
        constrained(
            fieldTypes.resolve(
                component.getType(),
                new CollectionFieldTypeRegistry.DeclarationOptions(definition.maxLength())),
            definition);
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
    Function<T, V> read = entity -> type.javaType().cast(BeanPropertyAccess.invoke(reader, entity));
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
      BiConsumer<T, V> write = (entity, value) -> BeanPropertyAccess.invoke(writer, entity, value);
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
    Stream.concat(Stream.of(definition.example()), Stream.of(definition.additionalExamples()))
        .filter(example -> !example.isBlank())
        .forEach(example -> wireValue(component, type, example, "example"));
    if (!definition.defaultValue().isBlank()) {
      field =
          field.defaultValue(
              wireValue(component, type, definition.defaultValue(), "default value"));
    }
    field =
        field.documented(
            new OpenApiSchemaDocumentation(
                definition.title(),
                definition.description(),
                definition.example(),
                definition.deprecated(),
                definition.format(),
                List.of(definition.additionalExamples()),
                Map.of()));
    return field
        .readableBy(definition.readAccess().resolve())
        .creatableBy(definition.createAccess().resolve())
        .updatableBy(definition.updateAccess().resolve())
        .withQueryCapabilities(definition.filterable(), definition.sortable());
  }

  /** Parses and validates a wire value from the annotation, so a bad one fails at startup. */
  private static <V> V wireValue(
      RecordComponent component, CollectionFieldType<V> type, String value, String label) {
    try {
      V parsed = type.parse(value);
      type.validate(parsed);
      return parsed;
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException(
          "Resource field " + component.getName() + " has an invalid " + label + ": " + value, ex);
    }
  }

  private static <V> CollectionFieldType<V> constrained(
      CollectionFieldType<V> type, ApiV2ResourceField definition) {
    CollectionFieldTypes.Constraints constraints =
        new CollectionFieldTypes.Constraints(
            definition.minimum().isBlank() ? null : new BigDecimal(definition.minimum()),
            definition.maximum().isBlank() ? null : new BigDecimal(definition.maximum()),
            definition.minLength() < 0 ? null : definition.minLength(),
            definition.pattern().isBlank() ? null : Pattern.compile(definition.pattern()),
            definition.enumValues().length == 0 ? null : List.of(definition.enumValues()));
    return constraints.isEmpty() ? type : CollectionFieldTypes.constrained(type, constraints);
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
    return ClassUtils.resolvePrimitiveIfNecessary(type);
  }
}
