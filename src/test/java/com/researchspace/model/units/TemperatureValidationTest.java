package com.researchspace.model.units;

import java.math.BigDecimal;

import com.researchspace.core.testutilJU5.JakartaValidatorTestJU5;
import org.junit.jupiter.api.Test;

import lombok.Value;

class TemperatureValidationTest extends JakartaValidatorTestJU5 {
	
	@Value
	static class Measurable {
		@ValidTemperature
		QuantityInfo temperature;
	}

	@Test
	void test() {
		QuantityInfo q = new QuantityInfo();
		q.setNumericValue(new BigDecimal(0));
		q.setUnitId(RSUnitDef.CELSIUS.getId());
		Measurable m = new Measurable(q);
		assertValid(m);	
		// c
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

}
