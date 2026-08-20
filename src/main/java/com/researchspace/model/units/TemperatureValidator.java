package com.researchspace.model.units;

import javax.measure.Quantity;
import javax.measure.quantity.Temperature;

import tech.units.indriya.quantity.Quantities;
import tech.units.indriya.unit.Units;

/**
 * Utility class for validating temperature measurements are sane:
 * <ul>
 * <li> Units are  a recognised temperature unit
 * <li> Value is &gt= absolute zero
 * </ul>
 * 
 * If <code>value</code> is null then the validator returns <code>true</code>
 */
public class TemperatureValidator {


	public static boolean validate(Quantifiable value) {
		if (value == null) {
			return true;
		}
		if (!RSUnitDef.exists(value.getUnitId())) {
			return false;
		}
		RSUnitDef def = RSUnitDef.getUnitById(value.getUnitId());
		if (!RSUnits.TEMPERATURE_CATEGORY.equalsIgnoreCase(def.getCategory())) {
			return false;
		}
		
		Quantity<Temperature> q =  Quantities.getQuantity(value.getNumericValue(), def.getDefinition())
				.asType(Temperature.class);
		if(q.to(Units.KELVIN).getValue().doubleValue() < 0) {
			return false;
		}
		return true;
	}

}
