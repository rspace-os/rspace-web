package com.researchspace.model.collection;

import com.researchspace.model.User;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.hibernate.proxy.HibernateProxy;

/** Builds the standard date fields and user relationships inferred from audit properties. */
final class AuditResourceMembers {

  private AuditResourceMembers() {}

  static <T> List<Field<T, ?>> fields(Map<String, PropertyDescriptor> properties) {
    List<Field<T, ?>> fields = new ArrayList<>();
    AuditResourceMembers.<T, Date>auditProperty(properties, Date.class, "createdAt", "creationDate")
        .map(
            source ->
                auditDateField("createdAt", "Time at which the resource was created.", source))
        .ifPresent(fields::add);
    AuditResourceMembers.<T, Date>auditProperty(
            properties, Date.class, "updatedAt", "modificationDate")
        .map(
            source ->
                auditDateField("updatedAt", "Time at which the resource was last updated.", source))
        .ifPresent(fields::add);
    return List.copyOf(fields);
  }

  static <T> List<Relationship<T>> relationships(Map<String, PropertyDescriptor> properties) {
    List<Relationship<T>> relationships = new ArrayList<>();
    AuditResourceMembers.<T>auditUserRelationship(properties, "createdBy", "createdBy")
        .ifPresent(relationships::add);
    AuditResourceMembers.<T>auditUserRelationship(
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

  private static <T> Optional<Relationship<T>> auditUserRelationship(
      Map<String, PropertyDescriptor> properties, String name, String... candidates) {
    AuditReader source = auditReader(properties, candidates);
    if (source == null) {
      return Optional.empty();
    }
    Method reader = source.reader();
    if (!User.class.isAssignableFrom(reader.getReturnType())) {
      throw new IllegalArgumentException(
          "Audit property " + reader.getName() + " must return " + User.class.getName());
    }
    Function<T, User> userReader = entity -> (User) BeanPropertyAccess.invoke(reader, entity);
    return Optional.of(
        Relationship.referenceToOne(
                name,
                "users",
                CollectionFieldTypes.longNumber(),
                User.class,
                userReader,
                AuditResourceMembers::auditUserId,
                source.property() + ".id")
            .allowNull()
            .documented(
                auditDocumentation(
                    name.equals("createdBy")
                        ? "User who created the resource."
                        : "User who last updated the resource.")));
  }

  /** Reads a proxy ID without initializing it after the manager transaction closes. */
  private static Long auditUserId(User user) {
    if (user instanceof HibernateProxy proxy) {
      return (Long) proxy.getHibernateLazyInitializer().getIdentifier();
    }
    return user.getId();
  }

  private static OpenApiSchemaDocumentation auditDocumentation(String description) {
    return OpenApiSchemaDocumentation.of(null, description, null);
  }

  private static <T, V> Optional<AuditProperty<T, V>> auditProperty(
      Map<String, PropertyDescriptor> properties, Class<V> type, String... candidates) {
    AuditReader source = auditReader(properties, candidates);
    if (source == null) {
      return Optional.empty();
    }
    Method reader = source.reader();
    if (!type.isAssignableFrom(reader.getReturnType())) {
      throw new IllegalArgumentException(
          "Audit property " + reader.getName() + " must return " + type.getName());
    }
    return Optional.of(
        new AuditProperty<>(
            source.property(), entity -> type.cast(BeanPropertyAccess.invoke(reader, entity))));
  }

  private static AuditReader auditReader(
      Map<String, PropertyDescriptor> properties, String... candidates) {
    for (String candidate : candidates) {
      PropertyDescriptor descriptor = properties.get(candidate);
      if (descriptor != null && descriptor.getReadMethod() != null) {
        return new AuditReader(candidate, descriptor.getReadMethod());
      }
    }
    return null;
  }

  private record AuditReader(String property, Method reader) {}

  private record AuditProperty<T, V>(String property, Function<T, V> reader) {}
}
