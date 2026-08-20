package com.researchspace.model.units;

import static com.researchspace.core.util.TransformerUtils.toList;
import static org.apache.commons.lang3.Validate.isTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.format.MeasurementParseException;
import javax.measure.quantity.Dimensionless;
import javax.measure.quantity.Mass;
import javax.measure.quantity.Temperature;
import javax.measure.quantity.Volume;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import tech.units.indriya.AbstractUnit;
import tech.units.indriya.format.SimpleQuantityFormat;
import tech.units.indriya.format.SimpleUnitFormat;
import tech.units.indriya.quantity.Quantities;

public class QuantityUtils {
	
	/**
	 * Get unit by id
	 * @param id
	 * @return The found unit definition, or RSUnitDefs.DIMENSIONLESS if not found
	 */
	public RSUnitDef getUnitById(Integer id) {
		return EnumSet.allOf(RSUnitDef.class).stream().filter(def->def.getId() == id)
				.findFirst().orElse(RSUnitDef.DIMENSIONLESS);
	}
	
	public  <Q extends Quantity<Q>> Quantity<Q> getQuantityFor (Quantifiable quantifiable, Class<Q> clazz) {
		Unit<Q> unit = getUnitById(quantifiable.getUnitId()).getDefinition().asType(clazz);
		Quantity<Q> q1 = Quantities.getQuantity(quantifiable.getNumericValue(), unit).asType(clazz);
		return q1;
	}
	
	/**
	 * Returns boolean on whether arithmetic operations can be performed on the given list of {@link Quantifiable}
	 *  objects. To be comparable all must be commensurate,  in same measurement category (mass, volume, dimensionless etc)
	 * @param toTest
	 * @return <code>true</code> if quantities are commensurate, i.e. arithmetic operations can be performed
	 * @throws IllegalArgumentException if <code>toTest</code> is empty
	 */
	public boolean isComparableQuantities (Collection<? extends Quantifiable> toTest) {
		Validate.isTrue(!toTest.isEmpty(), "cannot compare an empty collection of quantities");
		Quantifiable initialComparator = toTest.iterator().next();
		Unit<?> initialDefUnit = getUnitById(initialComparator.getUnitId()).getDefinition();
		return toTest.stream().map(q->getUnitById(q.getUnitId()))
				.map(RSUnitDef::getDefinition)
				.allMatch(initialDefUnit::isCompatible);
	}
	
	/**
	 *Convenience method to detect if a pair of quantities are commensurate, i.e. have comparable units
	 *@see #isComparableQuantities (Collection toTest)
	 */
	public boolean isComparableQuantities (Quantifiable q1, Quantifiable q2) {
		return isComparableQuantities(toList(q1, q2));
	}
	/**
	 * Integer value MAX_VALUE representing an incompatible comparison.
	 * Comparators in this class sort ascendingly, which means that items incompatible should be put 
	 * at the end of a list
	 */
	public Integer INCOMPARABLE = Integer.MAX_VALUE;

	/**
	 * Comparator to use for sorting /comparing temperatures in ascending order.
	 * The arguments must be temperature quantities
	 *  * @throws ClassCastException if the quantifiables are not temperatures
	 */
	public Comparator<Quantifiable> TemperatureComparator = (a, b) -> {
		return doCompare(a, b, Temperature.class);
	};

	/**
	 * Comparator to use for sorting /comparing volumes in ascending order.
	 * The arguments must be volume quantities
	 *  * @throws ClassCastException if the quantifiables are not volumes
	 */
	public Comparator<Quantifiable> VolumeComparator = (a, b) -> {
		return doCompare(a, b, Volume.class);
	};
	
	/**
	 * Comparator to use for sorting /comparing masses in ascending order.
	 * The arguments must be mass quantities
	 *  * @throws ClassCastException if the quantifiables are not masses
	 */
	public Comparator<Quantifiable> MassComparator = (a, b) -> {
		return doCompare(a, b, Mass.class);
	};
	
	/**
	 * Comparator to use for sorting /comparing masses in ascending order.
	 * The arguments must be dimensionless quantities
	 * @throws ClassCastException if the quantifiables are not dimensionless
	 */
	public Comparator<Quantifiable> DimensionlessComparator = (a, b) -> {
		return doCompare(a, b, Dimensionless.class);
	};
	
	public Comparator<Quantifiable> getComparatorFor(Quantifiable q1) {
		return getForQuantifiable(toList(q1), new QuantityComparatorFactoryVisitor());
	}
	
