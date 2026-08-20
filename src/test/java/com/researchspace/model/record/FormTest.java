package com.researchspace.model.record;

import static com.researchspace.core.util.TransformerUtils.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.researchspace.core.testutil.ModelTestUtils;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.AccessControl;
import com.researchspace.model.Version;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.field.FieldTestUtils;
import com.researchspace.model.field.StringFieldForm;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.permissions.PermissionType;

public class FormTest {

	RSForm form = null;

	@BeforeEach
	public void setUp() throws Exception {
		form = TestFactory.createAnyForm();
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testForm() {
		form = new RSForm();
		assertInitialInvariants(form);
		assertEquals(FormType.NORMAL, form.getFormType());
	}

	private void assertInitialInvariants(AbstractForm form) {
		assertNotNull(form.getCreationDateAsDate());
		assertNotNull(form.getModificationDate());
		assertTrue(form.isNewState());
		assertNotNull(form.getPublishingState());
		assertFalse(form.isCurrent());
		assertNull(form.getPreviousVersion());
		assertFalse(form.isTemporary());
	}

	@Test
	public void testMakeFormCurrent() throws InterruptedException {
		form = TestFactory.createAnyForm();
		form.setCurrent(true);
		form.publish();
		AccessControl ac = new AccessControl();
		ac.setGroupPermissionType(PermissionType.WRITE);
		form.setAccessControl(ac);
		assertFalse(form.isTemporary());
		assertTrue(form.isCurrent());
		Thread.sleep(2);
		RSForm form2 = form.copy(new TemporaryCopyLinkedToOriginalCopyPolicy());
		assertTrue(form2.isTemporary());
		assertFalse(form2.isCurrent());
		assertTrue(form2.makeCurrentVersion(form));
		assertTrue(form2.isCurrent());
		assertEquals(FormState.OLD, form.getPublishingState());
		assertFalse(form.isCurrent());
		assertFalse(form2.isTemporary());
		assertEquals(form2.getAccessControl(), form.getAccessControl());
	}

	@Test
	public void testMakeFormCurrentThrowsIAAEIfOldFormNull() {
		form = TestFactory.createAnyForm();
		form.setCurrent(true);
		form.publish();
		assertThrows(IllegalArgumentException.class, ()->form.makeCurrentVersion(null));
	}

	@Test
	public void testMakeFormCurrentRequiresArgumentToBeCurrentForm() throws InterruptedException {
		form = TestFactory.createAnyForm();
		form.setCurrent(false);
		form.publish();
		Thread.sleep(2); // ensure copy has a different creationTime
		RSForm form2 = form.copy(new TemporaryCopyLinkedToOriginalCopyPolicy());
		assertFalse(form2.makeCurrentVersion(form));

		// and vice versa, copy must be a temp field
		form2.setTemporary(false);
		form.setCurrent(true);
		assertFalse(form2.makeCurrentVersion(form));
	}

	@Test()
	public void testMakeFormCurrentThrowsIAAEIfArgumentFormIsThisForm() {
		form = TestFactory.createAnyForm();
		form.setCurrent(true);
		form.publish();
		assertThrows(IllegalArgumentException.class, ()->form.makeCurrentVersion(form));

	}

	@Test
	public void publishingStateTest() {
		form = TestFactory.createAnyForm();
		form.publish();

		//
		assertTrue(form.isPublishedAndVisible());
		assertFalse(form.isPublishedAndHidden());
		assertFalse(form.isNewState());

		form.unpublish();
		assertFalse(form.isPublishedAndVisible());
		assertTrue(form.isPublishedAndHidden());
		assertFalse(form.isNewState());

		// this has no effect, cannot revert to New once published
		form.setPublishingState(FormState.NEW);
		assertTrue(form.isPublishedAndHidden());// same as previous state
	}

	@Test
	public void testForm2() {
		form = TestFactory.createAnyForm();
		assertNotNull(form.getCreationDateAsDate());
		assertNotNull(form.getModificationDate());
		assertEquals("name", form.getName());
		assertEquals("desc", form.getDescription());
		assertEquals("user", form.getCreatedBy());
	}

	@Test
	public void testGetId() {
		assertNull(form.getId()); // check not persisted by default
	}

	@Test
	public void testGetFieldForm() {
		assertNotNull(form.getFieldForms());
	}

	@Test
	public void testCopy() throws InterruptedException, IllegalArgumentException, IllegalAccessException {
		Thread.sleep(1); // ensure copy created after original
		form.setVersion(new Version(1L));
		form.publish();
		form.addFieldForm(FieldTestUtils.createANumberFieldForm());
		RSForm formCopy = form.copy(new CopyIndependentFormAndFieldFormPolicy());
		assertTrue(formCopy.getCreationDateAsDate().after(form.getCreationDateAsDate()));
		assertTrue(formCopy.getModificationDateAsDate().after(form.getModificationDateAsDate()));
		assertEquals(form.getName(), formCopy.getName());
		assertEquals(form.getDescription(), formCopy.getDescription());

		// assert properties of copied template
		assertFalse(formCopy.isPublishedAndVisible());
		assertTrue(formCopy.isNewState());
		// check fields are copied properly as well
		List<Class<? super RSForm>> classesToConsider2 = new ArrayList<>();
		classesToConsider2.add(RSForm.class);

		Set<String> toIgnore2 = ModelTestUtils.generateExclusionFieldsFrom();
		toIgnore2.add("editInfo");
		toIgnore2.add("publishingState");
		toIgnore2.add("version");
		toIgnore2.add("editStatus");
		toIgnore2.add("owner");
		toIgnore2.add("stableID");
		ModelTestUtils.assertCopiedFieldsAreEqual(formCopy, form, toIgnore2, classesToConsider2);

		assertEquals(2, formCopy.getFieldForms().size());
		TextFieldForm origF = (TextFieldForm) form.getFieldForms().get(0);
		TextFieldForm copyF = (TextFieldForm) formCopy.getFieldForms().get(0);

		// check fields are copied properly as well
		List<Class<? super TextFieldForm>> classesToConsider = new ArrayList<>();
		classesToConsider.add(TextFieldForm.class);
		classesToConsider.add(FieldForm.class);
		Set<String> toIgnore = ModelTestUtils.generateExclusionFieldsFrom("form", "modificationDate");
		ModelTestUtils.assertCopiedFieldsAreEqual(copyF, origF, toIgnore, classesToConsider);
	}

	@Test
	public void testGetEditInfo() {
		assertNotNull(form.getEditInfo());
	}

	@Test
	public void testCreationDateIsEncapsulated() {
		Date orig = form.getCreationDateAsDate();
		Date toChange = form.getCreationDateAsDate();

		toChange.setYear(1);
		Date d2 = form.getCreationDateAsDate();
		assertEquals(orig, d2);

	}

	@Test
	public void testModificationDateIsEncapsulated() {
		Date orig = form.getModificationDateAsDate();
		Date toChange = form.getModificationDateAsDate();

		toChange.setYear(1);
		Date d2 = form.getModificationDateAsDate();
		assertEquals(orig, d2);
	}

	@Test
	public void addRemoveFieldForm() {
		FieldForm ft = createAnyFieldForm(1L);
		assertTrue(form.addFieldForm(ft));
		assertEquals(2, form.getFieldForms().size());
		// duplicates not allowwed, can't be added twice
		assertFalse(form.addFieldForm(ft));
		assertEquals(2, form.getFieldForms().size());
		form.removeFieldForm(ft);
		assertEquals(1, form.getFieldForms().size());
	}

	@Test
	public void numFieldsTest() {
		assertEquals(form.getNumAllFields(), form.getFieldForms().size());
	}

	@Test
	public void testErrorLogging() {
		form.setFieldForms(null);
		form.getFieldForms();
	}

	@Test
	public void addFieldFormOrderedByColumnOrder() {
		FieldForm ft = createAnyFieldForm(1L);
		FieldForm ft2 = createAnyFieldForm(2L);
		FieldForm ft3 = createAnyFieldForm(3L);
		ft3.setColumnIndex(2);
		ft2.setColumnIndex(1);
		ft.setColumnIndex(5);
		form.removeFieldForm(form.getFieldForms().get(0));// remove default fieldT

		form.addAllFieldForms(ft, ft2, ft3);
		Iterator<FieldForm> it = form.getFieldForms().iterator();
		// ordered in ascending column index order
		assertEquals(ft2, it.next());
		assertEquals(ft3, it.next());
		assertEquals(ft, it.next());
	}

	@Test
	public void testSortOrder() {
		FieldForm ft = createAnyFieldForm(1L);
		FieldForm ft2 = createAnyFieldForm(2L);
		FieldForm ft3 = createAnyFieldForm(3L);
		ft3.setColumnIndex(2);
		ft2.setColumnIndex(1);
		ft.setColumnIndex(5);
		form.removeFieldForm(form.getFieldForms().get(0));// remove default fieldT

		form.addAllFieldForms(ft, ft2, ft3);
		Iterator<FieldForm> it = form.getFieldForms().iterator();
		// ordered in ascending column index order
		assertEquals(ft2, it.next());
		assertEquals(ft3, it.next());
		assertEquals(ft, it.next());

		// now let's reoder:
		form.reorderFields(TransformerUtils.toList(3L, 1L, 2L));
		Iterator<FieldForm> it2 = form.getFieldForms().iterator();
		// ordered in ascending column index order
		assertEquals(ft3, it2.next());
		assertEquals(ft, it2.next());
		assertEquals(ft2, it2.next());
	}

	@Test
	public void testReorderRequiresSameNumberOfFields() {
		setUpFormWith3Fields();
		// too few args
		assertThrows(IllegalArgumentException.class, ()->form.reorderFields(toList(1L)));
	}

	@Test
	public void testReorderRequiresSameFieldIds() {
		setUpFormWith3Fields();
		// 4L is not in form
		assertThrows(IllegalArgumentException.class, ()->form.reorderFields(toList(1L, 2L, 4L)));
		
	}

	@Test
	public void testReorderRequiresUndeletedFieldIds() {
		setUpFormWith3Fields();
		form.getFieldForms().get(0).setDeleted(true);
		// one is now delted
		assertThrows(IllegalArgumentException.class, ()->form.reorderFields(TransformerUtils.toList(1L,
				2L, 3L)));
	}

	private void setUpFormWith3Fields() {
		FieldForm ft = createAnyFieldForm(1L);
		FieldForm ft2 = createAnyFieldForm(2L);
		FieldForm ft3 = createAnyFieldForm(3L);
		ft3.setColumnIndex(2);
		ft2.setColumnIndex(1);
		ft.setColumnIndex(5);
		form.removeFieldForm(form.getFieldForms().get(0));// remove default fieldT
		form.addAllFieldForms(ft, ft2, ft3);
	}

	private FieldForm createAnyFieldForm(Long id) {
		StringFieldForm ft = new StringFieldForm();
		ft.setIfPassword(false);
		ft.setName("Name");
		ft.setColumnIndex(0);
		ft.setDefaultStringValue("default");
		ft.setId(id);
		return ft;
	}

}
