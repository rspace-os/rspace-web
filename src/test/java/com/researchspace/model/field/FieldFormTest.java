package com.researchspace.model.field;

import static org.junit.Assert.assertEquals;
import org.junit.jupiter.api.Test;



public class FieldFormTest {


	@Test
	public void getSummaryDoesNotThrowNPEForUnspecifiedValuesTest() {
		FieldForm[] fts = createArrayOfEmptyFieldForms();

		for (FieldForm ft : fts) {
			ft.getSummary();
		}
	}

	private FieldForm[] createArrayOfEmptyFieldForms() {
		FieldForm[] fts = new FieldForm[8];

		fts[0] = new ChoiceFieldForm();
		fts[1] = new DateFieldForm();
		fts[2] = new NumberFieldForm();
		fts[3] = new RadioFieldForm();
		fts[4] = new TextFieldForm();
		fts[5] = new TimeFieldForm();
		fts[6] = new StringFieldForm();
		fts[7] = new ReferenceFieldForm();
		return fts;
	}

	@Test
	public void testEqualsHashCodeConsistentWithComparable() {

		FieldForm[] fts1 = createArrayOfEmptyFieldForms();
		FieldForm[] fts2 = createArrayOfEmptyFieldForms();
		for (int i = 0; i < fts1.length; i++) {
			// ensure modification dates are the same
			fts2[i].setModificationDate(fts1[i].getModificationDate());
			assertEquals(fts1[i], fts2[i]);
			assertEquals(0, fts1[i].compareTo(fts2[i]));
			assertEquals(fts1[i].hashCode(), fts2[i].hashCode());
		}

	}
}
