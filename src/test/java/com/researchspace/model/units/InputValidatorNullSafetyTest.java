package com.researchspace.model.units;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The class-level validators can be invoked with null values even when field-level @NotNull
 * constraints are also violated, so they must treat null as valid rather than throw.
 */
class InputValidatorNullSafetyTest {

  @Test
  void amountValidatorTreatsNullAsValid() {
    assertTrue(new AmountConstraintValidator().isValid(null, null));
  }

  @Test
  void a260ValidatorHandlesNullA260() {
    A260Input input = new A260Input();
    input.setA320(1.0);
    assertTrue(new ValidA260InputValidator().isValid(input, null));
  }

  @Test
  void a260ValidatorStillRejectsA260NotAboveA320() {
    A260Input input = new A260Input();
    input.setA260(1.0);
    input.setA320(2.0);
    assertFalse(new ValidA260InputValidator().isValid(input, null));
  }

  @Test
  void cellDoublingValidatorHandlesNullConcentrations() {
    assertTrue(new ValidCellDoublingInputValidator().isValid(new CellDoublingTimeInput(), null));
  }

  @Test
  void cellDoublingValidatorStillRejectsNonIncreasingConcentration() {
    CellDoublingTimeInput input = new CellDoublingTimeInput();
    input.setInitConc(5.0);
    input.setFinalConc(4.0);
    assertFalse(new ValidCellDoublingInputValidator().isValid(input, null));
  }
}