	/**
	 * Sorts a list of quantifiables in ascending order of amount in place.
	 * All items in the collection must be of all the same type (e.g. all masses, or all volumes)
	 * @param toSort A List of Quantifiable objects
	 */
	public void sortAsc (List<? extends Quantifiable> toSort) {
		if(toSort.isEmpty()) {
			return;
		}
		Collections.sort(toSort, getComparatorFor(toSort.iterator().next()));
	}
	
	
	/**
	 * Sorts a list of quantifiables in descending order of amount, in place.
	 * All items in the collection must be of all the same type (e.g. all masses, or all volumes)
	 * <br>
	 * To sort in reverse 
	 * @param toSort
	 */
	public void sortDesc (List<? extends Quantifiable> toSort) {
		if(toSort.isEmpty()) {
			return;
		}
		Collections.sort(toSort,  Collections.reverseOrder(getComparatorFor(toSort.iterator().next())));
	}
	
	 /*
	  * Control method to invoke a unit-specific method based on the quantity type.
	  */
	 <T> T getForQuantifiable(Collection<? extends Quantifiable>  q1, QuantityTypeVisitor<T> visitor) {
		RSUnitDef def = getUnitById(q1.iterator().next().getUnitId());
		if (def.isMass()) {
			return visitor.visitMass(q1);
		} else if (def.isVolume()) {
			return visitor.visitVolume(q1);
		} else if (def.isDimensionless()) {
			return visitor.visitDimensionless(q1);
		} else if (def.isTemperature()) {
			return visitor.visitTemperature(q1);
		} else {
			throw new IllegalStateException("Unsupported unit type: " + def.getDefinition());
		}
	}

	<Q extends Quantity<Q>> int doCompare(Quantifiable q1, Quantifiable q2, Class<Q> clazz) {
		// if are same units, we can just compare the numeric values
		if (q1.getUnitId().equals(q2.getUnitId())) {
			return q1.getNumericValue().compareTo(q2.getNumericValue());
		}
		// return Integer.MAX if not comparable
		if (!isComparableQuantities(q1, q2)) {
			return INCOMPARABLE;
		}

		// else we have to convert to common units.
		Quantity<Q> qA = getQuantityFor(q1, clazz).toSystemUnit();
		Quantity<Q> qB = getQuantityFor(q2, clazz).toSystemUnit();
		return BigDecimal.valueOf(qA.getValue().doubleValue())
				.compareTo(BigDecimal.valueOf(qB.getValue().doubleValue()));
	}
	
	/**
	 * Facade method to sum a collection of Quantifiables.
	 * 
	 * The returned quantity may be specified using a smaller or larger unit than 
	 * one from the argument collection.
	 * <br/>
	 * For example, when adding 500 and 1500 milligrams, the result will be 2 grams.
	 * And if adding 0.010 and 0.015 grams, the result will be 25 milligrams. 
	 *  
	 * @param toSum
	 * @return A QuantityInfo result of summing all the quantities in the collection
	 * @throws IllegalArgumentException if <code>toSum</code> is empty, or if items are not commensurate, i.e not comparable units
	 * @throws IllegalStateException if there is a problem with converting units 
	 */
	public QuantityInfo sum(Collection<? extends Quantifiable> toSum) {
		isTrue(isComparableQuantities(toSum), "items to sum are not commensurate - they have different unit categories");
		Quantity<?> result = getForQuantifiable(toSum, new QuantitySummingVisitor());
		result = convertToMoreUsefulUnit(result); 

		return QuantityInfo.of(BigDecimal.valueOf(result.getValue().doubleValue()), 
				RSUnitDef.getUnitDefByUnit(result.getUnit()).get());
	}
	
