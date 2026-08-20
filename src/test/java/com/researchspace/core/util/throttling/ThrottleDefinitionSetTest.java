package com.researchspace.core.util.throttling;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;



public class ThrottleDefinitionSetTest {
	ThrottleDefinitionSet definitions;

	@BeforeEach
	public void setUp() throws Exception {
		definitions = new ThrottleDefinitionSet("requests");
	}

	@Test
	public void testThrottleDefinitionRejectNegativeRate() {	
		assertThrows(IllegalArgumentException.class, ()->definitions.addDefinition(ThrottleInterval.DAY, -1));
	}

	@Test
	public void testAddDefinition() {
		assertEquals(0, definitions.getDefinitionCount());
		definitions.addDefinition(ThrottleInterval.DAY, 2);
		assertEquals(1, definitions.getDefinitionCount());
	}

}
