package com.researchspace.api.v1.model.stoichiometry;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The stock-deduction endpoint binds its body as {@code @Valid StockDeductionRequest}. The list
 * itself is constrained, but without an element-level constraint {@code {"linkIds":[null]}} reaches
 * the manager, whose lock-ordering pre-pass ({@code Map.entry}, {@code getSafeNull}) rejects a null
 * id outside the per-link try/catch, turning a malformed public request into a 500 (Copilot review,
 * PR #1090). Uses the plain Jakarta validator, the same engine Spring's request-body validation
 * delegates to.
 */
class StockDeductionRequestBeanValidationTest {

  private static final Validator validator =
      Validation.buildDefaultValidatorFactory().getValidator();

  private Set<String> violatedPaths(StockDeductionRequest request) {
    return validator.validate(request).stream()
        .map(ConstraintViolation::getPropertyPath)
        .map(Object::toString)
        .collect(Collectors.toSet());
  }

  @Test
  void aWellFormedRequestHasNoViolations() {
    StockDeductionRequest request = new StockDeductionRequest(5L, List.of(1L, 2L), false);
    assertTrue(violatedPaths(request).isEmpty());
  }

  @Test
  void aNullLinkIdElementIsAViolationNotAServerError() {
    StockDeductionRequest request = new StockDeductionRequest(5L, Arrays.asList(1L, null), false);
    Set<String> violated = violatedPaths(request);
    assertTrue(
        violated.stream().anyMatch(path -> path.startsWith("linkIds[1]")),
        () -> "expected a violation under linkIds[1], got: " + violated);
  }
}
