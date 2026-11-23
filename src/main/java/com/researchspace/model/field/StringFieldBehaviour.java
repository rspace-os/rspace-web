package com.researchspace.model.field;

public class StringFieldBehaviour {

  public static StringFieldForm realOrProxy(IFieldForm ft) {
    if (ft == null) {
      return null;
    }
    if (ft instanceof StringFieldForm) {
      return (StringFieldForm) ft;
    } else if (AbstractField.isAuditingProxy(ft)) {
      return new StringFieldForm();
    } else throw new IllegalStateException();
  }
}
