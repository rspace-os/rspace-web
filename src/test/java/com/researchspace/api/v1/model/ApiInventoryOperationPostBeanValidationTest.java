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
  void nullElementsInTheNewSamplesCollectionsAreViolationsNotServerErrors() {
    // The review's fuzzer turned "extraFields": [null] and friends into 500s: downstream code
    // iterates these lists assuming non-null elements. Element-level @NotNull turns each shape
    // into a clean 400 at binding (security review, finding 6 residue).
    ApiInventoryOperationPost post = minimalPost();
    post.getNewSample().getExtraFields().add(null);
    post.getNewSample().getTags().add(null);
    post.getNewSample().getBarcodes().add(null);
    ApiSubSample subSample = post.getNewSample().getSubSamples().get(0);
    subSample.getExtraFields().add(null);
    subSample.getNotes().add(null);

    Set<String> violated = violatedPaths(post);
    for (String path :
        Set.of(
            "newSample.extraFields[0]",
            "newSample.tags[0]",
            "newSample.barcodes[0]",
            "newSample.subSamples[0].extraFields[0]",
            "newSample.subSamples[0].notes[0]")) {
      assertTrue(
          violated.stream().anyMatch(violation -> violation.startsWith(path)),
          () -> "expected a violation under " + path + ", got: " + violated);
    }
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
