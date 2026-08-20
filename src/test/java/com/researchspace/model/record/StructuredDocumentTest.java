package com.researchspace.model.record;



import static com.researchspace.model.record.TestFactory.createAnyGroup;
import static com.researchspace.model.record.TestFactory.createAnyUserWithRole;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.researchspace.Constants;
import com.researchspace.core.testutil.ModelTestUtils;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.Version;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.permissions.RecordSharingACL;

public class StructuredDocumentTest {
	
	private StructuredDocument sd;

	@BeforeEach
	public void setUp() throws Exception {
		sd = new StructuredDocument();
	}

	@AfterEach
	public void tearDown() throws Exception {
	}
	
	@Test
	public void testGetSetNameCantBeEmptyName() {
		Assertions.assertThrows(IllegalArgumentException.class, ()->sd.setName(" "));
	}
	
	@Test
	public void testSettingSameNameDoesNotGenerateDelta() {
		sd.setName("a");
		assertFalse(StringUtils.isEmpty(sd.getDeltaStr()));
		
		sd.clearDelta();
		sd.setName("a");
		assertTrue(StringUtils.isEmpty(sd.getDeltaStr()));
	}

	@Test
	public void testInititalProperties() {
		sd = new StructuredDocument();
		assertNotNull(sd.getModificationDate());
		assertNotNull(sd.getCreationDate());
	}
	
	@Test
	public void testAddRemoveField() {
		// creates an SD with 1 field
		StructuredDocument sd = TestFactory.createAnySD();
		Field f = TestFactory.createAnyField();
		sd.addField(f);
		assertNotNull(f.getStructuredDocument());
		assertEquals(2, sd.getFields().size());
		
		sd.removeField(f);
		assertNull(f.getStructuredDocument());
		assertEquals(1, sd.getFields().size());
	}
	
	@Test
	public void testSetFields() {
		// creates an SD with 1 field
		StructuredDocument sd = TestFactory.createAnySD();
		sd.addField(TestFactory.createDateFieldForm().
				createNewFieldFromForm());
		
		List<Field> fields = sd.getFields();
		List<Field> copies = new ArrayList<>();
		for (Field orig: fields) {
			copies.add((Field)orig.shallowCopy());
		}
		assertTrue(sd.setFields(copies));
		// these are allowed
		assertTrue(sd.setFields(null));
		assertTrue(sd.setFields(Collections.EMPTY_LIST));
	}
	
	@Test
	public void testEditablity() {
		// creates an SD with 1 field
		StructuredDocument sd = TestFactory.createAnySD();
		assertTrue(sd.isEditable()); // by default
		// not editable if is an example document
		sd.addType(RecordType.NORMAL_EXAMPLE);
		assertTrue(sd.isEditable()); // all records are editable for now
	}
	
	@Test
	public void testGetConcatenatedFieldContent() {
	    StructuredDocument sd = TestFactory.createAnySD();
	    String content = sd.getFields().get(0).getData();
	    assertEquals("0" + content, sd.getConcatenatedFieldContent());
	    
	    Field f = TestFactory.createAnyField();
        sd.addField(f);
        assertEquals("0" + content + "1" + f.getData(), sd.getConcatenatedFieldContent());
	}
	
	@Test
	public void testRemoveAllFields() {
		// creates an SD with 1 field
		StructuredDocument sd = TestFactory.createAnySD();
		Field f = TestFactory.createAnyField();
		sd.addField(f);
		assertNotNull(f.getStructuredDocument());
		assertEquals(2,sd.getFields().size());
		
		sd.removeAllFields();
		
		assertEquals(0,sd.getFields().size());
	}

	@Test
	public void shallowCopyCopiesAllRequiredFieldsTest() throws IllegalAccessException, InterruptedException {
		StructuredDocument sd = TestFactory.createAnySD();
		sd.setId(1L);// persistence id
		Thread.sleep(2);
        sd.setDocTag("tagTest");
        sd.setTagMetaData("tagMetaDataTest");
		sd.setSharingACL(RecordSharingACL.createACLForUserOrGroup(
											new Group("a",TestFactory.createAnyUser("any")), 
											PermissionType.WRITE));
		StructuredDocument copy = sd.copy();
		assertNull(copy.getId()); // copy initially transient
		Set<String>toExclude = ModelTestUtils.generateExclusionFieldsFrom(
				"id","creationDate","modificationDate","fields","editInfo", "oid", "version");
        assertNotNull(copy.getTagMetaData());
		assertEquals(sd.getDocTag(), copy.getDocTag());

		List<Class<? super StructuredDocument>> classesToConsider = new ArrayList<>();
		classesToConsider.add(StructuredDocument.class);
		classesToConsider.add(Record.class);
		ModelTestUtils.assertCopiedFieldsAreEqual(copy, sd, toExclude, classesToConsider);

		fieldsAreEqualSize(sd, copy);
	}
	
