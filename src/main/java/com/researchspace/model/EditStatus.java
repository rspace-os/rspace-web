package com.researchspace.model;

/**
 * Defines result of the edit-permission status for display in UI
 */
public enum EditStatus {

	/**
	 * No read or edit access allowed
	 */
	ACCESS_DENIED(false),

	/**
	 * User is allowed to start editing the document, if they want
	 */
	VIEW_MODE(true),

	/**
	 * Document is currently edited by the user (user has edit lock)
	 */
	EDIT_MODE(true),

	/**
	 * User has edit permission, but Document is edited by someone else
	 */
	CANNOT_EDIT_OTHER_EDITING(false),

	/**
	 * User can view the document, but has no permission to edit it
	 */
	CANNOT_EDIT_NO_PERMISSION(false),

	/**
	 * An intrinsic property of the document makes it never editable, regardless
	 * of permissions (for example the document is signed, or is an old
	 * revision).
	 */
	CAN_NEVER_EDIT(false);

	private boolean canEdit;

	EditStatus(boolean canEdit) {
		this.canEdit = canEdit;
	}

	/**
	 * Boolean test as to whether an {@link EditStatus} represents an editable
	 * document or not.
	 * 
	 * @return
	 */
	public boolean isEditable() {
		return canEdit;
	}

}
