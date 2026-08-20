package com.researchspace.model.units;

import java.util.EnumSet;

import javax.measure.MetricPrefix;
import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Radioactivity;

import org.junit.Test;

import tech.units.indriya.AbstractUnit;
import tech.units.indriya.format.SimpleUnitFormat;
import tech.units.indriya.quantity.Quantities;
import tech.units.indriya.unit.Units;

public class UnitConversionsTest {
	
	@Test
	public void RSUnitDefs() {
		for (RSUnitDef def: EnumSet.allOf(RSUnitDef.class)) {
			System.err.println(def.toString());
		}
	}

	@Test
	public void test() {
		int quantity = 23;
		Unit<Radioactivity> kBq = MetricPrefix.KILO(Units.BECQUEREL);
		Unit<Radioactivity> mBq = MetricPrefix.MEGA(Units.BECQUEREL);
		
		Unit<Radioactivity> curie = Units.BECQUEREL.multiply(3.7e10);
		Unit<Radioactivity>microCurie = MetricPrefix.MICRO(curie);
		SimpleUnitFormat.getInstance().label(curie, "Ci");
		
		double converted = mBq.getConverterTo(kBq).convert(quantity);
		System.err.println(converted);
		
		Quantity<Radioactivity> mbqs = Quantities.getQuantity(370000, mBq);
		Quantity<Radioactivity> mbqs2 = mbqs.to(microCurie);
		System.err.println(mbqs2);
		
		double curies = Units.BECQUEREL.getConverterTo(curie).convert(3.7e10);
		System.err.println(curies);
		System.err.println(mBq.getSymbol() +"," + mBq.getSystemUnit() +", to string:" + mBq);
		

		double parsed = AbstractUnit.parse("MBq").asType(Radioactivity.class).getConverterTo(curie).convert(37000);
	    System.err.println(parsed);
	}

}
