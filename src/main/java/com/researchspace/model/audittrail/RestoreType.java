package com.researchspace.model.audittrail;

/**
 * The type of restoration event
 */
public enum RestoreType {
	/**
	 * Restoring from a deleted state
	 */
	DELETION,

	/**
	 * Restoring from a revision
	 */
	REVISION

}
