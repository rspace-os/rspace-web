package com.researchspace.model.units;

import java.util.Arrays;
import java.util.Optional;

import javax.measure.Unit;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.ToString;
import tech.units.indriya.AbstractUnit;
import tech.units.indriya.unit.Units;

/**
 * Defines a unit for use throughout RSpace. This class is a light wrapper around the javax.measure classes,
 * and wraps instances of these unit classes in the <code>definition</code> field
 * 
 * The 'order' field orders the scaling of units and is used to generate an appropriate string representation
 * for quantities in mixed units (the underlying library shows measurements in std units which is sometimes
 * hard to read, e.g. 0.0001g where 100ug would be more easily read.
 * 
 */
@ToString
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum RSUnitDef {

	// no quantity type
	DIMENSIONLESS(AbstractUnit.ONE, 1, RSUnits.DIMENSIONLESS_CATEGORY,1, "items"),

	//VOLUME
	MICRO_LITRE(RSUnits.MICROLITRE, 2, RSUnits.VOLUME_CATEGORY,3), // starts with 3 as smaller units defined down in enum
	MILLI_LITRE(RSUnits.MILLILITRE, 3, RSUnits.VOLUME_CATEGORY,4),
	LITRE(Units.LITRE, 4, RSUnits.VOLUME_CATEGORY,5),

	//MASS
	MICRO_GRAM(RSUnits.MICROGRAM, 5, RSUnits.MASS_CATEGORY,3), // starts with 3 as smaller units defined down in enum
	MILLI_GRAM(RSUnits.MILLIGRAM, 6, RSUnits.MASS_CATEGORY,4),
	GRAM(Units.GRAM, 7, RSUnits.MASS_CATEGORY,5),
	
	//TEMP
	CELSIUS(Units.CELSIUS, 8, RSUnits.TEMPERATURE_CATEGORY,1),
	KELVIN(Units.KELVIN, 9, RSUnits.TEMPERATURE_CATEGORY,10),
	FAHRENHEIT(RSUnits.FAHRENHEIT, 10, RSUnits.TEMPERATURE_CATEGORY,20),
	
	// MOLARITY
	NANOMOLAR(RSUnits.NANO_MOLAR, 11, RSUnits.MOLARITY_CATEGORY, 1),
	MICROMOLAR(RSUnits.MICRO_MOLAR, 12, RSUnits.MOLARITY_CATEGORY, 2),
	MILLIMOLAR(RSUnits.MILLI_MOLAR, 13, RSUnits.MOLARITY_CATEGORY, 3),
	MOLAR(RSUnits.MOLAR, 14, RSUnits.MOLARITY_CATEGORY, 4),

	// CONCENTRATION
	MICROGM_PER_MICROLITRE(RSUnits.MICROGM_PER_MICROL, 15, RSUnits.CONCENTRATION_CATEGORY, 1),
	MGMS_PER_ML(RSUnits.MILLIGM_PER_ML, 16, RSUnits.CONCENTRATION_CATEGORY, 2),
	GMS_PER_L(RSUnits.GM_PER_L, 17, RSUnits.CONCENTRATION_CATEGORY, 3),

	// VOLUME - extended
	PICO_LITRE(RSUnits.PICOLITRE, 18, RSUnits.VOLUME_CATEGORY,1),
	NANO_LITRE(RSUnits.NANOLITRE, 19, RSUnits.VOLUME_CATEGORY,2),

	// MASS - extended
	PICO_GRAM(RSUnits.PICOGRAM, 20, RSUnits.MASS_CATEGORY,1),
	NANO_GRAM(RSUnits.NANOGRAM, 21, RSUnits.MASS_CATEGORY,2),
	KILO(Units.KILOGRAM, 22, RSUnits.MASS_CATEGORY,6),

	//VOLUME (ALTERNATIVE)
	CUBIC_MILLIMETRE(RSUnits.CUBIC_MILLIMETRE, 23, RSUnits.VOLUME_CATEGORY,11),
	CUBIC_CENTIMETRE(RSUnits.CUBIC_CENTIMETRE, 24, RSUnits.VOLUME_CATEGORY,12),
	CUBIC_DECIMETRE(RSUnits.CUBIC_DECIMETRE, 25, RSUnits.VOLUME_CATEGORY,13),
	CUBIC_METRE(RSUnits.CUBIC_METRE, 26, RSUnits.VOLUME_CATEGORY,14)

	;
	
	/**
	 * Gets an RSUnitDef by its ID
	 * @param id
	 * @return
	 * @throws IllegalArgumentException if id does not exist
	 */
	public static RSUnitDef getUnitById(Integer id) {
		return getUnitByIdOptional(id).orElseThrow(()->new IllegalArgumentException("invalid ID for unit"));
	}

	private static Optional<RSUnitDef> getUnitByIdOptional(Integer id) {
		return Arrays.stream(values()).filter(def->def.getId().equals(id)).findFirst();
	}
	
	public static Optional<RSUnitDef> getUnitDefByUnit(Unit<?> unit) {
		return Arrays.stream(values()).filter(def->def.getDefinition().equals(unit))
				.findFirst();
	}

	private static Optional<RSUnitDef> getUnitByCategoryAndConversionOrder(String category, Integer conversionOrder) {
		return Arrays.stream(values())
				.filter(def->category.equals(def.getCategory()) && conversionOrder.equals(def.getOrder()))
				.findFirst();
	}
	
	
	/**
	 * Boolean test for whether a unit with the given ID exists
	 * @param id
	 * @return <code>true</code> if exists, <code>false</code>otherwise
	 */
	public static  boolean exists(Integer id) {
		return Arrays.stream(values()).anyMatch(def->def.getId().equals(id));
	}
	@JsonIgnore
	public Unit<?> getDefinition() {
		return definition;
	}

	public Integer getId() {
		return id;
	}

	public String getLabel() {
		return label;
	}

	public String getCategory() {
		return category;
	}
	
	Unit<?> definition;
	private Integer id;
	private String label;
	private String category;
	private Integer order;

	private RSUnitDef(Unit<?> definition, int id, String category, int order, String label) {
		this(definition, id, category, order);
		this.label = label;
	}
	
	private RSUnitDef(Unit<?> definition, int id, String category, int order) {
		this.definition = definition;
		this.id = id;
		this.label = definition.toString();
		this.category = category;
		this.order = order;
	}
	
	@JsonIgnore
	public boolean isMass () {
		return definition.isCompatible(Units.GRAM);
	}
	@JsonIgnore
	public boolean isVolume () {
		return definition.isCompatible(Units.LITRE);
	}
	
	@JsonIgnore
	public boolean isTemperature () {
		return definition.isCompatible(Units.KELVIN);
	}
	
	@JsonIgnore
	public boolean isDimensionless() {
		return definition.isCompatible(AbstractUnit.ONE);
	}
	/**
	 * Convenience for whether unit is mass, volume or dimensionless for measuring the physical amount of something.
	 */
	@JsonIgnore
	public boolean isAmount () {
		return isMass() || isVolume() || isDimensionless();
	}

	/**
	 * An ordering for the units based on increasing unit size within a given category. 
	 * Units with subsequent display order values (e.g. 8,9,10) are auto-convertible after QuantityUtils operations 
	 */
	@JsonIgnore
	public Integer getOrder() {
		return order;
	}

	/**
	 * Convenience for whether two definitions are compatible.
	 */
	@JsonIgnore
	public boolean isComparable(RSUnitDef otherUnit) {
		return getDefinition().isCompatible(otherUnit.getDefinition());
	}

	/**
	 * Given a unit definition returns smaller unit definition
	 * from the same conversion family, if such smaller unit exists. 
	 * 
	 * @param initialUnit for which the smaller one is looked for
	 * @return smaller unit, or null if passed unit is already smallest
	 */
	public static RSUnitDef getUnitSmallerThan(RSUnitDef initialUnit) {
		Optional<RSUnitDef> prevUnit = getUnitByCategoryAndConversionOrder(initialUnit.getCategory(), initialUnit.getOrder() - 1);
		if (prevUnit.isPresent() && prevUnit.get().getCategory().equals(initialUnit.getCategory())) {
			return prevUnit.get();
		}
		return null; // already smallest unit in the convertable category
	}

	/**
	 * Given a unit definition returns larger unit definition 
	 * from the same conversion family, if such larger unit exists. 
	 * 
	 * @param initialUnit for which the larger one is looked for
	 * @return larger unit, or null if passed unit is already largest 
	 */
	public static RSUnitDef getUnitLargerThan(RSUnitDef initialUnit) {
		Optional<RSUnitDef> nextUnit = getUnitByCategoryAndConversionOrder(initialUnit.getCategory(), initialUnit.getOrder() + 1);
		if (nextUnit.isPresent() && nextUnit.get().getCategory().equals(initialUnit.getCategory())) {
			return nextUnit.get();
		}
		return null; // already largest unit in the convertable category
	}

}
