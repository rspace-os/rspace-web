package com.researchspace.model.audittrail;

import com.researchspace.model.core.Person;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

/**
 * Audits a rename event when an object is copied.
 */
@EqualsAndHashCode(callSuper = false)
public class RenameAuditEvent extends MoveAuditEvent {

  public RenameAuditEvent(Person subject, Object item, String oldName, String newName) {
    super(subject, item, oldName, newName);
    super.description = buildDescriptionFromParameters();
  }

  public RenameAuditEvent(Person subject, Object item, String oldName, String newName,
      String desc) {
    super(subject, item, oldName, newName, desc);
  }

  @Override
  public AuditAction getAuditAction() {
    return AuditAction.RENAME;
  }

  protected AuditData getAuditData() {
    AuditData top = getAuditData(getAuditedObject());
    top.put("from", source);
    top.put("to", destination);
    return top;
  }

  @Override
  void accept(AuditEventVisitor visitor) {
    visitor.visit(this);
  }

  private String buildDescriptionFromParameters() {
    StringBuilder desc = new StringBuilder();
    desc.append("from: \"");
    if (StringUtils.isNotBlank(source.toString())) {
      desc.append(source.toString());
    }
    desc.append("\" to: \"");
    if (StringUtils.isNotBlank(destination.toString())) {
      desc.append(destination.toString());
    }
    desc.append("\"");
    return desc.toString();
  }

}
