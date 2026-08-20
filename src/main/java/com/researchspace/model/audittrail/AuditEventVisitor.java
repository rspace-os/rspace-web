package com.researchspace.model.audittrail;

import java.util.List;

/**
 * Base class for AuditEvent visitors. Subclasses can optionally override
 * specific visitXXX methods - in this class, these methods are stubbed.
 */
public abstract class AuditEventVisitor {

	/**
	 * Visits an AuditSearchEvent
	 *
   */
	public void visit(AuditSearchEvent searchEvent) {
	}


	abstract List<HistoricData> getHistoryData();

	/*
	 * Helper method for validating event objects
	 */
	void validateArgs(HistoricalEvent event) {
		if (event.getAuditAction() == null) {
			throw new IllegalArgumentException("Audit action cannot be null in an event");
		}
	}

	public void visit(SigningEvent signingEvent) {}

	public void visit(RestoreEvent restoreEvent) {}

	public void visit(GenericEvent genericHistoricalEvent) {}

	public void visit(ShareRecordAuditEvent shareRecordAuditEvent) {}
	
	public void visit(MoveAuditEvent shareRecordAuditEvent) {}

	public void visit(RenameAuditEvent renameAuditEvent) {}

	public void visit(DuplicateAuditEvent duplicateAuditEvent) {}

	public void visit(CreateAuditEvent createAuditEvent) {}

}
