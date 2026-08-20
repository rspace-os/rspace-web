package com.researchspace.model.permissions;

public enum PermissionType {
	// declare in alphabetical order for sorting; enum natural sort order is its
	// declaration order
	COPY, CREATE, CREATE_FOLDER, DELETE, EXPORT,

	/**
	 * View permission
	 */
	READ,

	/**
	 * Folder can be a target for another record ( i.e., something can be moved
	 * into it.)
	 */
	FOLDER_RECEIVE,

	/**
	 * Record can be moved from current folder location.
	 */
	SEND,
	/**
	 * Share /unshare permissions
	 */
	SHARE,

	/**
	 * Edit/write permission
	 */
	WRITE,

	/**
	 * Explicit statement of no permissions
	 */
	NONE,

	/**
	 * Rename of an entity
	 */
	RENAME,

	/**
	 * // * Permissions to request external sharing of a record, to be used with
	 * {@link PermissionDomain}.GROUP
	 */
	REQUEST_EXTERNAL_SHARE,

	SIGN,
	PUBLISH;
	/**
	 * Gets display name for this permission type
	 * 
	 * @return
	 */
	public String getDisplayName() {
		if (this.equals(PermissionType.WRITE)) {
			return "EDIT";
		} else {
			return name();
		}
	}
}
