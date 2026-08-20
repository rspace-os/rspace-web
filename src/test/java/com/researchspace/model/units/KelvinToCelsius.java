package com.researchspace.model.units;

import static org.junit.Assert.assertEquals;

import javax.measure.MetricPrefix;
import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Area;
import javax.measure.quantity.CatalyticActivity;
import javax.measure.quantity.Radioactivity;
import javax.measure.quantity.Temperature;

import org.junit.Test;

import tech.units.indriya.AbstractUnit;
import tech.units.indriya.format.SimpleUnitFormat;
import tech.units.indriya.quantity.Quantities;
import tech.units.indriya.unit.Units;


public class KelvinToCelsius {

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
	    
	   //adding temperatures
	   Quantity<Temperature> celc = Quantities.getQuantity(22, Units.CELSIUS);
	   Quantity<Temperature> kelvin = Quantities.getQuantity(373.16, Units.KELVIN);
	   // get temp difference
	   kelvin = kelvin.subtract(celc);
	   assertEquals(78.0,kelvin.getValue().doubleValue(),0.1);
	}
	
	@Test
	public void addEnzymeActivities () {
		  // 1 EU  = 16.67 nanokatal
		  Quantity<CatalyticActivity> nanos = Quantities.getQuantity(16.67, RSUnits.NANOKATAL);
		  Quantity<CatalyticActivity> enzymeUnits = Quantities.getQuantity(1, RSUnits.ENZYME_UNIT);
		  assertEquals(2.0, enzymeUnits.add(nanos).getValue().doubleValue(), 0.01);
	}
	
	@Test
	public void areas () {
		  // 1 EU  = 16.67 nanokatal
		  Quantity<Area> km_sq = Quantities.getQuantity(10, RSUnits.KM_SQUARED);
		  Quantity<Area> m_sq = Quantities.getQuantity(10, Units.SQUARE_METRE);
		  System.err.println(m_sq.add(km_sq));
	}

}
