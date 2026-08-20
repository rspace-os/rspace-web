package com.researchspace.model.units;

import static com.researchspace.model.units.RSUnits.DIMENSIONLESS_CATEGORY;
import static com.researchspace.model.units.RSUnits.MASS_CATEGORY;
import static com.researchspace.model.units.RSUnits.VOLUME_CATEGORY;

/**
 * Utility class for validating amount measurements are sane:
 * <ul>
 * <li> Units are  mass, volume or dimensionless
 * <li> Value is &gt= zero
 * </ul>
 */
public class AmountValidator {


	public static boolean validate(Quantifiable value) {
		if (!RSUnitDef.exists(value.getUnitId())) {
			return false;
		}
		RSUnitDef def = RSUnitDef.getUnitById(value.getUnitId());
		if (! (VOLUME_CATEGORY.equalsIgnoreCase(def.getCategory()) ||
				MASS_CATEGORY.equalsIgnoreCase(def.getCategory()) ||
				DIMENSIONLESS_CATEGORY.equalsIgnoreCase(def.getCategory()))) {
			return false;
		}
		return value.getNumericValue().doubleValue() >= 0;
	}

}
