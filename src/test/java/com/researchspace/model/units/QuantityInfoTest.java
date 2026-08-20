package com.researchspace.model.units;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class QuantityInfoTest {
	
	@Test
	void displayStrings() {
		QuantityInfo quantity10L = QuantityInfo.of(BigDecimal.valueOf(10), RSUnitDef.LITRE);
		assertEquals("10 l", quantity10L.toPlainString());

		QuantityInfo quantity10dot0L = QuantityInfo.of(BigDecimal.valueOf(10.0), RSUnitDef.LITRE);
		assertEquals("10 l", quantity10dot0L.toPlainString());
		
		QuantityInfo quantity5dot1525MG = QuantityInfo.of(BigDecimal.valueOf(5.1525), RSUnitDef.MILLI_GRAM);
		assertEquals("5.153 mg", quantity5dot1525MG.toPlainString());
		
		QuantityInfo quantity5dot1524MG = QuantityInfo.of(BigDecimal.valueOf(5.1524), RSUnitDef.MILLI_GRAM);
		assertEquals("5.152 mg", quantity5dot1524MG.toPlainString());
	}
	
	@Test
	void copyEqualsOriginal() {
		QuantityInfo quantity10L = QuantityInfo.of(BigDecimal.valueOf(10), RSUnitDef.LITRE);
		assertEquals(quantity10L, quantity10L.copy());
	}
	
	@DisplayName("create 0 quantity for all units")
	@ParameterizedTest
	@EnumSource(RSUnitDef.class)
	void zero(RSUnitDef unitDef) {
		QuantityInfo zero = QuantityInfo.zero(unitDef);
		assertEquals(0, zero.getNumericValue().intValue());
	}

	@Test
	void checkEqualsIgnoringValueScale() {
		QuantityInfo quantity1L = QuantityInfo.of(BigDecimal.valueOf(1), RSUnitDef.LITRE);
		QuantityInfo quantity1dot0L = QuantityInfo.of(BigDecimal.valueOf(1.0), RSUnitDef.LITRE);
		assertTrue(quantity1L.equals(quantity1dot0L));
	}
	
	
}
