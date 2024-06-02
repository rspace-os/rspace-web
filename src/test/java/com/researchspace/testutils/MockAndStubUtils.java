package com.researchspace.testutils;

import com.researchspace.model.User;
import java.lang.reflect.Method;
import java.util.Date;
import org.springframework.util.ReflectionUtils;

/** Reusable methods for assisting JUnit test setup */
public class MockAndStubUtils {

  /**
   * Modify user creation date, uses reflection as no public setter
   *
   * @param user
   * @param newDate
   * @return the modified user object for chaining.
   */
  public static User modifyUserCreationDate(User user, Date newDate) {
    return modifyCreationDate(user, newDate, User.class);
  }

  /**
   * Modify user lastLogin date, uses reflection as no public setter
   *
   * @param data.user
   * @param newDate
   * @return the modified user object for chaining.
   */
  public static <T> T modifyDateField(
      T objectWithCreationDate, Date newDate, Class<T> clazz, String setterMethodName) {
    Method method = ReflectionUtils.findMethod(clazz, setterMethodName, Date.class);
    method.setAccessible(true);
    ReflectionUtils.invokeMethod(method, objectWithCreationDate, newDate);
    return objectWithCreationDate;
  }

  /**
   * Modifies creation date of any object with a non-public 'setCreationDate' method accepting a
   * java.util.Date object
   *
   * @param objectWithCreationDate
   * @param newDate
   * @param clazz
   * @return
   */
  public static <T> T modifyCreationDate(T objectWithCreationDate, Date newDate, Class<T> clazz) {
    return modifyDateField(objectWithCreationDate, newDate, clazz, "setCreationDate");
  }
}