	/**
	 * Divide a quantity by a specific divisor.
	 * 
	 * The returned quantity may be specified using a smaller or larger unit than 
	 * one from the argument collection.
	 * <br/>
	 * For example, when dividing 1 gram by 2, the result will be 500 milligrams.
	 * 
	 * 
	 * @param quantity
	 * @param divisor
	 * @return resulting quantity
	 */
	public QuantityInfo divide(Quantifiable quantity, BigDecimal divisor) {
		if (quantity == null || divisor == null || divisor.compareTo(BigDecimal.ZERO) == 0) {
			throw new IllegalArgumentException("divide method requires dividend quantity and non-zero divisor as parameters");
		}
		
		QuantityInfo divisorAsQuantity = QuantityInfo.of(divisor, RSUnitDef.DIMENSIONLESS);
		Quantity<?> result = getForQuantifiable(Arrays.asList(quantity, divisorAsQuantity), 
				new QuantityDividingVisitor());
		result = convertToMoreUsefulUnit(result); 

		return QuantityInfo.of(BigDecimal.valueOf(result.getValue().doubleValue()), 
				RSUnitDef.getUnitDefByUnit(result.getUnit()).get());
	}
	
	
	/**
	 * Try converting quantity to one of a different unit, if that creates more useful result.
	 * 
	 * The rules:
	 * 1. if abs(value) < 1: try switching to smaller unit
	 * 2. if abs(value) >= 1000 and divides by 1000 without remainder: try switching to larger unit 
	 * 
	 * The 2nd rule only make sense for decimal unit systems (where larger unit is a 1000x of smaller one). 
	 * 
	 * @param orgQuantity
	 * 
	 * @return
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	Quantity<?> convertToMoreUsefulUnit(Quantity orgQuantity) {

		RSUnitDef initialUnit = RSUnitDef.getUnitDefByUnit(orgQuantity.getUnit()).orElseThrow(
				() -> new IllegalStateException("unknown rsunit for unit type: " + orgQuantity.getUnit().getName()));

		double orgQuantityValue = orgQuantity.getValue().doubleValue();

		if (orgQuantityValue == 0) {
			return orgQuantity;
		}
		
		// if abs(value) < 1: try switching to smaller unit
		if (Math.abs(orgQuantityValue) < 1.0) {
			RSUnitDef smallerUnit = RSUnitDef.getUnitSmallerThan(initialUnit);
			if (smallerUnit != null) {
				return orgQuantity.to(smallerUnit.getDefinition());
			}
		}

		// if abs(value) >= 1000 and divides by 1000 without remainder: try switching to larger unit
		if (Math.abs(orgQuantityValue) >= 1000.0) {
			/* let's multiply to 1000, cast to long, then divide by 1000 * 1000 to check if there 
			 * is something on last 3 decimal and non-decimal places of the original double*/
			long quantityAsLong1000Times = (long) (orgQuantityValue * 1000);
			if (quantityAsLong1000Times % (1000 * 1000) == 0) {
				// try switching to larger unit
				RSUnitDef largerUnit = RSUnitDef.getUnitLargerThan(initialUnit);
				if (largerUnit != null) {
					return orgQuantity.to(largerUnit.getDefinition());
				}
			}
		}

		return orgQuantity;
	}

	private Unit<?> parseUnit(String fmtString) {
		return SimpleUnitFormat.getInstance().parse(fmtString);
	}

	private String getLargestUnitLabel(Collection<? extends Quantifiable> toSum) {
		Quantifiable largestQuantifiable = Collections.max(toSum, this::sortByOrder);
		return getUnitById(largestQuantifiable.getUnitId()).getLabel();
	}
	
	int sortByOrder(Quantifiable a, Quantifiable b) {
		return getUnitById(a.getUnitId()).getOrder().compareTo(getUnitById(b.getUnitId()).getOrder());
	}

	/**
	 * Tries parsing provided string into QuantityInfo. Unit string must be defined in RSUnitDef.
	 *
	 * @return QuantityInfo object matching the input, or null for empty input
	 * @throws IllegalArgumentException if string cannot be parsed into QuantityInfo
	 */
	public static QuantityInfo parseQuantityInfo(String quantityString) {
		if (StringUtils.isBlank(quantityString)) {
			return null;
		}

		Quantity<?> parsedQuantity = null;
		try {
			String quantityStringWithSpace = addSpaceBetweenNumberAndUnitPartOfQuantityString(quantityString);
			parsedQuantity = SimpleQuantityFormat.getInstance().parse(quantityStringWithSpace);
		} catch (MeasurementParseException mpe) {
			String cause = mpe.getMessage();
			if ("Parse Error".equals(cause)) {
				cause += " - " + mpe.getParsedString();
			}
			throw new IllegalArgumentException("Cannot parse quantity string: " + cause, mpe);
		}

		Unit<?> parsedUnit = parsedQuantity.getUnit();
		Optional<RSUnitDef> rsUnitDefOpt = RSUnitDef.getUnitDefByUnit(parsedUnit);
		if (!rsUnitDefOpt.isPresent()) {
			throw new IllegalArgumentException(String.format("Cannot parse quantity string: unit not recognized [%s]", parsedUnit.toString()));
		}
		return QuantityInfo.of(new BigDecimal(parsedQuantity.getValue().toString()), rsUnitDefOpt.get());
	}

	private static String addSpaceBetweenNumberAndUnitPartOfQuantityString(String quantityString) {
		// if string is build as number fragment followed by letters, put a space between number and letters
		return quantityString.replaceAll("^(.*\\d)([^0-9^\\s]+)$", "$1 $2");
	}

	/*
	 * =================================
	 * quantity type visitors below
	 * =================================
	 * 
	 * internal visitor pattern to decouple choosing-unit-type logic from handling logic
	 * if we add more methods for different quantitiy types we will get compilation errors 
	 * to remind us to update all the handlers.
	 * 
	 */
	interface QuantityTypeVisitor <T> {	
		 T visitMass(Collection<? extends Quantifiable> q1);
		 T visitVolume( Collection<? extends Quantifiable> q1);
		 T visitTemperature(Collection<? extends Quantifiable> q1);
		 T visitDimensionless (Collection<? extends Quantifiable> q1);
	}
	
	class QuantityComparatorFactoryVisitor implements QuantityTypeVisitor<Comparator<Quantifiable>> {
		public Comparator<Quantifiable> visitMass(Collection<? extends Quantifiable> q1) {
			return MassComparator;
		}
		public Comparator<Quantifiable> visitVolume( Collection<? extends Quantifiable> q1){
			return VolumeComparator;
		}
		public Comparator<Quantifiable> visitTemperature(Collection<? extends Quantifiable> q1){
			return TemperatureComparator;
		}
		public Comparator<Quantifiable> visitDimensionless (Collection<? extends Quantifiable> q1){
			return DimensionlessComparator;
		}
	}
	
	class QuantitySummingVisitor implements QuantityTypeVisitor<Quantity<?>> {

		@Override
		public Quantity<?> visitMass(Collection<? extends Quantifiable> q1) {
			String fmtString = getLargestUnitLabel(q1);
			Quantity<Mass> sum = Quantities.getQuantity(0, parseUnit(fmtString).asType(Mass.class));
			for (Quantifiable q: q1) {
				sum = sum.add(getQuantityFor(q, Mass.class));
			}
			return sum;
		}

		@Override
		public Quantity<?> visitVolume(Collection<? extends Quantifiable> q1) {
			String fmtString = getLargestUnitLabel(q1);
			Quantity<Volume> sum = Quantities.getQuantity(0, parseUnit(fmtString).asType(Volume.class));
			for (Quantifiable q: q1) {
				sum = sum.add(getQuantityFor(q, Volume.class));
			}
			return sum;
		}

		@Override
		public Quantity<?> visitTemperature(Collection<? extends Quantifiable> q1) {
			return null; // summing temperatures is meaningless
		}

		@Override
		public Quantity<?> visitDimensionless(Collection<? extends Quantifiable> q1) {
			Quantity<Dimensionless> sum = Quantities.getQuantity(0, AbstractUnit.ONE);
			for (Quantifiable q: q1) {
				sum = sum.add(getQuantityFor(q, Dimensionless.class));
			}
			return sum;
		}
	}

	class QuantityDividingVisitor implements QuantityTypeVisitor<Quantity<?>> {

		@Override
		public Quantity<?> visitMass(Collection<? extends Quantifiable> q1) {
			Iterator<? extends Quantifiable> iterator = q1.iterator();
			Quantifiable quantityToDivide = iterator.next();
			Quantifiable divider = iterator.next();
			return getQuantityFor(quantityToDivide, Mass.class).divide(divider.getNumericValue());
		}

		@Override
		public Quantity<?> visitVolume(Collection<? extends Quantifiable> q1) {
			Iterator<? extends Quantifiable> iterator = q1.iterator();
			Quantifiable quantityToDivide = iterator.next();
			Quantifiable divider = iterator.next();
			return getQuantityFor(quantityToDivide, Volume.class).divide(divider.getNumericValue());
		}

		@Override
		public Quantity<?> visitTemperature(Collection<? extends Quantifiable> q1) {
			return null; // dividing temperatures is meaningless
		}

		@Override
		public Quantity<?> visitDimensionless(Collection<? extends Quantifiable> q1) {
			Iterator<? extends Quantifiable> iterator = q1.iterator();
			Quantifiable quantityToDivide = iterator.next();
			Quantifiable divider = iterator.next();
			return getQuantityFor(quantityToDivide, Dimensionless.class).divide(divider.getNumericValue());
		}
	}

}
