package com.researchspace.model.record;

/**
 * Provides specific copy behaviour for a form copy operation, depending on the
 * circumstances for which the copy is needed.
 */
public interface IFormCopyPolicy <T extends AbstractForm> {

	/**
	 * Returns a copy of the Form that is passed in as an argument.<br/>
	 * 
	 * @param toCopy,
	 *            a valid {@link AbstractForm} object.
	 * @return The copied {@link AbstractForm}
	 */
	T copy(T toCopy);

	/**
	 * Boolean test as to whether the original owner should be the copy's owner,
	 * or not
	 * 
	 * @return
	 */
	boolean isKeepOriginalOwnerInCopy();

}
