package com.researchspace.model.audittrail;

import com.researchspace.model.core.Person;

/** Audit event for unsharing a record, keeping the removed sharing's details. */
public class UnshareRecordAuditEvent extends GenericEvent {
  private final Object sharing;

  public UnshareRecordAuditEvent(Person subject, Object unsharedRecord, Object sharing) {
    super(subject, unsharedRecord, AuditAction.UNSHARE);
    this.sharing = sharing;
  }

  @Override
  protected AuditData getAuditData() {
    AuditData data = getAuditData(sharing);
    // The record's fields win, so "id" links the event to the unshared record.
    data.getData().putAll(super.getAuditData().getData());
    return data;
  }
}
