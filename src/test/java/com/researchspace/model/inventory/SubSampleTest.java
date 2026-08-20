package com.researchspace.model.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.researchspace.core.testutil.ModelTestUtils;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;

public class SubSampleTest {
	private Sample sample;
	private SubSample subSample;
	private User anyUser;

	@BeforeEach
	void setUp() throws Exception {
		anyUser = TestFactory.createAnyUser("any");
		sample = TestFactory.createSampleWithSubSamplesAndEverything(anyUser, 1);

		sample.setId(5L);
		subSample = sample.getActiveSubSamples().get(0);
	}

	@Test
	 void assertQuantityCantBeNegative() throws Exception {
		QuantityInfo positiveQuantity = QuantityInfo.of(BigDecimal.valueOf(0), RSUnitDef.GRAM);
		subSample.setQuantity(positiveQuantity);
		QuantityInfo negativeQuantity = QuantityInfo.of(BigDecimal.valueOf(-0.001), RSUnitDef.GRAM);
		IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, ()->subSample.setQuantity(negativeQuantity));
		assertEquals("Trying to set negative record quantity: -0.001", iae.getMessage());
	}
	
	@Test
	void shallowCopy() throws IllegalArgumentException, IllegalAccessException, IOException {
		
		SubSample copy = subSample.shallowCopy();
		Set<String>toIgnore= TransformerUtils.toSet("id", "sample","activeExtraFields", "extraFields",
				"activeBarcodes", "barcodes", "attachedFiles", "files", "editInfo", "notes", "thumbnailFileProperty", "imageFileProperty");
		ModelTestUtils.assertCopiedFieldsAreEqual(copy, subSample, toIgnore, TransformerUtils.toList(SubSample.class,InventoryRecord.class));
		assertNull(copy.getGlobalIdentifier());
		assertNull(copy.getImageFileProperty());
	}
	
	@Test
	 void copy() throws IllegalArgumentException, IllegalAccessException, IOException {
		SubSample copy = subSample.copy(anyUser);
		
		
		Set<String>toIgnore= TransformerUtils.toSet("id","activeExtraFields", "sample", "notes", "extraFields",
				"activeBarcodes", "barcodes", "attachedFiles", "files", "editInfo");
		ModelTestUtils.assertCopiedFieldsAreEqual(copy, subSample, toIgnore, TransformerUtils.toList(SubSample.class,InventoryRecord.class));
		assertNull(copy.getGlobalIdentifier());
		assertNotNull(copy.getImageFileProperty());
		assertNotNull(copy.getThumbnailFileProperty());
		assertEquals(subSample.getImageFileProperty(), copy.getImageFileProperty());
		assertEquals(subSample.getThumbnailFileProperty(), copy.getThumbnailFileProperty());
		assertEquals(sample, copy.getSample());
		assertEquals(2, copy.getActiveExtraFields().size());
		assertEquals(2, copy.getNotes().size());
		assertEquals(subSample.getName()+"_COPY", copy.getName());
		
	}
	
}
