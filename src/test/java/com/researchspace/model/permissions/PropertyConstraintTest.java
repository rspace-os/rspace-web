package com.researchspace.model.permissions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PropertyConstraintTest {

	PropertyConstraint constraint = null;
	private static final String UNAME="user";
 class PropertyConstraintTSS extends PropertyConstraint {
	 public PropertyConstraintTSS(String name, String value) {
		super(name, value);
		}
	String uname=UNAME;

	// override to decouple from application security. Always return a single 
	String getPrincipalFromSecurityCtxt() {
		 return uname;
	 }
 }
	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetString() {
		constraint=create("name", "value");
		assertEquals("property_name=value", constraint.getString());
		constraint=create("name", "value1,value2,value3");
		// lists are handled too
		assertEquals("property_name=value1,value2,value3", constraint.getString());
	}

	@Test
	public void testRemoveValue() {
		constraint=create("name", "value");
		assertTrue(constraint.removeValue("value"));
		assertNotNull(constraint.getValue());
		
		constraint=create("name", "value");
		assertFalse(constraint.removeValue("wrongvalue"));
		assertEquals("value", constraint.getValue());
		//remove from list 1 at  time
		constraint=create("name", "value1,value2,value3");
		assertFalse(constraint.removeValue("value2"));
		assertEquals("property_name=value1,value3", constraint.getString());
		// remove unknown does nothing
		assertFalse(constraint.removeValue("unknown"));
		assertEquals("property_name=value1,value3", constraint.getString());
		
		assertFalse(constraint.removeValue("value3"));
		assertEquals("property_name=value1", constraint.getString());
		// this is the same as removing a single value
		assertTrue(constraint.removeValue("value1"));
		assertNotNull(constraint.getValue());

	
	}
	@Test
	public void testSatisfies() {
		constraint=create("name", "value");
		PropertyConstraint constraint2=create("name2", "value2");
		
		// now test all combinations of matchin name, value or both.
		assertFalse(constraint.satisfies(constraint2));
		constraint2.setValue("value");
		assertFalse(constraint.satisfies(constraint2));
		constraint2.setName("name");
		assertTrue(constraint.satisfies(constraint2));
		constraint2.setValue("value2");
		assertFalse(constraint.satisfies(constraint2));
	}
	
	@Test
	public void testSatisfiesWildCard() {
		constraint=create("name", "*");
		PropertyConstraint constraint2=create("name", "anyvalue");
		assertTrue(constraint.satisfies(constraint2));
		
	}
	
	@Test
	public void testSatisfiesSelfVariableCard() {
		constraint=create("owner", "${self}");
		PropertyConstraint constraint2=create("owner", UNAME);
		assertTrue(constraint.satisfies(constraint2));
		constraint=create("owner", " ${ self }"); // handles whitespace
		assertTrue(constraint.satisfies(constraint2));
		
	}
	
	@Test
	public void testSatisfiesValueListCard() {
		constraint=create("type", " a , b, c,d");
		PropertyConstraint toTest=create("type", "a");
		assertTrue(constraint.satisfies(toTest));
		
	  toTest=create("type", "a, b,c");
		assertFalse(constraint.satisfies(toTest));
	}
	
	@Test
	(expected=IllegalArgumentException.class)
	public void testPropertyConstraintInvalidCharactersThrowsIAE() {
		constraint=create("any", "dsds_");
	}
	
	@Test
	(expected=IllegalArgumentException.class)
	public void testPropertyConstraintInvalidCharactersThrowsIAE2() {
		constraint=create("any", "ds:ds");
	}
	
	@Test
	(expected=IllegalArgumentException.class)
	public void testPropertyConstraintInvalidCharactersThrowsIAE3() {
		constraint=create("any", "ds=ds");
	}
	
	PropertyConstraint create(String name, String value) {
		PropertyConstraintTSS tss= new PropertyConstraintTSS(name, value);
		tss.uname=UNAME;
		return tss;
	}

}
