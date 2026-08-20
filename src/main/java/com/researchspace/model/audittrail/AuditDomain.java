package com.researchspace.model.audittrail;

/**
 * The type of entity that has been accessed by the operation to be audited.
 */
public enum AuditDomain {
	MESSAGING,
	RECORD, FOLDER, NOTEBOOK, MEDIA, USER, GROUP, COMMUNITY, FORM, AUDIT, 
	/** 
	 * Inventory domains 
	 */
	INV_SAMPLE, INV_SUBSAMPLE, INV_CONTAINER, INV_INSTRUMENT,
	/**
	 * A general term for workspace/ all resources
	 */
	WORKSPACE,
	/**
	 * Default fall-through domain
	 */
	UNKNOWN
}
