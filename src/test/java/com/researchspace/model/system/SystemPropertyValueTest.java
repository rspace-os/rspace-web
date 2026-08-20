package com.researchspace.model.system;

import static com.researchspace.core.testutil.CoreTestUtils.assertIllegalArgumentException;
import static com.researchspace.model.system.SystemPropertyTestFactory.createASystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.researchspace.model.preference.SettingsType;

public class SystemPropertyValueTest {

	@Test
	public void systemPropertyValueNoNulProperty() {
		assertThrows(NullPointerException.class, ()->new SystemPropertyValue(null));
	}
	
	@Test
	public void validateValueByType() {
		SystemProperty property = createASystemProperty();
		property.getDescriptor().setType(SettingsType.BOOLEAN);
		assertIllegalArgumentException(()->new SystemPropertyValue(property).setValue("not a boolean"));
		
	}
	
	@Test()
	public void setValueHappyCase() {
		SystemProperty property = createASystemProperty();
		property.getDescriptor().setType(SettingsType.BOOLEAN);
		SystemPropertyValue value = new SystemPropertyValue(property);
		
		value.setValue("true");
		assertTrue(Boolean.parseBoolean(value.getValue()));
		property.getDescriptor().setType(SettingsType.NUMBER);
		value = new SystemPropertyValue(property);
		value.setValue("123.45");
		assertEquals(123.45, Double.parseDouble(value.getValue()), 0.01);
		property.getDescriptor().setType(SettingsType.STRING);
		value = new SystemPropertyValue(property);
		String ANY_STRING = "can be any string, no restriction";
		value.setValue(ANY_STRING);
		assertEquals(ANY_STRING, value.getValue());
		
	}
	
	@Test()
	public void noCyclesAllowedInDependencies (){
	SystemProperty child = createASystemProperty();
	SystemProperty parent = createASystemProperty();
	parent.getDescriptor().setName("parent");
	SystemProperty gp = createASystemProperty();
	gp.getDescriptor().setName("gp");
	child.setDependent(parent);
	parent.setDependent(gp);
	assertNotNull(child.getDependent());
	gp.setDependent(child);
	assertNull(gp.getDependent());
	// can unset a dependence
	child.setDependent(null);
	assertNull(child.getDependent());
	
	}

}
