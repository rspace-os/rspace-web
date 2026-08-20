package com.researchspace.model.audittrail;

import com.researchspace.model.core.Person;

/**
 * Audit event for an object being restored from a deleted state, or previous
 * revision to a current state
 */
public class RestoreEvent extends HistoricalEvent {

	private RestoreType restoreType;
	private int revision;
	

	/**
	 * Constructor for a restoration from deleted state
	 * 
	 * @param restored
	 *            The deleted object being restored
	 */
	public RestoreEvent(Person subject, Object restored) {
		super(subject,restored);
	
		this.restoreType = RestoreType.DELETION;

	}

	/**
	 * Constructor for a restoration from a previous revision
	 * 
	 * @param restored
	 *            The deleted object being restored
	 * @param revision
	 *            the revision number being restored from.
	 */
	public RestoreEvent(Person subject, Object restored, int revision) {
		super(subject,restored);
		this.restoreType = RestoreType.REVISION;
		this.revision = revision;
	}

	@Override
	public AuditAction getAuditAction() {
		return AuditAction.RESTORE;
	}

	@Override
	void accept(AuditEventVisitor visitor) {
		visitor.visit(this);
	}

	public RestoreType getRestoreType() {
		return restoreType;
	}

	public int getRevision() {
		return revision;
	}

	

}
