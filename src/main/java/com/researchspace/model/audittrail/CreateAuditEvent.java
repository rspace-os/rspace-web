package com.researchspace.model.audittrail;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import com.researchspace.model.core.Person;

/**
 * Audits a duplicate event when an object is copied.
 *
 */
public class CreateAuditEvent extends RenameAuditEvent {

  public CreateAuditEvent(Person subject, Object auditedObject, String newName) {
    super(subject, auditedObject, EMPTY, newName, null);
  }

  @Override
  public AuditAction getAuditAction() {
    return AuditAction.CREATE;
  }

  @Override
  protected AuditData getAuditData() {
    AuditData top = getAuditData(getAuditedObject());
    top.put("name", destination);
    return top;
  }

  @Override
  void accept(AuditEventVisitor visitor) {
    visitor.visit(this);
  }

}
