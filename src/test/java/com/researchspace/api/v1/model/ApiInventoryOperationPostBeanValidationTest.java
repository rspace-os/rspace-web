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
 * The operations endpoint binds its body as {@code @Valid ApiInventoryOperationPost}, so every Bean
 * Validation constraint ordinary sample creation enforces must cascade through this DTO graph too
 * (newSample, its subSamples, their notes). Without the cascade an operation request could smuggle
 * in what POST /samples rejects, e.g. an over-sized image. Uses the plain Jakarta validator, the
 * same engine Spring's request-body validation delegates to.
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
    ApiSampleWithFullSubSamples sample = new ApiSampleWithFullSubSamples("Aliquots");
    sample.getSubSamples().add(new ApiSubSample());
    post.setNewSample(sample);
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
  void imageSizeConstraintCascadesIntoTheNewSample() {
    ApiInventoryOperationPost post = minimalPost();
    post.getNewSample().setNewBase64Image("x".repeat(10_000_001));
    assertTrue(violatedPaths(post).contains("newSample.newBase64Image"));
  }

  @Test
  void imageSizeConstraintCascadesIntoEachExplicitSubSample() {
    ApiInventoryOperationPost post = minimalPost();
    post.getNewSample().getSubSamples().get(0).setNewBase64Image("x".repeat(10_000_001));
    assertTrue(violatedPaths(post).contains("newSample.subSamples[0].newBase64Image"));
  }

  @Test
  void noteContentConstraintsCascadeIntoEachSubSampleNote() {
    ApiInventoryOperationPost post = minimalPost();
    post.getNewSample()
        .getSubSamples()
        .get(0)
        .getNotes()
        .add(new ApiSubSampleNote("x".repeat(ApiSubSampleNote.MAX_CONTENT_LENGTH + 1)));
    assertTrue(violatedPaths(post).contains("newSample.subSamples[0].notes[0].content"));
  }

  @Test
  void explicitSubSamplesListIsCappedAt100() {
    // newSampleSubSamplesCount is capped at 100 by SampleApiPostFullValidator, but that cap never
    // applied to an explicitly supplied subSamples list; each subsample costs a full create cycle,
    // so the list gets the same ceiling.
    ApiInventoryOperationPost post = minimalPost();
    post.getNewSample()
        .getSubSamples()
        .addAll(Stream.generate(ApiSubSample::new).limit(100).collect(Collectors.toList()));
    assertTrue(violatedPaths(post).contains("newSample.subSamples"));
  }
}
