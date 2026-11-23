package com.researchspace.model.field;

public class ReferenceFieldBehaviour {

  public static ReferenceFieldForm realOrProxy(IFieldForm ft) {
    if (ft == null) {
      return null;
    }
    if (ft instanceof ReferenceFieldForm) {
      return (ReferenceFieldForm) ft;
    } else if (AbstractField.isAuditingProxy(ft)) {
      return new ReferenceFieldForm();
    } else throw new IllegalStateException();
  }
}