	@Test
	public void testDeleted() throws InterruptedException {
		StructuredDocument sd = TestFactory.createAnySD();
		assertFalse(sd.isDeleted());// initial state is false
		sd.setRecordDeleted(true);
		assertTrue(sd.isDeleted());
		assertTrue(sd.getDeltaStr().contains(DeltaType.DELETED.toString()));
		assertNotNull(sd.getDeletedDate());
	}
	
	@DisplayName("Validate that document creation throws IAE if not 'NORMAL' form")
	@ParameterizedTest
	@EnumSource(value = FormType.class, mode = EnumSource.Mode.EXCLUDE, names= {"NORMAL"})
	public void docMustBeCreatedFromNormalForm(FormType type)  {
		RSForm form = TestFactory.createAnyForm();
		form.setFormType(type);
		assertThrows(IllegalArgumentException.class, ()->new StructuredDocument(form));	
	}
	
	@Test
	@DisplayName("Validation of setTemplate()")
	public void templateMustBeTemplate() throws InterruptedException {
		StructuredDocument any = TestFactory.createAnySD();
		StructuredDocument notTemplate = TestFactory.createAnySD();
		assertThrows(IllegalArgumentException.class, ()->any.setTemplateSource(notTemplate));
		
		StructuredDocument isTemplate = TestFactory.createAnySD();
		isTemplate.addType(RecordType.TEMPLATE);
		any.setTemplateSource(isTemplate);
		assertEquals(isTemplate, any.getTemplate());
		// also assert copying doc also copies association
		StructuredDocument copy = any.copy();
		assertEquals(isTemplate, any.getTemplate());
	}
	
	@Test
	public void testNotifyChanges() throws InterruptedException {

		StructuredDocument sd = TestFactory.createAnySD(TestFactory.createAnyForm());

		assertNotNull(sd.getDelta());
		assertTrue(sd.getDelta().getDeltaString().contains(DeltaType.RENAME.toString()));
		Long b4 = sd.getEditInfo().getModificationDateMillis();
		assertFalse(sd.getDelta().getDeltaString().contains(DeltaType.FIELD_CHG.toString()));
		Thread.sleep(5); // ensure time difference
		sd.getFields().get(0).setFieldData("NEW");

		assertTrue(sd.getDelta().getDeltaString().contains(DeltaType.FIELD_CHG.toString()));

		Long after1 = sd.getEditInfo().getModificationDateMillis();
		assertTrue(after1 > b4);

		assertTrue(sd.hasAuditableDeltas());
		sd.clearDelta();
		assertFalse(sd.hasAuditableDeltas());
   }
   
   @Test
   public void markingVersionIncrement() {
	    Instant oldModified = Instant.now().minusMillis(1000);
		StructuredDocument sd = TestFactory.createAnySD(TestFactory.createAnyForm());
		sd.setModificationDate(oldModified.toEpochMilli());
		sd.clearDelta();

		sd.notifyDelta(DeltaType.RESTORED);
		assertTrue(sd.isMarkedForVersionIncrement());

		assertTrue(oldModified.toEpochMilli() < sd.getModificationDateMillis());
		

		sd.clearDelta();
		sd.setModificationDate(oldModified.toEpochMilli());
		sd.notifyDelta(DeltaType.DELETED);
		assertFalse(sd.isMarkedForVersionIncrement());

		assertEquals(Long.valueOf(oldModified.toEpochMilli()), sd.getModificationDateMillis());
		
		sd.notifyDelta(DeltaType.UNDELETED);
		assertFalse(sd.isMarkedForVersionIncrement());
		assertEquals(Long.valueOf(oldModified.toEpochMilli()), sd.getModificationDateMillis());

		sd.notifyDelta(DeltaType.RENAME);
		assertTrue(sd.isMarkedForVersionIncrement());

		sd.clearDelta();
		assertFalse(sd.isMarkedForVersionIncrement());

		sd.notifyDelta(DeltaType.FIELD_CHG);
		assertTrue(sd.isMarkedForVersionIncrement());
		
		// clear delta, these deltas should not trigger modification date or version number change.
		sd.clearDelta();
	    
	    sd.setModificationDate(oldModified.toEpochMilli());
	    
		sd.notifyDelta(DeltaType.NOREVISION_UPDATE);
		assertFalse(sd.isMarkedForVersionIncrement());
		assertEquals(Long.valueOf(oldModified.toEpochMilli()), sd.getModificationDateMillis());
   }
   
