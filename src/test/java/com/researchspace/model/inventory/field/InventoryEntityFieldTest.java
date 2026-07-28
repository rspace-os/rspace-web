package com.researchspace.model.inventory.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleTemplate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class InventoryEntityFieldTest {

	@Test
	void choiceField() {
		InventoryChoiceField field = createChoiceField();
	
		field.setSelectedOptions(Arrays.asList("e","d"));
		assertEquals(Arrays.asList("e", "d"), field.getSelectedOptions()); // check selected options
		assertEquals("[\"e\",\"d\"]", field.getData()); // selected options string as saved in db
		assertEquals(Arrays.asList("b", "c", "d", "e", "\"mr Smith\"'s option", " pi=3.14", "{a}, [b:] & \\c/"), 
				field.getAllOptions()); // check all options
		assertEquals("[\"b\",\"c\",\"d\",\"e\",\"\\\"mr Smith\\\"'s option\",\" pi=3.14\",\"{a}, [b:] & \\\\c/\"]", 
				field.getChoiceDef().getChoiceOptions()); // all options string as saved in db
		
		// select fancy options
		field.setSelectedOptions(Arrays.asList("b", "\"mr Smith\"'s option", " pi=3.14"));
		assertEquals("[\"b\",\"\\\"mr Smith\\\"'s option\",\" pi=3.14\"]", field.getData()); // selected options string as saved in db
		
		// option outside definition list is invalid
		IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () -> field.setSelectedOptions(List.of("a")));
		assertEquals("[[\"a\"]] is invalid for field type Choice: Some supplied values are not allowed options", iae.getMessage());
		// option that differs by trailing space is also invalid
		iae = assertThrows(IllegalArgumentException.class, () -> field.setSelectedOptions(List.of("pi=3.14")));
		assertEquals("[[\"pi=3.14\"]] is invalid for field type Choice: Some supplied values are not allowed options", iae.getMessage());
	}
	
	@ParameterizedTest
	@NullAndEmptySource
	void ChoiceFieldNullOrBlankOk(String empty) {
		InventoryChoiceField field = createChoiceField();
		field.setFieldData(empty);
		assertEquals(Collections.EMPTY_LIST, field.getSelectedOptions());
	}

	@ParameterizedTest
	// x is not defined in the choice definition
	@ValueSource(strings = { "", " ", "[]", "[\"b\",\"c\",\"d\",\"e\",\"\\\"mr Smith\\\"'s option\",\" pi=3.14\",\"{a}, [b:] & \\\\c/\"]" })
	void ChoiceFieldValidValues(String value) {
		InventoryChoiceField field = createChoiceField();
		field.setFieldData(value);
		assertFalse(field.validate(value).hasErrorMessages());
	}
	
	private InventoryChoiceField createChoiceField() {
		InventoryChoiceFieldDef defn = createAChoiceDefinition();
		return new InventoryChoiceField(defn, "choice");
	}

	private InventoryChoiceFieldDef createAChoiceDefinition() {
		InventoryChoiceFieldDef defn = new InventoryChoiceFieldDef();
		defn.setChoiceOptionsList(getTestChoiceAndRadioOptionsList());
		return defn;
	}

	private List<String> getTestChoiceAndRadioOptionsList() {
		return Arrays.asList("b", "c", "d", "e", "\"mr Smith\"'s option", " pi=3.14", "{a}, [b:] & \\c/");
	}
	
	@ParameterizedTest
	@NullAndEmptySource
	void RadioFieldNullOrBlankOk(String empty) {
		InventoryRadioField field = createRadioField();
		field.setFieldData(empty);
	}

	@Test
	void radioField() {
		InventoryRadioField field = createRadioField();

		assertThrows(IllegalArgumentException.class, () -> field.setFieldData(" "));
		assertThrows(IllegalArgumentException.class, () -> field.setFieldData("incorrect formt"));
		assertThrows(IllegalArgumentException.class, () -> field.setFieldData("pi=3.14"));
	
		field.setFieldData("\"mr Smith\"'s option");
		assertEquals(Arrays.asList("b", "c", "d", "e", "\"mr Smith\"'s option", " pi=3.14", "{a}, [b:] & \\c/"), 
				field.getAllOptions()); // all possible options
		assertEquals("[\"b\",\"c\",\"d\",\"e\",\"\\\"mr Smith\\\"'s option\",\" pi=3.14\",\"{a}, [b:] & \\\\c/\"]", 
				field.getRadioDef().getRadioOptions()); // options string as saved in db
		assertEquals("\"mr Smith\"'s option", field.getData()); // selected option as saved in db

		// invalid option rejected
		IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, 
				() -> field.setFieldData("invalid"));
		assertEquals("[invalid] is invalid for field type Radio: Some supplied values are not allowed options", iae.getMessage());
	}
	

	private InventoryRadioField createRadioField() {
		InventoryRadioFieldDef defn = createRadioDefn();
		return new InventoryRadioField(defn, "radio");
	}
	
	private InventoryRadioFieldDef createRadioDefn() {
		InventoryRadioFieldDef defn = new InventoryRadioFieldDef();
		defn.setRadioOptionsList(getTestChoiceAndRadioOptionsList());
		return defn;
	}

	@Test
	void numberFieldInvalidThrowsIAE() {
		InventoryNumberField field = new InventoryNumberField("num");
		assertThrows(IllegalArgumentException.class, () -> field.setFieldData("incorrect formt"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "1", " ", "-1.20", "456.44", "5.3e2", "5.3e2", "-5.3e2" })
	void numberFieldValidValues(String value) {
		InventoryNumberField field = new InventoryNumberField("num");
		field.setFieldData(value);
		if (StringUtils.isNotBlank(value)) {
			assertTrue(field.isSuggestedFieldForData(value));
		}
	}

	@ParameterizedTest
	@NullAndEmptySource
	void numberFieldEmptyValues(String value) {
		InventoryNumberField field = new InventoryNumberField("num");
		field.setFieldData(value);
	}

	@ParameterizedTest
	@ValueSource(strings = { "2020-01-31", "2024-08-19", "1999-12-31" })
	void dateFieldValidValues(String value) {
		InventoryDateField field = new InventoryDateField("date");
		field.setFieldData(value);
	}

	@ParameterizedTest
	@ValueSource(strings = {" ", "2020-01-31T15:50:00", "20200131",
			"2020.01.01", "anything", "456.44", "-123" })
	void setDateFieldWithInvalidFormatThrowsException(String value) {
		InventoryDateField field = new InventoryDateField("date");
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> field.setFieldData(value) );
		assertTrue(exception.getMessage().contains("is invalid for field type Date"));
	}


	@ParameterizedTest
	@ValueSource(strings = { "2020-01-31", "1900-01-01", "2124-08-19"})
	void dateFieldSuggestedForValue(String value) {
		InventoryDateField field = new InventoryDateField("date");
		assertTrue(field.isSuggestedFieldForData(value));
	}

	@ParameterizedTest
	@ValueSource(strings = { "2020.01.01", "anything", "456.44", "-123", "2020-01-31T15:50:00", "20200131" })
	void dateFieldNotSuggestedForValue(String value) {
		InventoryDateField field = new InventoryDateField("date");
		assertFalse(field.isSuggestedFieldForData(value));
	}
	
	/*
	 * The test is here to document current behaviour.
	 * Validation should probably be stricter than it is.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "http://test.com", " ", "ftp://howler", "ldap://howler", "/test", "anything", "-3.14", })
	void uriFieldValidValues(String value) {
		InventoryUriField field = new InventoryUriField("uri");
		field.setFieldData(value);
	}

	@ParameterizedTest
	@ValueSource(strings = { "my uri", "\\"})
	void uriFieldInvalidThrowsIAE(String value) {
		InventoryUriField field = new InventoryUriField("uri");
		assertThrows(IllegalArgumentException.class, () -> field.setFieldData(value));
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://test.com", "ftp://howler" })
	void uriFieldSuggestedForValue(String value) {
		InventoryUriField field = new InventoryUriField("uri");
		assertTrue(field.isSuggestedFieldForData(value));
	}

	@ParameterizedTest
	@ValueSource(strings = { "uri", "anytext", "-3.14", "ldap://howler", "/test" })
	void uriFieldNotSuggestedForValue(String value) {
		InventoryUriField field = new InventoryUriField("uri");
		assertFalse(field.isSuggestedFieldForData(value));
	}

	@ParameterizedTest
	@ValueSource(strings = { "12:45", "00:00", "09:25", "23:59"})
	void timeFieldValidValues(String value) {
		InventoryTimeField field = new InventoryTimeField("time");
		field.setFieldData(value);
	}

	@ParameterizedTest
	@ValueSource(strings = { "12:45", "00:00", "09:25", "23:59" })
	void timeFieldSuggestedForValue(String value) {
		InventoryTimeField field = new InventoryTimeField("time");
		assertTrue(field.isSuggestedFieldForData(value));
	}

	@ParameterizedTest
	@ValueSource(strings = { "time", "no time", "-1", " ", "10:24am", "23:79"})
	void timeFieldNotSuggestedForValue(String value) {
		InventoryTimeField field = new InventoryTimeField("time");
		assertFalse(field.isSuggestedFieldForData(value));
	}

	@ParameterizedTest
	@ValueSource(strings = {"2020-01-31T15:50:00", "9:17", "10:24am", "23:79" })
	void setTimeInvalidFormatThrowsError(String value) {
		InventoryTimeField field = new InventoryTimeField("time");
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> field.setFieldData(value) );
		assertTrue(exception.getMessage().contains("is invalid for field type Time"));
	}

	@Test
	public void attachmentField() {
		InventoryAttachmentField field = new InventoryAttachmentField();
		InventoryFile fileA = new InventoryFile("fileA", null);
		InventoryFile fileB = new InventoryFile("fileB", null);
		field.setAttachedFile(fileA);
		field.setFieldData("test attachment");
		assertEquals(fileA, field.getAttachedFile());
		assertFalse(fileA.isDeleted());
		assertEquals(1, field.getFiles().size());
		assertEquals("test attachment", field.getData());
		
		field.setAttachedFile(fileB);
		assertEquals(fileB, field.getAttachedFile());
		assertTrue(fileA.isDeleted());
		assertEquals(2, field.getFiles().size());
		
		// file parameter validated to be not null
		assertThrows(NullPointerException.class, () -> field.setAttachedFile(null));
	}
	
	@Test
	void updateSampleFieldToLatestTemplateVersion() {
		// create 'template' field and corresponding 'sample' field
		InventoryNumberField templateField = new InventoryNumberField("num");
		templateField.setData("3.14");
		InventoryNumberField field = new InventoryNumberField("num");
		field.setTemplateField(templateField);
		assertEquals("num", field.getName());
		
		// try updating to latest template definition - no changes found
		boolean updateResult = field.updateToLatestTemplateDefinition();
		assertFalse(updateResult);
		
		// try updating template field to latest definition - but no definition 
		IllegalStateException ise = assertThrows(IllegalStateException.class, () -> templateField.updateToLatestTemplateDefinition());
		assertEquals("Field [num] has no connected template field", ise.getMessage());

		// update template field name, then try updating the sample field
		templateField.setName("num updated");
		updateResult = field.updateToLatestTemplateDefinition();
		assertTrue(updateResult);
		assertEquals("num updated", field.getName());

		// update template field mandatory, then try updating the sample field
		assertFalse(field.isMandatory());
		templateField.setMandatory(true);
		ise = assertThrows(IllegalStateException.class, () -> field.updateToLatestTemplateDefinition());
		assertEquals("Field [num updated] is empty, but is mandatory in latest template field definition", ise.getMessage());

		// reset field to non-mandatory (throwing exception doesn't reset the state here in the test code)
		field.setMandatory(false);
		// put a non-blank value, try updating again to latest mandatory field definition
		field.setFieldData("3.14");
		updateResult = field.updateToLatestTemplateDefinition();
		assertTrue(updateResult);
		assertTrue(field.isMandatory());
		
		// delete template field, then try updating the sample field
		templateField.setDeleted(true);
		updateResult = field.updateToLatestTemplateDefinition();
		assertFalse(updateResult);
		assertFalse(field.isDeleted());

		// delete template field with option to delete pre-existing fields, then try updating the sample field
		templateField.setDeleteOnSampleUpdate(true);
		updateResult = field.updateToLatestTemplateDefinition();
		assertTrue(updateResult);
		assertTrue(field.isDeleted());
	}

	@Test
	void updateRadioFieldToLatestTemplateVersion() {
		// create 'template' field and corresponding 'sample' field
		InventoryRadioField templateField = createRadioField();
		templateField.getRadioDef().setId(1L);
		InventoryRadioField field = templateField.shallowCopy();
		field.setTemplateField(templateField);
		field.setFieldData("b");
		assertEquals("radio", field.getName());
		assertEquals(Arrays.asList("b", "c", "d", "e", "\"mr Smith\"'s option", " pi=3.14", "{a}, [b:] & \\c/"), field.getAllOptions());
		
		// try updating to latest template definition - no changes found
		boolean updateResult = field.updateToLatestTemplateDefinition();
		assertFalse(updateResult);

		// update template radio with new options, try updating sample field
		InventoryRadioFieldDef newDefn = new InventoryRadioFieldDef();
		newDefn.setRadioOptionsList(Arrays.asList("c", "d", "e", "f"));
		newDefn.setId(2L);
		templateField.setRadioDef(newDefn);
		IllegalStateException ise = assertThrows(IllegalStateException.class, () -> field.updateToLatestTemplateDefinition());
		assertEquals("Field [radio] value [b] is invalid according to latest template field definition", ise.getMessage());

		// update sample field to value that's valid with new template
		field.setData("c");
		// try updating again
		updateResult = field.updateToLatestTemplateDefinition();
		assertTrue(updateResult);
		assertEquals(Arrays.asList("c", "d", "e", "f"), field.getAllOptions());
		assertEquals("c", field.getFieldData());
	}
	
	@Test
	void updateChoiceFieldToLatestTemplateVersion() {
		// create 'template' field and corresponding 'sample' field
		InventoryChoiceField templateField = createChoiceField();
		templateField.getChoiceDef().setId(1L);
		InventoryChoiceField field = templateField.shallowCopy();
		field.setTemplateField(templateField);
		field.setFieldData("[\"b\",\"{a}, [b:] & \\\\c/\"]");
		assertFalse(field.validate(field.getData()).hasErrorMessages());
		assertEquals("choice", field.getName());
		assertEquals(Arrays.asList("b", "c", "d", "e", "\"mr Smith\"'s option", " pi=3.14", "{a}, [b:] & \\c/"), field.getAllOptions());
		assertEquals(Arrays.asList("b", "{a}, [b:] & \\c/"), field.getSelectedOptions());
		
		// try updating to latest template definition - no changes found
		boolean updateResult = field.updateToLatestTemplateDefinition();
		assertFalse(updateResult);

		// update template radio with new options, try updating sample field
		InventoryChoiceFieldDef newDefn = new InventoryChoiceFieldDef();
		newDefn.setChoiceOptionsList(Arrays.asList("c", "d", "e", "f","{a}, [b:] & \\c/"));
		newDefn.setId(2L);
		templateField.setChoiceDef(newDefn);
		IllegalStateException ise = assertThrows(IllegalStateException.class, () -> field.updateToLatestTemplateDefinition());
		assertEquals("Field [choice] value [[\"b\",\"{a}, [b:] & \\\\c/\"]] is invalid according to latest template field definition", ise.getMessage());

		// update sample field to value that's valid with new template
		field.setFieldData("[\"c\",\"{a}, [b:] & \\\\c/\"]");
		// try updating again
		updateResult = field.updateToLatestTemplateDefinition();
		assertTrue(updateResult);
		assertEquals(Arrays.asList("c", "d", "e", "f", "{a}, [b:] & \\c/"), field.getAllOptions());
		assertEquals(Arrays.asList("c", "{a}, [b:] & \\c/"), field.getSelectedOptions());
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " ", "\t" })
	void mandatoryStringFieldRejectsBlankValue(String value) {
		Sample parentSample = new Sample();
		InventoryStringField field = new InventoryStringField("string");
		field.setInventoryRecord(parentSample);
		field.setMandatory(false);
		assertFalse(field.validate(value).hasErrorMessages());

		field.setMandatory(true);
		assertTrue(field.validate(value).hasErrorMessages());
		
		// but if field is a part of the sample template, then it accepts blank value fine
		field.setInventoryRecord(new SampleTemplate());
		assertFalse(field.validate(value).hasErrorMessages());
	}

	@ParameterizedTest
	@ValueSource(strings = { "test", " a ", "null" })
	void mandatoryStringFieldAcceptsNonBlankValue(String value) {
		InventoryStringField field = new InventoryStringField("string");
		field.setMandatory(false);
		assertFalse(field.validate(value).hasErrorMessages());

		field.setMandatory(true);
		assertFalse(field.validate(value).hasErrorMessages());
	}	

}
