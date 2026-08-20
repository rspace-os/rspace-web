package com.researchspace.model.record;

/**
 * Enum for types of changes made to a document.
 */
public enum DeltaType {
	/**
	 * change of 'name' property
	 */
	RENAME(true),
	/**
	 * Any change to the text of a TextField
	 */
	FIELD_CHG(true),
	/**
	 * Marking record as deleted
	 */
	DELETED(false),
	
	/**
	 * Restoring a revision to be the current record
	 */
	RESTORED(true),

	/**
	 * Adding a new comment item
	 */
	COMMENT(true),

	/**
	 * Editing a sketch
	 */
	SKETCH(true),

	/**
	 * Editing an image annotation
	 */
	IMAGE_ANNOTATION(true),
	/**
	 * This is a special delta type that does *not* increment doc user version,
	 * and does not result in a new entry in the revision history either.
	 * 
	 * See PermanentEntityFilter in rspace-web.
	 */
	NOREVISION_UPDATE(false),

	CREATED_FROM_TEMPLATE(true),
	
	/** 
	 * Gallery file attached to the record was updated 
	 */
	ATTACHMENT_CHG(true),
	
	/**
	 * Undeletion event is like 'restored' but does not trigger modification date
	 */
	UNDELETED (false),
	
	/**
	 * Change to inventory list of materials attached to the field
	 */
	LIST_OF_MATERIALS_CHG(true);

	private boolean incrementVersion;

	/**
	 * Whether this change warrants an increment to the user-version
	 * 
	 * @return
	 */
	boolean isIncrementVersion() {
		return incrementVersion;
	}

	private DeltaType(boolean incrementVersion) {
		this.incrementVersion = incrementVersion;
	}

	void setIncrementVersion(boolean incrementVersion) {
		this.incrementVersion = incrementVersion;
	}
}
