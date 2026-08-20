package com.researchspace.model.units;

import static com.researchspace.core.util.TransformerUtils.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.measure.Quantity;
import javax.measure.quantity.Mass;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.researchspace.core.util.TransformerUtils;

import lombok.Value;
import tech.units.indriya.AbstractUnit;
import tech.units.indriya.unit.Units;

public class QuantityUtilsTest {
	
	QuantityUtils qUtils = new QuantityUtils();
	@Value
	static class SomeQuantifiableThing implements Quantifiable {
		BigDecimal numericValue;
		RSUnitDef unitDef;
		@Override
		public Integer getUnitId() {
			return unitDef.getId();
		}
	}

	@Test
	public void getByIdAndCalculate() {
		SomeQuantifiableThing inGrammes = new SomeQuantifiableThing(BigDecimal.valueOf(5), RSUnitDef.GRAM);
		SomeQuantifiableThing inMilliGrammes = new SomeQuantifiableThing(BigDecimal.valueOf(52), RSUnitDef.MILLI_GRAM);
		
		Quantity<Mass> q1 = qUtils.getQuantityFor(inGrammes, Mass.class);
		Quantity<Mass> q2 = qUtils.getQuantityFor(inMilliGrammes, Mass.class);
		Quantity<? extends Quantity<?>> sum = q1.add(q2);
		assertEquals(5.052, sum.getValue().doubleValue(),0.001);
		assertEquals(Units.GRAM, sum.getUnit());
	}
	
	@Test
	public void isComparable() {
		SomeQuantifiableThing inGrammes = new SomeQuantifiableThing(BigDecimal.valueOf(5), RSUnitDef.GRAM);
		SomeQuantifiableThing inMilliGrammes = new SomeQuantifiableThing(BigDecimal.valueOf(52), RSUnitDef.MILLI_GRAM);
		List<Quantifiable> stc = toList(inGrammes, inMilliGrammes);
		assertTrue(qUtils.isComparableQuantities(stc));
		stc.add(new SomeQuantifiableThing(BigDecimal.valueOf(5), RSUnitDef.DIMENSIONLESS));
		assertFalse(qUtils.isComparableQuantities(stc));
	}
	
	@Test
	public void sumValidation() {
		assertThrows(IllegalArgumentException.class, ()->qUtils.sum(Collections.emptyList()));
	}
	
	@Test
	public void sumMassReturnsAppropriateUnitsInString() {
		SomeQuantifiableThing inMicroGrammes = new SomeQuantifiableThing(BigDecimal.valueOf(37), RSUnitDef.MICRO_GRAM);
		SomeQuantifiableThing inMicroGrammes2 = new SomeQuantifiableThing(BigDecimal.valueOf(52), RSUnitDef.MICRO_GRAM);
		QuantityInfo sum = qUtils.sum(toList(inMicroGrammes, inMicroGrammes2));
		assertEquals("89 µg", sum.toPlainString());
		
		SomeQuantifiableThing inMillis = new SomeQuantifiableThing(BigDecimal.valueOf(17), RSUnitDef.MILLI_GRAM);
		sum = qUtils.sum(toList(inMicroGrammes, inMicroGrammes2, inMillis));
		assertEquals("17.089 mg", sum.toPlainString());
		
		SomeQuantifiableThing inGr = new SomeQuantifiableThing(BigDecimal.valueOf(4), RSUnitDef.GRAM);
		sum = qUtils.sum(toList(inMicroGrammes, inMicroGrammes2, inMillis, inGr));
		assertEquals("4.017 g", sum.toPlainString());
	}

