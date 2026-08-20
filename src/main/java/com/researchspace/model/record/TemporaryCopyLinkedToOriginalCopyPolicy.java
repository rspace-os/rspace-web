package com.researchspace.model.record;

import com.researchspace.model.field.FieldForm;
import com.researchspace.model.field.IFieldForm;

/**
 * Generates an temporary copy of the Form and its field forms, linked to the
 * original; the original will have getTempForm() return the copy.<br/>
 * This is the only modification made to the original. <br/>
 * The copy will have the same stable ID as the original, and the same owner.
 */
public class TemporaryCopyLinkedToOriginalCopyPolicy implements IFormCopyPolicy<RSForm> {

	public RSForm copy(final RSForm sourceForm) {
		RSForm copy = new RSForm();
		sourceForm.setTempForm(copy);
		copy.setEditInfo(sourceForm.getEditInfo().shallowCopy());
		copy.setStableID(sourceForm.getStableID());
		copy.setTags(sourceForm.getTags());
		copy.setFormType(sourceForm.getFormType());
		for (IFieldForm srcFieldForm : sourceForm.getFieldForms()) {
			FieldForm fieldCpy = srcFieldForm.shallowCopy();
			srcFieldForm.setTempFieldForm(fieldCpy);
			copy.addFieldForm(fieldCpy);
		}
		return copy;
	}

	@Override
	public boolean isKeepOriginalOwnerInCopy() {
		return true;
	}

}
