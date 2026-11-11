package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuntimeCollectionFieldsTest {

  @Test
  void authorizedIdValuesRequireExplicitProviderSupport() {
    RuntimeCollectionFields<Object> provider = RuntimeCollectionFields.none();

    assertThrows(
        UnsupportedOperationException.class,
        () -> provider.valuesForAuthorizedIds(List.of(1L), Set.of("SF1"), null));
  }
}
