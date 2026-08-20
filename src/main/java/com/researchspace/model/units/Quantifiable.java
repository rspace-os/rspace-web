package com.researchspace.model.units;

import java.math.BigDecimal;

/**
 * Quantifiable enables implementing classes to be measured with a specified unit.
 */
public interface Quantifiable {
	
	/**
	 * The numeric value of the quantity. Handles decimal numbers (and fractions) of arbitrary precision.
	 * @return
	 */
	BigDecimal getNumericValue();

	/**
	 * The id of an {@link RSUnitDef}
	 * @return
	 */
	Integer getUnitId();

}
