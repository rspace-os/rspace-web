package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The operations endpoint binds its body as {@code @Valid ApiInventoryOperationPost}. Uses the
 * plain Jakarta validator, the same engine Spring's request-body validation delegates to.
 */
class ApiInventoryOperationPostBeanValidationTest {

  private static final Validator validator =
      Validation.buildDefaultValidatorFactory().getValidator();

  private static ApiInventoryOperationPost minimalPost() {
    ApiInventoryOperationPost post = new ApiInventoryOperationPost();
    post.setOperationType("aliquot");
    ApiInventoryOperationOriginUpdate origin = new ApiInventoryOperationOriginUpdate();
    origin.setId(1L);
    post.getOrigins().add(origin);
    return post;
  }

  private Set<String> violatedPaths(ApiInventoryOperationPost post) {
    return validator.validate(post).stream()
        .map(ConstraintViolation::getPropertyPath)
        .map(Object::toString)
        .collect(Collectors.toSet());
  }

  @Test
  void aWellFormedPostHasNoViolations() {
    assertTrue(violatedPaths(minimalPost()).isEmpty());
  }

  @Test
  void originsListIsCappedAtBindingNotOnlyInTheValidator() {
    // InventoryOperationPostValidator.MAX_ORIGINS rejects an over-long list, but only after Jackson
    // has materialised every element and the @Valid cascade has run bean validation over all of
    // them. The same ceiling at binding stops that work happening at all (security review).
    ApiInventoryOperationPost post = minimalPost();
    post.getOrigins()
        .addAll(
            Stream.generate(ApiInventoryOperationOriginUpdate::new)
                .limit(100)
                .collect(Collectors.toList()));
    assertTrue(violatedPaths(post).contains("origins"));
  }
}
