package com.researchspace.model.audittrail;

import com.researchspace.model.core.Person;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Audits a duplicate event when an object is copied.
 *
 */
@EqualsAndHashCode(callSuper = false)
public class DuplicateAuditEvent extends RenameAuditEvent {

  private @Getter Map<?,?>originalToCopy;

  public DuplicateAuditEvent(Person subject, Map<?,?> mapOriginalToCopy,
      Object item, String sourceFileName, String duplicatedFileName) {
    super(subject, item, sourceFileName, duplicatedFileName);
    if(mapOriginalToCopy == null){
      throw new IllegalArgumentException("map can't be null");
    }
    this.originalToCopy = mapOriginalToCopy;
  }

  @Override
  public AuditAction getAuditAction() {
    return AuditAction.DUPLICATE;
  }

  protected AuditData getAuditData(Object fromObject, Object target) {
    AuditData top = super.getAuditData(getAuditedObject());
    AuditData from = super.getAuditData(fromObject);
    AuditData to = super.getAuditData(target);
    top.put("from", from);
    top.put("to", to);
    top.put("name", source);
    return top;
  }

  protected AuditData getAuditData(){
    return this.getAuditData(null, null);
  }

  @Override
  void accept(AuditEventVisitor visitor) {
    visitor.visit(this);
  }

}
