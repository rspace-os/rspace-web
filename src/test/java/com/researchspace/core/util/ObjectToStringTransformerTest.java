package com.researchspace.core.util;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ObjectToStringTransformerTest {

  TestObject fu = null;
  ObjectToStringPropertyTransformer<TestObject> transformer = null;

  public class TestObject {
    private String p1;
    private String p2;
    private TestObjectInner inner;

    public TestObjectInner getInner() {
      return inner;
    }

    public void setInner(TestObjectInner inner) {
      this.inner = inner;
    }

    public TestObject(String p1, String p2) {
      super();
      this.p1 = p1;
      this.p2 = p2;
    }

    public String getP1() {
      return p1;
    }

    public void setP1(String p1) {
      this.p1 = p1;
    }
  }

  public class TestObjectInner {
    private String p1;

    public TestObjectInner(String p1) {
      super();
      this.p1 = p1;
    }

    public String getP1() {
      return p1;
    }

    public void setP1(String p1) {
      this.p1 = p1;
    }
  }

  @BeforeEach
  public void setUp() throws Exception {}

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testTransform() {
    TestObject f1 = new TestObject("u1", "1234");
    TestObject f2 = new TestObject("u12", "5678");
    List<TestObject> list = Arrays.asList(new TestObject[] {f1, f2});
    transformer = new ObjectToStringPropertyTransformer<>("p1");
    List<String> usages = list.stream().map(transformer).collect(Collectors.toList());
    assertEquals(2, usages.size());
  }

  @Test
  public void testTransformNestedProperty() {
    TestObject f1 = new TestObject("u1", "1234");
    TestObject f2 = new TestObject("u12", "5678");
    f1.setInner(new TestObjectInner("inner1"));
    f2.setInner(new TestObjectInner("inner2"));
    List<TestObject> list = asList(new TestObject[] {f1, f2});
    transformer = new ObjectToStringPropertyTransformer<>("inner.p1");
    List<String> usages = list.stream().map(transformer).collect(Collectors.toList());
    assertEquals(2, usages.size());
    assertTrue(usages.contains("inner1") && usages.contains("inner2"));
  }

  @Test
  public void testTransformThrowsIAEIfPropertyNotFound() {
    TestObject f1 = new TestObject("u1", "1234");
    TestObject f2 = new TestObject("u12", "5678");
    List<TestObject> list = Arrays.asList(new TestObject[] {f1, f2});
    transformer = new ObjectToStringPropertyTransformer<>("uXXXXs");
    var transformed = list.stream().map(transformer);
    var collector = Collectors.<String>toList();

    assertThrows(IllegalArgumentException.class, () -> transformed.collect(collector));
  }
}
