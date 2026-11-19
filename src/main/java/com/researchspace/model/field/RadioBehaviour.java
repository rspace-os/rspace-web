package com.researchspace.model.field;

import com.researchspace.model.field.rule.ElnChoiceValues;
import java.util.List;

public class RadioBehaviour {

  public static RadioFieldForm realOrProxy(IFieldForm ft) {
    if (ft instanceof RadioFieldForm) {
      return (RadioFieldForm) ft;
    } else if (AbstractField.isAuditingProxy(ft)) {
      return new RadioFieldForm();
    } else throw new IllegalStateException();
  }

  /** Radio options stored as {@code name=a&name=b}; see {@link ElnChoiceValues#parse}. */
  public List<String> getAsList(String value) {
    return ElnChoiceValues.parse(value);
  }
}
