package com.researchspace.model.field;

import com.researchspace.model.record.AbstractForm;

/**
 * A definition of read-only methods on a FieldForm, which can be publicly
 * accessed. <br/>
 * See {@link FieldForm} for more detail on these methods.
 */
public interface IFieldForm {

	String getSummary();

	/**
	 * This method validates input that will be stored in the data's field.
	 * 
	 * @param data
	 * @return An {@link ErrorList} which has validated successfully if it has no
	 *         messages contained in it.
	 */
	ErrorList validate(String data);

	FieldForm shallowCopy();

	boolean isTemporary();

	boolean isMandatory();

	IFieldForm getTempFieldForm();

	AbstractForm getForm();

	Field createNewFieldFromForm();

	String getDefault();

	String getName();

	int getColumnIndex();

	Long getModificationDate();

	FieldType getType();

	/**
	 * Persistence ID
	 * 
	 * @return
	 */
	Long getId();

	/**
	 * 
	 * @param template
	 */
	void setForm(AbstractForm template);

	void setTempFieldForm(IFieldForm fieldCpy);

	void setTemporary(boolean b);

}
