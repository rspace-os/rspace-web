package com.researchspace.model.apps;

import static com.researchspace.model.system.SystemPropertyTestFactory.createAPropertyDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.PropertyDescriptor;
import com.researchspace.model.preference.SettingsType;

public class AppConfigElementSetTest {
	
	AppConfigElementSet set1, set2;
	PropertyDescriptor pd1, pd2, pd3;
	AppConfigElementDescriptor desc1, desc2, desc3;
	

	@BeforeEach
	public void setUp() throws Exception {
		pd1 = createAPropertyDescriptor("p1", SettingsType.STRING, "");
		pd2 = createAPropertyDescriptor("p2", SettingsType.STRING, "");
		pd3 = createAPropertyDescriptor("p3", SettingsType.STRING, "");
		desc1 = new AppConfigElementDescriptor(pd1);
		desc2 = new AppConfigElementDescriptor(pd2);
		desc3 = new AppConfigElementDescriptor(pd3);
		set1 = new AppConfigElementSet();
		set2 = new AppConfigElementSet();
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetProperties() {
		AppConfigElement el1 = new AppConfigElement(desc1);
		AppConfigElement el2 = new AppConfigElement(desc2);
		set1.setConfigElements(TransformerUtils.toSet(el1, el2));
		assertEquals(2, set1.getProperties().size());
		assertTrue( set1.getProperties().contains(pd1)
				&& set1.getProperties().contains(pd2));
	}

	@Test
	public void testPropertiesMatch() {
		AppConfigElement el1 = new AppConfigElement(desc1);
		AppConfigElement el2 = new AppConfigElement(desc2);
		set1.setConfigElements(TransformerUtils.toSet(el1, el2));
		set2.setConfigElements(TransformerUtils.toSet(el1, el2));
		assertTrue(set1.propertiesMatch(set2));
		assertTrue(set2.propertiesMatch(set1));
		AppConfigElement el3 = new AppConfigElement(desc3);
		set1.getConfigElements().add(el3);
		assertFalse(set2.propertiesMatch(set1));
		
	}

	@Test
	public void testMerge() {
		AppConfigElement el1 = new AppConfigElement(desc1, "val1");
		AppConfigElement el2 = new AppConfigElement(desc2, "val2");
		set1.setConfigElements(TransformerUtils.toSet(el1, el2));
		AppConfigElement el3 = new AppConfigElement(desc1, "valNew1");
		AppConfigElement el4 = new AppConfigElement(desc2, "valNew2");
		set2.setConfigElements(TransformerUtils.toSet(el3, el4));
		set1.merge(set2);
		assertEquals("valNew2", set1.findElementByPropertyName(pd2.getName()).getValue());
	}

	@Test
	public void testFindElementByPropertyName() {
		AppConfigElement el1 = new AppConfigElement(desc1);
		AppConfigElement el2 = new AppConfigElement(desc2);
		set1.setConfigElements(TransformerUtils.toSet(el1, el2));
		assertNotNull(set1.findElementByPropertyName(pd1.getName()));
		assertNull(set1.findElementByPropertyName("any"));
	}

}
