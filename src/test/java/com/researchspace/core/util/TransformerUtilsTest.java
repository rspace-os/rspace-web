package com.researchspace.core.util;

import static com.researchspace.core.util.TransformerUtils.toEnums;
import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.core.util.TransformerUtils.toSet;
import static com.researchspace.core.util.TransformerUtils.transform;
import static com.researchspace.core.util.TransformerUtils.transformToString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.Data;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TransformerUtilsTest {

  @BeforeEach
  public void setUp() throws Exception {}

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testTransform() {
    Set<Object> set = TransformerUtils.toSet(new Object());
    List<Object> transformed =
        transform(
            set,
            new Transformer<>() {
              // null transformer, just for testing
              @Override
              public Object transform(Object toTransform) {
                // TODO Auto-generated method stub
                return toTransform;
              }
            });
    assertThat(transformed).hasSize(1);
  }

  @Test
  public void testToSet() {
    assertThat(toSet(new Object())).hasSize(1);
    assertThat(toSet(null)).isEmpty();
    assertThat(toSet(new Object[] {})).isEmpty();
  }

  @Test
  public void testToList() {
    assertThat(toList(new Object())).hasSize(1);
    assertThat(toList(null)).isEmpty();
    assertThat(toList(new Object[] {})).isEmpty();
  }

  @Test
  public void testStringArrayToEnum() {
    assertEquals(EnumSet.of(SortOrder.ASC), toEnums(new String[] {"ASC"}, SortOrder.class));
  }

  @Data
  public static class Any {
    private String x = "abcde";
  }

  @Test
  public void testTransformToString() throws Exception {
    final Any any = new Any();
    final List<Any> anys = toList(any);
    assertThat(transformToString(anys, "x")).element(0).isEqualTo("abcde");
    assertThat(transformToString(new ArrayList<>(), "x")).isEmpty();
    assertThrows(IllegalArgumentException.class, () -> transformToString(anys, "y"));
  }
}
