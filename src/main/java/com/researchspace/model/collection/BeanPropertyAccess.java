package com.researchspace.model.collection;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.beans.BeanUtils;

/** Discovers JavaBean properties and invokes their accessors. */
final class BeanPropertyAccess {

  private BeanPropertyAccess() {}

  static Map<String, PropertyDescriptor> properties(Class<?> entityType) {
    Map<String, PropertyDescriptor> properties = new LinkedHashMap<>();
    for (PropertyDescriptor property : BeanUtils.getPropertyDescriptors(entityType)) {
      properties.put(property.getName(), property);
    }
    return properties;
  }

  static Method reader(Class<?> type, String property) {
    PropertyDescriptor descriptor = properties(type).get(property);
    Method reader = descriptor == null ? null : descriptor.getReadMethod();
    if (reader == null) {
      try {
        reader = type.getMethod(property);
      } catch (NoSuchMethodException ex) {
        throw new IllegalArgumentException(
            "Resource property " + type.getName() + "." + property + " is not readable", ex);
      }
    }
    if (!reader.trySetAccessible()) {
      throw new IllegalArgumentException(
          "Resource property " + type.getName() + "." + property + " is not readable");
    }
    return reader;
  }

  static Object readId(Object entity, Method reader) {
    if (entity == null) {
      return null;
    }
    if (entity instanceof HibernateProxy proxy) {
      return proxy.getHibernateLazyInitializer().getIdentifier();
    }
    return invoke(reader, entity);
  }

  static Object invoke(Method method, Object target, Object... arguments) {
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
