package com.researchspace.model.units;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.core.testutilJU5.JakartaValidatorTestJU5;
import com.researchspace.service.JsonMessageSource;
import jakarta.validation.ConstraintViolation;
import java.math.BigDecimal;
import java.util.Set;
import lombok.Value;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class TemperatureValidationTest extends JakartaValidatorTestJU5 {

  private static LocalValidatorFactoryBean validatorFactory;

  @BeforeAll
  static void configureJsonValidationMessages() {
    validatorFactory = new LocalValidatorFactoryBean();
    validatorFactory.setValidationMessageSource(new JsonMessageSource());
    validatorFactory.afterPropertiesSet();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void closeValidatorFactory() {
    validatorFactory.close();
  }

  @Value
  static class Measurable {
    @ValidTemperature QuantityInfo temperature;
  }

  @Test
  void test() {
    QuantityInfo q = new QuantityInfo();
    q.setNumericValue(new BigDecimal(0));
    q.setUnitId(RSUnitDef.CELSIUS.getId());
    Measurable m = new Measurable(q);
    assertValid(m);
    q.setNumericValue(new BigDecimal(-274));
    assertNErrors(m, 1, true);
    q.setNumericValue(new BigDecimal(-273));
    assertValid(m);

    q.setUnitId(RSUnitDef.KELVIN.getId());
    q.setNumericValue(new BigDecimal(-1));
    assertNErrors(m, 1, true);
    q.setNumericValue(new BigDecimal(0));
    assertValid(m);

    q.setUnitId(RSUnitDef.FAHRENHEIT.getId());
    q.setNumericValue(new BigDecimal(-460));
    assertNErrors(m, 1, true);
    q.setNumericValue(new BigDecimal(-459));
    assertValid(m);
    // non-existent
    q.setUnitId(500);
    assertNErrors(m, 1, true);

    // id exists but not temperature
    q.setUnitId(RSUnitDef.MICRO_GRAM.getId());
    q.setNumericValue(new BigDecimal(20));
    assertNErrors(m, 1, true);
  }

  @Test
  void aTemperatureWithAUnitButNoNumberIsInvalidNotAServerError() {
    QuantityInfo q = new QuantityInfo();
    q.setUnitId(RSUnitDef.CELSIUS.getId());
    q.setNumericValue(null);
    assertNErrors(new Measurable(q), 1, true);
  }

  @Test
  void aTemperatureWithAUnitButNoNumberSaysTheNumberIsMissingNotAbsoluteZero() {
    QuantityInfo q = new QuantityInfo();
    q.setUnitId(RSUnitDef.CELSIUS.getId());
    q.setNumericValue(null);
    assertEquals("A temperature needs a numeric value.", singleMessage(new Measurable(q)));
  }

  @Test
  void otherTemperatureFailuresKeepTheDefaultMessage() {
    QuantityInfo q = new QuantityInfo();
    q.setUnitId(RSUnitDef.CELSIUS.getId());
    q.setNumericValue(new BigDecimal(-274));
    assertEquals(DEFAULT_MESSAGE, singleMessage(new Measurable(q)));
    q.setUnitId(RSUnitDef.MICRO_GRAM.getId());
    q.setNumericValue(new BigDecimal(20));
    assertEquals(DEFAULT_MESSAGE, singleMessage(new Measurable(q)));
  }

  private static final String DEFAULT_MESSAGE =
      "Invalid temperature - must be a temperature measurement greater than absolute zero";

  private String singleMessage(Measurable m) {
    Set<ConstraintViolation<Measurable>> violations = validator.validate(m);
    assertEquals(1, violations.size());
    return violations.iterator().next().getMessage();
  }
}