   @Test
   public void versionIncrementOnlyHappensIfMarked() {
		StructuredDocument sd = TestFactory.createAnySD(TestFactory.createAnyForm());
		Version v1 = sd.getUserVersion();
		// no effect- no changes
		sd.clearDelta();
		sd.incrementVersion();
		assertEquals(v1, sd.getUserVersion());

		// will now update
		sd.setMarkedForVersionIncrement(true);
		sd.incrementVersion();
		assertEquals(v1.increment(), sd.getUserVersion());
		// after updating, flag is reset.
		assertFalse(sd.isMarkedForVersionIncrement());
   }

    @Test
	public void isDeletedForUserTest() {
		StructuredDocument sd = TestFactory.createAnySD();
		User user = TestFactory.createAnyUser("user");
		User user2 = TestFactory.createAnyUser("user2");
		Folder f1 = TestFactory.createAFolder("f1", user);
		Folder f2 = TestFactory.createAFolder("f1", user2);
		// add 2 users' folders
		RecordToFolder rtf1 = f1.addChild(sd, user, true);
		f2.addChild(sd, user2, true);
	   
		rtf1.setRecordInFolderDeleted(true);
		assertTrue(sd.isDeletedForUser(user));
		assertFalse(sd.isDeletedForUser(user2));
	}
	
	@Test
	public void alteringCopyDoesNotAffectOriginalTest() {
		StructuredDocument sd = TestFactory.createAnySD();
		sd.setId(1L); // persistence id
		StructuredDocument copy = sd.copy();
		
		Field copiedField = copy.getFields().get(0);
		copiedField.setFieldData("New data");
		assertFalse("New data".equals(sd.getFields().get(0).getFieldData()));
	}

	private void fieldsAreEqualSize(StructuredDocument sd2, StructuredDocument copy) {
		assertEquals(sd2.getFields().size(), copy.getFields().size());
	}
	
	@Test
	public void testIsNotebookEntry() {
		StructuredDocument sd = TestFactory.createAnySD();
		assertFalse(sd.isNotebookEntry()); //false by default
		User user = TestFactory.createAnyUser("any");
		Folder flder = TestFactory.createAFolder("flder", user);
		flder.addChild(sd, user, true);
		sd.setOwner(user);
		assertFalse(sd.isNotebookEntry()); //still false, it's just a regular record in a folder.
		assertNull(sd.getParentNotebook());

		// let's say we shared same entry into other person's notebook
		User other = TestFactory.createAnyUser("other");
		Notebook otherNB = TestFactory.createANotebook("otherNotebook", other);

		// user add to other notebook
		otherNB.addChild(sd, user, true);
		// can still find the parent
		assertNotNull(sd.getParentNotebook());
		// but searching for original parent returns null
		assertNull(sd.getNonSharedParentNotebook());

		// now we'll add to our notebook.
		sd.notebook = null; // reset cached value
		Notebook userNB = TestFactory.createANotebook("userNotebook", user);
		userNB.addChild(sd, user, true);
		assertTrue(sd.isNotebookEntry());
		assertNotNull(sd.getParentNotebook());
		// searching for original parent returns it
		assertNotNull(sd.getNonSharedParentNotebook());
	}
	
	@Test
	public void testIsDeletedForUser() {
		// set up 3 users with a folder each
		User u1 = TestFactory.createAnyUser("f1user");
		User u2 = TestFactory.createAnyUser("f2user");
		User u3 = TestFactory.createAnyUser("f3user");
		Folder f1 = TestFactory.createAFolder("f1", u1);
		Folder f2 = TestFactory.createAFolder("f2", u2);
		Folder f3 = TestFactory.createAFolder("f3", u3);
		// add a document to each user's folder
		StructuredDocument sd = TestFactory.createAnySD();
		RecordToFolder rtf1 = f1.addChild(sd, u1, true);
		f2.addChild(sd, u2, true);
		f3.addChild(sd, u3, true);
		// and  mark as deleted from 1st user's folder 
		rtf1.setRecordInFolderDeleted(true);
		
		assertTrue(sd.isDeletedForUser(u1));
		assertFalse(sd.isDeletedForUser(u2));
		assertFalse(sd.isDeletedForUser(u3));
	}
	
	@Test
	public void isBasicDocumentTest() {
		
		User user = TestFactory.createAnyUser("user");
		
		// real basic document form is named 'Basic Document' and is a system form
		StructuredDocument basicDocument = TestFactory.createAnySD();
		RSForm basicDocumentForm = new RSForm(RecordFactory.BASIC_DOCUMENT_FORM_NAME, "my form", user);
		basicDocumentForm.setSystemForm(true);
		basicDocument.setForm(basicDocumentForm);

		StructuredDocument fakeBasicDocument = TestFactory.createAnySD();
		RSForm notRealBasicDocumentForm = new RSForm(RecordFactory.BASIC_DOCUMENT_FORM_NAME, "my form", user);
		fakeBasicDocument.setForm(notRealBasicDocumentForm);

		assertTrue( basicDocument.isBasicDocument(), "basic document seems not to be basic document");
		assertFalse( fakeBasicDocument.isBasicDocument(),"not real basic document seems to be basic document");
	}
	
