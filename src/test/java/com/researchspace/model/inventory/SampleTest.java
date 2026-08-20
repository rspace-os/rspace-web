package com.researchspace.model.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.researchspace.core.testutil.ModelTestUtils;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierOtherProperty;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryNumberField;
import com.researchspace.model.inventory.field.InventoryRadioField;
import com.researchspace.model.inventory.field.InventoryRadioFieldDef;
import com.researchspace.model.inventory.field.InventoryTextField;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class SampleTest {
	
	private Sample sample;
	private User anyUser;

	@BeforeEach
	public void setUp() throws Exception {
		anyUser = TestFactory.createAnyUser("any");
		sample = TestFactory.createSampleWithSubSamplesAndEverything(anyUser, 1);
		sample.setId(5L);
	}

	@Test
	public void testInitialProperties() {
		assertNotNull(sample.getModificationDate());
		assertNotNull(sample.getCreationDate());
		assertNotNull(sample.getSampleSource());
		assertFalse(sample.isDeleted());
		assertEquals(InventoryRecord.InventoryRecordType.SAMPLE, sample.getType());
		assertTrue(sample.isSample());
		assertFalse(sample.isSampleTemplate());
		assertEquals(1, sample.getAttachedFiles().size());
		assertEquals(1, sample.getActiveBarcodes().size());
		assertEquals(1, sample.getActiveSubSamplesCount());
		assertEquals(1, sample.getVersion());
		assertEquals(1, sample.getSubSamples().get(0).getVersion());
		assertEquals("SA" + sample.getId(), sample.getOid().toString());
		assertEquals("SA" + sample.getId() + "v1", sample.getOidWithVersion().toString());
	}

	@Test
	public void assertQuantityCantBeNegative() {
		QuantityInfo positiveQuantity = QuantityInfo.of(BigDecimal.valueOf(10), RSUnitDef.LITRE);
		sample.setTotalQuantity(positiveQuantity);
		try {
			QuantityInfo negativeQuantity = QuantityInfo.of(BigDecimal.valueOf(-10), RSUnitDef.LITRE);
			sample.setTotalQuantity(negativeQuantity);
			fail("shouldn't be able to set negative total quantity");
		} catch (IllegalArgumentException iae) {
			assertEquals("Trying to set negative record quantity: -10", iae.getMessage());
		}
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void copy() throws IllegalArgumentException, IllegalAccessException {
		Sample copy = sample.copy(anyUser);
		
		Set<String> toIgnore = TransformerUtils.toSet("subSamples", "activeSubSamples", "id",
				"activeExtraFields", "extraFields", "activeBarcodes", "barcodes", "activeIdentifiers", "identifiers",
				"sample", "editInfo", "attachedFiles", "files");
		ModelTestUtils.assertCopiedFieldsAreEqual(copy, sample, toIgnore,
				TransformerUtils.toList(Sample.class, SampleEntity.class, InventoryRecord.class));
		assertNull(copy.getGlobalIdentifier());
		assertNotNull(copy.getImageFileProperty());
		assertNotNull(copy.getThumbnailFileProperty());
		assertEquals(sample.getImageFileProperty(), copy.getImageFileProperty());
		assertEquals(sample.getThumbnailFileProperty(), copy.getThumbnailFileProperty());
		assertEquals(1, copy.getSubSamples().size());
		assertEquals(1, copy.getActiveSubSamplesCount());
		assertEquals(1, copy.getActiveExtraFields().size());
		assertEquals(1, copy.getAttachedFiles().size());

		assertEquals(sample.getName() + "_COPY", copy.getName());
		assertEquals(sample.getTotalQuantity(), copy.getTotalQuantity());
		assertEquals(sample.getTotalQuantity(), copy.getSubSamples().get(0).getQuantity());
		assertEquals(sample.getStorageTempMax(), copy.getStorageTempMax());
		assertEquals(sample.getStorageTempMin(), copy.getStorageTempMin());
		assertEquals(sample.getSampleSource(), copy.getSampleSource());
	}
	
	@Test
	@DisplayName("Use OID Distinguish samples from templates")
	public void globalId() {
		assertTrue(sample.getGlobalIdentifier().startsWith("SA"));
		assertEquals(sample.getOid().getPrefix(), GlobalIdPrefix.SA);
		
		SampleTemplate template = sample.copyToTemplate(anyUser);
		template.setId(6L);
		assertTrue(template.getGlobalIdentifier().startsWith("IT"));
		assertTrue(template.getOid().getPrefix().equals(GlobalIdPrefix.IT));
		assertFalse(template.getOid().toString().endsWith("v1"));
		assertTrue(template.getOidWithVersion().toString().endsWith("v1"));
	}

	@Test
	public void subSampleAlias() {
		SampleTemplate template = sample.copyToTemplate(anyUser);
		template.setSubSampleAliases("alias", " aliases ");
		assertEquals("alias", template.getSubSampleAlias());
		assertEquals("aliases", template.getSubSampleAliasPlural()); // trimmed

		// try setting new empty alias
		IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () -> template.setSubSampleAliases("", "aliases"));
		assertEquals("SubSample alias cannot be blank", iae.getMessage());
		iae = assertThrows(IllegalArgumentException.class, () -> template.setSubSampleAliases("alias", ""));
		assertEquals("SubSample alias (plural) cannot be blank", iae.getMessage());
	}

	@Test
	public void igsnIdentifier() {
		assertEquals(1, sample.getIdentifiers().size());
		DigitalObjectIdentifier identifier = sample.getIdentifiers().get(0);
		assertEquals("{\"CREATOR_NAME\":\"testCreator\",\"SUBJECTS\":\"[\\\"subject1\\\",\\\"subject2\\\"]\"}", identifier.getOtherDataJsonString());
		assertEquals("testCreator", identifier.getOtherData(IdentifierOtherProperty.CREATOR_NAME));
		assertEquals(List.of("subject1", "subject2"), identifier.getOtherListData(DigitalObjectIdentifier.IdentifierOtherListProperty.SUBJECTS));
		assertNull(identifier.getOtherData(IdentifierOtherProperty.PUBLISHER));
		
		// add another
		identifier.addOtherData(IdentifierOtherProperty.PUBLISHER, "testPublisher");
		identifier.addOtherListData(DigitalObjectIdentifier.IdentifierOtherListProperty.DESCRIPTIONS, List.of("desc1"));
		assertEquals(1, sample.getIdentifiers().size());
		assertEquals("{\"CREATOR_NAME\":\"testCreator\",\"SUBJECTS\":\"[\\\"subject1\\\",\\\"subject2\\\"]\"" +
						",\"PUBLISHER\":\"testPublisher\",\"DESCRIPTIONS\":\"[\\\"desc1\\\"]\"}", 
				identifier.getOtherDataJsonString());
		assertEquals("testPublisher", identifier.getOtherData(IdentifierOtherProperty.PUBLISHER));
		
		// null handled fine
		assertEquals(1, sample.getIdentifiers().size());
		identifier.setOtherDataJsonString(null);
		assertNull(identifier.getOtherDataJsonString());
		assertNull(identifier.getOtherData(IdentifierOtherProperty.CREATOR_NAME));
		assertNull(identifier.getOtherData(IdentifierOtherProperty.PUBLISHER));
		assertNull(identifier.getOtherListData(DigitalObjectIdentifier.IdentifierOtherListProperty.DATES));
	}

	@Test
	@DisplayName("Make template from sample")
	public void copyToTemplate() {
		SampleTemplate template = sample.copyToTemplate(anyUser);
		assertTrue(template.isTemplate());
		
		// can assign a sample template fine 
		sample.setSampleTemplate(template);

		// can copy a sample, but can't sample from as if it was a template 
		Sample justACopy = sample.copy(anyUser);
		assertFalse(justACopy.isTemplate());
		IllegalStateException ise = assertThrows(IllegalStateException.class, ()-> justACopy.copyFromTemplate(anyUser));
		assertEquals("Only a SampleTemplate can be used to copy from a template", ise.getMessage());
		
		// can copy a template
		SampleTemplate newTemplate = justACopy.copyToTemplate(anyUser);
		assertTrue(newTemplate.isTemplate());
	}
	
	@Test
	@DisplayName("Make sample from template")
	public void copyFromTemplate() {
		//make a template
		SampleTemplate template = sample.copyToTemplate(anyUser);

		Sample newSample = template.copyFromTemplate(anyUser);
		assertFalse(newSample.isTemplate());
		assertEquals(template, newSample.getSTemplate());
		assertEquals(template.getVersion(), newSample.getSTemplateLinkedVersion());
		assertEquals(1, newSample.getVersion());
		assertEquals(1, newSample.getSubSamples().get(0).getVersion());
	}
	
	@Test
	@DisplayName("Update sample to latest template definition")
	public void updateSampleToLatestTemplateDefinition() {
		// make a template, and a new sample out of it
		SampleTemplate template = sample.copyToTemplate(anyUser);
		Sample newSample = template.copyFromTemplate(anyUser);
		assertEquals(SubSampleName.ALIQUOT.getDisplayName(), newSample.getSubSampleAlias());
		assertEquals(0, newSample.getActiveFields().size());
		assertEquals(1, newSample.getSTemplateLinkedVersion());
		
		// try updating to latest template version - no changes found
		boolean updateResult = newSample.updateToLatestTemplateVersion();
		assertFalse(updateResult);

		// update template: change subSampleName and add two fields: text & radio
		template.setSubSampleAliases(SubSampleName.PIECE);
		InventoryTextField textField = new InventoryTextField("text");
		textField.setId(1L);
		textField.setColumnIndex(2); // in reverse ordering
		template.addSampleField(textField);
		InventoryRadioFieldDef defn = new InventoryRadioFieldDef();
		defn.setRadioOptionsList(Arrays.asList("b", "c", "d", "e"));
		InventoryRadioField radioField = new InventoryRadioField(defn, "radio");
		radioField.setId(2L);
		radioField.setColumnIndex(1);
		template.addSampleField(radioField);
		template.refreshActiveFieldsAndColumnIndex();
		template.increaseVersion();
		
		// update sample to latest template version
		updateResult = newSample.updateToLatestTemplateVersion();
		assertTrue(updateResult);
		assertEquals("piece", newSample.getSubSampleAlias());
		assertEquals(2, newSample.getActiveFields().size());
		assertEquals("radio", newSample.getActiveFields().get(0).getName());
		assertEquals("text", newSample.getActiveFields().get(1).getName());
		assertEquals(2, newSample.getSTemplateLinkedVersion());
		
		// update template field name, then update sample
		textField.setName("text updated");
		template.increaseVersion();
		updateResult = newSample.updateToLatestTemplateVersion();
		assertTrue(updateResult);
		assertEquals("text updated", newSample.getActiveFields().get(1).getName());
		assertEquals(3, newSample.getSTemplateLinkedVersion());
	}

	@Test
	public void checkTemplateOperations() throws Exception {
		// make a template
		SampleTemplate template = sample.copyToTemplate(anyUser);

		// try attaching a file to workbench
		IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, 
				() -> template.addAttachedFile(new InventoryFile(null, null)));
		assertEquals("Sample Templates don't support file attachments yet", iae.getMessage());
	}
	
	@Test
	@DisplayName("Delete sample template field")
	public void deleteSampleTemplateField() {
		
		SampleTemplate template = sample.copyToTemplate(anyUser);

		// no fields to start with
		assertEquals(0, template.getFields().size());
		assertEquals(0, template.getActiveFields().size());
		assertEquals(0, template.getCurrMaxColIndex());
		
		// add two fields
		InventoryEntityField numberField = new InventoryNumberField("number");
		numberField.setId(1L);
		template.addSampleField(numberField);
		InventoryEntityField textField = new InventoryTextField("text");
		textField.setId(2L);
		template.addSampleField(textField);
		
		// check added fields
		assertEquals(2, template.getFields().size());
		assertEquals(2, template.getActiveFields().size());
		assertEquals(2, template.getCurrMaxColIndex());
		assertEquals("number", template.getActiveFields().get(0).getName());
		assertEquals(1, template.getActiveFields().get(0).getColumnIndex());
		assertEquals("text", template.getActiveFields().get(1).getName());
		assertEquals(2, template.getActiveFields().get(1).getColumnIndex());

		// delete a field
		template.deleteSampleField(numberField, true);
		template.refreshActiveFieldsAndColumnIndex();
		assertEquals(2, template.getFields().size());
		assertTrue(template.getFields().get(0).isDeleted());
		assertTrue(template.getFields().get(0).isDeleteOnSampleUpdate());
		assertEquals(1, template.getActiveFields().size());
		assertEquals(1, template.getCurrMaxColIndex());
		assertEquals("text", template.getActiveFields().get(0).getName());
		assertEquals(1, template.getActiveFields().get(0).getColumnIndex());
	}

}
