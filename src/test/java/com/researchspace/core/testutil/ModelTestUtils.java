package com.researchspace.core.testutil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang3.ArrayUtils;

public class ModelTestUtils {

  /**
   * Uses reflection to compare a copy's fields to the original object.
   *
   * @param <T> copy
   * @param <T> original
   * @param toExclude a list of field names to exclude from the comparison
   * @param classes A list of classes whose fields we want to include in the comparison. Typically,
   *     this will be the class for which we're testing the copy, plus its superclasses.
   * @throws IllegalAccessException
   * @throws IllegalArgumentException
   */
  public static <T> void assertCopiedFieldsAreEqual(
      T copy, T orig, Set<String> toExclude, Collection<Class<? super T>> classes)
      throws IllegalArgumentException, IllegalAccessException {

    Field[] allFields = new Field[] {};
    // add fields we want to compare from class and superclasses
    for (Class<?> c : classes) {
      Field[] f1 = c.getDeclaredFields();
      allFields = ArrayUtils.addAll(allFields, f1);
    }
    for (Field f : allFields) {
      // include private fields
      f.setAccessible(true);
      // ignore exclusions (e.g., id fields)
      if (toExclude.contains(f.getName())) {
        continue;
      }
      Object origF = f.get(orig);
      Object copyF = f.get(copy);

      // ignore null fields
      if (origF == null && copyF == null) {
        continue;
      }
      assertEquals(
          origF == null
              ? "null"
              : origF.toString() + " doesn't equal " + copyF == null
                  ? "null"
                  : copyF.toString() + " for field " + f.getName(),
          origF,
          copyF);
    }
  }

  /** Creates a Set of field names to be excluded from equality tests. */
  public static Set<String> generateExclusionFieldsFrom(String... fieldNamesToExclude) {
    return new HashSet<>(Arrays.asList(fieldNamesToExclude));
  }

  /**
   * Asserts that there are no null values for fields (including private fields) declared by <code>
   * object</code>'s class.
   *
   * @param object The instance to test
   */
  public static void assertNoNullFields(Object object)
      throws IllegalArgumentException, IllegalAccessException {
    Field[] allFields = object.getClass().getDeclaredFields();
    for (Field f : allFields) {
      // include private fields
      f.setAccessible(true);
      assertNotNull("Field " + f + " was null", f.get(object));
    }
  }
}
