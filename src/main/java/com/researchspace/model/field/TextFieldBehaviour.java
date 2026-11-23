package com.researchspace.model.field;

public class TextFieldBehaviour {

  public static TextFieldForm realOrProxy(IFieldForm ft) {
    if (ft == null) {
      return null;
    }
    if (ft instanceof TextFieldForm) {
      return (TextFieldForm) ft;
    } else if (AbstractField.isAuditingProxy(ft)) {
      return new TextFieldForm();
    } else throw new IllegalStateException();
  }
}
