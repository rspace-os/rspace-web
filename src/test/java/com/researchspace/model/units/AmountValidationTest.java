package com.researchspace.model.units;

import java.math.BigDecimal;

import com.researchspace.core.testutilJU5.JakartaValidatorTestJU5;
import org.junit.jupiter.api.Test;

import lombok.Value;

class AmountValidationTest extends JakartaValidatorTestJU5 {
	
	@Value
	private static class Measurable {
		@ValidAmount
		QuantityInfo amount;
	}

	@Test
	void test() {
		QuantityInfo q = new QuantityInfo();
		q.setNumericValue(new BigDecimal(0));
		
		// >=0 is ok
		q.setUnitId(RSUnitDef.MICRO_GRAM.getId());
		Measurable m = new Measurable(q);
		assertValid(m);		
		q.setNumericValue(BigDecimal.valueOf(0.00));
		assertValid(m);	
		q.setNumericValue(BigDecimal.valueOf(-0.01));
		assertNErrors(m, 1, true);
		
		q.setUnitId(RSUnitDef.LITRE.getId());
		q.setNumericValue(BigDecimal.valueOf(3.24));
		assertValid(m);	
		
		q.setUnitId(RSUnitDef.DIMENSIONLESS.getId());
		q.setNumericValue(BigDecimal.valueOf(3));
		assertValid(m);	
		
		// invalid quantity type
		q.setUnitId(RSUnitDef.CELSIUS.getId());
		q.setNumericValue(BigDecimal.valueOf(3));
		assertNErrors(m, 1, true);
		
		
		
	}

}