	@Test
	public void identifyAsTemplate() {
		StructuredDocument basicDocument = TestFactory.createAnySD();
		assertFalse(basicDocument.isTemplate());
		basicDocument.addType(RecordType.TEMPLATE);
		assertTrue(basicDocument.isTemplate());
		basicDocument.removeType(RecordType.TEMPLATE);
		assertFalse(basicDocument.isTemplate());
	}
	
	@Test
	public void addRemoveType() {
		StructuredDocument doc = TestFactory.createAnySD();
		assertTrue(doc.hasType(RecordType.NORMAL));
		
		doc.removeType(RecordType.NORMAL);
		assertFalse(doc.hasType(RecordType.NORMAL));
		assertTrue(StringUtils.isBlank(doc.getType()));
		
		doc.addType(RecordType.NORMAL);
		doc.addType(RecordType.TEMPLATE);
		doc.addType(RecordType.SYSTEM);
		assertTrue(doc.isTemplate());
		
		doc.removeType(RecordType.TEMPLATE);
		assertTrue(doc.hasType(RecordType.NORMAL));
		assertTrue(doc.hasType(RecordType.SYSTEM));
		assertFalse(doc.hasType(RecordType.TEMPLATE));
		assertFalse(doc.isTemplate());
	}
	
	@Test
	public void asStrucDoc() {
		BaseRecord br =  TestFactory.createAnySD();
		StructuredDocument sd = br.asStrucDoc();
		assertNotNull(sd);
	}

	@Test
	public void asStrucDocThrowsISEIFISNotAStrucDoc() {
		BaseRecord br =  TestFactory.createEcatImage(1L);
		assertThrows( IllegalStateException.class,()->br.asStrucDoc());
	}
	
	@Test
	public void oidRetrieval() {
		StructuredDocument sd =  TestFactory.createAnySD();
		sd.setId(2L);		
		GlobalIdentifier oid = sd.getOid();
		assertNull(oid.getVersionId());
		GlobalIdentifier oidWithVersion = sd.getOidWithVersion();
		assertEquals(0L, oidWithVersion.getVersionId());
	}
	
	@Test
	 void isAutosharable () {
		StructuredDocument sd =  TestFactory.createAnySD();
		Group g = createAnyGroup(createAnyUserWithRole("any", Constants.PI_ROLE), sd.getOwner());
		assertFalse(sd.isAutosharable());
		sd.getOwner().getUserGroups().iterator().next().setAutoshareEnabled(true);
		assertTrue(sd.isAutosharable());
	}
	
	@Test
	public void testDocIsInSharedNotebook () {
		Long groupFolderId = 1L;
		User anyUser = TestFactory.createAnyUser("any");
		Group group = new Group();
		group.setCommunalGroupFolderId(groupFolderId);
		StructuredDocument doc = TestFactory.createAnySD();
		doc.setOwner(anyUser);
		
		Notebook notebook = TestFactory.createANotebook("nb1", anyUser);
		notebook.setId(-1L);
		assertFalse(doc.isDocumentInSharedNotebook( group));
		// in notebook, but not shared
		notebook.addChild(doc, anyUser, true);
		assertFalse(doc.isDocumentInSharedNotebook( group));
		
		Folder someFolder  = TestFactory.createAFolder("normalFolder", anyUser);
		someFolder.setId(-3L);
		someFolder.addChild(notebook, anyUser, true);
		assertFalse(doc.isDocumentInSharedNotebook(group));
		
		Folder sharedFolder  = TestFactory.createAFolder("sahredFolder", anyUser);
		sharedFolder.setId(groupFolderId);
		sharedFolder.addChild(notebook, anyUser, true);
		assertTrue(doc.isDocumentInSharedNotebook( group));	
	}
	
	@Test
	@DisplayName("explicitly set creation/modification date for imported documents")
	void importOverride() {
		Instant created = Instant.now().minus(5, ChronoUnit.DAYS);
		Instant modified =	created.plus(3, ChronoUnit.DAYS);
		
		ImportOverride imported = new ImportOverride(created, modified, "someone");
		BaseRecord doc = new StructuredDocument(new RSForm(), imported);
		assertEquals(created.toEpochMilli(), doc.getCreationDateMillis());
		assertEquals(modified.toEpochMilli(), doc.getModificationDateMillis());
		assertTrue(doc.isFromImport());
		assertEquals("someone", doc.getOriginalCreatorUsername());
		 
	}

}


