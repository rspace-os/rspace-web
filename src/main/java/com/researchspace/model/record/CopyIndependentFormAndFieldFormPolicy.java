package com.researchspace.model.record;

import com.researchspace.model.field.IFieldForm;


/**
 * Generates an independent copy of the Form and its field forms, with new
 * creation and modification dates.<br/>
 * No modification is made to the original.<br/>
 * This copy will have a new stable Id, and the owner of the copy will be the
 * user performing the copy, <b>NOT</b> the owner of the original.
 */
public class CopyIndependentFormAndFieldFormPolicy implements IFormCopyPolicy<RSForm> {

	public RSForm copy(RSForm srcForm) {
		RSForm copy = new RSForm();
		copy.setEditInfo(srcForm.getEditInfo().shallowCopy());
		copy.setTags(srcForm.getTags());
		for (IFieldForm srcFieldForm : srcForm.getFieldForms()) {
			copy.addFieldForm(srcFieldForm.shallowCopy());
		}
		copy.setCurrent(true);
		copy.setFormType(srcForm.getFormType());
		return copy;
	}

	@Override
	public boolean isKeepOriginalOwnerInCopy() {
		return false;
	}

}
