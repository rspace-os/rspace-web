package com.researchspace.model.inventory.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.researchspace.core.testutil.ModelTestUtils;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.record.TestFactory;

class ExtraFieldTest {

	User anyUser;
	Sample sample;

	@BeforeEach
	void before() {
		anyUser = TestFactory.createAnyUser("any");
		sample = TestFactory.createBasicSampleInContainer(anyUser);
	}

	@Test
	void testShallowCopyNumber() throws InterruptedException, IllegalArgumentException, IllegalAccessException {
		ExtraNumberField original = TestFactory.createExtraNumberField("n1", anyUser, sample);
		original.setData("256");
		original.setId(22L);
		Thread.sleep(2);
		ExtraNumberField copy = original.shallowCopy();
		assertCopy(original, copy, TransformerUtils.toList(ExtraNumberField.class, ExtraField.class));
	}

	@Test
	void testShallowCopyText() throws InterruptedException, IllegalArgumentException, IllegalAccessException {
		ExtraTextField original = TestFactory.createExtraTextField("n1", anyUser, sample);
		original.setData("abcde");
		original.setId(22L);
		Thread.sleep(2);
		ExtraTextField copy = original.shallowCopy();		
		assertCopy(original, copy, TransformerUtils.toList(ExtraTextField.class, ExtraField.class));
	}

	private <T extends ExtraField> void assertCopy(T original, T copy, List<Class<? super T>> list)
			throws IllegalAccessException {
		assertFalse(copy == original);
		Set<String> fieldsToExclude = TransformerUtils.toSet("creationDate", "id", "editInfo", "sample");
		ModelTestUtils.assertCopiedFieldsAreEqual(original, copy, fieldsToExclude, list);
		
		assertEquals(original.getName(), copy.getName());
		assertEquals(original.getData(), copy.getData());
		assertNull(copy.getId());
		assertFalse(copy.getModificationDate().equals(original.getModificationDate()));
		assertNull(copy.getInventoryRecord());
		assertNotNull(original.getInventoryRecord());
	}

}
