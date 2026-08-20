package com.researchspace.model.inventory;

import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.core.util.TransformerUtils.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.researchspace.core.testutil.ModelTestUtils;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;

public class SubsampleNoteTest {
	
	private SubSampleNote  original, copy;
	SubSample subSample;
	User anyUser;

	@BeforeEach
	public void setUp() throws Exception {
		subSample = new SubSample();
		subSample.setSample(new Sample());
		anyUser = TestFactory.createAnyUser("any");
		subSample.setCreatedBy(anyUser.getUsername());
	}

	@Test
	public void shallowCopy() throws InterruptedException, IllegalArgumentException, IllegalAccessException {
		original = new SubSampleNote("content", anyUser);
		original.setSubSample(subSample);
		original.setId(1L);
		// forces possible new creation date.
		Thread.sleep(2);
		
		copy = original.shallowCopy();
		assertNull(copy.getId());
		assertFalse(copy == original);
		assertEquals(original.getContent(), copy.getContent());
		assertNull(copy.getCreatedBy());
		
		//creation date should be the same, RSPAC-51
		assertEquals(copy.getCreationDateMillis(), original.getCreationDateMillis());
		Set<String> fieldsToExclude = toSet( "id", "createdBy", "subSample");
		ModelTestUtils.assertCopiedFieldsAreEqual(original, copy, fieldsToExclude, toList(SubSampleNote.class));		
	}
	
}
