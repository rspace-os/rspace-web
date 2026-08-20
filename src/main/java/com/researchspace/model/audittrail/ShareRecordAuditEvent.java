package com.researchspace.model.audittrail;

import com.researchspace.model.core.Person;

/**
 * Audit event for sharing records
 */
public class ShareRecordAuditEvent extends HistoricalEvent {
	Object[] config;

	public ShareRecordAuditEvent(Person sharer, Object auditedObject, Object[] groupShareConfigElements) {
		super(sharer, auditedObject);
		this.config = groupShareConfigElements;
	}

	public Object[] getConfig() {
		return config;
	}

	@Override
	public AuditAction getAuditAction() {
		return AuditAction.SHARE;
	}

	@Override
	void accept(AuditEventVisitor visitor) {
		visitor.visit(this);
	}
	
	protected AuditData getAuditData() {
		AuditData data =  super.getAuditData();
		data.put("sharing", config);
		return data;
	}

}
