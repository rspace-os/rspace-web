package com.researchspace.model.collection;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.core.ResolvableType;
import org.springframework.util.ClassUtils;

/** Maps an annotated API record and its entity bean properties to a collection description. */
final class AnnotatedCollectionDescriptionFactory {

  private AnnotatedCollectionDescriptionFactory() {}

  static <T> CollectionDescription<T> create(
      Class<?> resourceType,
      Class<T> entityType,
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
    for (RecordComponent component : resourceType.getRecordComponents()) {
      fields.add(
          memberFrom(resourceType, component, entityType, definition, properties, fieldTypes));
    }
    if (definition.auditFields()) {
      fields.addAll(AuditResourceMembers.fields(properties));
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

  private static <T> ResourceMember<T> memberFrom(
      Class<?> resourceType,
      RecordComponent component,
      Class<T> entityType,
      ApiV2ResourceDefinition definition,
      Map<String, PropertyDescriptor> properties,
      CollectionFieldTypeRegistry fieldTypes) {
    ApiV2ResourceField field = component.getAnnotation(ApiV2ResourceField.class);
    ApiV2ResourceRelationship relationship =
        component.getAnnotation(ApiV2ResourceRelationship.class);
    ApiV2ResourceMember custom = component.getAnnotation(ApiV2ResourceMember.class);
    int declarations =
        (field == null ? 0 : 1) + (relationship == null ? 0 : 1) + (custom == null ? 0 : 1);
    if (declarations != 1) {
      throw new IllegalArgumentException(
          "Resource component "
              + resourceType.getName()
              + "."
              + component.getName()
              + " must have exactly one resource member annotation");
    }
    if (field != null) {
      return fieldFrom(resourceType, component, properties, definition.id(), fieldTypes);
    }
    if (relationship != null) {
      return relationshipFrom(component, entityType, relationship, fieldTypes);
    }
    return memberFromFactory(resourceType, component, custom, entityType);
  }

  private static <T> ResourceMember<T> memberFromFactory(
      Class<?> resourceType,
      RecordComponent component,
      ApiV2ResourceMember definition,
      Class<T> entityType) {
    Method factory;
    try {
      factory = resourceType.getDeclaredMethod(definition.factory());
    } catch (NoSuchMethodException ex) {
      throw new IllegalArgumentException(
          "Resource member factory must be a no-argument method: " + definition.factory(), ex);
    }
    if (!Modifier.isStatic(factory.getModifiers())
        || factory.getParameterCount() != 0
        || !ResourceMember.class.isAssignableFrom(factory.getReturnType())
        || !factory.trySetAccessible()) {
      throw new IllegalArgumentException(
          "Resource member factory must be an accessible static no-argument method returning a"
              + " ResourceMember: "
              + definition.factory());
    }
    ResolvableType returnType = ResolvableType.forMethodReturnType(factory);
    ResolvableType memberType = returnType.as(ResourceMember.class);
    Class<?> memberEntityType = resourceMemberEntityType(returnType, memberType);
    if (memberEntityType == null || !memberEntityType.isAssignableFrom(entityType)) {
      throw new IllegalArgumentException(
          "Resource member factory entity type must accept " + entityType.getName());
    }
    Object result;
    try {
      result = factory.invoke(null);
    } catch (ReflectiveOperationException ex) {
      Throwable cause =
          ex instanceof InvocationTargetException invocation ? invocation.getCause() : ex;
      throw new IllegalArgumentException(
          "Resource member factory failed: " + definition.factory(), cause);
    }
    if (!(result instanceof ResourceMember<?> member)
        || !component.getName().equals(member.name())) {
      throw new IllegalArgumentException(
          "Resource member factory must return a member named " + component.getName());
    }
    @SuppressWarnings("unchecked")
    ResourceMember<T> typed = (ResourceMember<T>) member;
    return typed;
  }

  private static Class<?> resourceMemberEntityType(
      ResolvableType returnType, ResolvableType memberType) {
    if (!memberType.hasGenerics() || !returnType.hasGenerics()) {
      return null;
    }
    Type declaredEntityType = returnType.getGeneric(0).getType();
    if (!(declaredEntityType instanceof Class<?> declaredType)) {
      return null;
    }
    Class<?> resolvedType = memberType.getGeneric(0).resolve();
    if (resolvedType == null || !resolvedType.isAssignableFrom(declaredType)) {
      return null;
    }
    return declaredType;
  }

  private static <T> Relationship<T> relationshipFrom(
      RecordComponent component,
      Class<T> entityType,
      ApiV2ResourceRelationship definition,
      CollectionFieldTypeRegistry fieldTypes) {
    if (component.getType() != ResourceReference.class) {
      throw new IllegalArgumentException(
          "Resource relationship component must be a ResourceReference: " + component.getName());
    }
    ApiV2RelationshipTarget[] declaredTargets = definition.relationTo();
    if (declaredTargets.length == 0) {
      throw new IllegalArgumentException(
          "Resource relationship must declare at least one target: " + component.getName());
    }
    boolean polymorphic = !declaredTargets[0].kind().isBlank();
    if (Arrays.stream(declaredTargets)
        .anyMatch(target -> !target.kind().isBlank() != polymorphic)) {
      throw new IllegalArgumentException(
          "Resource relationship kinds must be set on every target or none: "
              + component.getName());
    }
    if (!polymorphic && declaredTargets.length != 1) {
      throw new IllegalArgumentException(
          "A monomorphic relationship must have exactly one target: " + component.getName());
    }

    if (!polymorphic && !definition.kindProperty().isBlank()) {
      throw new IllegalArgumentException(
          "A monomorphic relationship cannot declare a kind property: " + component.getName());
    }
    String property = definition.property().isBlank() ? component.getName() : definition.property();
    String idProperty =
        definition.idProperty().isBlank() ? property + ".id" : definition.idProperty();
    String kindProperty =
        definition.kindProperty().isBlank() ? property + ".kind" : definition.kindProperty();
    Method propertyReader = BeanPropertyAccess.reader(entityType, property);
    Class<?> relatedType = propertyReader.getReturnType();
    Method idReader =
        BeanPropertyAccess.reader(relatedType, accessorName(property, idProperty, "id"));
    Method kindReader =
        polymorphic
            ? BeanPropertyAccess.reader(relatedType, accessorName(property, kindProperty, "kind"))
            : null;
    Class<?> kindType = kindReader == null ? String.class : kindReader.getReturnType();
    if (polymorphic && kindType != String.class && !kindType.isEnum()) {
      throw new IllegalArgumentException(
          "Polymorphic relationship kind property must return String or an enum: "
              + component.getName());
    }
    CollectionFieldType<?> idType =
        fieldTypes.resolve(
            idReader.getReturnType(), CollectionFieldTypeRegistry.DeclarationOptions.DEFAULT);

    List<RelationshipTarget<Object>> targets = new ArrayList<>();
    for (ApiV2RelationshipTarget target : declaredTargets) {
      Object storedKind =
          polymorphic ? kindValue(kindType, target.kind(), component) : target.resource();
      targets.add(
          new RelationshipTarget<>(
              target.resource(),
              storedKind,
              target.globalIdPrefix().isBlank() ? null : target.globalIdPrefix(),
              target.entity()));
    }

    Function<T, ResourceReference<Object, Object>> readReference =
        entity -> {
          Object related = BeanPropertyAccess.invoke(propertyReader, entity);
          if (related == null) {
            return null;
          }
          Object id = BeanPropertyAccess.readId(related, idReader);
          Object kind =
              polymorphic
                  ? BeanPropertyAccess.invoke(kindReader, related)
                  : targets.get(0).storedKind();
          return new ResourceReference<>(kind, id);
        };
    SplitReferenceBinding<T, Object, Object> binding =
        new SplitReferenceBinding<>(readReference, polymorphic ? kindProperty : null, idProperty);
    Relationship<T> result =
        AnnotatedCollectionDescriptionFactory.<T>polymorphicRelationship(
                component.getName(), idType, targets, binding)
            .writeOnlyOn(definition.writeOn());
    if (definition.requiredOnCreate()) {
      result = result.required();
    }
    if (definition.nullable() && !definition.requiredOnCreate()) {
      result = result.allowNull();
    }
    if (definition.acceptGlobalIdOn().length > 0) {
      result = result.acceptGlobalIdOn(definition.acceptGlobalIdOn());
    }
    if (definition.allowSelfReference()) {
      result = result.allowSelfReference();
    }
    return result
        .readableBy(definition.readAccess().resolve())
        .writableBy(definition.writeAccess().resolve())
        .documented(
            new OpenApiSchemaDocumentation(
                definition.title(),
                definition.description(),
                definition.example(),
                definition.deprecated(),
                null,
                List.of(),
                Map.of()));
  }

  private static String accessorName(String property, String path, String fallback) {
    String prefix = property + ".";
    return path.startsWith(prefix) ? path.substring(prefix.length()) : fallback;
  }

  @SuppressWarnings("unchecked")
  private static <T> Relationship<T> polymorphicRelationship(
      String name,
      CollectionFieldType<?> idType,
      List<RelationshipTarget<Object>> targets,
      SplitReferenceBinding<T, Object, Object> binding) {
    return Relationship.polymorphicToOne(
        name, (CollectionFieldType<Object>) idType, targets, binding);
  }

  private static Object kindValue(Class<?> kindType, String value, RecordComponent component) {
    if (kindType == String.class) {
      return value;
    }
    try {
      @SuppressWarnings({"rawtypes", "unchecked"})
      Enum<?> kind = Enum.valueOf((Class) kindType, value);
      return kind;
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(
          "Resource relationship " + component.getName() + " has an unknown kind " + value, ex);
    }
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
