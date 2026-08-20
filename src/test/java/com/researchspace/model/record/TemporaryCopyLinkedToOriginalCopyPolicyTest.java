package com.researchspace.model.record;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.researchspace.model.field.FieldForm;

public class TemporaryCopyLinkedToOriginalCopyPolicyTest {
	IFormCopyPolicy<RSForm> copier;
	@Before
	public void setUp() throws Exception {
		copier = new TemporaryCopyLinkedToOriginalCopyPolicy();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testCopy() throws InterruptedException {
		RSForm t1 = TestFactory.createAnyForm("t");
		FieldForm ft = TestFactory.createDateFieldForm();
		t1.addFieldForm(ft);
		Thread.sleep(2);
		RSForm copy = copier.copy(t1);
		
		// check relations of template
		assertNotNull(t1.getTempForm());
		assertNull(copy.getTempForm());
		assertEquals(t1.getTempForm(), copy);
		
		// and of fields
		FieldForm ftCpy = copy.getFieldForms().get(copy.getFieldForms().size() -1);
		assertNull(ftCpy.getTempFieldForm());
		assertEquals(ftCpy, ft.getTempFieldForm());
		
		assertTrue(copy.getCreationDateAsDate().after(t1.getCreationDateAsDate()));
		
		assertEquals(t1.getNumActiveFields(), copy.getNumActiveFields());
		assertEquals(t1.getNumAllFields(), copy.getNumAllFields());
		
	}

}
