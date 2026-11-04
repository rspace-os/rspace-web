package com.researchspace.model.collection;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.beans.BeanUtils;
import org.springframework.util.ReflectionUtils;

/** Resolves resource readers and handles Hibernate proxy identifiers. */
final class ResourcePropertyReaders {

  private ResourcePropertyReaders() {}

  static Method reader(Class<?> type, String property) {
    PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(type, property);
    // Spring also accepts capitalization variants; resource properties require exact names.
    Method reader =
        descriptor != null && descriptor.getName().equals(property)
            ? descriptor.getReadMethod()
            : null;
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
    return ReflectionUtils.invokeMethod(reader, entity);
  }
}