	@Test
	public void sumChangesUnitIfAppropriate() {
		// sum changes unit in some cases 
		SomeQuantifiableThing inMicroGrammes = new SomeQuantifiableThing(BigDecimal.valueOf(1500), RSUnitDef.MICRO_GRAM);
		SomeQuantifiableThing inMicroGrammes2 = new SomeQuantifiableThing(BigDecimal.valueOf(499), RSUnitDef.MICRO_GRAM);
		QuantityInfo sum = qUtils.sum(toList(inMicroGrammes, inMicroGrammes2));
		assertEquals("1999 µg", sum.toPlainString());

		SomeQuantifiableThing inMicroGrammes3 = new SomeQuantifiableThing(BigDecimal.valueOf(1), RSUnitDef.MICRO_GRAM);
		sum = qUtils.sum(toList(sum, inMicroGrammes3));
		assertEquals("2 mg", sum.toPlainString());
		
		SomeQuantifiableThing inMicroGrammes4 = new SomeQuantifiableThing(BigDecimal.valueOf(-2), RSUnitDef.MICRO_GRAM);
		sum = qUtils.sum(toList(sum, inMicroGrammes4));
		assertEquals("1.998 mg", sum.toPlainString());

		SomeQuantifiableThing inGrammes1 = new SomeQuantifiableThing(BigDecimal.valueOf(-1), RSUnitDef.MILLI_GRAM);
		sum = qUtils.sum(toList(sum, inGrammes1));
		assertEquals("998 µg", sum.toPlainString());

		// doesn't change unit in other case
		SomeQuantifiableThing inMicroGrammes5 = new SomeQuantifiableThing(BigDecimal.valueOf(553), RSUnitDef.MICRO_GRAM);
		SomeQuantifiableThing inMicroGrammes6 = new SomeQuantifiableThing(BigDecimal.valueOf(552), RSUnitDef.MICRO_GRAM);
		sum = qUtils.sum(toList(inMicroGrammes5, inMicroGrammes6));
		assertEquals("1105 µg", sum.toPlainString());

		SomeQuantifiableThing inGrammes2 = new SomeQuantifiableThing(BigDecimal.valueOf(553), RSUnitDef.GRAM);
		SomeQuantifiableThing inGrammes3 = new SomeQuantifiableThing(BigDecimal.valueOf(552), RSUnitDef.GRAM);
		sum = qUtils.sum(toList(inGrammes2, inGrammes3));
		assertEquals("1105 g", sum.toPlainString());

		SomeQuantifiableThing inGrammes4 = new SomeQuantifiableThing(BigDecimal.valueOf(500), RSUnitDef.GRAM);
		SomeQuantifiableThing inGrammes5 = new SomeQuantifiableThing(BigDecimal.valueOf(1500), RSUnitDef.GRAM);
		sum = qUtils.sum(toList(inGrammes4, inGrammes5));
		assertEquals("2 kg", sum.toPlainString());

		// keeps original unit if result is 0
		SomeQuantifiableThing inGrammes2Negative = new SomeQuantifiableThing(BigDecimal.valueOf(-553), RSUnitDef.GRAM);
		sum = qUtils.sum(toList(inGrammes2, inGrammes2Negative));
		assertEquals("0 g", sum.toPlainString());
	}
	
	@Test
	public void sumDimensionless() {
		SomeQuantifiableThing q1 = new SomeQuantifiableThing(BigDecimal.valueOf(5), RSUnitDef.DIMENSIONLESS);
		SomeQuantifiableThing q2 = new SomeQuantifiableThing(BigDecimal.valueOf(52), RSUnitDef.DIMENSIONLESS);
		QuantityInfo sum = qUtils.sum(toList(q1, q2));
		assertEquals("57 items", sum.toPlainString());
		assertEquals(57, sum.getNumericValue().intValue());
		assertEquals(AbstractUnit.ONE, RSUnitDef.getUnitById(sum.getUnitId()).getDefinition());
	}
	
	@Test
	public void divideValidation() {
		IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () -> qUtils.divide(null, null));
		assertEquals("divide method requires dividend quantity and non-zero divisor as parameters", iae.getMessage());

		SomeQuantifiableThing zeroQuantity = new SomeQuantifiableThing(BigDecimal.valueOf(0), RSUnitDef.MICRO_GRAM);
		iae = assertThrows(IllegalArgumentException.class, () -> qUtils.divide(zeroQuantity, zeroQuantity.getNumericValue()));
		assertEquals("divide method requires dividend quantity and non-zero divisor as parameters", iae.getMessage());

