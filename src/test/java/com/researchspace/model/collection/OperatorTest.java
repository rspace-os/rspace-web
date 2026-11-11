package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OperatorTest {

  private record Resource(Long id, String value) {}

  @ParameterizedTest
  @EnumSource(Operator.class)
  void canonicalTokenParsesBackToTheSameOperator(Operator operator) {
    var description =
        new CollectionDescription<>(
            "resources",
            Resource.class,
            List.of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Resource::id),
                Field.readOnly(
                    "value",
                    "value",
                    CollectionFieldTypes.text(EnumSet.allOf(Operator.class), false),
                    Resource::value)),
            "id",
            List.of(new Sort("id", true)));
    String argument =
        switch (operator) {
          case IN, NOT_IN -> "(example)";
          case EXISTS -> "true";
          default -> "example";
        };
    var parsed = new RsqlFilterParser(description).parse("value" + operator.token() + argument);
    assertEquals(operator, assertInstanceOf(FilterExpression.Comparison.class, parsed).operator());
  }
}
