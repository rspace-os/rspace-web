package com.researchspace.model.units;

import static javax.measure.MetricPrefix.CENTI;
import static javax.measure.MetricPrefix.DECI;
import static javax.measure.MetricPrefix.GIGA;
import static javax.measure.MetricPrefix.KILO;
import static javax.measure.MetricPrefix.MEGA;
import static javax.measure.MetricPrefix.MICRO;
import static javax.measure.MetricPrefix.MILLI;
import static javax.measure.MetricPrefix.NANO;
import static javax.measure.MetricPrefix.PICO;
import static tech.units.indriya.unit.Units.GRAM;
import static tech.units.indriya.unit.Units.LITRE;

import java.util.Arrays;
import java.util.List;

import javax.measure.MetricPrefix;
import javax.measure.Unit;
import javax.measure.quantity.Area;
import javax.measure.quantity.CatalyticActivity;
import javax.measure.quantity.Mass;
import javax.measure.quantity.Radioactivity;
import javax.measure.quantity.Temperature;
import javax.measure.quantity.Time;
import javax.measure.quantity.Volume;

import tech.units.indriya.function.AddConverter;
import tech.units.indriya.function.MultiplyConverter;
import tech.units.indriya.unit.TransformedUnit;
import tech.units.indriya.unit.Units;

public class RSUnits {
	public static final String DIMENSIONLESS_CATEGORY = "dimensionless";
	public static final String VOLUME_CATEGORY = "volume";
	public static final String MASS_CATEGORY = "mass";
	public static final String TEMPERATURE_CATEGORY = "temperature";
	public static final String MOLARITY_CATEGORY = "molarity";
	public static final String CONCENTRATION_CATEGORY = "concentration";

	public static final Unit<Radioactivity> curie = Units.BECQUEREL.multiply(3.7e10);
	public static final Unit<Radioactivity> millicurie = MILLI(curie);

	public static final Unit<Radioactivity> GBeq = GIGA(Units.BECQUEREL);
	public static final Unit<Radioactivity> MBeq = MEGA(Units.BECQUEREL);

	public static final Unit<Radioactivity> kBeq = KILO(Units.BECQUEREL);
	public static final Unit<Radioactivity> mBeq = MILLI(Units.BECQUEREL);

	public static final Unit<Concentration> MOLAR = Units.MOLE.divide(Units.LITRE).asType(Concentration.class);
	public static final Unit<Concentration> MILLI_MOLAR = MetricPrefix.MILLI(Units.MOLE).divide(Units.LITRE)
			.asType(Concentration.class);
	public static final Unit<Concentration> MICRO_MOLAR = MICRO(Units.MOLE).divide(Units.LITRE)
			.asType(Concentration.class);
	public static final Unit<Concentration> NANO_MOLAR = MetricPrefix.NANO(Units.MOLE).divide(Units.LITRE)
			.asType(Concentration.class);

	public static final Unit<Volume> PICOLITRE = PICO(Units.LITRE);
	public static final Unit<Volume> NANOLITRE = NANO(Units.LITRE);
	public static final Unit<Volume> MICROLITRE = MICRO(Units.LITRE);
	public static final Unit<Volume> MILLILITRE = MILLI(LITRE);

	public static final Unit<Mass> PICOGRAM = PICO(Units.GRAM);
	public static final Unit<Mass> NANOGRAM = NANO(Units.GRAM);
	public static final Unit<Mass> MICROGRAM = MICRO(Units.GRAM);
	public static final Unit<Mass> MILLIGRAM = MILLI(Units.GRAM);

	public static final Unit<Concentration> MICROGM_PER_MICROL = MICROGRAM.divide(MICROLITRE)
			.asType(Concentration.class);
	public static final Unit<Concentration> MILLIGM_PER_ML = MILLIGRAM.divide(MILLILITRE).asType(Concentration.class);
	public static final Unit<Concentration> GM_PER_L = GRAM.divide(LITRE).asType(Concentration.class);
	public static final Unit<Efficiency> TRANSFORMANTS_PER_UG = MICROGRAM.inverse().asType(Efficiency.class);

