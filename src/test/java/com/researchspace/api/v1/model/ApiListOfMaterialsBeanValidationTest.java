package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ApiListOfMaterialsBeanValidationTest {

  private static final Validator validator =
      Validation.buildDefaultValidatorFactory().getValidator();

  private static ApiListOfMaterials withMaterials(int count) {
    ApiListOfMaterials lom = new ApiListOfMaterials();
    List<ApiMaterialUsage> materials = new ArrayList<>();
    IntStream.range(0, count).forEach(i -> materials.add(new ApiMaterialUsage()));
    lom.setMaterials(materials);
    return lom;
  }

  private static Set<String> violatedPaths(ApiListOfMaterials lom) {
    return validator.validate(lom).stream()
        .map(ConstraintViolation::getPropertyPath)
        .map(Object::toString)
        .collect(Collectors.toSet());
  }

  @Test
  void twoHundredAndFiftyMaterialsIsAccepted() {
    assertTrue(violatedPaths(withMaterials(250)).isEmpty());
  }

  @Test
  void twoHundredAndFiftyOneMaterialsIsAViolation() {
    assertEquals(Set.of("materials"), violatedPaths(withMaterials(251)));
  }
}
