package com.researchspace.model.permissions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class IdConstraintTest {
	Set<Long> ids=null;
	IdConstraint constraint;
	@Before
	public void setUp() throws Exception {
		
	}

	@After
	public void tearDown() throws Exception {
	}
	
	
	
	@Test
	(expected=UnsupportedOperationException.class)
	public void testInvariants2 (){
		createConstraintFromLongArray(new Long []{2L,3L,1L});
		ids.clear();
		// encapsulated  set is unaltered
		assertEquals(3,constraint.getId().size());
		// check ids are ordered in ascending order
		assertEquals(1L, constraint.getId().iterator().next().longValue());
		
		// return list is read only!
		constraint.getId().clear();
	}

	@Test
	public void testSatisfiesNormalCase() {
		 createConstraintFromLongArray(new Long []{1L,2L});

		 assertTrue(constraint.satisfies(1L));
		 assertFalse(constraint.satisfies(4L));
		 // test 
		 createConstraintFromLongArray(new Long []{});
		 assertFalse(constraint.satisfies(1L));
	 
	}

	

	@Test
	public void testGetString() {
		createConstraintFromLongArray(new Long []{1L,2L});
		 assertEquals("id=1,2",constraint.getString());
		 
			createConstraintFromLongArray(new Long []{1L,2L});
			 assertEquals("id=1,2",constraint.getString());
		 
		 createConstraintFromLongArray(new Long []{1L});
		 assertEquals("id=1",constraint.getString());
	}
	
	void createConstraintFromLongArray(Long [] array){
	 ids = new HashSet<>(Arrays.asList(array));

	
	 constraint = new IdConstraint(ids);
	}

}