	// derived enzyme units
	public static final Unit<CatalyticActivity> MICROKATAL = MICRO(Units.KATAL);
	public static final Unit<CatalyticActivity> NANOKATAL = NANO(Units.KATAL);

	// see https://en.wikipedia.org/wiki/Enzyme_assay
	public static final Unit<CatalyticActivity> ENZYME_UNIT = MICRO(Units.MOLE).divide(Units.MINUTE)
			.asType(CatalyticActivity.class);

	// derived area units
	public static final Unit<Area> CM_SQUARED = CENTI(Units.METRE).multiply(CENTI(Units.METRE)).asType(Area.class);
	public static final Unit<Area> MM_SQUARED = MILLI(Units.METRE).multiply(MILLI(Units.METRE)).asType(Area.class);
	public static final Unit<Area> KM_SQUARED = KILO(Units.METRE).multiply(KILO(Units.METRE)).asType(Area.class);

	// derived volume units
	public static final Unit<Volume> CUBIC_MILLIMETRE = MILLI(Units.METRE).multiply(MILLI(Units.METRE)).multiply(MILLI(Units.METRE)).asType(Volume.class);
	public static final Unit<Volume> CUBIC_CENTIMETRE = CENTI(Units.METRE).multiply(CENTI(Units.METRE)).multiply(CENTI(Units.METRE)).asType(Volume.class);
	public static final Unit<Volume> CUBIC_DECIMETRE = DECI(Units.METRE).multiply(DECI(Units.METRE)).multiply(DECI(Units.METRE)).asType(Volume.class);
	public static final Unit<Volume> CUBIC_METRE = Units.CUBIC_METRE;
	
	public static final Unit<Temperature> FAHRENHEIT = new TransformedUnit<>("F", Units.CELSIUS,
			MultiplyConverter.ofRational(5, 9).concatenate(new AddConverter(-32)));

	@SuppressWarnings("unchecked")
	public static final List<Unit<Radioactivity>> BEQS = Arrays
			.asList(new Unit[] { MBeq, kBeq, Units.BECQUEREL, mBeq });

	@SuppressWarnings("unchecked")
	public static final List<Unit<Radioactivity>> CURIES = Arrays.asList(new Unit[] { millicurie, curie });

	@SuppressWarnings("unchecked")
	public static final List<Unit<Mass>> MASS_UNITS = Arrays
			.asList(new Unit[] { GRAM, MILLI(GRAM), MICRO(GRAM), NANO(GRAM), PICO(GRAM), MetricPrefix.FEMTO(GRAM) });

	@SuppressWarnings("unchecked")
	public static final List<Unit<Volume>> VOLUME_UNITS = Arrays
			.asList(new Unit[] { MICROLITRE, MILLILITRE, Units.LITRE });

	@SuppressWarnings("unchecked")
	public static final List<Unit<Area>> AREA_UNITS = Arrays.asList(new Unit[] { Units.SQUARE_METRE });

	@SuppressWarnings("unchecked")
	public static final List<Unit<Concentration>> CONCENTRATION_UNITS = Arrays
			.asList(new Unit[] { MILLI_MOLAR, MOLAR, MICRO_MOLAR, NANO_MOLAR });

	@SuppressWarnings("unchecked")
	public static final List<Unit<Temperature>> TEMPERATURE_UNITS = Arrays
			.asList(new Unit[] { Units.KELVIN, Units.CELSIUS });

	@SuppressWarnings("unchecked")
	public static final List<Unit<CatalyticActivity>> CATALYSIS_UNITS = Arrays
			.asList(new Unit[] { NANOKATAL, MICROKATAL, ENZYME_UNIT });

	@SuppressWarnings("unchecked")
	public static final List<Unit<Time>> TIME_UNITS = Arrays
			.asList(new Unit[] { Units.SECOND, Units.MINUTE, Units.HOUR, Units.DAY, Units.YEAR });

}
