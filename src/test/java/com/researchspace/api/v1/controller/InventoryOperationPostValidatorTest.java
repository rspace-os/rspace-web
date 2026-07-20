package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.units.RSUnitDef;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

class InventoryOperationPostValidatorTest {

  private final InventoryOperationPostValidator validator = new InventoryOperationPostValidator();

  private Errors validate(ApiInventoryOperationPost request) {
    Errors errors = new BeanPropertyBindingResult(request, "request");
    validator.validate(request, errors);
    return errors;
  }

  private static ApiInventoryOperationPost validRequest() {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));
    ApiInventoryOperationOriginUpdate origin = new ApiInventoryOperationOriginUpdate();
    origin.setId(100L);
    origin.setAmountTaken(new ApiQuantityInfo(new BigDecimal("0.6"), 3));
    List<ApiInventoryOperationOriginUpdate> origins = new ArrayList<>();
    origins.add(origin);
    request.setOrigins(origins);
    return request;
  }

  @Test
  void validRequestHasNoErrors() {
    assertFalse(validate(validRequest()).hasErrors());
  }

  @Test
  void rejectsMissingNewSample() {
    ApiInventoryOperationPost request = validRequest();
    request.setNewSample(null);
    assertTrue(validate(request).hasFieldErrors("newSample"));
  }

  @Test
  void rejectsBlankSampleName() {
    ApiInventoryOperationPost request = validRequest();
    request.setNewSample(new ApiSampleWithFullSubSamples("   "));
    assertTrue(validate(request).hasFieldErrors("newSample.name"));
  }

  @Test
  void rejectsEmptyOrigins() {
    ApiInventoryOperationPost request = validRequest();
    request.setOrigins(new ArrayList<>());
    assertTrue(validate(request).hasFieldErrors("origins"));
  }

  @Test
  void rejectsOriginWithoutId() {
    ApiInventoryOperationPost request = validRequest();
    request.getOrigins().get(0).setId(null);
    assertTrue(validate(request).hasErrors());
  }

  @Test
  void rejectsNegativeAmountTaken() {
    ApiInventoryOperationPost request = validRequest();
    request.getOrigins().get(0).setAmountTaken(new ApiQuantityInfo(new BigDecimal("-1"), 3));
    assertTrue(validate(request).hasErrors());
  }

  @Test
  void rejectsZeroAmountTaken() {
    ApiInventoryOperationPost request = validRequest();
    request.getOrigins().get(0).setAmountTaken(new ApiQuantityInfo(BigDecimal.ZERO, 3));
    assertTrue(validate(request).hasErrors());
  }

  @Test
  void rejectsMissingAmountTaken() {
    ApiInventoryOperationPost request = validRequest();
    request.getOrigins().get(0).setAmountTaken(null);
    assertTrue(validate(request).hasErrors());
  }

  @Test
  void rejectsAmountTakenWithoutNumericValue() {
    ApiInventoryOperationPost request = validRequest();
    request.getOrigins().get(0).setAmountTaken(new ApiQuantityInfo(null, 3));
    assertTrue(validate(request).hasErrors());
  }

  private static ApiQuantityInfo grams(String value) {
    return new ApiQuantityInfo(new BigDecimal(value), RSUnitDef.GRAM.getId());
  }

  @Test
  void detectsOverRemovalInTheSameUnit() {
    assertTrue(InventoryOperationPostValidator.amountTakenExceedsOrigin(grams("6"), grams("5")));
  }

  @Test
  void allowsTakingUpToAndWithinTheOriginQuantity() {
    assertFalse(InventoryOperationPostValidator.amountTakenExceedsOrigin(grams("5"), grams("5")));
    assertFalse(InventoryOperationPostValidator.amountTakenExceedsOrigin(grams("4"), grams("5")));
  }

  @Test
  void comparesUnitAwareAcrossUnitsInTheSameCategory() {
    // 0.006 kg = 6 g, which exceeds a 5 g origin.
    ApiQuantityInfo sixGramsAsKilos =
        new ApiQuantityInfo(new BigDecimal("0.006"), RSUnitDef.KILO.getId());
    assertTrue(
        InventoryOperationPostValidator.amountTakenExceedsOrigin(sixGramsAsKilos, grams("5")));
    ApiQuantityInfo fourGramsAsKilos =
        new ApiQuantityInfo(new BigDecimal("0.004"), RSUnitDef.KILO.getId());
    assertFalse(
        InventoryOperationPostValidator.amountTakenExceedsOrigin(fourGramsAsKilos, grams("5")));
  }

  @Test
  void doesNotFlagNullAmountTakenOrDifferentCategories() {
    assertFalse(InventoryOperationPostValidator.amountTakenExceedsOrigin(null, grams("5")));
    // a volume amount against a mass origin is not commensurate, so it is not treated as
    // over-removal
    ApiQuantityInfo sixMillilitres =
        new ApiQuantityInfo(new BigDecimal("6"), RSUnitDef.MILLI_LITRE.getId());
    assertFalse(
        InventoryOperationPostValidator.amountTakenExceedsOrigin(sixMillilitres, grams("5")));
  }

  @Test
  void flagsPositiveAmountTakenFromOriginWithNoQuantity() {
    // A subsample whose quantity was never set holds nothing, so taking any positive amount from it
    // is over-removal (adr/0005). A null origin quantity, or one with a null numeric value, is
    // treated as zero available rather than as "no limit".
    assertTrue(InventoryOperationPostValidator.amountTakenExceedsOrigin(grams("6"), null));
    assertTrue(
        InventoryOperationPostValidator.amountTakenExceedsOrigin(
            grams("6"), new ApiQuantityInfo(null, RSUnitDef.GRAM.getId())));
  }
}
