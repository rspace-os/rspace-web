package com.researchspace.model.audittrail;

import com.researchspace.core.util.IPagination;
import com.researchspace.model.core.Person;

/**
 * Audit event representing search or listing activities
 */
public class AuditSearchEvent extends HistoricalEvent {

	private IPagination<?> pgCrit;

	public AuditSearchEvent(Person subject, Object searchConfig,  IPagination<?> pgCrit) {
		super(subject,searchConfig,null);
		if (pgCrit == null) {
			throw new IllegalArgumentException("Pagination criteria can't be null");
		}
		this.pgCrit = pgCrit;
	}

	@Override
	public AuditAction getAuditAction() {
		return AuditAction.SEARCH;
	}

	@Override
	void accept(AuditEventVisitor visitor) {
		visitor.visit(this);

	}

	IPagination<?> getPgCrit() {
		return pgCrit;
	}
}