		// sanity check
		SomeQuantifiableThing zeroDotOneMgQuantity = new SomeQuantifiableThing(BigDecimal.valueOf(0.1), RSUnitDef.MILLI_GRAM);
		QuantityInfo result = qUtils.divide(zeroDotOneMgQuantity, BigDecimal.valueOf(2));
		assertEquals("50 µg", result.toPlainString());
	}
	
	@Test
	public void divideMassReturnsAppropriateUnitsInString() {
		
		SomeQuantifiableThing inMilliGrammes = new SomeQuantifiableThing(BigDecimal.valueOf(4), RSUnitDef.MILLI_GRAM);
		QuantityInfo result = qUtils.divide(inMilliGrammes, BigDecimal.valueOf(2));
		assertEquals("2 mg", result.toPlainString());
		result = qUtils.divide(inMilliGrammes, BigDecimal.valueOf(3));
		assertEquals("1.333 mg", result.toPlainString());
		result = qUtils.divide(inMilliGrammes, BigDecimal.valueOf(16));
		assertEquals("250 µg", result.toPlainString());

		SomeQuantifiableThing inMicroGrammes = new SomeQuantifiableThing(BigDecimal.valueOf(37), RSUnitDef.MICRO_GRAM);
		result = qUtils.divide(inMicroGrammes, BigDecimal.valueOf(2));
		assertEquals("18.5 µg", result.toPlainString());
		result = qUtils.divide(inMicroGrammes, BigDecimal.valueOf(740));
		assertEquals("50 ng", result.toPlainString());

		SomeQuantifiableThing inNanoGrammes = new SomeQuantifiableThing(BigDecimal.valueOf(2), RSUnitDef.NANO_GRAM);
		result = qUtils.divide(inNanoGrammes, BigDecimal.valueOf(3));
		assertEquals("666.667 pg", result.toPlainString());
		result = qUtils.divide(inNanoGrammes, BigDecimal.valueOf(20));
		assertEquals("100 pg", result.toPlainString());

		// check rounding at the end of precision 
		SomeQuantifiableThing onePicoGram = new SomeQuantifiableThing(BigDecimal.valueOf(1), RSUnitDef.PICO_GRAM);
		result = qUtils.divide(onePicoGram, BigDecimal.valueOf(50));
		assertEquals("0.02 pg", result.toPlainString());
		result = qUtils.divide(onePicoGram, BigDecimal.valueOf(500));
		assertEquals("0.002 pg", result.toPlainString()); 
		result = qUtils.divide(onePicoGram, BigDecimal.valueOf(1500));
		assertEquals("0.001 pg", result.toPlainString()); // rounds to 0.001
		result = qUtils.divide(onePicoGram, BigDecimal.valueOf(2001));
		assertEquals("0 pg", result.toPlainString()); // rounds to 0
	}

	@Test
	public void divideVolumeReturnsAppropriateUnitsInString() {

		SomeQuantifiableThing inLiters = new SomeQuantifiableThing(BigDecimal.valueOf(4), RSUnitDef.LITRE);
		QuantityInfo result = qUtils.divide(inLiters, BigDecimal.valueOf(2));
		assertEquals("2 l", result.toPlainString());
		result = qUtils.divide(inLiters, BigDecimal.valueOf(80));
		assertEquals("50 ml", result.toPlainString());

		SomeQuantifiableThing inMicroLiters = new SomeQuantifiableThing(BigDecimal.valueOf(5), RSUnitDef.MICRO_LITRE);
		result = qUtils.divide(inMicroLiters, BigDecimal.valueOf(2));
		assertEquals("2.5 µl", result.toPlainString());
		result = qUtils.divide(inMicroLiters, BigDecimal.valueOf(40));
		assertEquals("125 nl", result.toPlainString());

		SomeQuantifiableThing inNanoLitres = new SomeQuantifiableThing(BigDecimal.valueOf(2), RSUnitDef.NANO_LITRE);
		result = qUtils.divide(inNanoLitres, BigDecimal.valueOf(4));
		assertEquals("500 pl", result.toPlainString());
	}
	
	
	@Test
	public void divideDimensionless() {
		SomeQuantifiableThing q1 = new SomeQuantifiableThing(BigDecimal.valueOf(5), RSUnitDef.DIMENSIONLESS);
		QuantityInfo sum = qUtils.divide(q1, BigDecimal.valueOf(25));
		assertEquals("0.2 items", sum.toPlainString());
	}

	@Test
	public void sumCubicAndLiterVolumes() {
		SomeQuantifiableThing q1 = new SomeQuantifiableThing(BigDecimal.valueOf(5), RSUnitDef.CUBIC_METRE);
		SomeQuantifiableThing q2 = new SomeQuantifiableThing(BigDecimal.valueOf(3), RSUnitDef.CUBIC_DECIMETRE);
		SomeQuantifiableThing q3 = new SomeQuantifiableThing(BigDecimal.valueOf(2), RSUnitDef.CUBIC_CENTIMETRE);
		SomeQuantifiableThing q4 = new SomeQuantifiableThing(BigDecimal.valueOf(1), RSUnitDef.LITRE);
		QuantityInfo sum = qUtils.sum(toList(q1, q2));
		assertEquals("5.003 ㎥", sum.toPlainString());
		sum = qUtils.sum(toList(q2, q3));
		assertEquals("3.002 dm³", sum.toPlainString());
		sum = qUtils.sum(toList(q2, q3, q4));
		assertEquals("4.002 dm³", sum.toPlainString());
	}

	@Test
	public void divideCubicVolumes() {
		SomeQuantifiableThing inCubicMetres = new SomeQuantifiableThing(BigDecimal.valueOf(1), RSUnitDef.CUBIC_METRE);
		QuantityInfo result = qUtils.divide(inCubicMetres, BigDecimal.valueOf(2));
		assertEquals("500 dm³", result.toPlainString());
		SomeQuantifiableThing inCubicDeciMetres = new SomeQuantifiableThing(BigDecimal.valueOf(1), RSUnitDef.CUBIC_DECIMETRE);
		result = qUtils.divide(inCubicDeciMetres, BigDecimal.valueOf(3));
		assertEquals("333.333 cm³", result.toPlainString());
		SomeQuantifiableThing inCubicCentiMetres = new SomeQuantifiableThing(BigDecimal.valueOf(1), RSUnitDef.CUBIC_CENTIMETRE);
		result = qUtils.divide(inCubicCentiMetres, BigDecimal.valueOf(4));
		assertEquals("250 mm³", result.toPlainString());
		result = qUtils.divide(inCubicCentiMetres, BigDecimal.valueOf(5000));
		assertEquals("0.2 mm³", result.toPlainString());
	}
	
	@Test
	public void comparatorTest() {
		// pure numeric ordering is 1,3,2
		SomeQuantifiableThing q1 = new SomeQuantifiableThing(BigDecimal.valueOf(5), RSUnitDef.GRAM);
		SomeQuantifiableThing q2 = new SomeQuantifiableThing(BigDecimal.valueOf(75), RSUnitDef.MICRO_GRAM);
		SomeQuantifiableThing q3 = new SomeQuantifiableThing(BigDecimal.valueOf(36), RSUnitDef.MILLI_GRAM);
		
		// correct ordering is 2,3,1 (ascending)
		List<Quantifiable> toSort = TransformerUtils.toList(q1,q2,q3);
		Collections.sort(toSort, qUtils.MassComparator);
		assertEquals(q2, toSort.get(0));
		assertEquals(q3, toSort.get(1));
		assertEquals(q1, toSort.get(2));
		
		// correct ordering is 1,3,2 (descending)
		Collections.sort(toSort, Collections.reverseOrder(qUtils.MassComparator));
		assertEquals(q1, toSort.get(0));
		assertEquals(q3, toSort.get(1));
		assertEquals(q2, toSort.get(2));
	}
	
	@Test
	@DisplayName("An invalid quantity type is ordered last")
	public void invalidUnitComparisonIsOrderedLast() {
		SomeQuantifiableThing q1 = new SomeQuantifiableThing(BigDecimal.valueOf(5), RSUnitDef.LITRE);
		SomeQuantifiableThing q2 = new SomeQuantifiableThing(BigDecimal.valueOf(75), RSUnitDef.MICRO_LITRE);
		SomeQuantifiableThing q3 = new SomeQuantifiableThing(BigDecimal.valueOf(36), RSUnitDef.MILLI_LITRE);
		SomeQuantifiableThing q4 = new SomeQuantifiableThing(BigDecimal.valueOf(23), RSUnitDef.MILLI_GRAM);
		
		// correct ordering is 2,3,1 (ascending)
		List<Quantifiable> toSort = TransformerUtils.toList(q1,q2,q3,q4);
		Comparator<Quantifiable> cmp = qUtils.getComparatorFor(q1);
		Collections.sort(toSort, cmp);
		assertEquals(q4, toSort.get(3));
	}

	@Test
	@DisplayName("Parse quantity info out of various Strings")
	public void parseQuantityInfoStrings() {

		// mass units
		QuantityInfo quantityInfo = QuantityUtils.parseQuantityInfo("2kg");
		assertEquals("2 kg", quantityInfo.toPlainString());
		quantityInfo = QuantityUtils.parseQuantityInfo("5.5 g");
		assertEquals("5.5 g", quantityInfo.toPlainString());
		quantityInfo = QuantityUtils.parseQuantityInfo("1000mg");
		assertEquals("1000 mg", quantityInfo.toPlainString());
		quantityInfo = QuantityUtils.parseQuantityInfo("0.21 μg");
		assertEquals("0.21 µg", quantityInfo.toPlainString());
		quantityInfo = QuantityUtils.parseQuantityInfo("12.2ng");
		assertEquals("12.2 ng", quantityInfo.toPlainString());
		quantityInfo = QuantityUtils.parseQuantityInfo("0.4 pg");
		assertEquals("0.4 pg", quantityInfo.toPlainString());
		
		// volume units
		quantityInfo = QuantityUtils.parseQuantityInfo("12l");
		assertEquals("12 l", quantityInfo.toPlainString());
		quantityInfo = QuantityUtils.parseQuantityInfo("5.25 ml");
		assertEquals("5.25 ml", quantityInfo.toPlainString());
		quantityInfo = QuantityUtils.parseQuantityInfo("0.01µl");
		assertEquals("0.01 µl", quantityInfo.toPlainString());
		quantityInfo = QuantityUtils.parseQuantityInfo("0.51 nl");
		assertEquals("0.51 nl", quantityInfo.toPlainString());
		quantityInfo = QuantityUtils.parseQuantityInfo("16.3pl");
		assertEquals("16.3 pl", quantityInfo.toPlainString());

		// cubic volume units
		quantityInfo = QuantityUtils.parseQuantityInfo("4m³");
		assertEquals("4 ㎥", quantityInfo.toPlainString());
		quantityInfo = QuantityUtils.parseQuantityInfo("3 dm³");
		assertEquals("3 dm³", quantityInfo.toPlainString());
		quantityInfo = QuantityUtils.parseQuantityInfo("2.15cm³");
		assertEquals("2.15 cm³", quantityInfo.toPlainString());
		quantityInfo = QuantityUtils.parseQuantityInfo("0.5 mm³");
		assertEquals("0.5 mm³", quantityInfo.toPlainString());
		
		// dimensionless
		quantityInfo = QuantityUtils.parseQuantityInfo("15");
		assertEquals("15 items", quantityInfo.toPlainString());

		// edge cases
		assertNull(QuantityUtils.parseQuantityInfo(""));
		assertNull(QuantityUtils.parseQuantityInfo(null));

		// various parsing errors returned as IAE
		// unit unknown to library
		IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () -> QuantityUtils.parseQuantityInfo("2 tonne"));
		assertEquals("Cannot parse quantity string: Parse Error - tonne not recognized (in 2 tonne at index 2)", iae.getMessage());
		// unit parseable by library, but unknown to rspace
		iae = assertThrows(IllegalArgumentException.class, () -> QuantityUtils.parseQuantityInfo("2 mol"));
		assertEquals("Cannot parse quantity string: unit not recognized [mol]", iae.getMessage());
		// invalid string
		iae = assertThrows(IllegalArgumentException.class, () -> QuantityUtils.parseQuantityInfo("asdf"));
		assertEquals("Cannot parse quantity string: Failed to parse number-literal 'asdf'.", iae.getMessage());
	}

}
