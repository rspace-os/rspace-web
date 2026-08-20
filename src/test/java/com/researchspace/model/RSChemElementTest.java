package com.researchspace.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.researchspace.core.testutil.ModelTestUtils;


public class RSChemElementTest {
	
	private static final long PARENT_ID = 7L;
	RSChemElement ele;
	
	@BeforeEach
	public void setUp() throws Exception {
		ele = new RSChemElement();
	}
	
	@Test
	public void testShallowCopy() throws IllegalArgumentException, IllegalAccessException {
		
		setChemELementProperties();
		
		RSChemElement copy = ele.shallowCopy();
		Set<String> toExclude = ModelTestUtils.generateExclusionFieldsFrom("id", "parentId");
		
		List<Class<? super RSChemElement>> classes = new ArrayList<>();
		classes.add(RSChemElement.class);
	    ModelTestUtils.assertCopiedFieldsAreEqual(copy, ele, toExclude, classes);
	}
	
	@Test
	public void testInvariants() {
		setChemELementProperties();
		ele.setChemId(1);
		assertTrue(ele.isMoleculeOrMultiStepReaction());
		assertNull(ele.getReactionId());
		
		ele.setReactionId(2);
		assertNull(ele.getChemId());
		assertNotNull(ele.getReactionId());
		assertTrue(ele.isReaction());
		
		ele.setChemId(1);
		assertNull(ele.getReactionId());
		assertNotNull(ele.getChemId());
		assertTrue(ele.isMoleculeOrMultiStepReaction());

		ele.setRgroupId(1);
		assertNull(ele.getReactionId());
		assertNull(ele.getChemId());
		assertNotNull(ele.getRgroupId());
		assertFalse(ele.isReaction());
		assertFalse(ele.isMoleculeOrMultiStepReaction());
	}

	private void setChemELementProperties() {
		ele.setChemElements("{()}");
		ele.setId(1L);
		ele.setDataImage(new byte[10]);
		ele.setParentId(PARENT_ID);
		ele.setSmilesString("");
		ele.setChemElementsFormat(ChemElementsFormat.MOL);
		ele.setChemId(1);
	}

}
